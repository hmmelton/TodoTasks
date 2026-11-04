package com.harrison.todotasks.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Covers how sync requests are enqueued as unique work. */
@RunWith(RobolectricTestRunner::class)
class SyncSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: SyncScheduler

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context,
            Configuration.Builder().setExecutor(SynchronousExecutor()).build(),
        )
        workManager = WorkManager.getInstance(context)
        scheduler = SyncScheduler(workManager)
    }

    @Test
    fun `requestSync enqueues one sync that waits for a network`() {
        scheduler.requestSync()

        val work = syncWork().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    }

    @Test
    fun `a request while a sync is waiting is absorbed by it`() {
        scheduler.requestSync()
        val first = syncWork().single()

        scheduler.requestSync()

        assertEquals(first.id, syncWork().single().id)
    }

    private fun syncWork(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(SYNC_WORK_NAME).get()
}
