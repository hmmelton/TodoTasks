package com.harrison.todotasks.data.local

import com.harrison.todotasks.data.local.dao.SyncStateDao
import com.harrison.todotasks.data.local.dao.TodoTaskUpdateDao
import com.harrison.todotasks.data.local.entities.TodoTaskUpdateEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local storage for sync bookkeeping: the outbox of task updates waiting for upload, and the
 * cursor for downloads.
 *
 * This is a thin layer over the DAOs. When the outbox changes is decided by its callers. It does
 * not open transactions; a write made inside a transaction opened on [TodoTaskDatabase] joins it.
 */
@Singleton
class TodoTaskSyncLocalDataSource @Inject constructor(
    private val todoTaskUpdateDao: TodoTaskUpdateDao,
    private val syncStateDao: SyncStateDao,
) {

    /** Returns the update row for [taskId], or null if the task has no pending changes. */
    suspend fun getTaskUpdate(taskId: String): TodoTaskUpdateEntity? =
        todoTaskUpdateDao.getUpdate(taskId)

    /** Returns the rows waiting for upload, oldest first, excluding failed rows. */
    suspend fun getPendingTaskUpdates(): List<TodoTaskUpdateEntity> =
        todoTaskUpdateDao.getPendingUpdates()

    /** Inserts [update], or replaces the row for the same task. */
    suspend fun upsertTaskUpdate(update: TodoTaskUpdateEntity) = todoTaskUpdateDao.upsert(update)

    /**
     * Deletes the update row for [taskId] only if it is still at [revision].
     *
     * @return The number of rows deleted, which is 0 if the task was edited or has no row.
     */
    suspend fun deleteTaskUpdateIfRevisionMatches(taskId: String, revision: Long): Int =
        todoTaskUpdateDao.deleteIfRevisionMatches(taskId, revision)

    /** Records that uploading the row for [taskId] failed, only if it is still at [revision]. */
    suspend fun markTaskUpdateFailedIfRevisionMatches(
        taskId: String,
        revision: Long,
        failedAt: Long,
    ) = todoTaskUpdateDao.markFailedIfRevisionMatches(taskId, revision, failedAt)

    /** Returns the highest server version seen, or 0 if nothing has been synced. */
    suspend fun getCursor(): Long = syncStateDao.getCursor()

    /** Stores [cursor] as the highest server version seen. */
    suspend fun setCursor(cursor: Long) = syncStateDao.setCursor(cursor)
}
