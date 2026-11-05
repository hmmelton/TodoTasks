package com.harrison.todotasks.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.harrison.todotasks.data.local.entities.TodoTaskUpdateEntity

/** Reads and writes the `todo_task_updates` outbox table. */
@Dao
interface TodoTaskUpdateDao {

    /** Returns the update row for [taskId], or null if the task has no pending changes. */
    @Query("SELECT * FROM todo_task_updates WHERE task_id = :taskId")
    suspend fun getUpdate(taskId: String): TodoTaskUpdateEntity?

    /**
     * Returns the rows waiting for upload, oldest first, excluding failed rows. Age is the implicit
     * `rowid`, so a row keeps its place when [upsert] updates it.
     */
    @Query("SELECT * FROM todo_task_updates WHERE failed_at IS NULL ORDER BY rowid ASC")
    suspend fun getPendingUpdates(): List<TodoTaskUpdateEntity>

    /** Inserts [update], or replaces the row for the same task. */
    @Upsert
    suspend fun upsert(update: TodoTaskUpdateEntity)

    /**
     * Deletes the row for [taskId] only if it is still at [revision].
     *
     * @return The number of rows deleted, which is 0 if the task was edited or has no row.
     */
    @Query("DELETE FROM todo_task_updates WHERE task_id = :taskId AND revision = :revision")
    suspend fun deleteIfRevisionMatches(taskId: String, revision: Long): Int

    /** Records that uploading the row for [taskId] failed, only if it is still at [revision]. */
    @Query(
        "UPDATE todo_task_updates SET failed_at = :failedAt " +
            "WHERE task_id = :taskId AND revision = :revision",
    )
    suspend fun markFailedIfRevisionMatches(taskId: String, revision: Long, failedAt: Long)
}
