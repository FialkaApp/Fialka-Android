/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks blockchain sync state per coin (currently XMR only).
 *
 * The [restoreHeight] is critical for Monero: scanning from block 0 takes several days.
 * - On wallet creation: set to the current blockchain tip height (fetched from LWS).
 * - On wallet restore: user provides an approximate date → [restoreHeight] is the
 *   corresponding block height. Scanning starts from there rather than genesis.
 *
 * If [restoreHeight] is 0 (unknown), scanning starts from genesis — slow but correct.
 */
@Entity(tableName = "wallet_sync_state")
data class WalletSyncState(
    /** Coin identifier (currently always "XMR"). */
    @PrimaryKey val coin: String = "XMR",

    /**
     * The block height where scanning should begin (restore height).
     * Set to the current blockchain tip on wallet creation.
     * Set by the user (via date input) on wallet restore.
     * 0 = unknown, scan from genesis (slow).
     */
    val restoreHeight: Long = 0L,

    /**
     * The highest block height that has been fully scanned so far.
     * Starts at [restoreHeight] and advances with each sync cycle.
     */
    val lastScannedHeight: Long = 0L,

    /**
     * The current tip of the blockchain as reported by the LWS server
     * (updated every sync cycle). Used to compute sync progress %.
     */
    val networkHeight: Long = 0L,

    /** Unix epoch timestamp in milliseconds of the last successful sync. */
    val lastSyncedAt: Long = 0L,

    /**
     * True once the initial full scan from [restoreHeight] to [networkHeight]
     * has completed. Before this, show a "Syncing wallet…" progress indicator.
     */
    val isInitialSyncDone: Boolean = false,

    /**
     * Next subaddress index to use when generating a fresh receive address.
     * Starts at 1 (index 0 = primary address, reserved).
     * Incremented each time a new subaddress is generated.
     */
    val nextSubaddressIndex: Int = 1
)
