package com.harrison.todotasks.ui.edittask

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import androidx.sqlite.SQLiteException
import com.harrison.todotasks.data.model.TodoTask
import com.harrison.todotasks.data.repository.TodoTasksRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

private const val TAG = "EditTaskViewModel"

private const val DRAFT_KEY = "draft"

/** The unsaved form fields, kept in a [SavedStateHandle] so they survive process death. */
@Serializable
private data class EditTaskDraft(
    val title: String,
    val description: String,
    val dueAt: Long?,
)

/**
 * Holds the state of the screen that adds a task, when [taskId] is null, or edits the task with
 * [taskId].
 *
 * An existing task is read once to seed the form. Later changes to it, such as from a sync, don't
 * replace the form, and saving writes over them.
 *
 * Each field change is kept in [savedStateHandle] as a draft. A restored draft seeds the form
 * instead of reading the task again; an unchanged form has no draft, so the task is re-read.
 * Only one save or delete runs at a time.
 */
@HiltViewModel(assistedFactory = EditTaskViewModel.Factory::class)
class EditTaskViewModel @AssistedInject constructor(
    @Assisted private val taskId: String?,
    private val savedStateHandle: SavedStateHandle,
    private val repository: TodoTasksRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<EditTaskUiState>(
        savedStateHandle.get<SavedState>(DRAFT_KEY)
            ?.let { formOf(decodeFromSavedState<EditTaskDraft>(it)) }
            ?: if (taskId == null) newTaskForm() else EditTaskUiState.Loading,
    )

    /** The state of the screen. */
    val uiState: StateFlow<EditTaskUiState> = _uiState.asStateFlow()

    /** The save or delete in progress, if any. */
    private var write: Job? = null

    init {
        if (taskId != null && _uiState.value is EditTaskUiState.Loading) {
            viewModelScope.launch { _uiState.value = load(taskId) }
        }
    }

    /** Replaces the title being edited. */
    fun onTitleChange(title: String) {
        editForm { it.copy(title = title) }
    }

    /** Replaces the description being edited. */
    fun onDescriptionChange(description: String) {
        editForm { it.copy(description = description) }
    }

    /** Replaces the due date being edited, in epoch milliseconds, or clears it if null. */
    fun onDueAtChange(dueAt: Long?) {
        editForm { it.copy(dueAt = dueAt) }
    }

    /**
     * Saves the form, creating or updating the task, then finishes. Does nothing unless
     * [EditTaskUiState.Editing.canSave] and no other write is in progress.
     */
    fun onSave() {
        val form = _uiState.value as? EditTaskUiState.Editing ?: return
        if (!form.canSave) return
        startWrite(failure = EditTaskMessage.SaveFailed) { save(form) }
    }

    /**
     * Deletes the edited task, then finishes. Does nothing when adding a task, or while another
     * write is in progress.
     */
    fun onDelete() {
        val id = taskId ?: return
        if (_uiState.value !is EditTaskUiState.Editing) return
        startWrite(failure = EditTaskMessage.DeleteFailed) {
            repository.deleteTask(id)
            EditTaskUiState.Finished
        }
    }

    /** Clears the message the UI has just shown. */
    fun onMessageShown() {
        updateForm { it.copy(message = null) }
    }

    /** Reads the task with [id] once and returns the state to show for it. */
    private suspend fun load(id: String): EditTaskUiState =
        repository.observeTask(id)
            .map { task -> task?.let(::formOf) ?: EditTaskUiState.NotFound }
            .catch { e ->
                Log.e(TAG, "Failed to read task $id", e)
                emit(EditTaskUiState.Error)
            }
            .first()

    /** Writes [form] and returns the state to show after. */
    private suspend fun save(form: EditTaskUiState.Editing): EditTaskUiState {
        if (taskId == null) {
            val id = repository.createTask(form.title, form.description, form.dueAt)
            // createTask only rejects a blank title, which canSave already rules out.
            return if (id != null) EditTaskUiState.Finished else form
        }
        val saved = repository.updateTask(taskId, form.title, form.description, form.dueAt)
        return if (saved) EditTaskUiState.Finished else EditTaskUiState.NotFound
    }

    /**
     * Runs [block] unless another write is in progress, and shows the state it returns. If it
     * throws a [SQLiteException], the form stays and shows [failure] instead.
     */
    private fun startWrite(failure: EditTaskMessage, block: suspend () -> EditTaskUiState) {
        if (write?.isActive == true) return
        write = viewModelScope.launch {
            try {
                _uiState.value = block()
            } catch (e: SQLiteException) {
                Log.e(TAG, "Failed to write ${taskId ?: "a new task"}", e)
                updateForm { it.copy(message = failure) }
            }
        }
    }

    /** Applies [transform] to the form, if the form is showing. */
    private fun updateForm(transform: (EditTaskUiState.Editing) -> EditTaskUiState.Editing) {
        _uiState.update { state ->
            if (state is EditTaskUiState.Editing) transform(state) else state
        }
    }

    /** Applies the user's change [transform] to the form, if it's showing, and saves the draft. */
    private fun editForm(transform: (EditTaskUiState.Editing) -> EditTaskUiState.Editing) {
        updateForm(transform)
        val form = _uiState.value as? EditTaskUiState.Editing ?: return
        savedStateHandle[DRAFT_KEY] =
            encodeToSavedState(EditTaskDraft(form.title, form.description, form.dueAt))
    }

    private fun newTaskForm(): EditTaskUiState.Editing = EditTaskUiState.Editing(
        title = "",
        description = "",
        dueAt = null,
        isNewTask = true,
    )

    private fun formOf(task: TodoTask): EditTaskUiState.Editing = EditTaskUiState.Editing(
        title = task.title,
        description = task.description,
        dueAt = task.dueAt,
        isNewTask = false,
    )

    private fun formOf(draft: EditTaskDraft): EditTaskUiState.Editing = EditTaskUiState.Editing(
        title = draft.title,
        description = draft.description,
        dueAt = draft.dueAt,
        isNewTask = taskId == null,
    )

    /** Creates an [EditTaskViewModel] for the task ID in the screen's navigation key. */
    @AssistedFactory
    interface Factory {

        /** Creates the ViewModel for [taskId], or for a new task if it's null. */
        fun create(taskId: String?): EditTaskViewModel
    }
}
