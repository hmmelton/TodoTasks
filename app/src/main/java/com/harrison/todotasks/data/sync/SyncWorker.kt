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
 * Syncs tasks with the server in the background. [SyncScheduler] enqueues it. Each run makes one
 * attempt, and returns [Result.retry] to have WorkManager retry with backoff.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: TodoTasksRepository,
) : CoroutineWorker(context, params) {

    /**
     * Downloads the server's changes, then uploads the pending outbox rows. Downloading first
     * merges remote edits into pending rows before upload, which avoids most conflicts.
     *
     * @return [Result.success] if nothing is left to upload. [Result.retry] if a row is still
     * pending, or if the server can't be reached, responds with a 5xx status, or sends a body that
     * isn't the expected JSON. [Result.failure] for any other error status.
     * @throws SQLiteException if a database read or write fails. The failed transaction writes
     * nothing.
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
