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

    /**
     * Whether a sync is running now. Emits true only while the sync work is
     * [WorkInfo.State.RUNNING]. A sync that is waiting for a network or backing off before a retry
     * is enqueued, not running, so it emits false.
     */
    val isSyncing: Flow<Boolean> = workManager.getWorkInfosForUniqueWorkFlow(SYNC_WORK_NAME)
        .map { works -> works.any { it.state == WorkInfo.State.RUNNING } }
        .distinctUntilChanged()

    /** Requests that tasks are synced with the server soon, once the device is online. */
    fun requestSync() {
        workManager.enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.KEEP, syncRequest())
    }

    /**
     * Requests that tasks are synced with the server now, such as when the user pulls to refresh.
     * A running sync is left alone, since it is already doing what was asked. A waiting sync is
     * replaced by a new one, so a sync backing off before a retry runs as soon as the device is
     * online, instead of after the backoff.
     *
     * [requestSync]'s KEEP would absorb this into a backed-off sync, and APPEND_OR_REPLACE would
     * chain it after one, so neither runs it now. WorkManager has no policy that replaces only
     * waiting work, so this checks the state and then enqueues with REPLACE. If the sync starts
     * running between the two, REPLACE cancels it, which is safe and costs only a repeated round
     * trip. Cancellation rolls back any open transaction, so the outbox keeps every row not yet
     * saved as uploaded. If an upload's PUT reached the server but its response was lost, the new
     * sync downloads the server's copy and merges it, or uploads with the old base version, gets a
     * conflict, and merges then. Either way no edit is lost.
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
