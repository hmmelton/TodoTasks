package com.harrison.todotasks.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.harrison.todotasks.data.network.respondChanges
import com.harrison.todotasks.data.network.respondJson
import com.harrison.todotasks.data.network.respondTask
import com.harrison.todotasks.data.network.sentTask
import com.harrison.todotasks.data.repository.RepositoryTest
import com.harrison.todotasks.data.repository.syncedTask
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import java.io.IOException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** Covers the order of a sync and how each outcome maps to a WorkManager [Result]. */
class SyncWorkerTest : RepositoryTest() {

    @Test
    fun `succeeds when everything uploads`() = runTest {
        seed(syncedTask("id"))
        repository.setComplete("id", isComplete = true)
        respond = { request ->
            when (request.method) {
                HttpMethod.Get -> respondChanges(tasks = emptyList(), cursor = 7L)
                else -> respondTask(request.sentTask().copy(version = 8L))
            }
        }

        assertEquals(Result.success(), syncWorker().doWork())
        assertEquals(0, sync.getPendingTaskUpdates().size)
    }

    @Test
    fun `downloads before uploading`() = runTest {
        seed(syncedTask("id"))
        repository.setComplete("id", isComplete = true)
        respond = { request ->
            when (request.method) {
                HttpMethod.Get -> respondChanges(tasks = emptyList(), cursor = 7L)
                else -> respondTask(request.sentTask().copy(version = 8L))
            }
        }

        syncWorker().doWork()

        assertEquals(listOf(HttpMethod.Get, HttpMethod.Put), server.requests.map { it.method })
    }

    @Test
    fun `retries when a merge is left to upload`() = runTest {
        val synced = syncedTask("id")
        seed(synced)
        clock.nowMillis = 2_000L
        repository.setComplete("id", isComplete = true)
        val remote = synced.copy(title = "Server title", lastUpdatedAt = 1_500L, version = 9L)
        respond = { request ->
            when (request.method) {
                HttpMethod.Get -> respondChanges(tasks = emptyList(), cursor = 7L)
                else -> respondTask(remote, HttpStatusCode.Conflict)
            }
        }

        assertEquals(Result.retry(), syncWorker().doWork())
        assertEquals(1, sync.getPendingTaskUpdates().size)
    }

    @Test
    fun `retries when the server can't be reached`() = runTest {
        respond = { throw IOException("Offline") }

        assertEquals(Result.retry(), syncWorker().doWork())
    }

    @Test
    fun `retries on a 5xx status`() = runTest {
        respond = { respondJson("{}", HttpStatusCode.ServiceUnavailable) }

        assertEquals(Result.retry(), syncWorker().doWork())
    }

    @Test
    fun `retries when the body isn't the expected JSON`() = runTest {
        respond = { respondJson("""{"unexpected": true}""") }

        assertEquals(Result.retry(), syncWorker().doWork())
    }

    @Test
    fun `fails on a 4xx status from the download without uploading`() = runTest {
        seed(syncedTask("id"))
        repository.setComplete("id", isComplete = true)
        respond = { respondJson("{}", HttpStatusCode.Unauthorized) }

        assertEquals(Result.failure(), syncWorker().doWork())
        assertEquals(listOf(HttpMethod.Get), server.requests.map { it.method })
    }

    /** Builds a [SyncWorker] over the test's [repository]. */
    private fun syncWorker(): SyncWorker =
        TestListenableWorkerBuilder<SyncWorker>(ApplicationProvider.getApplicationContext())
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ): ListenableWorker = SyncWorker(appContext, workerParameters, repository)
                },
            )
            .build()
}
