package com.harrison.todotasks.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.harrison.todotasks.ui.edittask.EditTaskScreen
import com.harrison.todotasks.ui.edittask.EditTaskViewModel
import com.harrison.todotasks.ui.tasks.TaskListScreen
import kotlinx.serialization.Serializable

/** The task list, which is the start of the back stack. */
@Serializable
data object TaskList : NavKey

/** The form that adds a task, when [taskId] is null, or edits the task with [taskId]. */
@Serializable
data class EditTask(val taskId: String?) : NavKey

/**
 * The app's screens, starting on [TaskList]. Each entry has its own saved state and ViewModels,
 * which are cleared when it's popped.
 */
@Composable
fun TodoTasksNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(TaskList)

    // Ignores a second tap while the form is already on top.
    fun openEditTask(taskId: String?) {
        if (backStack.lastOrNull() !is EditTask) backStack.add(EditTask(taskId))
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<TaskList> {
                TaskListScreen(
                    onTaskClick = { id -> openEditTask(id) },
                    onAddTask = { openEditTask(null) },
                )
            }
            entry<EditTask> { key ->
                val viewModel = hiltViewModel<EditTaskViewModel, EditTaskViewModel.Factory>(
                    creationCallback = { factory -> factory.create(key.taskId) },
                )
                // Removes this entry, not the top one, so finishing after the user has already
                // navigated back can't pop the task list.
                EditTaskScreen(viewModel = viewModel, onBack = { backStack.remove(key) })
            }
        },
    )
}
