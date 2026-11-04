package com.harrison.todotasks

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.harrison.todotasks.data.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * The application. It configures WorkManager to create workers with Hilt, so the default
 * WorkManager initializer is removed in the manifest.
 */
@HiltAndroidApp
class TodoAppApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // A backstop for a write committed just before a crash, or a request dropped mid-sync.
        syncScheduler.requestSync()
    }
}
