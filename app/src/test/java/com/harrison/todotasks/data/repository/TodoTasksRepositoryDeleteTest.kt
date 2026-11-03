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
    fun `deleting a top-level task tombstones each live subtask with its own row`() = runTest {
        val parent = syncedTask("parent")
        val first = syncedTask("first", parentTaskId = "parent")
        val second = syncedTask("second", parentTaskId = "parent")
        seed(parent, first, second)
        clock.nowMillis = 2_000L

        repository.deleteTask("parent")

        for (task in listOf(parent, first, second)) {
            val tombstone = task.copy(deletedAt = 2_000L, lastUpdatedAt = 2_000L)
            assertEquals(tombstone, tasks.getTask(task.id))
            assertEquals(
                todoTaskUpdate(task.id, base = task.asEntity()),
                sync.getTaskUpdate(task.id),
            )
        }
        assertEquals(RowCounts(tasks = 3, updates = 3), rowCounts())
    }

    @Test
    fun `deleting a top-level task skips subtasks that are already deleted`() = runTest {
        val alreadyDeleted = syncedTask("deleted", parentTaskId = "parent", deletedAt = 50L)
        seed(syncedTask("parent"), syncedTask("live", parentTaskId = "parent"), alreadyDeleted)

        repository.deleteTask("parent")

        assertEquals(alreadyDeleted, tasks.getTask("deleted"))
        assertNull(sync.getTaskUpdate("deleted"))
        assertEquals(RowCounts(tasks = 3, updates = 2), rowCounts())
    }

    @Test
    fun `cascade continues a subtask's pending row instead of replacing it`() = runTest {
        val parentId = createTask(title = "Parent")
        val childId = createTask(title = "Child", parentTaskId = parentId)
        repository.setComplete(childId, isComplete = true)

        repository.deleteTask(parentId)

        assertEquals(todoTaskUpdate(childId, revision = 3L), sync.getTaskUpdate(childId))
    }

    @Test
    fun `deleting a subtask leaves its parent and siblings alone`() = runTest {
        val parent = syncedTask("parent")
        val sibling = syncedTask("sibling", parentTaskId = "parent")
        seed(parent, syncedTask("child", parentTaskId = "parent"), sibling)

        repository.deleteTask("child")

        assertEquals(parent, tasks.getTask("parent"))
        assertEquals(sibling, tasks.getTask("sibling"))
        assertEquals(RowCounts(tasks = 3, updates = 1), rowCounts())
    }

    @Test
    fun `deleting an already-deleted task writes nothing`() = runTest {
        val tombstone = syncedTask("id", deletedAt = 50L)
        seed(tombstone, syncedTask("child", parentTaskId = "id"))
        clock.nowMillis = 9_000L

        repository.deleteTask("id")

        assertEquals(tombstone, tasks.getTask("id"))
        assertEquals(RowCounts(tasks = 2, updates = 0), rowCounts())
    }

    @Test
    fun `deleting a missing task writes nothing`() = runTest {
        assertWritesNothing("missing") { repository.deleteTask("missing") }
    }
}
