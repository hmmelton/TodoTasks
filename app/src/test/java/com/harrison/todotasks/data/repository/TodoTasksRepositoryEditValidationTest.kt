package com.harrison.todotasks.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test

/** Covers updates and completions that are rejected, so must write nothing. */
class TodoTasksRepositoryEditValidationTest : RepositoryTest() {

    @Test
    fun `update with a blank title is rejected`() = runTest {
        seed(syncedTask("id"))

        assertWritesNothing("id") {
            assertFalse(repository.updateTask("id", title = " ", description = "", dueAt = null))
        }
    }

    @Test
    fun `update of a missing task is rejected`() = runTest {
        assertWritesNothing("missing") {
            assertFalse(
                repository.updateTask("missing", title = "Title", description = "", dueAt = null),
            )
        }
    }

    @Test
    fun `update of a deleted task is rejected`() = runTest {
        seed(syncedTask("id", deletedAt = 50L))

        assertWritesNothing("id") {
            assertFalse(
                repository.updateTask("id", title = "Title", description = "", dueAt = null),
            )
        }
    }

    @Test
    fun `completing a missing task is rejected`() = runTest {
        assertWritesNothing("missing") {
            assertFalse(repository.setComplete("missing", isComplete = true))
        }
    }

    @Test
    fun `completing a deleted task is rejected`() = runTest {
        seed(syncedTask("id", deletedAt = 50L))

        assertWritesNothing("id") { assertFalse(repository.setComplete("id", isComplete = true)) }
    }

    @Test
    fun `a rejected edit leaves a pending outbox row unchanged`() = runTest {
        val id = createTask(title = "Task")

        assertWritesNothing(id) {
            assertFalse(repository.updateTask(id, title = "", description = "", dueAt = null))
        }
    }
}
