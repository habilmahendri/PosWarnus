package com.example.poswarnus.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.poswarnus.data.auth.AuthRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class TransactionSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val auth: AuthRepository,
    private val syncer: TransactionSyncer,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        try {
            auth.ensureSessionForSync()
            if (syncer.syncPending().needsRetry) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Room errors from markSynced/recordFailure must not crash the worker.
            Result.retry()
        }
}
