package com.harrison.todotasks.data.local

import androidx.room3.withWriteTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Checks that a write transaction opened on [TodoTaskDatabase] covers writes made through both
 * local data sources.
 */
@RunWith(RobolectricTestRunner::class)
class LocalDataSourcesTransactionTest {

    private lateinit var todoTaskDatabase: TodoTaskDatabase
    private lateinit var tasks: TodoTasksLocalDataSource
    private lateinit var sync: TodoTaskSyncLocalDataSource

    @Before
    fun setUp() {
        todoTaskDatabase = inMemoryTodoTaskDatabase()
        tasks = TodoTasksLocalDataSource(todoTaskDao = todoTaskDatabase.todoTaskDao())
        sync = TodoTaskSyncLocalDataSource(
            todoTaskUpdateDao = todoTaskDatabase.todoTaskUpdateDao(),
            syncStateDao = todoTaskDatabase.syncStateDao(),
        )
    }

    @After
    fun tearDown() {
        todoTaskDatabase.close()
    }

    @Test
    fun `transaction commits task and update writes and returns the block result`() = runTest {
        val result = todoTaskDatabase.withWriteTransaction {
            tasks.upsertTask(sparseTask)
            sync.upsertTaskUpdate(todoTaskUpdate(sparseTask.id))
            "done"
        }

        assertEquals("done", result)
        assertEquals(sparseTask, tasks.getTask(sparseTask.id))
        assertEquals(todoTaskUpdate(sparseTask.id), sync.getTaskUpdate(sparseTask.id))
    }

    @Test
    fun `transaction that throws rolls back task and update writes together`() = runTest {
        try {
            todoTaskDatabase.withWriteTransaction {
                tasks.upsertTask(sparseTask)
                sync.upsertTaskUpdate(todoTaskUpdate(sparseTask.id))
                sync.setCursor(42L)
                throw IllegalStateException("Boom")
            }
            fail("Expected the transaction to rethrow")
        } catch (e: IllegalStateException) {
            assertEquals("Boom", e.message)
        }

        assertNull(tasks.getTask(sparseTask.id))
        assertNull(sync.getTaskUpdate(sparseTask.id))
        assertEquals(0L, sync.getCursor())
    }

    @Test
    fun `transaction rolls back and rethrows on cancellation`() = runTest {
        val thrown = CancellationException("Cancelled")

        try {
            todoTaskDatabase.withWriteTransaction {
                tasks.upsertTask(sparseTask)
                sync.upsertTaskUpdate(todoTaskUpdate(sparseTask.id))
                throw thrown
            }
            fail("Expected the transaction to rethrow")
        } catch (e: CancellationException) {
            assertEquals(thrown.message, e.message)
        }

        assertNull(tasks.getTask(sparseTask.id))
        assertTrue(sync.getPendingTaskUpdates().isEmpty())
    }
}
