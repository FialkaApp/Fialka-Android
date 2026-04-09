/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.wallet

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.fialkaapp.fialka.crypto.WalletSeedManager
import com.fialkaapp.fialka.tor.TorManager
import com.fialkaapp.fialka.tor.TorState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * MoneroSyncWorker — periodic WorkManager worker that syncs the Monero wallet
 * against the Light Wallet Server (LWS) over Tor.
 *
 * **Scheduling**: every 15 minutes (WorkManager minimum), unique work name
 * [WORK_NAME] with [ExistingPeriodicWorkPolicy.KEEP].
 *
 * **Guards**:
 * 1. Wallet must be enabled and have a seed — skips immediately otherwise.
 * 2. Tor must be [TorState.CONNECTED] or [TorState.ONION_PUBLISHED] — waits up
 *    to [TOR_WAIT_TIMEOUT_MS] milliseconds, then returns [Result.retry()].
 *
 * Returning [Result.retry()] triggers exponential backoff (initial 30s, max 5min),
 * so the worker re-attempts quickly if Tor was still bootstrapping.
 */
class MoneroSyncWorker(
    private val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "MoneroSyncWorker"

        /** WorkManager unique work name — must stay stable across releases. */
        const val WORK_NAME = "monero_sync_periodic"

        /** How long to wait for Tor to be ready before giving up this run. */
        private const val TOR_WAIT_TIMEOUT_MS = 30_000L

        /** Periodic interval — WorkManager enforces a 15-minute minimum. */
        private const val INTERVAL_MINUTES = 15L

        /**
         * Enqueue the periodic sync worker.
         *
         * Safe to call multiple times — [ExistingPeriodicWorkPolicy.KEEP] ensures
         * only a single instance is ever queued.
         *
         * @param context  Application context
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                // Tor handles all routing — any network type is fine (CONNECTED = device has internet)
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<MoneroSyncWorker>(
                INTERVAL_MINUTES, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )

            Log.d(TAG, "schedule: periodic work enqueued (interval=${INTERVAL_MINUTES}m)")
        }

        /**
         * Cancel the periodic sync worker.
         * Called when the user disables the wallet feature.
         */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "cancel: periodic work cancelled")
        }
    }

    override suspend fun doWork(): Result {
        // Guard 1 — wallet feature must be enabled and have a seed
        if (!WalletSeedManager.isWalletEnabled(appContext) ||
            !WalletSeedManager.hasWalletSeed(appContext)
        ) {
            Log.d(TAG, "doWork: wallet not enabled — skipping")
            return Result.success()
        }

        // Guard 2 — wait for Tor to be ready
        val torReady = waitForTor()
        if (!torReady) {
            Log.w(TAG, "doWork: Tor not ready after ${TOR_WAIT_TIMEOUT_MS}ms — retry")
            return Result.retry()
        }

        // Run sync
        return try {
            val repo = WalletRepository(appContext)
            val ok = repo.syncOnce()
            if (ok) {
                Log.d(TAG, "doWork: sync completed successfully")
                Result.success()
            } else {
                Log.w(TAG, "doWork: sync returned false — retry")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "doWork: unexpected error", e)
            Result.retry()
        }
    }

    /**
     * Wait up to [TOR_WAIT_TIMEOUT_MS] ms for Tor to reach [TorState.CONNECTED]
     * or [TorState.ONION_PUBLISHED].
     *
     * @return true if Tor is ready, false if timed out
     */
    private suspend fun waitForTor(): Boolean {
        val readyState = withTimeoutOrNull(TOR_WAIT_TIMEOUT_MS) {
            TorManager.state.first { state ->
                state is TorState.CONNECTED || state is TorState.ONION_PUBLISHED
            }
        }
        return readyState != null
    }
}
