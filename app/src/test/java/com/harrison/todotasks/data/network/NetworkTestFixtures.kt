package com.harrison.todotasks.data.network

import com.harrison.todotasks.data.di.NetworkModule
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

const val TEST_BASE_URL = "http://sync.test/"

/**
 * Builds a [TodoTasksNetworkDataSource] whose client has the production configuration, but answers
 * every request with [respond]. Requests are recorded in [requests].
 */
class MockServer(
    private val respond: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
) {
    private val recorded = mutableListOf<HttpRequestData>()

    /** Every request sent so far, oldest first. */
    val requests: List<HttpRequestData> get() = recorded

    val dataSource = TodoTasksNetworkDataSource(
        NetworkModule.createHttpClient(
            engine = MockEngine { request ->
                recorded += request
                respond(request)
            },
            baseUrl = TEST_BASE_URL,
        ),
    )
}

/** Responds with [json] as the body and [status]. */
fun MockRequestHandleScope.respondJson(
    json: String,
    status: HttpStatusCode = HttpStatusCode.OK,
): HttpResponseData = respond(
    content = json,
    status = status,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)

/** Server JSON for a task with a value in every field, plus a key the app doesn't know. */
const val FULL_TASK_JSON = """
{
  "id": "task-id",
  "title": "Title",
  "description": "Description",
  "createdAt": 1,
  "lastUpdatedAt": 2,
  "deletedAt": 3,
  "dueAt": 4,
  "isComplete": true,
  "version": 5,
  "addedLater": "ignored"
}
"""

/** Server JSON for a live task with null in every nullable field. */
const val SPARSE_TASK_JSON = """
{
  "id": "task-id",
  "title": "Title",
  "description": "Description",
  "createdAt": 1,
  "lastUpdatedAt": 2,
  "deletedAt": null,
  "dueAt": null,
  "isComplete": false,
  "version": 6
}
"""
