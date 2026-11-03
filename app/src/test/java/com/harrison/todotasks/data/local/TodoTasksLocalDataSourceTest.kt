package com.harrison.todotasks.data.local

import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TodoTasksLocalDataSourceTest {

    private lateinit var todoTaskDatabase: TodoTaskDatabase
    private lateinit var dataSource: TodoTasksLocalDataSource

    @Before
    fun setUp() {
        todoTaskDatabase = inMemoryTodoTaskDatabase()
        dataSource = TodoTasksLocalDataSource(todoTaskDao = todoTaskDatabase.todoTaskDao())
    }

    @After
    fun tearDown() {
        todoTaskDatabase.close()
    }

    @Test
    fun `task with every field set round-trips through storage`() = runTest {
        dataSource.upsertTask(fullTask)

        assertEquals(fullTask, dataSource.getTask(fullTask.id))
    }

    @Test
    fun `task with null fields round-trips through storage`() = runTest {
        dataSource.upsertTask(sparseTask)

        assertEquals(sparseTask, dataSource.getTask(sparseTask.id))
    }

    @Test
    fun `flows expose mapped tasks`() = runTest {
        val first = sparseTask.copy(id = "first")
        val second = sparseTask.copy(id = "second", createdAt = 2L)

        dataSource.upsertTasks(listOf(first, second))

        assertEquals(listOf(first, second), dataSource.observeTopLevelTasks().first())
        assertEquals(second, dataSource.observeTask("second").first())
    }

    @Test
    fun `observed task re-emits on write`() = runTest {
        dataSource.observeTask(sparseTask.id).test {
            assertNull(awaitItem())

            dataSource.upsertTask(sparseTask)

            assertEquals(sparseTask, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
