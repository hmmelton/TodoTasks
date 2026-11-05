package com.harrison.todotasks.ui.edittask

import androidx.sqlite.SQLiteException
import com.harrison.todotasks.data.local.sparseTask
import com.harrison.todotasks.data.repository.TodoTasksRepository
import com.harrison.todotasks.ui.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Checks saving and deleting from the add or edit form, and how each outcome is shown. */
// Robolectric provides android.util.Log and the SQLiteException the repository throws.
@RunWith(RobolectricTestRunner::class)
class EditTaskViewModelWriteTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<TodoTasksRepository>()

    private val task = sparseTask.copy(title = "Title", description = "Details", dueAt = 4L)

    @Test
    fun `saving a new task creates it and finishes`() = runTest {
        coEvery { repository.createTask(any(), any(), any()) } returns "new-id"
        val viewModel = newTaskViewModel(title = "New")

        viewModel.onSave()

        coVerify(exactly = 1) { repository.createTask("New", "", null) }
        assertEquals(EditTaskUiState.Finished, viewModel.uiState.value)
    }

    @Test
    fun `saving an edited task updates it and finishes`() = runTest {
        coEvery { repository.updateTask(any(), any(), any(), any()) } returns true
        val viewModel = editTaskViewModel()
        viewModel.onTitleChange("Edited")

        viewModel.onSave()

        coVerify(exactly = 1) { repository.updateTask(task.id, "Edited", "Details", 4L) }
        assertEquals(EditTaskUiState.Finished, viewModel.uiState.value)
    }

    @Test
    fun `saving a task deleted meanwhile shows not found`() = runTest {
        coEvery { repository.updateTask(any(), any(), any(), any()) } returns false
        val viewModel = editTaskViewModel()

        viewModel.onSave()

        assertEquals(EditTaskUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun `saving a blank title writes nothing`() = runTest {
        val viewModel = newTaskViewModel(title = " ")

        viewModel.onSave()

        coVerify(exactly = 0) { repository.createTask(any(), any(), any()) }
    }

    @Test
    fun `a failed save keeps the form and shows a message until it is shown`() = runTest {
        coEvery { repository.createTask(any(), any(), any()) } throws SQLiteException("Disk full")
        val viewModel = newTaskViewModel(title = "New")
        val form = viewModel.uiState.value as EditTaskUiState.Editing

        viewModel.onSave()
        assertEquals(form.copy(message = EditTaskMessage.SaveFailed), viewModel.uiState.value)

        viewModel.onMessageShown()
        assertEquals(form, viewModel.uiState.value)
    }

    @Test
    fun `a second save while the first is running is ignored`() = runTest {
        val created = CompletableDeferred<String?>()
        coEvery { repository.createTask(any(), any(), any()) } coAnswers { created.await() }
        val viewModel = newTaskViewModel(title = "New")

        viewModel.onSave()
        viewModel.onSave()
        created.complete("new-id")
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.createTask(any(), any(), any()) }
        assertEquals(EditTaskUiState.Finished, viewModel.uiState.value)
    }

    @Test
    fun `deleting an edited task deletes it and finishes`() = runTest {
        coEvery { repository.deleteTask(any()) } returns Unit
        val viewModel = editTaskViewModel()

        viewModel.onDelete()

        coVerify(exactly = 1) { repository.deleteTask(task.id) }
        assertEquals(EditTaskUiState.Finished, viewModel.uiState.value)
    }

    @Test
    fun `a failed delete keeps the form and shows a message`() = runTest {
        coEvery { repository.deleteTask(any()) } throws SQLiteException("Disk full")
        val viewModel = editTaskViewModel()
        val form = viewModel.uiState.value as EditTaskUiState.Editing

        viewModel.onDelete()

        assertEquals(form.copy(message = EditTaskMessage.DeleteFailed), viewModel.uiState.value)
    }

    @Test
    fun `deleting while adding a task does nothing`() = runTest {
        val viewModel = newTaskViewModel(title = "New")

        viewModel.onDelete()

        coVerify(exactly = 0) { repository.deleteTask(any()) }
    }

    /** Creates a ViewModel adding a task, with [title] typed in. */
    private fun newTaskViewModel(title: String): EditTaskViewModel =
        EditTaskViewModel(taskId = null, repository = repository).apply { onTitleChange(title) }

    /** Creates a ViewModel editing [task], with the form already seeded. */
    private fun editTaskViewModel(): EditTaskViewModel {
        every { repository.observeTask(task.id) } returns flowOf(task)
        return EditTaskViewModel(task.id, repository)
    }
}
