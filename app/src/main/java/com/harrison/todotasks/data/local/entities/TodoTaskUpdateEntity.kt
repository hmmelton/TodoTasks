package com.harrison.todotasks.data.local.entities

import androidx.room3.ColumnInfo
import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.RoomWarnings

/**
 * Outbox row recording that a task has local changes to upload. There is at most one row per task.
 *
 * What changed is derived by comparing the task with [base], so no operation or changed-field data
 * is stored. A delete is an ordinary edit that sets `deletedAt`.
 *
 * @property taskId ID of the task with pending changes.
 * @property base The last synced copy of the task, or null if it has never been synced. The base
 * version is `base.version`.
 * @property revision Incremented on each local edit, so an edit made during an upload is detected.
 * @property failedAt When the upload failed, or null if the row is still eligible for upload.
 */
// base reuses TodoTaskEntity so a new task column can't be missed here. Dropping its primary key
// and indexes is correct: task_id is the key, and nothing queries the base_ columns.
@Suppress(
    RoomWarnings.PRIMARY_KEY_FROM_EMBEDDED_IS_DROPPED,
    RoomWarnings.INDEX_FROM_EMBEDDED_ENTITY_IS_DROPPED,
)
@Entity(tableName = "todo_task_updates")
data class TodoTaskUpdateEntity(
    @PrimaryKey @ColumnInfo(name = "task_id") val taskId: String,
    @Embedded(prefix = "base_") val base: TodoTaskEntity?,
    val revision: Long,
    @ColumnInfo(name = "failed_at") val failedAt: Long?,
)
