package com.harrison.todotasks.data.network

import com.harrison.todotasks.data.local.fullTask
import com.harrison.todotasks.data.local.sparseTask
import com.harrison.todotasks.data.repository.assertFailsWith
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TodoTasksNetworkDataSourceGetTest {

    @Test
    fun `sends GET tasks with the cursor as since`() = runTest {
        val server = MockServer { respondJson("""{"tasks": [], "cursor": 7}""") }

        server.dataSource.getTasksSince(cursor = 7L)

        val request = server.requests.single()
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("${TEST_BASE_URL}tasks?since=7", request.url.toString())
    }

    @Test
    fun `maps every task, tombstones and nulls included, and the cursor`() = runTest {
        val server = MockServer {
            respondJson("""{"tasks": [$FULL_TASK_JSON, $SPARSE_TASK_JSON], "cursor": 9}""")
        }

        val changes = server.dataSource.getTasksSince(cursor = 0L)

        assertEquals(
            TodoTaskChanges(tasks = listOf(fullTask, sparseTask.copy(version = 6L)), cursor = 9L),
            changes,
        )
    }

    @Test
    fun `empty download keeps the server cursor`() = runTest {
        val server = MockServer { respondJson("""{"tasks": [], "cursor": 3}""") }

        assertEquals(
            TodoTaskChanges(tasks = emptyList(), cursor = 3L),
            server.dataSource.getTasksSince(cursor = 3L),
        )
    }

    @Test
    fun `server error is thrown`() = runTest {
        val server = MockServer { respondJson("{}", HttpStatusCode.InternalServerError) }

        val thrown = assertFailsWith<ResponseException> { server.dataSource.getTasksSince(0L) }

        assertEquals(HttpStatusCode.InternalServerError, thrown.response.status)
    }
}
