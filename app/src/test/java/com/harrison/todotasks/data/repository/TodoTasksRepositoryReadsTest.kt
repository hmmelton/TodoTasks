package com.harrison.todotasks.data.repository

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Checks that the repository's reads reflect its writes while they are being observed. */
class TodoTasksRepositoryReadsTest : RepositoryTest() {

    @Test
    fun `top-level tasks show created tasks and drop deleted ones`() = runTest {
        repository.observeTopLevelTasks().test {
            assertTrue(awaitItem().isEmpty())

            val id = createTask(title = "Task")
            assertEquals(listOf(id), awaitItem().map { it.id })

            repository.deleteTask(id)
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observed task reflects each edit and emits null once deleted`() = runTest {
        val id = createTask(title = "Old")

        repository.observeTask(id).test {
            assertEquals("Old", awaitItem()?.title)

            repository.updateTask(id, title = "New", description = "", dueAt = null)
            assertEquals("New", awaitItem()?.title)

            repository.setComplete(id, isComplete = true)
            assertEquals(true, awaitItem()?.isComplete)

            repository.deleteTask(id)
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a no-op edit does not re-emit`() = runTest {
        val id = createTask(title = "Task")

        repository.observeTask(id).test {
            awaitItem()

            repository.setComplete(id, isComplete = false)

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
