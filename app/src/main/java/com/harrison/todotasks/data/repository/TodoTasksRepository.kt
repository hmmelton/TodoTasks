package com.harrison.todotasks.data.repository

import android.util.Log
import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteException
import com.harrison.todotasks.data.local.TodoTaskDatabase
import com.harrison.todotasks.data.local.TodoTaskSyncLocalDataSource
import com.harrison.todotasks.data.local.TodoTasksLocalDataSource
import com.harrison.todotasks.data.local.entities.TodoTaskUpdateEntity
import com.harrison.todotasks.data.local.entities.asEntity
import com.harrison.todotasks.data.local.entities.asTodoTask
import com.harrison.todotasks.data.model.TodoTask
import com.harrison.todotasks.data.network.TodoTasksNetworkDataSource
import com.harrison.todotasks.data.network.UploadResult
import com.harrison.todotasks.data.sync.ConflictResolver
import io.ktor.client.plugins.ResponseException
import io.ktor.serialization.JsonConvertException
import java.io.IOException
import java.time.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

private const val TAG = "TodoTasksRepository"

/**
 * The entry point to task data for the rest of the app, using offline-first lazy writes.
 *
 * Reads come only from the local database, which is the source of truth. Each write changes the
 * local tasks and queues them for upload in one transaction, so a task is never changed without
 * being queued, and the outbox never holds a change that was rolled back.
 *
 * Writes validate against the stored tasks inside that transaction, and never throw for a bad
 * request. A rejected write writes nothing, logs a warning, and returns null or false. A write
 * that would change nothing, or that completes or deletes a task that is gone, is a no-op: it
 * writes nothing, not even an outbox row. Database failures still throw a [SQLiteException],
 * after rolling back the transaction.
 *
 * Only tasks that are not deleted can be edited, because they are the only ones the UI can show.
 *
 * Sync uploads the outbox later with [uploadPendingChanges] and downloads the server's changes
 * with [downloadChanges], and WorkManager decides when to run them and when to retry. Network
 * calls are never made inside a database transaction, so local writes are never blocked on the
 * network. Instead, each outbox row's revision is checked after the call, so an edit made during
 * an upload is never lost.
 */
