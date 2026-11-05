package com.harrison.todotasks.data.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The name of the unique work that runs [SyncWorker]. */
internal const val SYNC_WORK_NAME = "sync"

/**
 * Schedules [SyncWorker] as unique work, so at most one sync runs or waits at a time. Each sync
 * waits for a network connection and runs expedited when quota allows.
 *
 * A request while a sync is running is dropped. The worker retries if rows are left after
 * uploading, and the app requests a sync at every start.
 */
@Singleton
class SyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) {

    /**
     * Whether a sync is [WorkInfo.State.RUNNING]. A sync waiting for a network or backing off
     * emits false.
     */
    val isSyncing: Flow<Boolean> = workManager.getWorkInfosForUniqueWorkFlow(SYNC_WORK_NAME)
        .map { works -> works.any { it.state == WorkInfo.State.RUNNING } }
        .distinctUntilChanged()

    /** Requests that tasks are synced with the server soon, once the device is online. */
    fun requestSync() {
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, syncRequest())
    }

    /**
     * Requests that tasks are synced with the server as soon as the device is online, skipping
     * any backoff, such as when the user pulls to refresh. A running sync is left alone.
     *
     * A sync that starts between the state check and the REPLACE is cancelled and rerun; no edit
     * is lost.
     */
    suspend fun syncNow() {
        if (isSyncing.first()) return
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, syncRequest())
    }

    private fun syncRequest(): OneTimeWorkRequest = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .build()
}
