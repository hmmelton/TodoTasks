package com.harrison.todotasks.data.repository

import com.harrison.todotasks.data.local.entities.asEntity
import com.harrison.todotasks.data.local.todoTaskUpdate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoTasksRepositoryDeleteTest : RepositoryTest() {

    @Test
    fun `delete tombstones the task at the clock time and queues it`() = runTest {
        val id = createTask(title = "Task")
        clock.nowMillis = 2_000L

        repository.deleteTask(id)

        val deleted = tasks.getTask(id)
        assertEquals(2_000L, deleted?.deletedAt)
        assertEquals(2_000L, deleted?.lastUpdatedAt)
        assertEquals(todoTaskUpdate(id, base = null, revision = 2L), sync.getTaskUpdate(id))
    }

    @Test
    fun `delete of a synced task stores the synced copy as base`() = runTest {
        val synced = syncedTask("id")
        seed(synced)

        repository.deleteTask("id")

        assertEquals(todoTaskUpdate("id", base = synced.asEntity()), sync.getTaskUpdate("id"))
    }

    @Test
    fun `deleting a task leaves other tasks alone`() = runTest {
        val other = syncedTask("other")
        seed(syncedTask("id"), other)

        repository.deleteTask("id")

        assertEquals(other, tasks.getTask("other"))
        assertNull(sync.getTaskUpdate("other"))
        assertEquals(RowCounts(tasks = 2, updates = 1), rowCounts())
    }

    @Test
    fun `deleting an already-deleted task writes nothing`() = runTest {
        val tombstone = syncedTask("id", deletedAt = 50L)
        seed(tombstone)
        clock.nowMillis = 9_000L

        repository.deleteTask("id")

        assertEquals(tombstone, tasks.getTask("id"))
        assertEquals(RowCounts(tasks = 1, updates = 0), rowCounts())
    }

    @Test
    fun `deleting a missing task writes nothing`() = runTest {
        assertWritesNothing("missing") { repository.deleteTask("missing") }
    }
}
