/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A Monero subaddress derived for and used by this wallet.
 *
 * One row is created per address generated (receive screen, chat payment request...).
 * Addresses are never reused for different purposes — once [usedInTxId] is set,
 * the address must not be offered again for new incoming payments.
 *
 * Derivation: `FialkaNative.xmrSubAddress(spendPub, viewPriv, account=0, index=[subaddressIndex])`
 */
@Entity(
    tableName = "wallet_addresses",
    indices = [
        Index(value = ["address"], unique = true),
        Index(value = ["conversationId"]),
        Index(value = ["usedInTxId"])
    ]
)
data class WalletAddress(
    /**
     * Auto-generated surrogate primary key.
     * Use [subaddressIndex] for the Monero derivation index.
     */
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,

    /** Always "XMR" for the current implementation. */
    val coin: String = "XMR",

    /**
     * Monero subaddress index within account 0.
     * Index 0 is reserved for the primary address (never used for per-contact sharing).
     * Generated addresses start at index 1 and increment.
     */
    val subaddressIndex: Int,

    /** The full Monero subaddress string (95 chars, starts with "8"). */
    val address: String,

    /**
     * Optional link to the conversation this address was generated for.
     * Null if generated from the receive screen (not tied to a specific contact).
     */
    val conversationId: String? = null,

    /**
     * If non-null, this address has been used in a transaction (incoming payment received).
     * Do NOT reassign this address to a new payment once set.
     */
    val usedInTxId: String? = null,

    /** Unix epoch timestamp in milliseconds when this address was created. */
    val createdAt: Long = System.currentTimeMillis()
)
