package com.harrison.todotasks.data.repository

import com.harrison.todotasks.data.local.todoTaskUpdate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Checks that each write commits its tasks and outbox rows together, by failing the last outbox
 * write and expecting every earlier write in the same call to roll back.
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

    @Test
    fun `cascade rolls back the parent and every subtask when one subtask's row fails`() =
        runTest {
            val seeded = listOf(
                syncedTask("parent"),
                syncedTask("first", parentTaskId = "parent"),
                syncedTask("second", parentTaskId = "parent"),
                syncedTask("third", parentTaskId = "parent"),
            )
            seed(*seeded.toTypedArray())
            // The parent is written before any subtask, so its tombstone and outbox row (and
            // possibly other subtasks') are already written when this subtask's row fails.
            failOutboxWrites("second")

            assertInjectedFailure { repository.deleteTask("parent") }

            seeded.forEach { assertEquals(it, tasks.getTask(it.id)) }
            assertEquals(RowCounts(tasks = 4, updates = 0), rowCounts())
        }

    private inline fun assertInjectedFailure(write: () -> Unit) {
        val thrown = assertFailsWith<Exception>(write)
        assertTrue("Unexpected failure: $thrown", INJECTED_FAILURE in thrown.message.orEmpty())
    }
}
