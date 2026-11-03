package com.harrison.todotasks.data.local

import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.harrison.todotasks.data.local.entities.TodoTaskEntity
import com.harrison.todotasks.data.local.entities.TodoTaskUpdateEntity
import com.harrison.todotasks.data.model.TodoTask

/** Builds an in-memory database with the same driver as production. */
fun inMemoryTodoTaskDatabase(): TodoTaskDatabase =
    Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        TodoTaskDatabase::class.java,
    )
        .setDriver(AndroidSQLiteDriver())
        .build()

fun todoTaskEntity(
    id: String,
    createdAt: Long = 1_000L,
    deletedAt: Long? = null,
    dueAt: Long? = null,
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
    isComplete = isComplete,
    version = null,
)

fun todoTaskUpdate(
    taskId: String,
    base: TodoTaskEntity? = null,
    revision: Long = 1L,
    failedAt: Long? = null,
): TodoTaskUpdateEntity = TodoTaskUpdateEntity(
    taskId = taskId,
    base = base,
    revision = revision,
    failedAt = failedAt,
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
    isComplete = true,
    version = 5L,
)

/** A task with null in every nullable field. */
val sparseTask = fullTask.copy(
    deletedAt = null,
    dueAt = null,
    isComplete = false,
    version = null,
)
