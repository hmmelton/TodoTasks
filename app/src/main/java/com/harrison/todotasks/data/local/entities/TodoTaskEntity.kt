package com.harrison.todotasks.data.local.entities

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import com.harrison.todotasks.data.model.TodoTask

/** Local row for a [TodoTask]. See [TodoTask] for the meaning of each field. */
@Entity(tableName = "todo_tasks", indices = [Index(value = ["deleted_at"])])
data class TodoTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "last_updated_at") val lastUpdatedAt: Long,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long?,
    @ColumnInfo(name = "due_at") val dueAt: Long?,
    @ColumnInfo(name = "is_complete") val isComplete: Boolean,
    val version: Long?,
)

/** Maps this row to the model exposed by the data layer. */
fun TodoTaskEntity.asTodoTask(): TodoTask = TodoTask(
    id = id,
    title = title,
    description = description,
    createdAt = createdAt,
    lastUpdatedAt = lastUpdatedAt,
    deletedAt = deletedAt,
    dueAt = dueAt,
    isComplete = isComplete,
    version = version,
)

/** Maps this model to its local row. */
fun TodoTask.asEntity(): TodoTaskEntity = TodoTaskEntity(
    id = id,
    title = title,
    description = description,
    createdAt = createdAt,
    lastUpdatedAt = lastUpdatedAt,
    deletedAt = deletedAt,
    dueAt = dueAt,
    isComplete = isComplete,
    version = version,
)
