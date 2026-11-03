package com.harrison.todotasks.data.network

import com.harrison.todotasks.data.local.fullTask
import com.harrison.todotasks.data.local.sparseTask
import com.harrison.todotasks.data.repository.assertFailsWith
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.ResponseException
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoTasksNetworkDataSourcePutTest {

    @Test
    fun `sends PUT to the task path with the task, its version as the base`() = runTest {
        val server = MockServer { respondJson(FULL_TASK_JSON) }

        server.dataSource.putTask(fullTask)

        val request = server.requests.single()
        assertEquals(HttpMethod.Put, request.method)
        assertEquals("${TEST_BASE_URL}tasks/task-id", request.url.toString())
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
        val expected = """
            {
              "id": "task-id",
              "title": "Title",
              "description": "Description",
              "createdAt": 1,
              "lastUpdatedAt": 2,
              "deletedAt": 3,
              "dueAt": 4,
              "isComplete": true,
              "version": 5
            }
        """
        assertEquals(
            Json.parseToJsonElement(expected),
            Json.parseToJsonElement(request.body.toByteArray().decodeToString()),
        )
    }

    @Test
    fun `never-synced task sends nulls for its version and empty fields`() = runTest {
        val server = MockServer { respondJson(SPARSE_TASK_JSON) }

        server.dataSource.putTask(sparseTask)

        val body = server.requests.single().body.toByteArray().decodeToString()
        val expected = """
            {
              "id": "task-id",
              "title": "Title",
              "description": "Description",
              "createdAt": 1,
              "lastUpdatedAt": 2,
              "deletedAt": null,
              "dueAt": null,
              "isComplete": false,
              "version": null
            }
        """
        assertEquals(Json.parseToJsonElement(expected), Json.parseToJsonElement(body))
    }

    @Test
    fun `id is encoded as a single path segment`() = runTest {
        val server = MockServer { respondJson(SPARSE_TASK_JSON) }

        server.dataSource.putTask(sparseTask.copy(id = "a b/c"))

        assertEquals("${TEST_BASE_URL}tasks/a%20b%2Fc", server.requests.single().url.toString())
    }

    @Test
    fun `200 is accepted with the stored task`() = runTest {
        val server = MockServer { respondJson(SPARSE_TASK_JSON) }

        val result = server.dataSource.putTask(sparseTask)

        assertEquals(UploadResult.Accepted(sparseTask.copy(version = 6L)), result)
    }

    @Test
    fun `409 is a conflict with the server's task`() = runTest {
        val server = MockServer { respondJson(FULL_TASK_JSON, HttpStatusCode.Conflict) }

        val result = server.dataSource.putTask(sparseTask.copy(version = 1L))

        assertEquals(UploadResult.Conflict(fullTask), result)
    }

    @Test
    fun `other statuses are thrown`() = runTest {
        for (status in listOf(HttpStatusCode.BadRequest, HttpStatusCode.InternalServerError)) {
            val server = MockServer { respondJson("{}", status) }

            val thrown = assertFailsWith<ResponseException> {
                server.dataSource.putTask(fullTask)
            }

            assertEquals(status, thrown.response.status)
        }
    }
}
