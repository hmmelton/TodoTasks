package com.harrison.todotasks.data.local.dao

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Upsert
import com.harrison.todotasks.data.local.entities.TodoTaskEntity
import kotlinx.coroutines.flow.Flow

/** A task is visible unless it or its parent is tombstoned. A parent not yet downloaded is fine. */
private const val IS_VISIBLE = """
    deleted_at IS NULL
    AND NOT EXISTS (
        SELECT 1 FROM todo_tasks AS parent
        WHERE parent.id = todo_tasks.parent_task_id AND parent.deleted_at IS NOT NULL
    )
"""

/** Incomplete tasks first, then by due date with undated tasks last, then oldest first. */
private const val DISPLAY_ORDER = "is_complete ASC, due_at IS NULL ASC, due_at ASC, created_at ASC"

/** Reads and writes the `todo_tasks` table. */
@Dao
interface TodoTaskDao {

    /** Observes visible tasks that have no parent, in display order. */
    @Query(
        "SELECT * FROM todo_tasks WHERE deleted_at IS NULL AND parent_task_id IS NULL " +
            "ORDER BY $DISPLAY_ORDER",
    )
    fun observeTopLevelTasks(): Flow<List<TodoTaskEntity>>

    /** Observes the visible subtasks of [parentId], in display order. */
    @Query(
        "SELECT * FROM todo_tasks WHERE parent_task_id = :parentId AND $IS_VISIBLE " +
            "ORDER BY $DISPLAY_ORDER",
    )
    fun observeSubtasks(parentId: String): Flow<List<TodoTaskEntity>>

    /** Observes the task with [id], emitting null if it does not exist or is not visible. */
    @Query("SELECT * FROM todo_tasks WHERE id = :id AND $IS_VISIBLE")
    fun observeTask(id: String): Flow<TodoTaskEntity?>

    /**
     * Returns the task with [id], or null if it does not exist. Tombstoned tasks are included,
     * because sync needs to read them.
     */
    @Query("SELECT * FROM todo_tasks WHERE id = :id")
    suspend fun getTask(id: String): TodoTaskEntity?

    /**
     * Returns every subtask of [parentId], in no particular order. Hidden subtasks are included,
     * so a delete can reach all of them.
     */
    @Query("SELECT * FROM todo_tasks WHERE parent_task_id = :parentId")
    suspend fun getSubtasks(parentId: String): List<TodoTaskEntity>

    /** Inserts [task], or replaces the row with the same ID. */
    @Upsert
    suspend fun upsert(task: TodoTaskEntity)

    /** Inserts [tasks], replacing any rows with the same IDs. */
    @Upsert
    suspend fun upsert(tasks: List<TodoTaskEntity>)
}
