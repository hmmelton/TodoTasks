package com.harrison.todotasks.data.network

import com.harrison.todotasks.data.model.TodoTask
import com.harrison.todotasks.data.network.model.NetworkTodoTask
import com.harrison.todotasks.data.network.model.NetworkTodoTaskChanges
import com.harrison.todotasks.data.network.model.asNetworkTodoTask
import com.harrison.todotasks.data.network.model.asTodoTask
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.expectSuccess
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.encodeURLPathPart
import io.ktor.http.isSuccess
import io.ktor.serialization.JsonConvertException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Downloads and uploads tasks over the sync server's HTTP API. [httpClient] must resolve relative
 * URLs against the server and handle JSON, as the client from `NetworkModule` does.
 */
@Singleton
class TodoTasksNetworkDataSource @Inject constructor(
    private val httpClient: HttpClient,
) {

    /**
     * Downloads every task, tombstones included, whose server version is greater than [cursor].
     *
     * @throws IOException if the server can't be reached.
     * @throws ResponseException if the server responds with a non-2xx status.
     * @throws JsonConvertException if the response body isn't the expected JSON.
     */
    suspend fun getTasksSince(cursor: Long): TodoTaskChanges {
        val changes: NetworkTodoTaskChanges = httpClient.get("tasks") {
            expectSuccess = true
            parameter("since", cursor)
        }.body()
        return TodoTaskChanges(
            tasks = changes.tasks.map { it.asTodoTask() },
            cursor = changes.cursor,
        )
    }

    /**
     * Uploads [task] if the server's copy is still at the task's version, which is null if the
     * task has never been synced.
     *
     * @return [UploadResult.Accepted] with the stored task, or [UploadResult.Conflict] with the
     * server's task if it has changed since the task's version.
     * @throws IOException if the server can't be reached.
     * @throws ResponseException if the server responds with a status other than 2xx or 409.
     * @throws JsonConvertException if the response body isn't the expected JSON.
     */
    suspend fun putTask(task: TodoTask): UploadResult {
        val response = httpClient.put("tasks/${task.id.encodeURLPathPart()}") {
            // A 409 is an expected outcome, so check the status here instead of throwing.
            expectSuccess = false
            contentType(ContentType.Application.Json)
            setBody(task.asNetworkTodoTask())
        }
        return when {
            response.status == HttpStatusCode.Conflict ->
                UploadResult.Conflict(response.body<NetworkTodoTask>().asTodoTask())
            response.status.isSuccess() ->
                UploadResult.Accepted(response.body<NetworkTodoTask>().asTodoTask())
            else -> throw ResponseException(response, response.bodyAsText())
        }
    }
}