@Singleton
class TodoTasksRepository @Inject constructor(
    private val tasksLocalDataSource: TodoTasksLocalDataSource,
    private val syncLocalDataSource: TodoTaskSyncLocalDataSource,
    private val tasksNetworkDataSource: TodoTasksNetworkDataSource,
    private val conflictResolver: ConflictResolver,
    private val todoTaskDatabase: TodoTaskDatabase,
    private val clock: Clock,
) {

    /** Observes the tasks that are not deleted, in display order. */
    fun observeTasks(): Flow<List<TodoTask>> = tasksLocalDataSource.observeTasks()

    /** Observes the task with [id], emitting null if it does not exist or is deleted. */
    fun observeTask(id: String): Flow<TodoTask?> = tasksLocalDataSource.observeTask(id)

    /**
     * Creates an incomplete task and queues it for upload.
     *
     * @return The ID of the new task, or null if it was rejected because [title] is blank.
     * @throws SQLiteException if the database write fails. Nothing is written.
     */
    suspend fun createTask(title: String, description: String = "", dueAt: Long? = null): String? {
        if (title.isBlank()) {
            Log.w(TAG, "Rejected createTask: blank title")
            return null
        }
        return todoTaskDatabase.withWriteTransaction {
            val now = clock.millis()
            val task = TodoTask(
                id = UUID.randomUUID().toString(),
                title = title,
                description = description,
                createdAt = now,
                lastUpdatedAt = now,
                deletedAt = null,
                dueAt = dueAt,
                isComplete = false,
                version = null,
            )
            saveEdit(previous = null, edited = task)
            task.id
        }
    }

    /**
     * Replaces the title, description, and due date of the task with [id], and queues the change
     * for upload. Does nothing if the values are unchanged.
     *
     * @return True if the edit was saved or changed nothing, or false if nothing was saved because
     * [title] is blank or the task is missing or deleted.
     * @throws SQLiteException if the database write fails. Nothing is written.
     */
    suspend fun updateTask(id: String, title: String, description: String, dueAt: Long?): Boolean {
        if (title.isBlank()) {
            Log.w(TAG, "Rejected updateTask: blank title")
            return false
        }
        return todoTaskDatabase.withWriteTransaction {
            val previous = getLiveTask(id)
            if (previous == null) {
                Log.w(TAG, "Rejected updateTask: task $id is missing or deleted")
                return@withWriteTransaction false
            }
            saveEditIfChanged(
                previous,
                previous.copy(title = title, description = description, dueAt = dueAt),
            )
            true
        }
    }

    /**
     * Marks the task with [id] complete or incomplete, and queues the change for upload. Does
     * nothing if it is already in that state.
     *
     * @return True if the change was saved or changed nothing, or false if nothing was saved
     * because the task is missing or deleted.
     * @throws SQLiteException if the database write fails. Nothing is written.
     */
    suspend fun setComplete(id: String, isComplete: Boolean): Boolean =
        todoTaskDatabase.withWriteTransaction {
            val previous = getLiveTask(id)
            if (previous == null) {
                Log.w(TAG, "Rejected setComplete: task $id is missing or deleted")
                return@withWriteTransaction false
            }
            saveEditIfChanged(previous, previous.copy(isComplete = isComplete))
            true
        }

    /**
     * Tombstones the task with [id] and queues the delete for upload. Does nothing if the task is
     * missing or already deleted.
     *
     * @throws SQLiteException if the database write fails. Nothing is written.
     */
    suspend fun deleteTask(id: String) {
        todoTaskDatabase.withWriteTransaction {
            val previous = getLiveTask(id) ?: return@withWriteTransaction
            val now = clock.millis()
            saveEdit(previous, previous.copy(deletedAt = now, lastUpdatedAt = now))
        }
    }

    /**
     * Uploads each pending outbox row once, oldest first. Failed rows are skipped until a new edit
     * clears their failure.
     *
     * Each task is sent with its version as the base version, and the outcome is saved in one
     * transaction:
     * - Accepted: the stored task replaces the local one and the row is deleted. If the task was
     *   edited during the upload, the edit is kept instead and rebased onto the stored version, so
     *   it is uploaded on the next sync.
     * - Conflict: the local task is merged with the server's using [ConflictResolver], and takes
     *   the server's version. If the merge is the server's task, nothing is left to upload and the
     *   row is deleted. Otherwise the row stays pending, so the merge is uploaded on the next sync.
     * - Rejected with a 4xx status: the server will keep rejecting the row, so it is marked
     *   failed, a warning is logged, and the next row is uploaded.
     *
     * Any other failure stops the upload. Rows already uploaded stay uploaded, and the rest stay
     * pending, so calling this again later picks up where it stopped. This never retries an
     * upload itself; the caller schedules the next attempt.
     *
     * @return True if no rows are left pending, or false if some still need uploading, such as a
     * merge after a conflict or an edit made during this sync, so the caller should sync again.
     * @throws IOException if the server can't be reached.
     * @throws ResponseException if the server responds with a 5xx status.
     * @throws JsonConvertException if a response body isn't the expected JSON.
     * @throws SQLiteException if a database read or write fails. The failed transaction writes
     * nothing.
     */
    suspend fun uploadPendingChanges(): Boolean {
        for (update in syncLocalDataSource.getPendingTaskUpdates()) {
            uploadTaskUpdate(update.taskId)
        }
        return syncLocalDataSource.getPendingTaskUpdates().isEmpty()
    }

    /**
     * Downloads every task that changed on the server since the stored cursor, and saves them.
     *
     * Each downloaded task is saved according to its outbox row:
     * - No row: nothing local is pending, so the server's task replaces the local one.
     * - A row, pending or failed, and the local task has the server's version: the local edit is
     *   already based on the server's task, such as an accepted upload echoed back, so it is kept.
     * - A row, and the versions differ: the local task is merged with the server's using
     *   [ConflictResolver], as after an upload conflict. If the row is pending, the merge is
     *   uploaded by the next [uploadPendingChanges].
     *
     * The download is made outside any transaction. The tasks and the new cursor are then saved in
     * one transaction, so a crash never skips a download or saves only part of one. This never
     * retries a download itself; the caller schedules the next attempt.
     *
     * @throws IOException if the server can't be reached.
     * @throws ResponseException if the server responds with a non-2xx status.
     * @throws JsonConvertException if the response body isn't the expected JSON.
     * @throws SQLiteException if the database write fails. Nothing is written, and the cursor is
     * unchanged.
     */
    suspend fun downloadChanges() {
        val changes = tasksNetworkDataSource.getTasksSince(syncLocalDataSource.getCursor())
        todoTaskDatabase.withWriteTransaction {
            for (remote in changes.tasks) {
                saveDownloadedTask(remote)
            }
            syncLocalDataSource.setCursor(changes.cursor)
        }
    }

    /** Uploads the outbox row for [taskId] once, and saves the outcome. */
    private suspend fun uploadTaskUpdate(taskId: String) {
        // Read both together, so the task is exactly the copy at the row's revision.
        val (task, revision) = todoTaskDatabase.withReadTransaction {
            val update = checkNotNull(syncLocalDataSource.getTaskUpdate(taskId)) {
                "Task $taskId has no outbox row"
            }
            getQueuedTask(taskId) to update.revision
        }
        val result = try {
            tasksNetworkDataSource.putTask(task)
        } catch (e: ResponseException) {
            // Retrying a 4xx gets the same answer, so stop retrying this row. A 5xx may pass later.
            if (e.response.status.value !in 400..499) throw e
            Log.w(TAG, "Server rejected the upload of task $taskId: ${e.response.status}", e)
            syncLocalDataSource.markTaskUpdateFailedIfRevisionMatches(
                taskId,
                revision,
                clock.millis(),
            )
            return
        }
        when (result) {
            is UploadResult.Accepted -> saveAcceptedUpload(revision, stored = result.task)
            is UploadResult.Conflict -> saveMergedConflict(remote = result.current)
        }
    }

    /**
     * Saves [stored], the server's copy of an accepted upload of the outbox row at [revision].
     *
     * If the row is still at [revision], the server has every local change, so [stored] replaces
     * the local task and the row is deleted. Otherwise the task was edited during the upload. The
     * edit is kept and rebased onto [stored]: the task takes its version, and [stored] becomes the
     * row's base. The row stays pending.
     */
    private suspend fun saveAcceptedUpload(revision: Long, stored: TodoTask) {
        todoTaskDatabase.withWriteTransaction {
            val update = checkNotNull(syncLocalDataSource.getTaskUpdate(stored.id)) {
                "Task ${stored.id} has no outbox row"
            }
            if (update.revision == revision) {
                tasksLocalDataSource.upsertTask(stored)
                syncLocalDataSource.deleteTaskUpdateIfRevisionMatches(stored.id, revision)
            } else {
                val local = getQueuedTask(stored.id)
                tasksLocalDataSource.upsertTask(local.copy(version = stored.version))
                syncLocalDataSource.upsertTaskUpdate(update.copy(base = stored.asEntity()))
            }
        }
    }

    /**
     * Merges the local task with [remote], the server's copy after a conflict, and saves the
     * merge. See [mergeIntoQueuedTask].
     */
    private suspend fun saveMergedConflict(remote: TodoTask) {
        todoTaskDatabase.withWriteTransaction {
            val update = checkNotNull(syncLocalDataSource.getTaskUpdate(remote.id)) {
                "Task ${remote.id} has no outbox row"
            }
            mergeIntoQueuedTask(update, remote)
        }
    }

    /**
     * Saves [remote], a downloaded task, as described in [downloadChanges]. Must run inside a
     * write transaction.
     */
    private suspend fun saveDownloadedTask(remote: TodoTask) {
        val update = syncLocalDataSource.getTaskUpdate(remote.id)
        when {
            update == null -> tasksLocalDataSource.upsertTask(remote)
            // The server has nothing the local edit isn't already based on.
            getQueuedTask(remote.id).version == remote.version -> Unit
            else -> mergeIntoQueuedTask(update, remote)
        }
    }

    /**
     * Merges the local task, which has the outbox row [update], with [remote], the server's newer
     * copy, and saves the merge, which carries the server's version. Must run inside a write
     * transaction.
     *
     * The row's base is the common ancestor. A row with no base is a task that was never synced,
     * but the server already has it, such as when an earlier upload's response was lost. Then
     * [remote] stands in for the base, so every local field that differs from it wins.
     *
     * If the merge is [remote], nothing is left to upload and the row is deleted. Otherwise
     * [remote] becomes the row's base, and the row keeps its revision and failure, so a pending row
     * uploads the merge next.
     */
    private suspend fun mergeIntoQueuedTask(update: TodoTaskUpdateEntity, remote: TodoTask) {
        val base = update.base?.asTodoTask() ?: remote
        val merged = conflictResolver.resolve(base, getQueuedTask(remote.id), remote)
        tasksLocalDataSource.upsertTask(merged)
        if (merged == remote) {
            syncLocalDataSource.deleteTaskUpdateIfRevisionMatches(remote.id, update.revision)
        } else {
            syncLocalDataSource.upsertTaskUpdate(update.copy(base = remote.asEntity()))
        }
    }

    /**
     * Returns the task with [id], which has an outbox row. Tasks are only tombstoned, never
     * removed, so a task with a row always exists.
     */
    private suspend fun getQueuedTask(id: String): TodoTask =
        checkNotNull(tasksLocalDataSource.getTask(id)) { "Task $id has an outbox row but no task" }

    /** Returns the task with [id], or null if it does not exist or is deleted. */
    private suspend fun getLiveTask(id: String): TodoTask? =
        tasksLocalDataSource.getTask(id)?.takeIf { it.deletedAt == null }

    /** Saves [edited] with a new `lastUpdatedAt`, unless it is the same as [previous]. */
    private suspend fun saveEditIfChanged(previous: TodoTask, edited: TodoTask) {
        if (edited != previous) saveEdit(previous, edited.copy(lastUpdatedAt = clock.millis()))
    }

    /**
     * Writes [edited] and records the edit in the outbox. Must run inside a write transaction.
     *
     * If the task already has an outbox row, its base is kept, because that is still the last
     * copy the server agreed on. Its revision is incremented, so an upload of the older revision
     * doesn't clear the row, and its failure is cleared, so the new edit is retried.
     *
     * Otherwise a row at revision 1 is created, with [previous] as the base if it has been synced.
     * A task that has never been synced, including a new one, has no base.
     *
     * @param previous The task as it was before this edit, or null if it is being created.
     */
    private suspend fun saveEdit(previous: TodoTask?, edited: TodoTask) {
        tasksLocalDataSource.upsertTask(edited)
        val pending = syncLocalDataSource.getTaskUpdate(edited.id)
        syncLocalDataSource.upsertTaskUpdate(
            pending?.copy(revision = pending.revision + 1, failedAt = null)
                ?: TodoTaskUpdateEntity(
                    taskId = edited.id,
                    base = previous?.takeIf { it.version != null }?.asEntity(),
                    revision = 1L,
                    failedAt = null,
                ),
        )
    }
}
