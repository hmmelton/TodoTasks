package com.harrison.todotasks.data.repository

import androidx.sqlite.SQLiteException
import com.harrison.todotasks.data.local.entities.asEntity
import com.harrison.todotasks.data.local.todoTaskUpdate
import com.harrison.todotasks.data.network.respondChanges
import com.harrison.todotasks.data.network.respondJson
import com.harrison.todotasks.data.network.respondTask
import com.harrison.todotasks.data.network.sentTask
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.JsonConvertException
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers how downloaded tasks change the local tasks, the outbox, and the cursor. */
class TodoTasksRepositoryDownloadTest : RepositoryTest() {

    @Test
    fun `sends the stored cursor as since and stores the returned cursor`() = runTest {
        respond = { respondChanges(tasks = emptyList(), cursor = 5L) }

        repository.downloadChanges()
        assertEquals(5L, sync.getCursor())
        repository.downloadChanges()

        assertEquals(listOf("0", "5"), server.requests.map { it.url.parameters["since"] })
    }

    @Test
    fun `new remote task is inserted and a remote tombstone is saved`() = runTest {
        val live = syncedTask("live")
        val deleted = syncedTask("deleted", deletedAt = 200L)
        respond = { respondChanges(tasks = listOf(live, deleted), cursor = 7L) }

        repository.downloadChanges()

        assertEquals(live, tasks.getTask("live"))
        assertEquals(deleted, tasks.getTask("deleted"))
        assertEquals(RowCounts(tasks = 2, updates = 0), rowCounts())
    }

    @Test
    fun `remote change overwrites a task with no outbox row`() = runTest {
        val synced = syncedTask("id")
        seed(synced)
        val remote = synced.copy(title = "Server title", version = 8L)
        respond = { respondChanges(tasks = listOf(remote), cursor = 8L) }

        repository.downloadChanges()

        assertEquals(remote, tasks.getTask("id"))
        assertEquals(RowCounts(tasks = 1, updates = 0), rowCounts())
    }

    @Test
    fun `pending row at the server's version keeps the local edit and its row`() = runTest {
        val synced = syncedTask("id")
        seed(synced)
        repository.setComplete("id", isComplete = true)
        respond = { respondChanges(tasks = listOf(synced), cursor = 7L) }

        assertWritesNothing("id") { repository.downloadChanges() }

        assertEquals(7L, sync.getCursor())
    }

    @Test
    fun `remote edit to a different field is merged and uploaded on the next sync`() = runTest {
        val synced = syncedTask("id")
        seed(synced)
        clock.nowMillis = 2_000L
        repository.setComplete("id", isComplete = true)
        val remote = synced.copy(title = "Server title", lastUpdatedAt = 1_500L, version = 9L)
        respond = { respondChanges(tasks = listOf(remote), cursor = 9L) }

        repository.downloadChanges()

        val merged = remote.copy(isComplete = true, lastUpdatedAt = 2_000L)
        assertEquals(merged, tasks.getTask("id"))
        assertEquals(
            todoTaskUpdate("id", base = remote.asEntity(), revision = 1L),
            sync.getTaskUpdate("id"),
        )

        respond = { request -> respondTask(request.sentTask().copy(version = 10L)) }

        assertTrue(repository.uploadPendingChanges())
        assertEquals(merged, server.requests[1].sentTask())
        assertEquals(merged.copy(version = 10L), tasks.getTask("id"))
        assertEquals(RowCounts(tasks = 1, updates = 0), rowCounts())
    }

    @Test
    fun `merge that is the server's task deletes the row`() = runTest {
        val synced = syncedTask("id")
        seed(synced)
        clock.nowMillis = 2_000L
        repository.setComplete("id", isComplete = true)
        val remote = synced.copy(isComplete = true, lastUpdatedAt = 3_000L, version = 9L)
        respond = { respondChanges(tasks = listOf(remote), cursor = 9L) }

        repository.downloadChanges()

        assertEquals(remote, tasks.getTask("id"))
        assertEquals(RowCounts(tasks = 1, updates = 0), rowCounts())
    }

    @Test
    fun `remote edit is merged into a failed row, keeping the local edit`() = runTest {
        val synced = syncedTask("id")
        seed(synced)
        clock.nowMillis = 2_000L
        repository.setComplete("id", isComplete = true)
        sync.markTaskUpdateFailedIfRevisionMatches("id", revision = 1L, failedAt = 9L)
        val remote = synced.copy(title = "Server title", lastUpdatedAt = 1_500L, version = 9L)
        respond = { respondChanges(tasks = listOf(remote), cursor = 9L) }

        repository.downloadChanges()

        assertEquals(remote.copy(isComplete = true, lastUpdatedAt = 2_000L), tasks.getTask("id"))
        assertEquals(
            todoTaskUpdate("id", base = remote.asEntity(), failedAt = 9L),
            sync.getTaskUpdate("id"),
        )
    }

    @Test
    fun `IOException propagates and writes nothing`() = runTest {
        seedPendingEdit()
        respond = { throw IOException("Offline") }

        assertWritesNothing("id") {
            assertFailsWith<IOException> { repository.downloadChanges() }
        }

        assertEquals(4L, sync.getCursor())
    }

    @Test
    fun `5xx propagates and writes nothing`() = runTest {
        seedPendingEdit()
        respond = { respondJson("{}", HttpStatusCode.InternalServerError) }

        assertWritesNothing("id") {
            val thrown = assertFailsWith<ResponseException> { repository.downloadChanges() }
            assertEquals(HttpStatusCode.InternalServerError, thrown.response.status)
        }

        assertEquals(4L, sync.getCursor())
    }

    @Test
    fun `body that isn't the expected JSON propagates and writes nothing`() = runTest {
        seedPendingEdit()
        respond = { respondJson("""{"tasks": "not a list", "cursor": 9}""") }

        assertWritesNothing("id") {
            assertFailsWith<JsonConvertException> { repository.downloadChanges() }
        }

        assertEquals(4L, sync.getCursor())
    }

    @Test
    fun `failed database write rolls back every task and the cursor`() = runTest {
        seedPendingEdit()
        failOutboxWrites("id")
        val new = syncedTask("new")
        val remote = syncedTask("id").copy(title = "Server title", version = 9L)
        respond = { respondChanges(tasks = listOf(new, remote), cursor = 9L) }

        assertWritesNothing("id", "new") {
            assertFailsWith<SQLiteException> { repository.downloadChanges() }
        }

        assertEquals(4L, sync.getCursor())
    }

    /** Seeds the synced task `id` with a pending local edit, and stores a cursor of 4. */
    private suspend fun seedPendingEdit() {
        seed(syncedTask("id"))
        repository.setComplete("id", isComplete = true)
        sync.setCursor(4L)
    }
}
