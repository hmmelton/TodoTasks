package com.harrison.todotasks.ui.edittask

import androidx.sqlite.SQLiteException
import app.cash.turbine.test
import com.harrison.todotasks.data.local.sparseTask
import com.harrison.todotasks.data.model.TodoTask
import com.harrison.todotasks.data.repository.TodoTasksRepository
import com.harrison.todotasks.ui.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Checks how the add or edit form is seeded and edited, before anything is saved. */
// Robolectric provides android.util.Log and the SQLiteException the repository throws.
@RunWith(RobolectricTestRunner::class)
class EditTaskViewModelLoadTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<TodoTasksRepository>()

    private val task = sparseTask.copy(title = "Title", description = "Details", dueAt = 4L)

    @Test
    fun `adding a task starts with an empty form and reads nothing`() = runTest {
        val viewModel = EditTaskViewModel(taskId = null, repository = repository)

        assertEquals(
            EditTaskUiState.Editing(title = "", description = "", dueAt = null, isNewTask = true),
            viewModel.uiState.value,
        )
        verify(exactly = 0) { repository.observeTask(any()) }
    }

    @Test
    fun `editing a task loads, then seeds the form from it`() = runTest {
        val observed = MutableSharedFlow<TodoTask?>()
        every { repository.observeTask(task.id) } returns observed

        EditTaskViewModel(task.id, repository).uiState.test {
            assertEquals(EditTaskUiState.Loading, awaitItem())

            observed.emit(task)
            assertEquals(
                EditTaskUiState.Editing(
                    title = "Title",
                    description = "Details",
                    dueAt = 4L,
                    isNewTask = false,
                ),
                awaitItem(),
            )
        }
    }

    @Test
    fun `later changes to the task don't replace the form`() = runTest {
        val observed = MutableSharedFlow<TodoTask?>(replay = 1)
        observed.emit(task)
        every { repository.observeTask(task.id) } returns observed
        val viewModel = EditTaskViewModel(task.id, repository)
        viewModel.onTitleChange("Typed")

        observed.emit(task.copy(title = "Synced"))

        assertEquals("Typed", (viewModel.uiState.value as EditTaskUiState.Editing).title)
    }

    @Test
    fun `editing a missing or deleted task shows not found`() = runTest {
        every { repository.observeTask(task.id) } returns flowOf(null)

        val viewModel = EditTaskViewModel(task.id, repository)

        assertEquals(EditTaskUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun `a failed read shows the error state`() = runTest {
        every { repository.observeTask(task.id) } returns flow {
            throw SQLiteException("Disk I/O error")
        }

        val viewModel = EditTaskViewModel(task.id, repository)

        assertEquals(EditTaskUiState.Error, viewModel.uiState.value)
    }

    @Test
    fun `field changes update the form`() = runTest {
        val viewModel = EditTaskViewModel(taskId = null, repository = repository)

        viewModel.onTitleChange("Title")
        viewModel.onDescriptionChange("Details")
        viewModel.onDueAtChange(4L)

        assertEquals(
            EditTaskUiState.Editing(
                title = "Title",
                description = "Details",
                dueAt = 4L,
                isNewTask = true,
            ),
            viewModel.uiState.value,
        )

        viewModel.onDueAtChange(null)
        assertEquals(null, (viewModel.uiState.value as EditTaskUiState.Editing).dueAt)
    }

    @Test
    fun `can save only with a title that isn't blank`() {
        val form = EditTaskUiState.Editing(
            title = "",
            description = "",
            dueAt = null,
            isNewTask = true,
        )

        assertFalse(form.canSave)
        assertFalse(form.copy(title = "  ").canSave)
        assertTrue(form.copy(title = "Title").canSave)
    }

    @Test
    fun `field changes are ignored until the form is showing`() = runTest {
        every { repository.observeTask(task.id) } returns MutableSharedFlow()
        val viewModel = EditTaskViewModel(task.id, repository)

        viewModel.onTitleChange("Typed")

        assertEquals(EditTaskUiState.Loading, viewModel.uiState.value)
    }
}
