package com.harrison.todotasks.data.repository

import androidx.room3.executeSQL
import androidx.room3.useReaderConnection
import androidx.room3.useWriterConnection
import com.harrison.todotasks.data.local.TodoTaskDatabase
import com.harrison.todotasks.data.local.TodoTaskSyncLocalDataSource
import com.harrison.todotasks.data.local.TodoTasksLocalDataSource
import com.harrison.todotasks.data.local.inMemoryTodoTaskDatabase
import com.harrison.todotasks.data.local.sparseTask
import com.harrison.todotasks.data.model.TodoTask
import com.harrison.todotasks.data.network.MockServer
import com.harrison.todotasks.data.sync.ConflictResolver
import com.harrison.todotasks.data.sync.SyncScheduler
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** A UTC [Clock] whose time the test sets. */
class MutableClock(var nowMillis: Long = 1_000L) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = throw UnsupportedOperationException()
    override fun instant(): Instant = Instant.ofEpochMilli(nowMillis)
    override fun millis(): Long = nowMillis
}

/**
 * Builds a [TodoTasksRepository] over a real in-memory database and real data sources, with a
 * clock the test controls, a [server] that answers with [respond], and a mock [syncScheduler] that
 * records the syncs requested. Tests seed and inspect storage through [tasks] and [sync].
 */
@RunWith(RobolectricTestRunner::class)
abstract class RepositoryTest {

    protected val clock = MutableClock()
    protected val syncScheduler = mockk<SyncScheduler>(relaxUnitFun = true)
    protected lateinit var todoTaskDatabase: TodoTaskDatabase
    protected lateinit var tasks: TodoTasksLocalDataSource
    protected lateinit var sync: TodoTaskSyncLocalDataSource
    protected lateinit var repository: TodoTasksRepository

    /** Answers each request the repository sends. By default, any request fails the test. */
    protected var respond: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData =
        { request -> throw AssertionError("Unexpected request: ${request.url}") }

    /** The sync server, which records each request and answers it with [respond]. */
    protected val server = MockServer { request -> respond(request) }

    @Before
    fun setUpRepository() {
        todoTaskDatabase = inMemoryTodoTaskDatabase()
        tasks = TodoTasksLocalDataSource(todoTaskDao = todoTaskDatabase.todoTaskDao())
        sync = TodoTaskSyncLocalDataSource(
            todoTaskUpdateDao = todoTaskDatabase.todoTaskUpdateDao(),
            syncStateDao = todoTaskDatabase.syncStateDao(),
        )
        repository = TodoTasksRepository(
            tasksLocalDataSource = tasks,
            syncLocalDataSource = sync,
            tasksNetworkDataSource = server.dataSource,
            conflictResolver = ConflictResolver(),
            todoTaskDatabase = todoTaskDatabase,
            clock = clock,
            syncScheduler = syncScheduler,
        )
    }

    @After
    fun tearDownRepository() {
        todoTaskDatabase.close()
    }

    /** Stores [task] without an outbox row, as a download would. */
    protected suspend fun seed(vararg task: TodoTask) = tasks.upsertTasks(task.toList())

    /** Counts the rows in the task and outbox tables, including tombstoned and failed rows. */
    protected suspend fun rowCounts(): RowCounts = todoTaskDatabase.useReaderConnection { conn ->
        suspend fun count(table: String): Long =
            conn.usePrepared("SELECT COUNT(*) FROM $table") { it.step(); it.getLong(0) }
        RowCounts(tasks = count("todo_tasks"), updates = count("todo_task_updates"))
    }

    /** Creates a task through the repository, failing the test if it is rejected. */
    protected suspend fun createTask(
        title: String,
        description: String = "",
        dueAt: Long? = null,
    ): String = checkNotNull(repository.createTask(title, description, dueAt)) {
        "createTask(\"$title\") was rejected"
    }

    /**
     * Runs [write] and checks that it leaves the row counts, and each of [taskIds]' task and
     * outbox rows, unchanged. Assert the write's return value inside [write].
     */
    protected suspend fun assertWritesNothing(vararg taskIds: String, write: suspend () -> Unit) {
        val before = snapshot(taskIds)
        write()
        assertEquals(before, snapshot(taskIds))
    }

    /**
     * Makes inserting an outbox row fail for [taskId], or for every task if it is null, standing
     * in for a disk error partway through a transaction. Room's upsert always inserts first, so
     * edits to an existing row fail too.
     */
    protected suspend fun failOutboxWrites(taskId: String? = null) {
        val condition = taskId?.let { "NEW.task_id = '$it'" } ?: "1"
        todoTaskDatabase.useWriterConnection {
            it.executeSQL(
                "CREATE TRIGGER fail_outbox_writes BEFORE INSERT ON todo_task_updates " +
                    "WHEN $condition BEGIN SELECT RAISE(ABORT, '$INJECTED_FAILURE'); END",
            )
        }
    }

    private suspend fun snapshot(taskIds: Array<out String>): List<Any?> =
        listOf(rowCounts()) + taskIds.flatMap { listOf(tasks.getTask(it), sync.getTaskUpdate(it)) }
}

/** The number of rows in each table, used to show that nothing extra was written. */
data class RowCounts(val tasks: Long, val updates: Long)

/** The message of the failure injected by [RepositoryTest.failOutboxWrites]. */
const val INJECTED_FAILURE = "Injected failure"

/** A task as downloaded from the server, so it has a version and is eligible as a base. */
fun syncedTask(id: String, deletedAt: Long? = null): TodoTask =
    sparseTask.copy(
        id = id,
        title = "Title $id",
        createdAt = 100L,
        lastUpdatedAt = 100L,
        deletedAt = deletedAt,
        version = 7L,
    )

/** Runs [block], which must throw [T], and returns what it threw. */
inline fun <reified T : Throwable> assertFailsWith(block: () -> Unit): T {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError("Expected ${T::class.simpleName} but got $e", e)
    }
    throw AssertionError("Expected ${T::class.simpleName} to be thrown")
}
