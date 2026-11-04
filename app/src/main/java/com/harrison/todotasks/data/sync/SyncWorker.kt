package com.harrison.todotasks.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.sqlite.SQLiteException
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.harrison.todotasks.data.repository.TodoTasksRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.ktor.client.plugins.ResponseException
import io.ktor.serialization.JsonConvertException
import java.io.IOException

private const val TAG = "SyncWorker"

/**
 * Syncs tasks with the server in the background: downloads the server's changes, then uploads the
 * outbox. [SyncScheduler] enqueues it, and WorkManager runs it when the device is online.
 *
 * The worker never retries anything itself. Each run makes one attempt, and returns
 * [Result.retry] to have WorkManager run it again later, with backoff.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: TodoTasksRepository,
) : CoroutineWorker(context, params) {

    /**
     * Downloads the server's changes, then uploads the pending outbox rows.
     *
     * The download comes first, so remote edits are merged into pending rows before they are
     * uploaded. That avoids most conflicts, and means the upload's result reflects every merge the
     * download made.
     *
     * @return [Result.success] if nothing is left to upload. [Result.retry] if a row is still
     * pending, such as a merge after a conflict or an edit made during the sync, or if the server
     * can't be reached, responds with a 5xx status, or sends a body that isn't the expected JSON.
     * [Result.failure] if the server responds with any other error status, because retrying gets
     * the same answer. The next requested sync, or the next app start, tries again.
     * @throws SQLiteException if a database read or write fails. WorkManager then fails the work,
     * and the failed transaction writes nothing. Cancellation is never caught, so it stops the
     * sync.
     */
    override suspend fun doWork(): Result = try {
        repository.downloadChanges()
        val done = repository.uploadPendingChanges()
        if (done) Result.success() else Result.retry()
    } catch (e: IOException) {
        Result.retry()
    } catch (e: JsonConvertException) {
        Result.retry()
    } catch (e: ResponseException) {
        if (e.response.status.value in 500..599) {
            Result.retry()
        } else {
            Log.w(TAG, "Server rejected the sync: ${e.response.status}", e)
            Result.failure()
        }
    }
}
