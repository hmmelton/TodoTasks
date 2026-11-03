package com.harrison.todotasks.data.local

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TodoTaskSyncLocalDataSourceTest {

    private lateinit var todoTaskDatabase: TodoTaskDatabase
    private lateinit var dataSource: TodoTaskSyncLocalDataSource

    @Before
    fun setUp() {
        todoTaskDatabase = inMemoryTodoTaskDatabase()
        dataSource = TodoTaskSyncLocalDataSource(
            todoTaskUpdateDao = todoTaskDatabase.todoTaskUpdateDao(),
            syncStateDao = todoTaskDatabase.syncStateDao(),
        )
    }

    @After
    fun tearDown() {
        todoTaskDatabase.close()
    }

    @Test
    fun `update row operations reach the outbox`() = runTest {
        dataSource.upsertTaskUpdate(todoTaskUpdate("a", revision = 2L))
        dataSource.upsertTaskUpdate(todoTaskUpdate("b", revision = 1L))
        dataSource.markTaskUpdateFailedIfRevisionMatches("b", revision = 1L, failedAt = 9L)

        assertEquals(listOf("a"), dataSource.getPendingTaskUpdates().map { it.taskId })
        assertEquals(9L, dataSource.getTaskUpdate("b")?.failedAt)
        assertEquals(0, dataSource.deleteTaskUpdateIfRevisionMatches("a", revision = 1L))
        assertEquals(1, dataSource.deleteTaskUpdateIfRevisionMatches("a", revision = 2L))
    }

    @Test
    fun `cursor defaults to zero and can be updated`() = runTest {
        assertEquals(0L, dataSource.getCursor())

        dataSource.setCursor(42L)

        assertEquals(42L, dataSource.getCursor())
    }
}
