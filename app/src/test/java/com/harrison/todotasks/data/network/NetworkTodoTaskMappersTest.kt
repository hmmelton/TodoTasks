package com.harrison.todotasks.data.network

import com.harrison.todotasks.data.local.fullTask
import com.harrison.todotasks.data.local.sparseTask
import com.harrison.todotasks.data.network.model.NetworkTodoTask
import com.harrison.todotasks.data.network.model.asNetworkTodoTask
import com.harrison.todotasks.data.network.model.asTodoTask
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkTodoTaskMappersTest {

    @Test
    fun `asTodoTask maps each field to its own property`() {
        val networkTask = NetworkTodoTask(
            id = "task-id",
            title = "Title",
            description = "Description",
            createdAt = 1L,
            lastUpdatedAt = 2L,
            deletedAt = 3L,
            dueAt = 4L,
            isComplete = true,
            version = 5L,
        )

        assertEquals(fullTask, networkTask.asTodoTask())
    }

    @Test
    fun `asNetworkTodoTask maps each field and keeps the version`() {
        val expected = NetworkTodoTask(
            id = "task-id",
            title = "Title",
            description = "Description",
            createdAt = 1L,
            lastUpdatedAt = 2L,
            deletedAt = 3L,
            dueAt = 4L,
            isComplete = true,
            version = 5L,
        )

        assertEquals(expected, fullTask.asNetworkTodoTask())
    }

    @Test
    fun `asNetworkTodoTask keeps null fields, including a never-synced version`() {
        val networkTask = sparseTask.asNetworkTodoTask()

        assertEquals(null, networkTask.deletedAt)
        assertEquals(null, networkTask.dueAt)
        assertEquals(false, networkTask.isComplete)
        assertEquals(null, networkTask.version)
    }

    @Test
    fun `mapping to the network model and back returns the same task`() {
        for (task in listOf(fullTask, sparseTask)) {
            assertEquals(task, task.asNetworkTodoTask().asTodoTask())
        }
    }
}
