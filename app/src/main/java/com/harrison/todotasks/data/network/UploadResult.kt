package com.harrison.todotasks.data.network

import com.harrison.todotasks.data.model.TodoTask

/**
 * The outcome of uploading a task with [TodoTasksNetworkDataSource.putTask]. Failures are thrown
 * instead.
 */
sealed interface UploadResult {

    /** The server stored the upload as [task], which carries its new version. */
    data class Accepted(val task: TodoTask) : UploadResult

    /** The server's task, [current], has changed since the upload's base version. */
    data class Conflict(val current: TodoTask) : UploadResult
}
