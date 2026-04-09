/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.fialkaapp.fialka.data.model.WalletSyncState

@Dao
interface WalletSyncStateDao {

    /**
     * Upsert sync state for a coin. Called by [MoneroSyncWorker] after each sync cycle.
     * Uses REPLACE so a single row per coin is maintained.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: WalletSyncState)

    @Query("SELECT * FROM wallet_sync_state WHERE coin = :coin")
    suspend fun get(coin: String = "XMR"): WalletSyncState?

    /** Observe sync state for reactive UI (progress bar, "Syncing…" label). */
    @Query("SELECT * FROM wallet_sync_state WHERE coin = :coin")
    fun observe(coin: String = "XMR"): LiveData<WalletSyncState?>

    /**
     * Bump [lastScannedHeight] and [networkHeight] after a sync batch.
     * Also updates [lastSyncedAt] and flips [isInitialSyncDone] when scanning is complete.
     */
    @Query(
        """UPDATE wallet_sync_state
           SET lastScannedHeight = :scannedHeight,
               networkHeight     = :networkHeight,
               lastSyncedAt      = :syncedAt,
               isInitialSyncDone = CASE WHEN :scannedHeight >= :networkHeight THEN 1 ELSE isInitialSyncDone END
           WHERE coin = :coin"""
    )
    suspend fun updateProgress(
        coin: String = "XMR",
        scannedHeight: Long,
        networkHeight: Long,
        syncedAt: Long = System.currentTimeMillis()
    )

    /**
     * Increments [nextSubaddressIndex] atomically.
     * Called by [WalletSeedManager] when a new receive address is generated.
     */
    @Query(
        "UPDATE wallet_sync_state SET nextSubaddressIndex = nextSubaddressIndex + 1 WHERE coin = :coin"
    )
    suspend fun incrementSubaddressIndex(coin: String = "XMR")

    @Query("DELETE FROM wallet_sync_state WHERE coin = :coin")
    suspend fun delete(coin: String = "XMR")
}
