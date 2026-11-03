package com.harrison.todotasks.data.repository

import com.harrison.todotasks.data.local.entities.asEntity
import com.harrison.todotasks.data.local.todoTaskUpdate
import com.harrison.todotasks.data.network.respondTask
import com.harrison.todotasks.data.network.sentTask
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers how uploads, conflicts, and failures change the local tasks and the outbox. */
class TodoTasksRepositoryUploadTest : RepositoryTest() {

    @Test
    fun `new task is sent with no version and takes the server's version`() = runTest {
        val id = createTask(title = "Task")
        val created = tasks.getTask(id)
        respond = { request -> accept(request, version = 1L) }

        assertTrue(repository.uploadPendingChanges())
        assertEquals(null, server.requests.single().sentTask().version)
        assertEquals(created?.copy(version = 1L), tasks.getTask(id))
        assertEquals(RowCounts(tasks = 1, updates = 0), rowCounts())
    }

    @Test
    fun `edit to a synced task is sent with its base version`() = runTest {
        seed(syncedTask("id"))
        repository.setComplete("id", isComplete = true)
        respond = { request -> accept(request, version = 8L) }

        assertTrue(repository.uploadPendingChanges())
        val sent = server.requests.single().sentTask()
        assertEquals(7L, sent.version)
        assertEquals(true, sent.isComplete)
        assertEquals(sent.copy(version = 8L), tasks.getTask("id"))
        assertEquals(RowCounts(tasks = 1, updates = 0), rowCounts())
    }

    @Test
    fun `edit made during the upload is kept and rebased onto the stored task`() = runTest {
        seed(syncedTask("id"))
        repository.setComplete("id", isComplete = true)
        respond = { request ->
            repository.updateTask("id", title = "Renamed", description = "", dueAt = null)
            accept(request, version = 8L)
        }

        assertFalse(repository.uploadPendingChanges())

        val stored = server.requests.single().sentTask().copy(version = 8L)
        val local = tasks.getTask("id")
        assertEquals("Renamed", local?.title)
        assertEquals(true, local?.isComplete)
        assertEquals(8L, local?.version)
        assertEquals(
            todoTaskUpdate("id", base = stored.asEntity(), revision = 2L),
            sync.getTaskUpdate("id"),
        )
    }

    @Test
    fun `conflict with edits to different fields is merged and uploaded on the next sync`() =
        runTest {
            val synced = syncedTask("id")
            seed(synced)
            clock.nowMillis = 2_000L
            repository.setComplete("id", isComplete = true)
            val remote = synced.copy(title = "Server title", lastUpdatedAt = 1_500L, version = 9L)
            respond = { respondTask(remote, HttpStatusCode.Conflict) }

            assertFalse(repository.uploadPendingChanges())
            val merged = remote.copy(isComplete = true, lastUpdatedAt = 2_000L)
            assertEquals(1, server.requests.size)
            assertEquals(merged, tasks.getTask("id"))
            assertEquals(
                todoTaskUpdate("id", base = remote.asEntity(), revision = 1L),
                sync.getTaskUpdate("id"),
            )

            respond = { request -> accept(request, version = 10L) }

            assertTrue(repository.uploadPendingChanges())
            assertEquals(merged, server.requests[1].sentTask())
            assertEquals(merged.copy(version = 10L), tasks.getTask("id"))
            assertEquals(RowCounts(tasks = 1, updates = 0), rowCounts())
        }

    @Test
    fun `conflict whose merge is the server's task deletes the row without uploading`() =
        runTest {
            val synced = syncedTask("id")
            seed(synced)
            clock.nowMillis = 2_000L
            repository.setComplete("id", isComplete = true)
            val remote = synced.copy(isComplete = true, lastUpdatedAt = 3_000L, version = 9L)
            respond = { respondTask(remote, HttpStatusCode.Conflict) }

            assertTrue(repository.uploadPendingChanges())
            assertEquals(1, server.requests.size)
            assertEquals(remote, tasks.getTask("id"))
            assertEquals(RowCounts(tasks = 1, updates = 0), rowCounts())
        }

    @Test
    fun `conflict on a never-synced task keeps every local change`() = runTest {
        clock.nowMillis = 1_000L
        val id = createTask(title = "Task")
        // The server stored the first upload, but its response was lost.
        val remote = checkNotNull(tasks.getTask(id)).copy(version = 3L)
        clock.nowMillis = 2_000L
        repository.updateTask(id, title = "Local title", description = "", dueAt = null)
        respond = { respondTask(remote, HttpStatusCode.Conflict) }
        assertFalse(repository.uploadPendingChanges())
        respond = { request -> accept(request, version = 4L) }

        assertTrue(repository.uploadPendingChanges())
        val merged = remote.copy(title = "Local title", lastUpdatedAt = 2_000L)
        assertEquals(merged, server.requests[1].sentTask())
        assertEquals(merged.copy(version = 4L), tasks.getTask(id))
        assertEquals(RowCounts(tasks = 1, updates = 0), rowCounts())
    }


    /** Accepts the upload in [request], storing it at [version] as the server would. */
    private suspend fun MockRequestHandleScope.accept(
        request: HttpRequestData,
        version: Long,
    ): HttpResponseData = respondTask(request.sentTask().copy(version = version))
}
