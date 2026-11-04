package com.harrison.todotasks.data.sync

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import javax.inject.Inject
import javax.inject.Singleton

/** The name of the unique work that runs [SyncWorker]. */
internal const val SYNC_WORK_NAME = "sync"

/**
 * Requests background syncs with the server by scheduling [SyncWorker] with WorkManager as unique
 * work, so at most one sync runs or waits at a time.
 *
 * A request while a sync is waiting is absorbed by it. A request while a sync is running is
 * dropped, which is safe: the worker checks for pending rows after uploading and retries if any
 * are left, and the app requests a sync at every start as a backstop.
 *
 * Each sync waits for a network connection, and runs as expedited work when quota allows, so a
 * user's edit reaches the server quickly. The minimum SDK is 31, where expedited work runs as a
 * job, so the worker needs no foreground notification. Retries use WorkManager's default backoff,
 * which is exponential from 30 seconds.
 */
@Singleton
class SyncScheduler @Inject constructor(
    private val workManager: WorkManager,
) {

    /** Requests that tasks are synced with the server soon, once the device is online. */
    fun requestSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, request)
    }
}
