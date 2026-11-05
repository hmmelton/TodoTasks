package com.harrison.todotasks.ui.tasks

import androidx.sqlite.SQLiteException
import app.cash.turbine.test
import com.harrison.todotasks.data.local.fullTask
import com.harrison.todotasks.data.model.TodoTask
import com.harrison.todotasks.data.repository.TodoTasksRepository
import com.harrison.todotasks.data.sync.SyncScheduler
import com.harrison.todotasks.ui.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks the task list's states and events against a mock repository and sync scheduler.
 *
 * Main is unconfined, so a flow that emits without suspending replaces [TaskListUiState.Loading]
 * before Turbine sees it. The loading test emits from a [MutableSharedFlow] instead.
 */
// Robolectric provides android.util.Log and the SQLiteException the repository throws.
@RunWith(RobolectricTestRunner::class)
class TaskListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mockk<TodoTasksRepository>()

    private val isSyncing = MutableStateFlow(false)
    private val syncScheduler = mockk<SyncScheduler>(relaxUnitFun = true) {
        every { isSyncing } returns this@TaskListViewModelTest.isSyncing
    }

    private val task = fullTask.copy(deletedAt = null)

    @Test
    fun `starts loading, then shows the tasks as they are read`() = runTest {
        val tasks = MutableSharedFlow<List<TodoTask>>()
        every { repository.observeTasks() } returns tasks

        TaskListViewModel(repository, syncScheduler).uiState.test {
            assertEquals(TaskListUiState.Loading, awaitItem())

            tasks.emit(listOf(task))
            assertEquals(TaskListUiState.Content(tasks = listOf(task)), awaitItem())

            tasks.emit(emptyList())
            assertEquals(TaskListUiState.Content(tasks = emptyList()), awaitItem())
        }
    }

    @Test
    fun `a failed read shows the error state`() = runTest {
        every { repository.observeTasks() } returns flow {
            throw SQLiteException("Disk I/O error")
        }

        TaskListViewModel(repository, syncScheduler).uiState.test {
            assertEquals(TaskListUiState.Error, awaitItem())
        }
    }

    @Test
    fun `completing a task saves it through the repository`() = runTest {
        every { repository.observeTasks() } returns flowOf(listOf(task))
        coEvery { repository.setComplete(any(), any()) } returns true

        TaskListViewModel(repository, syncScheduler).onCompleteChange(task.id, isComplete = false)

        coVerify(exactly = 1) { repository.setComplete(task.id, isComplete = false) }
    }

    @Test
    fun `a failed complete change keeps the tasks and shows a message until it is shown`() =
        runTest {
            every { repository.observeTasks() } returns flowOf(listOf(task))
            coEvery { repository.setComplete(any(), any()) } throws SQLiteException("Disk full")
            val viewModel = TaskListViewModel(repository, syncScheduler)

            viewModel.uiState.test {
                assertEquals(TaskListUiState.Content(tasks = listOf(task)), awaitItem())

                viewModel.onCompleteChange(task.id, isComplete = false)
                assertEquals(
                    TaskListUiState.Content(
                        tasks = listOf(task),
                        message = TaskListMessage.CompleteChangeFailed,
                    ),
                    awaitItem(),
                )

                viewModel.onMessageShown()
                assertEquals(TaskListUiState.Content(tasks = listOf(task)), awaitItem())
            }
        }

    @Test
    fun `a rejected complete change, for a task deleted meanwhile, shows no message`() = runTest {
        every { repository.observeTasks() } returns flowOf(listOf(task))
        coEvery { repository.setComplete(any(), any()) } returns false
        val viewModel = TaskListViewModel(repository, syncScheduler)

        viewModel.uiState.test {
            assertEquals(TaskListUiState.Content(tasks = listOf(task)), awaitItem())

            viewModel.onCompleteChange(task.id, isComplete = false)
            expectNoEvents()
        }
    }

    @Test
    fun `refreshing syncs now`() = runTest {
        every { repository.observeTasks() } returns flowOf(listOf(task))

        TaskListViewModel(repository, syncScheduler).onRefresh()

        coVerify(exactly = 1) { syncScheduler.syncNow() }
    }

    @Test
    fun `the content shows whether a sync is running`() = runTest {
        every { repository.observeTasks() } returns flowOf(listOf(task))

        TaskListViewModel(repository, syncScheduler).uiState.test {
            assertEquals(TaskListUiState.Content(tasks = listOf(task)), awaitItem())

            isSyncing.value = true
            assertEquals(
                TaskListUiState.Content(tasks = listOf(task), isSyncing = true),
                awaitItem(),
            )

            isSyncing.value = false
            assertEquals(TaskListUiState.Content(tasks = listOf(task)), awaitItem())
        }
    }
}
