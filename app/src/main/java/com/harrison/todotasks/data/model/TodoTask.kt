package com.harrison.todotasks.data.model

/**
 * A to-do item as exposed by the data layer. Timestamps are epoch milliseconds in UTC.
 *
 * @property id Client-generated UUID.
 * @property deletedAt When the task was deleted, or null if it is not. A non-null value is a
 * tombstone, kept so the delete can sync.
 * @property dueAt When the task is due, or null if it has no due date.
 * @property version Server-assigned version, or null if the task has never been synced. Local
 * edits never change it.
 */
data class TodoTask(
    val id: String,
    val title: String,
    val description: String,
    val createdAt: Long,
    val lastUpdatedAt: Long,
    val deletedAt: Long?,
    val dueAt: Long?,
    val isComplete: Boolean,
    val version: Long?,
)
