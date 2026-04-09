/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.fialkaapp.fialka.data.model.WalletTransaction

@Dao
interface WalletTransactionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(tx: WalletTransaction)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(txs: List<WalletTransaction>)

    /** All transactions ordered newest first (for wallet history screen). */
    @Query("SELECT * FROM wallet_transactions ORDER BY timestamp DESC")
    fun observeAll(): LiveData<List<WalletTransaction>>

    /** All transactions linked to a specific conversation (for chat payment history). */
    @Query(
        "SELECT * FROM wallet_transactions WHERE conversationId = :conversationId ORDER BY timestamp DESC"
    )
    fun observeForConversation(conversationId: String): LiveData<List<WalletTransaction>>

    @Query("SELECT * FROM wallet_transactions WHERE txId = :txId")
    suspend fun getById(txId: String): WalletTransaction?

    /** Pending = not yet seen in a block. Updated to CONFIRMING when first seen. */
    @Query(
        "SELECT * FROM wallet_transactions WHERE status = '${WalletTransaction.STATUS_PENDING}'"
    )
    suspend fun getPending(): List<WalletTransaction>

    /** Transactions that have been seen but have fewer than 10 confirmations. */
    @Query(
        "SELECT * FROM wallet_transactions WHERE status = '${WalletTransaction.STATUS_CONFIRMING}'"
    )
    suspend fun getConfirming(): List<WalletTransaction>

    @Query(
        "UPDATE wallet_transactions SET confirmations = :confirmations, status = :status WHERE txId = :txId"
    )
    suspend fun updateConfirmations(txId: String, confirmations: Int, status: String)

    @Query("SELECT SUM(amountPiconero) FROM wallet_transactions WHERE direction = '${WalletTransaction.DIRECTION_INCOMING}' AND status = '${WalletTransaction.STATUS_CONFIRMED}'")
    suspend fun getTotalConfirmedIncoming(): Long?

    @Query("SELECT SUM(amountPiconero) FROM wallet_transactions WHERE direction = '${WalletTransaction.DIRECTION_OUTGOING}' AND status = '${WalletTransaction.STATUS_CONFIRMED}'")
    suspend fun getTotalConfirmedOutgoing(): Long?

    @Query("SELECT COUNT(*) FROM wallet_transactions")
    suspend fun count(): Int

    @Query("DELETE FROM wallet_transactions")
    suspend fun deleteAll()
}
