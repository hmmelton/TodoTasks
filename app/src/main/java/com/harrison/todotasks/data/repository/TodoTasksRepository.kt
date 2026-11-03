package com.harrison.todotasks.data.repository

import android.util.Log
import androidx.room3.withWriteTransaction
import androidx.sqlite.SQLiteException
import com.harrison.todotasks.data.local.TodoTaskDatabase
import com.harrison.todotasks.data.local.TodoTaskSyncLocalDataSource
import com.harrison.todotasks.data.local.TodoTasksLocalDataSource
import com.harrison.todotasks.data.local.entities.TodoTaskUpdateEntity
import com.harrison.todotasks.data.local.entities.asEntity
import com.harrison.todotasks.data.model.TodoTask
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
 */
@Singleton
class TodoTasksRepository @Inject constructor(
    private val tasksLocalDataSource: TodoTasksLocalDataSource,
    private val syncLocalDataSource: TodoTaskSyncLocalDataSource,
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
