package com.harrison.todotasks.data.local

import androidx.room3.Database
import androidx.room3.RoomDatabase
import com.harrison.todotasks.data.local.dao.TodoTaskDao
import com.harrison.todotasks.data.local.entities.TodoTaskEntity

/** The app's local database, which is the source of truth for task data. */
@Database(
    entities = [TodoTaskEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TodoTaskDatabase : RoomDatabase() {

    /** Returns the DAO for the `todo_tasks` table. */
    abstract fun todoTaskDao(): TodoTaskDao
}
