package com.harrison.todotasks.data.repository

import com.harrison.todotasks.data.local.todoTaskUpdate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks that each write commits its task and outbox row together, by failing the outbox write
 * and expecting the task write in the same call to roll back.
 */
class TodoTasksRepositoryAtomicityTest : RepositoryTest() {

    @Test
    fun `create rolls back the task when its outbox row fails`() = runTest {
        failOutboxWrites()

        assertInjectedFailure { repository.createTask(title = "Task") }

        assertEquals(RowCounts(tasks = 0, updates = 0), rowCounts())
    }

    @Test
    fun `update rolls back the task when its outbox row fails`() = runTest {
        val id = createTask(title = "Old")
        val before = tasks.getTask(id)
        failOutboxWrites(id)

        assertInjectedFailure {
            repository.updateTask(id, title = "New", description = "", dueAt = null)
        }

        assertEquals(before, tasks.getTask(id))
        assertEquals(todoTaskUpdate(id, revision = 1L), sync.getTaskUpdate(id))
    }

    @Test
    fun `complete rolls back the task when its outbox row fails`() = runTest {
        val synced = syncedTask("id")
        seed(synced)
        failOutboxWrites("id")

        assertInjectedFailure { repository.setComplete("id", isComplete = true) }

        assertEquals(synced, tasks.getTask("id"))
        assertEquals(RowCounts(tasks = 1, updates = 0), rowCounts())
    }

    @Test
    fun `delete rolls back the tombstone when its outbox row fails`() = runTest {
        val synced = syncedTask("id")
        seed(synced)
        failOutboxWrites("id")

        assertInjectedFailure { repository.deleteTask("id") }

        assertEquals(synced, tasks.getTask("id"))
        assertEquals(RowCounts(tasks = 1, updates = 0), rowCounts())
    }

    private inline fun assertInjectedFailure(write: () -> Unit) {
        val thrown = assertFailsWith<Exception>(write)
        assertTrue("Unexpected failure: $thrown", INJECTED_FAILURE in thrown.message.orEmpty())
    }
}
