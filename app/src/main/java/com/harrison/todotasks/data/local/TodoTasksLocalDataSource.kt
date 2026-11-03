package com.harrison.todotasks.data.local

import com.harrison.todotasks.data.local.dao.TodoTaskDao
import com.harrison.todotasks.data.local.entities.TodoTaskEntity
import com.harrison.todotasks.data.local.entities.asEntity
import com.harrison.todotasks.data.local.entities.asTodoTask
import com.harrison.todotasks.data.model.TodoTask
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Local storage for tasks, which is the app's source of truth for them.
 *
 * This is a thin layer over [TodoTaskDao] that maps tasks to [TodoTask]. It does not open
 * transactions; a write made inside a transaction opened on [TodoTaskDatabase] joins it.
 */
@Singleton
class TodoTasksLocalDataSource @Inject constructor(
    private val todoTaskDao: TodoTaskDao,
) {

    /** Observes the tasks that are not tombstoned, in display order. */
    fun observeTasks(): Flow<List<TodoTask>> =
        todoTaskDao.observeTasks().map { tasks -> tasks.map(TodoTaskEntity::asTodoTask) }

    /** Observes the task with [id], emitting null if it does not exist or is tombstoned. */
    fun observeTask(id: String): Flow<TodoTask?> =
        todoTaskDao.observeTask(id).map { task -> task?.asTodoTask() }

    /** Returns the task with [id], or null if it does not exist. Includes tombstoned tasks. */
    suspend fun getTask(id: String): TodoTask? = todoTaskDao.getTask(id)?.asTodoTask()

    /** Inserts [task], or replaces the task with the same ID. */
    suspend fun upsertTask(task: TodoTask) = todoTaskDao.upsert(task.asEntity())

    /** Inserts [tasks], replacing any tasks with the same IDs. */
    suspend fun upsertTasks(tasks: List<TodoTask>) =
        todoTaskDao.upsert(tasks.map(TodoTask::asEntity))
}
