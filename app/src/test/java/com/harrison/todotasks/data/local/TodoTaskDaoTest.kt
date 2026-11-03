package com.harrison.todotasks.data.local

import app.cash.turbine.test
import com.harrison.todotasks.data.local.dao.TodoTaskDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TodoTaskDaoTest {

    private lateinit var todoTaskDatabase: TodoTaskDatabase
    private lateinit var dao: TodoTaskDao

    @Before
    fun setUp() {
        todoTaskDatabase = inMemoryTodoTaskDatabase()
        dao = todoTaskDatabase.todoTaskDao()
    }

    @After
    fun tearDown() {
        todoTaskDatabase.close()
    }

    @Test
    fun `top-level query excludes subtasks and tombstones`() = runTest {
        dao.upsert(
            listOf(
                todoTaskEntity("visible"),
                todoTaskEntity("subtask", parentTaskId = "visible"),
                todoTaskEntity("tombstone", deletedAt = 5L),
            ),
        )

        assertEquals(listOf("visible"), dao.observeTopLevelTasks().first().map { it.id })
    }

    @Test
    fun `top-level query orders incomplete first, then due date with nulls last, then created`() =
        runTest {
            dao.upsert(
                listOf(
                    todoTaskEntity("complete-due-soonest", dueAt = 1L, isComplete = true),
                    todoTaskEntity("no-due-newer", createdAt = 20L),
                    todoTaskEntity("no-due-older", createdAt = 10L),
                    todoTaskEntity("due-later", dueAt = 200L),
                    todoTaskEntity("due-sooner-newer", dueAt = 100L, createdAt = 20L),
                    todoTaskEntity("due-sooner-older", dueAt = 100L, createdAt = 10L),
                ),
            )

            assertEquals(
                listOf(
                    "due-sooner-older",
                    "due-sooner-newer",
                    "due-later",
                    "no-due-older",
                    "no-due-newer",
                    "complete-due-soonest",
                ),
                dao.observeTopLevelTasks().first().map { it.id },
            )
        }

    @Test
    fun `subtask query returns only visible children of the parent, in display order`() = runTest {
        dao.upsert(
            listOf(
                todoTaskEntity("parent"),
                todoTaskEntity("other-parent"),
                todoTaskEntity("complete", parentTaskId = "parent", isComplete = true),
                todoTaskEntity("no-due", parentTaskId = "parent"),
                todoTaskEntity("due", parentTaskId = "parent", dueAt = 100L),
                todoTaskEntity("tombstone", parentTaskId = "parent", deletedAt = 5L),
                todoTaskEntity("other-child", parentTaskId = "other-parent"),
            ),
        )

        assertEquals(
            listOf("due", "no-due", "complete"),
            dao.observeSubtasks("parent").first().map { it.id },
        )
    }

    @Test
    fun `subtask query returns nothing for a deleted parent`() = runTest {
        dao.upsert(
            listOf(
                todoTaskEntity("parent", deletedAt = 5L),
                todoTaskEntity("child", parentTaskId = "parent"),
            ),
        )

        assertTrue(dao.observeSubtasks("parent").first().isEmpty())
    }

    @Test
    fun `observing a subtask of a deleted parent emits null`() = runTest {
        dao.upsert(
            listOf(
                todoTaskEntity("parent", deletedAt = 5L),
                todoTaskEntity("child", parentTaskId = "parent"),
            ),
        )

        assertNull(dao.observeTask("child").first())
    }

    @Test
    fun `observing a tombstoned task emits null`() = runTest {
        dao.upsert(todoTaskEntity("tombstone", deletedAt = 5L))

        assertNull(dao.observeTask("tombstone").first())
    }

    @Test
    fun `observing a missing task emits null`() = runTest {
        assertNull(dao.observeTask("missing").first())
    }

    @Test
    fun `get returns hidden rows, which sync needs`() = runTest {
        val parent = todoTaskEntity("parent", deletedAt = 5L)
        val child = todoTaskEntity("child", parentTaskId = "parent")
        dao.upsert(listOf(parent, child))

        assertEquals(parent, dao.getTask("parent"))
        assertEquals(child, dao.getTask("child"))
    }

    @Test
    fun `get returns null for a missing task`() = runTest {
        assertNull(dao.getTask("missing"))
    }

    @Test
    fun `get subtasks returns every child of the parent, including hidden ones`() = runTest {
        val visible = todoTaskEntity("visible", parentTaskId = "parent")
        val tombstone = todoTaskEntity("tombstone", parentTaskId = "parent", deletedAt = 5L)
        dao.upsert(
            listOf(
                todoTaskEntity("parent", deletedAt = 5L),
                visible,
                tombstone,
                todoTaskEntity("other-child", parentTaskId = "other-parent"),
            ),
        )

        assertEquals(setOf(visible, tombstone), dao.getSubtasks("parent").toSet())
        assertTrue(dao.getSubtasks("visible").isEmpty())
    }

    @Test
    fun `child inserted before its parent succeeds and is visible both before and after`() =
        runTest {
            val child = todoTaskEntity("child", parentTaskId = "parent")

            dao.upsert(child)

            assertEquals(child, dao.observeTask("child").first())
            assertEquals(listOf(child), dao.observeSubtasks("parent").first())

            dao.upsert(todoTaskEntity("parent"))

            assertEquals(child, dao.observeTask("child").first())
            assertEquals(listOf(child), dao.observeSubtasks("parent").first())
        }

    @Test
    fun `upsert replaces the row with the same id`() = runTest {
        dao.upsert(todoTaskEntity("id", title = "Old"))

        dao.upsert(todoTaskEntity("id", title = "New"))

        assertEquals(listOf("New"), dao.observeTopLevelTasks().first().map { it.title })
    }

    @Test
    fun `top-level flow re-emits on write`() = runTest {
        dao.observeTopLevelTasks().test {
            assertTrue(awaitItem().isEmpty())

            dao.upsert(todoTaskEntity("id"))

            assertEquals(listOf("id"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleting a parent hides its subtasks from active observers`() = runTest {
        val child = todoTaskEntity("child", parentTaskId = "parent")
        dao.upsert(listOf(todoTaskEntity("parent"), child))

        dao.observeSubtasks("parent").test {
            assertEquals(listOf(child), awaitItem())

            dao.upsert(todoTaskEntity("parent", deletedAt = 5L))

            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
