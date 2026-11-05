package com.harrison.todotasks.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers how sync requests are enqueued, against a test WorkManager, and how the sync's state is
 * read, against a mock WorkManager, since the test one can't hold work in [WorkInfo.State.RUNNING].
 */
@RunWith(RobolectricTestRunner::class)
class SyncSchedulerTest {

    private lateinit var workManager: WorkManager
    private lateinit var scheduler: SyncScheduler

    private val syncWorkStates = MutableStateFlow<List<WorkInfo>>(emptyList())
    private val mockWorkManager = mockk<WorkManager>(relaxed = true) {
        every { getWorkInfosForUniqueWorkFlow(SYNC_WORK_NAME) } returns syncWorkStates
    }
    private val mockScheduler = SyncScheduler(mockWorkManager)

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

    @Test
    fun `syncNow enqueues one sync that waits for a network`() = runTest {
        scheduler.syncNow()

        val work = syncWork().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
    }

    @Test
    fun `syncNow replaces a waiting sync`() = runTest {
        scheduler.requestSync()
        val waiting = syncWork().single()

        scheduler.syncNow()

        val replacement = syncWork().single { !it.state.isFinished }
        assertNotEquals(waiting.id, replacement.id)
        assertEquals(WorkInfo.State.ENQUEUED, replacement.state)
    }

    @Test
    fun `syncNow replaces waiting work with an expedited sync`() = runTest {
        syncWorkStates.value = listOf(workInfo(WorkInfo.State.ENQUEUED))

        mockScheduler.syncNow()

        verify(exactly = 1) {
            mockWorkManager.enqueueUniqueWork(
                SYNC_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                match<OneTimeWorkRequest> { it.workSpec.expedited },
            )
        }
    }

    @Test
    fun `syncNow leaves a running sync alone`() = runTest {
        syncWorkStates.value = listOf(workInfo(WorkInfo.State.RUNNING))

        mockScheduler.syncNow()

        verify(exactly = 0) {
            mockWorkManager.enqueueUniqueWork(any(), any(), any<OneTimeWorkRequest>())
        }
    }

    @Test
    fun `isSyncing is true only while the sync is running, and emits only changes`() = runTest {
        mockScheduler.isSyncing.test {
            assertEquals(false, awaitItem())

            syncWorkStates.value = listOf(workInfo(WorkInfo.State.ENQUEUED))
            syncWorkStates.value = listOf(workInfo(WorkInfo.State.RUNNING))
            assertEquals(true, awaitItem())

            syncWorkStates.value = listOf(workInfo(WorkInfo.State.RUNNING, runAttemptCount = 1))
            syncWorkStates.value = listOf(workInfo(WorkInfo.State.ENQUEUED, runAttemptCount = 1))
            assertEquals(false, awaitItem())

            syncWorkStates.value = listOf(workInfo(WorkInfo.State.RUNNING))
            assertEquals(true, awaitItem())

            syncWorkStates.value = listOf(workInfo(WorkInfo.State.SUCCEEDED))
            assertEquals(false, awaitItem())

            syncWorkStates.value = emptyList()
            expectNoEvents()
        }
    }

    @Test
    fun `isSyncing is false for a sync waiting for a network`() = runTest {
        scheduler.requestSync()

        scheduler.isSyncing.test {
            assertEquals(false, awaitItem())
        }
        assertEquals(WorkInfo.State.ENQUEUED, syncWork().single().state)
    }

    private fun syncWork(): List<WorkInfo> =
        workManager.getWorkInfosForUniqueWork(SYNC_WORK_NAME).get()

    private fun workInfo(state: WorkInfo.State, runAttemptCount: Int = 0): WorkInfo =
        WorkInfo(
            id = UUID(0L, 1L),
            state = state,
            tags = emptySet(),
            runAttemptCount = runAttemptCount,
        )
}
