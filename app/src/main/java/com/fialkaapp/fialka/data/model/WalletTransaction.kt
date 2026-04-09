/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A confirmed or pending Monero transaction belonging to this wallet.
 *
 * Incoming transactions are detected by scanning with the view key via the LWS.
 * Outgoing transactions are recorded locally at broadcast time.
 */
@Entity(
    tableName = "wallet_transactions",
    indices = [
        Index(value = ["coin", "timestamp"]),
        Index(value = ["conversationId"])
    ]
)
data class WalletTransaction(
    /**
     * Monero transaction ID (txid) — 64-char hex string.
     * For outgoing transactions recorded before first confirmation, this is the local tx hash.
     */
    @PrimaryKey val txId: String,

    /** Always "XMR" for the current implementation. */
    val coin: String = "XMR",

    /**
     * Amount in piconero (1 XMR = 1,000,000,000,000 piconero).
     * Stored as Long to avoid floating-point imprecision.
     * Use [xmrAmount] helper to get a human-readable string.
     */
    val amountPiconero: Long,

    /**
     * Transaction fee in piconero (outgoing only, 0 for incoming).
     */
    val feePiconero: Long = 0L,

    /** INCOMING or OUTGOING — see constants below. */
    val direction: Int,

    /** Current confirmation status — see STATUS_* constants. */
    val status: Int = STATUS_PENDING,

    /** Number of block confirmations (0 = in mempool). */
    val confirmations: Int = 0,

    /**
     * Block height at which this transaction was mined.
     * 0 = unconfirmed / mempool.
     */
    val blockHeight: Long = 0L,

    /**
     * The address that received (incoming) or was sent to (outgoing).
     * For incoming: the subaddress from WalletAddress that was used.
     * For outgoing: the recipient's address (may be contact's address or external).
     */
    val address: String = "",

    /**
     * Optional link to the chat conversation this payment is associated with.
     * Null if the payment was initiated from the wallet screen directly.
     */
    val conversationId: String? = null,

    /** Unix epoch timestamp in milliseconds. */
    val timestamp: Long = System.currentTimeMillis(),

    /** Optional user-defined note / memo for this transaction. */
    val note: String? = null
) {
    companion object {
        const val DIRECTION_INCOMING = 0
        const val DIRECTION_OUTGOING = 1

        const val STATUS_PENDING    = 0  // In mempool / broadcast, 0 confirmations
        const val STATUS_CONFIRMING = 1  // 1–9 confirmations
        const val STATUS_CONFIRMED  = 2  // 10+ confirmations (Monero default)
        const val STATUS_FAILED     = 3  // Broadcast rejected or double-spend detected

        private const val PICONERO_PER_XMR = 1_000_000_000_000L

        /** Format piconero amount as a human-readable XMR string (e.g. "0.500000000000"). */
        fun piconeroToXmr(piconero: Long): String {
            val whole = piconero / PICONERO_PER_XMR
            val frac  = piconero % PICONERO_PER_XMR
            return "%d.%012d".format(whole, frac)
        }

        /** Parse XMR string to piconero (e.g. "0.5" → 500000000000). */
        fun xmrToPiconero(xmr: String): Long {
            val parts = xmr.trim().split(".")
            val whole = parts[0].toLongOrNull() ?: 0L
            val frac  = if (parts.size > 1) {
                parts[1].padEnd(12, '0').take(12).toLongOrNull() ?: 0L
            } else 0L
            return whole * PICONERO_PER_XMR + frac
        }
    }

    /** Convenience: XMR amount as formatted string. */
    val xmrAmount: String get() = piconeroToXmr(amountPiconero)
}
