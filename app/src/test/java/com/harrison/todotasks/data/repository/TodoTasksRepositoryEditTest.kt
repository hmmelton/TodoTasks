package com.harrison.todotasks.data.repository

import com.harrison.todotasks.data.local.entities.asEntity
import com.harrison.todotasks.data.local.todoTaskUpdate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers what successful updates and completions write to the task and the outbox. */
class TodoTasksRepositoryEditTest : RepositoryTest() {

    @Test
    fun `update replaces the edited fields and stamps lastUpdatedAt from the clock`() = runTest {
        clock.nowMillis = 1_000L
        val id = createTask(title = "Old", description = "Old details", dueAt = 1L)
        clock.nowMillis = 2_000L

        assertTrue(
            repository.updateTask(id, title = "New", description = "New details", dueAt = null),
        )

        val updated = tasks.getTask(id)
        assertEquals("New", updated?.title)
        assertEquals("New details", updated?.description)
        assertEquals(null, updated?.dueAt)
        assertEquals(1_000L, updated?.createdAt)
        assertEquals(2_000L, updated?.lastUpdatedAt)
    }

    @Test
    fun `complete sets the flag and stamps lastUpdatedAt from the clock`() = runTest {
        val id = createTask(title = "Task")
        clock.nowMillis = 3_000L

        assertTrue(repository.setComplete(id, isComplete = true))

        assertEquals(true, tasks.getTask(id)?.isComplete)
        assertEquals(3_000L, tasks.getTask(id)?.lastUpdatedAt)

        clock.nowMillis = 4_000L
        repository.setComplete(id, isComplete = false)

        assertEquals(false, tasks.getTask(id)?.isComplete)
        assertEquals(4_000L, tasks.getTask(id)?.lastUpdatedAt)
    }

    @Test
    fun `edits to a never-synced task increment the revision and keep no base`() = runTest {
        val id = createTask(title = "Task")

        repository.updateTask(id, title = "Renamed", description = "", dueAt = null)
        repository.setComplete(id, isComplete = true)

        assertEquals(todoTaskUpdate(id, base = null, revision = 3L), sync.getTaskUpdate(id))
        assertEquals(RowCounts(tasks = 1, updates = 1), rowCounts())
    }

    @Test
    fun `first edit to a synced task stores the synced copy as base`() = runTest {
        val synced = syncedTask("id")
        seed(synced)

        repository.updateTask("id", title = "Renamed", description = "", dueAt = null)

        assertEquals(
            todoTaskUpdate("id", base = synced.asEntity(), revision = 1L),
            sync.getTaskUpdate("id"),
        )
    }

    @Test
    fun `later edits to a synced task keep the original base, not the previous edit`() =
        runTest {
            val synced = syncedTask("id")
            seed(synced)

            repository.setComplete("id", isComplete = true)
            repository.updateTask("id", title = "Renamed", description = "", dueAt = null)

            assertEquals(
                todoTaskUpdate("id", base = synced.asEntity(), revision = 2L),
                sync.getTaskUpdate("id"),
            )
        }

    @Test
    fun `a new edit clears failedAt so the row is uploaded again`() = runTest {
        val id = createTask(title = "Task")
        sync.markTaskUpdateFailedIfRevisionMatches(id, revision = 1L, failedAt = 9L)
        assertEquals(emptyList<String>(), sync.getPendingTaskUpdates().map { it.taskId })

        repository.setComplete(id, isComplete = true)

        assertEquals(todoTaskUpdate(id, revision = 2L, failedAt = null), sync.getTaskUpdate(id))
        assertEquals(listOf(id), sync.getPendingTaskUpdates().map { it.taskId })
    }

    @Test
    fun `an update that changes nothing writes nothing`() = runTest {
        val synced = syncedTask("id").copy(description = "Details", dueAt = 5L)
        seed(synced)
        clock.nowMillis = 9_000L

        assertTrue(
            repository.updateTask("id", title = synced.title, description = "Details", dueAt = 5L),
        )

        assertEquals(synced, tasks.getTask("id"))
        assertEquals(RowCounts(tasks = 1, updates = 0), rowCounts())
    }

    @Test
    fun `completing a task that is already in that state writes nothing`() = runTest {
        val synced = syncedTask("id")
        seed(synced)
        clock.nowMillis = 9_000L

        assertTrue(repository.setComplete("id", isComplete = false))

        assertEquals(synced, tasks.getTask("id"))
        assertEquals(RowCounts(tasks = 1, updates = 0), rowCounts())
    }

    @Test
    fun `a no-op edit leaves an existing row's revision alone`() = runTest {
        val id = createTask(title = "Task")

        repository.setComplete(id, isComplete = false)

        assertEquals(todoTaskUpdate(id, revision = 1L), sync.getTaskUpdate(id))
    }
}
