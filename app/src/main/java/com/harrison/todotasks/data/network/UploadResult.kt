package com.harrison.todotasks.data.network

import com.harrison.todotasks.data.model.TodoTask

/**
 * The outcome of uploading a task with [TodoTasksNetworkDataSource.putTask].
 *
 * A conflict is part of the sync protocol rather than an error, so it is a result. Real failures,
 * such as no network or a server error, are thrown instead.
 */
sealed interface UploadResult {

    /** The server stored the upload as [task], which carries its new version. */
    data class Accepted(val task: TodoTask) : UploadResult

    /**
     * The server rejected the upload because its task has changed since the base version. [current]
     * is the server's task, to merge with the local edit.
     */
    data class Conflict(val current: TodoTask) : UploadResult
}
