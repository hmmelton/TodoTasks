package com.harrison.todotasks.data.network

import com.harrison.todotasks.data.model.TodoTask

/**
 * Tasks downloaded with [TodoTasksNetworkDataSource.getTasksSince].
 *
 * @property tasks Every task, tombstones included, that changed on the server after the cursor
 * the download started from. Each has a non-null `version`.
 * @property cursor The cursor to store once [tasks] are saved, and to send on the next download.
 */
data class TodoTaskChanges(
    val tasks: List<TodoTask>,
    val cursor: Long,
)
