package com.example.poswarnus.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

interface SyncScheduler {
    /** Asks for a sync as soon as the network allows. Only enqueues; never blocks. */
    fun requestSync()

    /** Registers the 15-minute safety net once; later calls keep the existing request. */
    fun ensurePeriodic()
}

class WorkManagerSyncScheduler(private val context: Context) : SyncScheduler {
    // Obtained on first use so constructing the scheduler never touches WorkManager.
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    private val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    override fun requestSync() {
        // REPLACE: a run already in flight is cancelled and restarted, so the sale that was just
        // saved is picked up. The syncer marks an accepted sale SYNCED even when cancelled.
        val request = OneTimeWorkRequestBuilder<TransactionSyncWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniqueWork(NOW_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }

    override fun ensurePeriodic() {
        val request = PeriodicWorkRequestBuilder<TransactionSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val NOW_WORK_NAME = "transaction-sync-now"
        const val PERIODIC_WORK_NAME = "transaction-sync-periodic"
        const val BACKOFF_SECONDS = 30L
    }
}
