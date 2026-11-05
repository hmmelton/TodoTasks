package com.harrison.todotasks.ui.tasks

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.SQLiteException
import com.harrison.todotasks.data.repository.TodoTasksRepository
import com.harrison.todotasks.data.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "TaskListViewModel"

/**
 * Holds the state of the task list screen, which shows every task that isn't deleted and lets
 * the user complete tasks and sync them on demand.
 *
 * The list is read from [TodoTasksRepository], so it updates as tasks are edited locally or
 * synced. Syncs are requested from, and observed on, [SyncScheduler] directly, because the
 * repository would only pass the calls through. A failed read replaces the list with
 * [TaskListUiState.Error]. A failed write keeps the list and shows
 * [TaskListMessage.CompleteChangeFailed].
 */
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val repository: TodoTasksRepository,
    private val syncScheduler: SyncScheduler,
) : ViewModel() {

    private val message = MutableStateFlow<TaskListMessage?>(null)

    /** The state of the screen, which starts as [TaskListUiState.Loading]. */
    val uiState: StateFlow<TaskListUiState> =
        combine<_, _, _, TaskListUiState>(
            repository.observeTasks(),
            message,
            syncScheduler.isSyncing,
            TaskListUiState::Content,
        )
            .catch { e ->
                Log.e(TAG, "Failed to read tasks", e)
                emit(TaskListUiState.Error)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = TaskListUiState.Loading,
            )

    /**
     * Marks the task with [id] complete or incomplete. If the task was deleted meanwhile, such as
     * by a sync, nothing happens, and it drops out of the list.
     */
    fun onCompleteChange(id: String, isComplete: Boolean) {
        viewModelScope.launch {
            try {
                repository.setComplete(id, isComplete)
            } catch (e: SQLiteException) {
                Log.e(TAG, "Failed to set task $id complete to $isComplete", e)
                message.value = TaskListMessage.CompleteChangeFailed
            }
        }
    }

    /**
     * Syncs tasks with the server now, such as when the user pulls to refresh. Does nothing more if
     * a sync is already running. [TaskListUiState.Content.isSyncing] shows when one runs.
     */
    fun onRefresh() {
        viewModelScope.launch { syncScheduler.syncNow() }
    }

    /** Clears the message the UI has just shown. */
    fun onMessageShown() {
        message.value = null
    }
}
