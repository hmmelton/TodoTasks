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
import com.harrison.todotasks.data.sync.SyncScheduler
import com.harrison.todotasks.data.sync.SyncWorker
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
 * The entry point to task data, with offline-first writes. The local database is the source of
 * truth for reads.
 *
 * Each write changes the task and its outbox row in one transaction, then requests a sync once it
 * commits. Writes never throw for a bad request: a rejected write writes nothing, logs a warning,
 * and returns null or false, and a write that would change nothing writes nothing. Deleted tasks
 * can't be edited.
 *
 * [SyncWorker] runs [downloadChanges] and [uploadPendingChanges]. Network calls are never made
 * inside a transaction; outbox revisions are checked afterwards, so an edit made during an upload
 * is never lost.
 */
@Singleton
class TodoTasksRepository @Inject constructor(
    private val tasksLocalDataSource: TodoTasksLocalDataSource,
    private val syncLocalDataSource: TodoTaskSyncLocalDataSource,
    private val tasksNetworkDataSource: TodoTasksNetworkDataSource,
    private val conflictResolver: ConflictResolver,
    private val todoTaskDatabase: TodoTaskDatabase,
    private val clock: Clock,
    private val syncScheduler: SyncScheduler,
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
        val id = todoTaskDatabase.withWriteTransaction {
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
        syncScheduler.requestSync()
        return id
    }

    /**
     * Replaces the title, description, and due date of the task with [id], and queues the change
     * for upload.
     *
     * @return True if the edit was saved or changed nothing, or false if [title] is blank or the
     * task is missing or deleted.
     * @throws SQLiteException if the database write fails. Nothing is written.
     */
    suspend fun updateTask(id: String, title: String, description: String, dueAt: Long?): Boolean {
        if (title.isBlank()) {
            Log.w(TAG, "Rejected updateTask: blank title")
            return false
        }
        val result = todoTaskDatabase.withWriteTransaction {
            val previous = getLiveTask(id)
            if (previous == null) {
                Log.w(TAG, "Rejected updateTask: task $id is missing or deleted")
                return@withWriteTransaction EditResult.Rejected
            }
            saveEditIfChanged(
                previous,
                previous.copy(title = title, description = description, dueAt = dueAt),
            )
        }
        return requestSyncIfSaved(result)
    }

    /**
     * Marks the task with [id] complete or incomplete, and queues the change for upload.
     *
     * @return True if the change was saved or changed nothing, or false if the task is missing or
     * deleted.
     * @throws SQLiteException if the database write fails. Nothing is written.
     */
    suspend fun setComplete(id: String, isComplete: Boolean): Boolean {
        val result = todoTaskDatabase.withWriteTransaction {
            val previous = getLiveTask(id)
            if (previous == null) {
                Log.w(TAG, "Rejected setComplete: task $id is missing or deleted")
                return@withWriteTransaction EditResult.Rejected
            }
            saveEditIfChanged(previous, previous.copy(isComplete = isComplete))
        }
        return requestSyncIfSaved(result)
    }

    /**
     * Tombstones the task with [id] and queues the delete for upload. Does nothing if the task is
     * missing or already deleted.
     *
     * @throws SQLiteException if the database write fails. Nothing is written.
     */
    suspend fun deleteTask(id: String) {
        val deleted = todoTaskDatabase.withWriteTransaction {
            val previous = getLiveTask(id) ?: return@withWriteTransaction false
            val now = clock.millis()
            saveEdit(previous, previous.copy(deletedAt = now, lastUpdatedAt = now))
            true
        }
        if (deleted) syncScheduler.requestSync()
    }

    /**
     * Uploads each pending outbox row once, oldest first. Failed rows are skipped until a new edit
     * clears their failure. Each outcome is saved in its own transaction:
     * - Accepted: the stored task replaces the local one and the row is deleted, unless the task
     *   was edited during the upload. Then the edit is rebased onto the stored version and stays
     *   pending.
     * - Conflict: the local task is merged with the server's using [ConflictResolver]. The row is
     *   deleted if the merge is the server's task, and otherwise stays pending.
     * - 4xx: the row is marked failed and the next row is uploaded.
     *
     * Any other failure stops the upload, leaving the remaining rows pending. Never retries.
     *
     * @return True if no rows are left pending, or false if some still need uploading, such as a
     * merge after a conflict or an edit made during this sync.
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
     * Downloads every task that changed on the server since the stored cursor, and saves them with
     * the new cursor in one transaction. Each task is saved according to its outbox row:
     * - No row: the server's task replaces the local one.
     * - A row, and the local task has the server's version: the local edit is already based on
     *   it, so it is kept.
     * - A row, and the versions differ: the two are merged as after an upload conflict.
     *
     * Never retries.
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
            // A 4xx will keep failing, so stop retrying this row. A 5xx may pass later.
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
     * Saves [stored], the server's copy of an accepted upload of the outbox row at [revision]. If
     * the row has moved past [revision], the newer edit is kept and rebased onto [stored].
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

    /** Merges the local task with [remote], the server's copy after a conflict, and saves it. */
    private suspend fun saveMergedConflict(remote: TodoTask) {
        todoTaskDatabase.withWriteTransaction {
            val update = checkNotNull(syncLocalDataSource.getTaskUpdate(remote.id)) {
                "Task ${remote.id} has no outbox row"
            }
            mergeIntoQueuedTask(update, remote)
        }
    }

    /** Saves [remote], a downloaded task, as described in [downloadChanges]. Needs a transaction. */
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
     * copy, and saves the merge. Must run inside a write transaction.
     *
     * The row's base is the common ancestor. A row with no base is an unsynced task the server
     * already has, such as after a lost upload response, so [remote] stands in for the base.
     * If the merge is [remote] the row is deleted. Otherwise [remote] becomes the row's base, and
     * the row keeps its revision and failure.
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

    /** Returns the task with [id], which has an outbox row, so it always exists. */
    private suspend fun getQueuedTask(id: String): TodoTask =
        checkNotNull(tasksLocalDataSource.getTask(id)) { "Task $id has an outbox row but no task" }

    /** Returns the task with [id], or null if it does not exist or is deleted. */
    private suspend fun getLiveTask(id: String): TodoTask? =
        tasksLocalDataSource.getTask(id)?.takeIf { it.deletedAt == null }

    /** Saves [edited] with a new `lastUpdatedAt`, unless it is the same as [previous]. */
    private suspend fun saveEditIfChanged(previous: TodoTask, edited: TodoTask): EditResult {
        if (edited == previous) return EditResult.Unchanged
        saveEdit(previous, edited.copy(lastUpdatedAt = clock.millis()))
        return EditResult.Saved
    }

    /** Requests a sync if [result] saved something, and returns false only if it was rejected. */
    private fun requestSyncIfSaved(result: EditResult): Boolean {
        if (result == EditResult.Saved) syncScheduler.requestSync()
        return result != EditResult.Rejected
    }

    /**
     * Writes [edited] and records the edit in the outbox. Must run inside a write transaction.
     *
     * An existing row keeps its base, increments its revision, and clears its failure. A new row
     * starts at revision 1, with [previous] as the base only if it has been synced.
     *
     * @param previous The task before this edit, or null if it is being created.
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

/** The outcome of an edit to an existing task. */
private enum class EditResult {
    /** The edit was written and queued for upload. */
    Saved,

    /** The edit would change nothing, so nothing was written. */
    Unchanged,

    /** The task is missing or deleted, so nothing was written. */
    Rejected,
}
