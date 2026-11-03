package com.harrison.todotasks.data.model

/**
 * A to-do item as exposed by the data layer.
 *
 * All timestamps are epoch milliseconds in UTC.
 *
 * @property id Client-generated UUID, so tasks can be created while offline.
 * @property title Short summary of the task.
 * @property description Longer free-form details.
 * @property createdAt When the task was created.
 * @property lastUpdatedAt When the task was last modified.
 * @property deletedAt When the task was deleted, or null if it is not deleted. A non-null value is
 * a tombstone, which keeps the deletion around long enough to sync it.
 * @property dueAt When the task is due, or null if it has no due date.
 * @property parentTaskId ID of the parent task, or null for a top-level task. Set at creation and
 * never changed.
 * @property isComplete Whether the task has been completed.
 * @property version Server-assigned version, or null if the task has never been synced.
 */
data class TodoTask(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val lastUpdatedAt: Long,
    val deletedAt: Long?,
    val dueAt: Long?,
    val parentTaskId: String?,
    val isComplete: Boolean,
    val version: Long?,
)
