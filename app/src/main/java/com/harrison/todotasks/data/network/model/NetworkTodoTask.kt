package com.harrison.todotasks.data.network.model

import com.harrison.todotasks.data.model.TodoTask
import kotlinx.serialization.Serializable

/**
 * A task on the wire, in both directions. See [TodoTask] for the meaning of each field.
 *
 * @property version In an upload, the server version the edit is based on, or null if the task has
 * never been synced; the server compares it with its own copy and never stores it. In a response,
 * the version the server assigned, which is never null in practice.
 */
@Serializable
data class NetworkTodoTask(
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

/**
 * Body of the `GET /tasks?since={cursor}` response.
 *
 * @property tasks Every task, tombstones included, whose version is greater than the cursor.
 * @property cursor The server's highest version, read together with [tasks].
 */
@Serializable
data class NetworkTodoTaskChanges(
    val tasks: List<NetworkTodoTask>,
    val cursor: Long,
)

/** Maps this server task to the model exposed by the data layer. */
fun NetworkTodoTask.asTodoTask(): TodoTask = TodoTask(
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

/** Maps this model to a server task, for upload. */
fun TodoTask.asNetworkTodoTask(): NetworkTodoTask = NetworkTodoTask(
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
