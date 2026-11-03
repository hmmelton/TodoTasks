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
    fun `tasks query excludes tombstones`() = runTest {
        dao.upsert(listOf(todoTaskEntity("live"), todoTaskEntity("tombstone", deletedAt = 5L)))

        assertEquals(listOf("live"), dao.observeTasks().first().map { it.id })
    }

    @Test
    fun `tasks query orders incomplete first, then due date with nulls last, then created`() =
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
                dao.observeTasks().first().map { it.id },
            )
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
    fun `get returns tombstoned rows, which sync needs`() = runTest {
        val tombstone = todoTaskEntity("tombstone", deletedAt = 5L)
        dao.upsert(tombstone)

        assertEquals(tombstone, dao.getTask("tombstone"))
    }

    @Test
    fun `get returns null for a missing task`() = runTest {
        assertNull(dao.getTask("missing"))
    }

    @Test
    fun `upsert replaces the row with the same id`() = runTest {
        dao.upsert(todoTaskEntity("id", title = "Old"))

        dao.upsert(todoTaskEntity("id", title = "New"))

        assertEquals(listOf("New"), dao.observeTasks().first().map { it.title })
    }

    @Test
    fun `tasks flow re-emits on write`() = runTest {
        dao.observeTasks().test {
            assertTrue(awaitItem().isEmpty())

            dao.upsert(todoTaskEntity("id"))

            assertEquals(listOf("id"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
