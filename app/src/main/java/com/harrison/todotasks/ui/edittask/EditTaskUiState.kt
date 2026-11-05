package com.harrison.todotasks.ui.edittask

/** What the add or edit task screen shows. */
sealed interface EditTaskUiState {

    /** The task being edited hasn't been read yet. Adding a task never loads. */
    data object Loading : EditTaskUiState

    /**
     * The form, seeded with the task being edited, or empty when adding one.
     *
     * @property dueAt The due date in epoch milliseconds, or null if there is none.
     * @property isNewTask Whether a task is being added rather than edited. Only an existing task
     * can be deleted.
     * @property message A message to show once, or null if there is none. The UI calls
     * [EditTaskViewModel.onMessageShown] after showing it.
     */
    data class Editing(
        val title: String,
        val description: String,
        val dueAt: Long?,
        val isNewTask: Boolean,
        val message: EditTaskMessage? = null,
    ) : EditTaskUiState {

        /** Whether the form can be saved. */
        val canSave: Boolean
            get() = title.isNotBlank()
    }

    /** The task being edited doesn't exist or was deleted, such as by a sync. */
    data object NotFound : EditTaskUiState

    /** The task being edited couldn't be read. */
    data object Error : EditTaskUiState

    /** The task was saved or deleted, so the UI should leave the screen. */
    data object Finished : EditTaskUiState
}

/** A message the add or edit task screen shows once, such as in a snackbar. */
enum class EditTaskMessage {
    /** The task couldn't be saved. The form keeps what was typed. */
    SaveFailed,

    /** The task couldn't be deleted. */
    DeleteFailed,
}
