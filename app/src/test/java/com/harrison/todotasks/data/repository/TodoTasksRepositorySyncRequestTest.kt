package com.harrison.todotasks.data.repository

import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers which writes request a sync: only those that commit a change. */
class TodoTasksRepositorySyncRequestTest : RepositoryTest() {

    @Test
    fun `create requests a sync`() = runTest {
        createTask(title = "Task")

        verify(exactly = 1) { syncScheduler.requestSync() }
    }

    @Test
    fun `update requests a sync`() = runTest {
        seed(syncedTask("id"))

        assertTrue(repository.updateTask("id", title = "Renamed", description = "", dueAt = null))

        verify(exactly = 1) { syncScheduler.requestSync() }
    }

    @Test
    fun `setComplete requests a sync`() = runTest {
        seed(syncedTask("id"))

        assertTrue(repository.setComplete("id", isComplete = true))

        verify(exactly = 1) { syncScheduler.requestSync() }
    }

    @Test
    fun `delete requests a sync`() = runTest {
        seed(syncedTask("id"))

        repository.deleteTask("id")

        verify(exactly = 1) { syncScheduler.requestSync() }
    }

    @Test
    fun `rejected writes request no sync`() = runTest {
        seed(syncedTask("id"), syncedTask("deleted", deletedAt = 50L))

        assertNull(repository.createTask(title = " "))
        assertFalse(repository.updateTask("id", title = " ", description = "", dueAt = null))
        assertFalse(repository.updateTask("missing", title = "T", description = "", dueAt = null))
        assertFalse(repository.updateTask("deleted", title = "T", description = "", dueAt = null))
        assertFalse(repository.setComplete("missing", isComplete = true))
        assertFalse(repository.setComplete("deleted", isComplete = true))
        repository.deleteTask("missing")

        verify(exactly = 0) { syncScheduler.requestSync() }
    }

    @Test
    fun `writes that change nothing request no sync`() = runTest {
        val synced = syncedTask("id")
        seed(synced, syncedTask("deleted", deletedAt = 50L))

        assertTrue(
            repository.updateTask(
                "id",
                title = synced.title,
                description = synced.description,
                dueAt = synced.dueAt,
            ),
        )
        assertTrue(repository.setComplete("id", isComplete = synced.isComplete))
        repository.deleteTask("deleted")

        verify(exactly = 0) { syncScheduler.requestSync() }
    }

    @Test
    fun `completing an already-complete task requests no sync`() = runTest {
        seed(syncedTask("id").copy(isComplete = true))

        assertTrue(repository.setComplete("id", isComplete = true))

        verify(exactly = 0) { syncScheduler.requestSync() }
    }

    @Test
    fun `writes whose transaction fails request no sync`() = runTest {
        seed(syncedTask("id"))
        failOutboxWrites()

        assertFailsWith<Exception> { repository.createTask(title = "Task") }
        assertFailsWith<Exception> {
            repository.updateTask("id", title = "Renamed", description = "", dueAt = null)
        }
        assertFailsWith<Exception> { repository.setComplete("id", isComplete = true) }
        assertFailsWith<Exception> { repository.deleteTask("id") }

        verify(exactly = 0) { syncScheduler.requestSync() }
    }
}
