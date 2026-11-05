package com.harrison.todotasks.data.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides WorkManager, which schedules background syncs. */
@Module
@InstallIn(SingletonComponent::class)
object WorkManagerModule {

    /** Provides the app's [WorkManager], configured by the application. */
    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
