package com.harrison.todotasks.data.local.di

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import com.harrison.todotasks.data.local.TodoTaskDatabase
import com.harrison.todotasks.data.local.dao.SyncStateDao
import com.harrison.todotasks.data.local.dao.TodoTaskDao
import com.harrison.todotasks.data.local.dao.TodoTaskUpdateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private const val TODO_TASK_DATABASE_NAME = "todo_tasks.db"

/** Provides the local database and its DAOs. */
@Module
@InstallIn(SingletonComponent::class)
object TodoTaskDatabaseModule {

    /** Provides the single [TodoTaskDatabase] instance. */
    @Provides
    @Singleton
    fun provideTodoTaskDatabase(@ApplicationContext context: Context): TodoTaskDatabase =
        Room.databaseBuilder(context, TodoTaskDatabase::class.java, TODO_TASK_DATABASE_NAME)
            .setDriver(AndroidSQLiteDriver())
            .build()

    /** Provides the [TodoTaskDao] from [todoTaskDatabase]. */
    @Provides
    fun provideTodoTaskDao(todoTaskDatabase: TodoTaskDatabase): TodoTaskDao =
        todoTaskDatabase.todoTaskDao()

    /** Provides the [TodoTaskUpdateDao] from [todoTaskDatabase]. */
    @Provides
    fun provideTodoTaskUpdateDao(todoTaskDatabase: TodoTaskDatabase): TodoTaskUpdateDao =
        todoTaskDatabase.todoTaskUpdateDao()

    /** Provides the [SyncStateDao] from [todoTaskDatabase]. */
    @Provides
    fun provideSyncStateDao(todoTaskDatabase: TodoTaskDatabase): SyncStateDao =
        todoTaskDatabase.syncStateDao()
}
