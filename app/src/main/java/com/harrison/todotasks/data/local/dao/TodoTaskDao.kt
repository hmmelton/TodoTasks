package com.harrison.todotasks.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.harrison.todotasks.data.local.entities.TodoTaskEntity
import kotlinx.coroutines.flow.Flow

/** Incomplete tasks first, then by due date with undated tasks last, then oldest first. */
private const val DISPLAY_ORDER = "is_complete ASC, due_at IS NULL ASC, due_at ASC, created_at ASC"

/** Reads and writes the `todo_tasks` table. */
@Dao
interface TodoTaskDao {

    /** Observes the tasks that are not tombstoned, in display order. */
    @Query("SELECT * FROM todo_tasks WHERE deleted_at IS NULL ORDER BY $DISPLAY_ORDER")
    fun observeTasks(): Flow<List<TodoTaskEntity>>

    /** Observes the task with [id], emitting null if it does not exist or is tombstoned. */
    @Query("SELECT * FROM todo_tasks WHERE id = :id AND deleted_at IS NULL")
    fun observeTask(id: String): Flow<TodoTaskEntity?>

    /**
     * Returns the task with [id], or null if it does not exist. Tombstoned tasks are included,
     * because sync needs to read them.
     */
    @Query("SELECT * FROM todo_tasks WHERE id = :id")
    suspend fun getTask(id: String): TodoTaskEntity?

    /** Inserts [task], or replaces the row with the same ID. */
    @Upsert
    suspend fun upsert(task: TodoTaskEntity)

    /** Inserts [tasks], replacing any rows with the same IDs. */
    @Upsert
    suspend fun upsert(tasks: List<TodoTaskEntity>)
}
