package com.harrison.todotasks.ui.tasks

import com.harrison.todotasks.data.model.TodoTask

/** What the task list screen shows. */
sealed interface TaskListUiState {

    /** The tasks haven't been read yet. */
    data object Loading : TaskListUiState

    /**
     * The tasks, which may be empty.
     *
     * @property tasks The tasks to show, in display order.
     * @property message A message to show once, or null if there is none. The UI calls
     * [TaskListViewModel.onMessageShown] after showing it.
     */
    data class Content(
        val tasks: List<TodoTask>,
        val message: TaskListMessage? = null,
    ) : TaskListUiState

    /** The tasks couldn't be read. */
    data object Error : TaskListUiState
}

/** A message the task list shows once, such as in a snackbar. */
enum class TaskListMessage {
    /** Completing or uncompleting a task couldn't be saved. */
    CompleteChangeFailed,
}
