package com.harrison.todotasks.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.harrison.todotasks.data.local.dao.SyncStateDao
import com.harrison.todotasks.data.local.dao.TodoTaskDao
import com.harrison.todotasks.data.local.dao.TodoTaskUpdateDao
import com.harrison.todotasks.data.local.entities.SyncStateEntity
import com.harrison.todotasks.data.local.entities.TodoTaskEntity
import com.harrison.todotasks.data.local.entities.TodoTaskUpdateEntity

/** The app's local database, which is the source of truth for task data. */
@Database(
    entities = [TodoTaskEntity::class, TodoTaskUpdateEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TodoTaskDatabase : RoomDatabase() {

    /** Returns the DAO for the `todo_tasks` table. */
    abstract fun todoTaskDao(): TodoTaskDao

    /** Returns the DAO for the `todo_task_updates` table. */
    abstract fun todoTaskUpdateDao(): TodoTaskUpdateDao

    /** Returns the DAO for the `sync_state` table. */
    abstract fun syncStateDao(): SyncStateDao
}
