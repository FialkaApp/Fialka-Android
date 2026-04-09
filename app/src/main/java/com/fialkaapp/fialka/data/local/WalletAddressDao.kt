/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.data.local

import androidx.room.*
import com.fialkaapp.fialka.data.model.WalletAddress

@Dao
interface WalletAddressDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(address: WalletAddress): Long

    /** Retrieve the first unused address (not linked to any transaction). */
    @Query(
        "SELECT * FROM wallet_addresses WHERE usedInTxId IS NULL ORDER BY subaddressIndex ASC LIMIT 1"
    )
    suspend fun getFirstUnused(): WalletAddress?

    /** Retrieve address by its exact subaddress index. */
    @Query("SELECT * FROM wallet_addresses WHERE subaddressIndex = :index")
    suspend fun getByIndex(index: Int): WalletAddress?

    /** Retrieve address by its Monero address string. */
    @Query("SELECT * FROM wallet_addresses WHERE address = :address")
    suspend fun getByAddress(address: String): WalletAddress?

    /** Mark an address as used by a transaction — prevents re-use. */
    @Query("UPDATE wallet_addresses SET usedInTxId = :txId WHERE id = :id AND usedInTxId IS NULL")
    suspend fun markUsed(id: Long, txId: String)

    /** All addresses linked to a specific conversation (for "request payment" context). */
    @Query(
        "SELECT * FROM wallet_addresses WHERE conversationId = :conversationId ORDER BY createdAt DESC"
    )
    suspend fun getForConversation(conversationId: String): List<WalletAddress>

    /** All addresses, for auditing / backup export. */
    @Query("SELECT * FROM wallet_addresses ORDER BY subaddressIndex ASC")
    suspend fun getAll(): List<WalletAddress>

    /** Number of addresses already generated. */
    @Query("SELECT COUNT(*) FROM wallet_addresses")
    suspend fun count(): Int

    @Query("DELETE FROM wallet_addresses")
    suspend fun deleteAll()
}
