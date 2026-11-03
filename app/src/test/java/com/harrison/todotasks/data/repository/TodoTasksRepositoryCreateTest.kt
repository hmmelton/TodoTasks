package com.harrison.todotasks.data.repository

import com.harrison.todotasks.data.local.todoTaskUpdate
import com.harrison.todotasks.data.model.TodoTask
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodoTasksRepositoryCreateTest : RepositoryTest() {

    @Test
    fun `create writes an incomplete task stamped by the clock`() = runTest {
        clock.nowMillis = 5_000L

        val id = createTask(title = "Buy milk")

        assertEquals(
            TodoTask(
                id = id,
                title = "Buy milk",
                description = "",
                createdAt = 5_000L,
                lastUpdatedAt = 5_000L,
                deletedAt = null,
                dueAt = null,
                parentTaskId = null,
                isComplete = false,
                version = null,
            ),
            tasks.getTask(id),
        )
    }

    @Test
    fun `create queues one outbox row with no base at revision 1`() = runTest {
        val id = createTask(title = "Buy milk")

        assertEquals(todoTaskUpdate(id, base = null, revision = 1L), sync.getTaskUpdate(id))
        assertEquals(RowCounts(tasks = 1, updates = 1), rowCounts())
    }

    @Test
    fun `create stores the optional fields and returns a new id each time`() = runTest {
        val first = createTask(title = "A", description = "Details", dueAt = 9L)
        val second = createTask(title = "A", description = "Details", dueAt = 9L)

        assertNotEquals(first, second)
        val task = tasks.getTask(first)
        assertEquals("Details", task?.description)
        assertEquals(9L, task?.dueAt)
    }

    @Test
    fun `create under a top-level parent writes a subtask and leaves the parent alone`() =
        runTest {
            val parent = syncedTask("parent")
            seed(parent)

            val id = createTask(title = "Child", parentTaskId = "parent")

            assertEquals("parent", tasks.getTask(id)?.parentTaskId)
            assertEquals(todoTaskUpdate(id), sync.getTaskUpdate(id))
            assertEquals(parent, tasks.getTask("parent"))
            assertEquals(null, sync.getTaskUpdate("parent"))
        }

    @Test
    fun `create with a blank title is rejected and writes nothing`() = runTest {
        assertWritesNothing { assertNull(repository.createTask(title = "")) }
        assertWritesNothing { assertNull(repository.createTask(title = "  \t")) }
    }

    @Test
    fun `create under a missing parent is rejected and writes nothing`() = runTest {
        assertWritesNothing {
            assertNull(repository.createTask(title = "Child", parentTaskId = "missing"))
        }
    }

    @Test
    fun `create under a deleted parent is rejected and writes nothing`() = runTest {
        seed(syncedTask("parent", deletedAt = 50L))

        assertWritesNothing("parent") {
            assertNull(repository.createTask(title = "Child", parentTaskId = "parent"))
        }
    }

    @Test
    fun `create under a subtask is rejected, because subtasks are one level deep`() = runTest {
        seed(syncedTask("parent"), syncedTask("child", parentTaskId = "parent"))

        assertWritesNothing("parent", "child") {
            assertNull(repository.createTask(title = "Grandchild", parentTaskId = "child"))
        }
    }
}
