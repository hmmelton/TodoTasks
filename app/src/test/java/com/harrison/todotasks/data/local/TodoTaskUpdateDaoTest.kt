package com.harrison.todotasks.data.local

import com.harrison.todotasks.data.local.dao.TodoTaskUpdateDao
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
class TodoTaskUpdateDaoTest {

    private lateinit var todoTaskDatabase: TodoTaskDatabase
    private lateinit var dao: TodoTaskUpdateDao

    @Before
    fun setUp() {
        todoTaskDatabase = inMemoryTodoTaskDatabase()
        dao = todoTaskDatabase.todoTaskUpdateDao()
    }

    @After
    fun tearDown() {
        todoTaskDatabase.close()
    }

    @Test
    fun `get returns null when the task has no row`() = runTest {
        assertNull(dao.getUpdate("missing"))
    }

    @Test
    fun `null base stays null`() = runTest {
        val update = todoTaskUpdate("id", base = null)

        dao.upsert(update)

        assertEquals(update, dao.getUpdate("id"))
    }

    @Test
    fun `base round-trips with every field set`() = runTest {
        val base = todoTaskEntity("id").copy(
            lastUpdatedAt = 2L,
            deletedAt = 3L,
            dueAt = 4L,
            parentTaskId = "parent",
            isComplete = true,
            version = 5L,
        )
        val update = todoTaskUpdate("id", base = base)

        dao.upsert(update)

        assertEquals(update, dao.getUpdate("id"))
    }

    @Test
    fun `base round-trips with nullable fields null`() = runTest {
        val update = todoTaskUpdate("id", base = todoTaskEntity("id"), failedAt = 7L)

        dao.upsert(update)

        assertEquals(update, dao.getUpdate("id"))
    }

    @Test
    fun `there is one row per task`() = runTest {
        dao.upsert(todoTaskUpdate("id", revision = 1L))

        dao.upsert(todoTaskUpdate("id", revision = 2L))

        assertEquals(listOf(todoTaskUpdate("id", revision = 2L)), dao.getPendingUpdates())
    }

    @Test
    fun `delete removes the row when the revision matches`() = runTest {
        dao.upsert(todoTaskUpdate("id", revision = 3L))

        val deleted = dao.deleteIfRevisionMatches("id", revision = 3L)

        assertEquals(1, deleted)
        assertNull(dao.getUpdate("id"))
    }

    @Test
    fun `delete keeps the row when the revision does not match`() = runTest {
        val update = todoTaskUpdate("id", revision = 4L)
        dao.upsert(update)

        val deleted = dao.deleteIfRevisionMatches("id", revision = 3L)

        assertEquals(0, deleted)
        assertEquals(update, dao.getUpdate("id"))
    }

    @Test
    fun `delete only affects the given task`() = runTest {
        dao.upsert(todoTaskUpdate("a", revision = 1L))
        dao.upsert(todoTaskUpdate("b", revision = 1L))

        dao.deleteIfRevisionMatches("a", revision = 1L)

        assertEquals(listOf("b"), dao.getPendingUpdates().map { it.taskId })
    }

    @Test
    fun `failed rows are excluded from pending but still readable`() = runTest {
        dao.upsert(todoTaskUpdate("failed", revision = 1L))
        dao.upsert(todoTaskUpdate("pending", revision = 1L))

        dao.markFailedIfRevisionMatches("failed", revision = 1L, failedAt = 9L)

        assertEquals(listOf("pending"), dao.getPendingUpdates().map { it.taskId })
        assertEquals(9L, dao.getUpdate("failed")?.failedAt)
    }

    @Test
    fun `an edit made during an upload is not marked failed`() = runTest {
        dao.upsert(todoTaskUpdate("id", revision = 2L))

        dao.markFailedIfRevisionMatches("id", revision = 1L, failedAt = 9L)

        assertNull(dao.getUpdate("id")?.failedAt)
        assertEquals(listOf("id"), dao.getPendingUpdates().map { it.taskId })
    }

    @Test
    fun `clearing failedAt makes a row pending again`() = runTest {
        dao.upsert(todoTaskUpdate("id", failedAt = 9L))
        assertTrue(dao.getPendingUpdates().isEmpty())

        dao.upsert(todoTaskUpdate("id", failedAt = null))

        assertEquals(listOf("id"), dao.getPendingUpdates().map { it.taskId })
    }

    @Test
    fun `pending rows are returned oldest first`() = runTest {
        // IDs sort opposite to insertion order, so the primary key can't produce this result.
        dao.upsert(todoTaskUpdate("c"))
        dao.upsert(todoTaskUpdate("b"))
        dao.upsert(todoTaskUpdate("a"))

        assertEquals(listOf("c", "b", "a"), dao.getPendingUpdates().map { it.taskId })
    }

    @Test
    fun `a row keeps its place when its task is edited again`() = runTest {
        dao.upsert(todoTaskUpdate("first", revision = 1L))
        dao.upsert(todoTaskUpdate("second", revision = 1L))

        dao.upsert(todoTaskUpdate("first", revision = 2L))

        assertEquals(listOf("first", "second"), dao.getPendingUpdates().map { it.taskId })
    }
}
