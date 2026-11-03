package com.harrison.todotasks.data.local

import com.harrison.todotasks.data.local.entities.TodoTaskEntity
import com.harrison.todotasks.data.model.TodoTask

fun todoTaskEntity(
    id: String,
    createdAt: Long = 1_000L,
    deletedAt: Long? = null,
    dueAt: Long? = null,
    parentTaskId: String? = null,
    isComplete: Boolean = false,
    title: String = "Title $id",
): TodoTaskEntity = TodoTaskEntity(
    id = id,
    title = title,
    description = "Description $id",
    createdAt = createdAt,
    lastUpdatedAt = createdAt,
    deletedAt = deletedAt,
    dueAt = dueAt,
    parentTaskId = parentTaskId,
    isComplete = isComplete,
    version = null,
)

/** A task with a non-null value in every field. */
val fullTask = TodoTask(
    id = "task-id",
    title = "Title",
    description = "Description",
    createdAt = 1L,
    lastUpdatedAt = 2L,
    deletedAt = 3L,
    dueAt = 4L,
    parentTaskId = "parent-id",
    isComplete = true,
    version = 5L,
)

/** A task with null in every nullable field. */
val sparseTask = fullTask.copy(
    deletedAt = null,
    dueAt = null,
    parentTaskId = null,
    isComplete = false,
    version = null,
)
