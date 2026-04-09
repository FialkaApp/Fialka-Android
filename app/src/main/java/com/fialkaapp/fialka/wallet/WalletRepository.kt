/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.wallet

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import com.fialkaapp.fialka.crypto.FialkaNative
import com.fialkaapp.fialka.crypto.WalletSeedManager
import com.fialkaapp.fialka.data.local.FialkaDatabase
import com.fialkaapp.fialka.data.model.WalletAddress
import com.fialkaapp.fialka.data.model.WalletSyncState
import com.fialkaapp.fialka.data.model.WalletTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WalletRepository — single source of truth for wallet data.
 *
 * Coordinates between:
 * - Room (local wallet_transactions, wallet_addresses, wallet_sync_state)
 * - [MoneroLwsClient] (Light Wallet Server over Tor)
 * - [WalletSeedManager] + JNI crypto ([FialkaNative.xmrDeriveKeys],
 *   [FialkaNative.xmrSubAddress])
 *
 * All coroutine work runs on [Dispatchers.IO].
 * UI layers observe [LiveData] from the DAO — they never call Network directly.
 */
class WalletRepository(private val context: Context) {

    private val db = FialkaDatabase.getInstance(context)
    private val txDao = db.walletTransactionDao()
    private val addrDao = db.walletAddressDao()
    private val syncDao = db.walletSyncStateDao()
    private val lws = MoneroLwsClient(context)

    companion object {
        private const val TAG = "WalletRepository"
        private const val COIN = "XMR"
        /** Minimum confirmations to consider a transaction confirmed. */
        const val MIN_CONFIRMATIONS = 10
    }

    // ── Reactive observers (for UI) ───────────────────────────────────────────

    fun observeTransactions(): LiveData<List<WalletTransaction>> =
        txDao.observeAll()

    fun observeTransactionsForConversation(conversationId: String): LiveData<List<WalletTransaction>> =
        txDao.observeForConversation(conversationId)

    fun observeSyncState(): LiveData<WalletSyncState?> =
        syncDao.observe(COIN)

    // ── Address management ────────────────────────────────────────────────────

    /**
     * Get or generate the next fresh receive subaddress for an optional conversation.
     *
     * If [conversationId] is non-null, generates a dedicated subaddress for that chat.
     * Address non-reuse: each address can only be linked to one inbound transaction.
     *
     * The subaddress index is tracked in [WalletSyncState.nextSubaddressIndex] and
     * incremented atomically after each generation.
     *
     * @return  Monero subaddress string (95 chars, starts with "8"), or null on error
     */
    suspend fun getOrGenerateFreshAddress(conversationId: String? = null): String? =
        withContext(Dispatchers.IO) {
            // Check if we already have an unused address for this conversation
            if (conversationId != null) {
                val existing = addrDao.getForConversation(conversationId)
                    .firstOrNull { it.usedInTxId == null }
                if (existing != null) return@withContext existing.address
            }

            // Derive keys and generate new subaddress
            val keys = WalletSeedManager.deriveAndGetKeys(context) ?: return@withContext null
            try {
                val syncState = syncDao.get(COIN) ?: WalletSyncState(coin = COIN)
                val index = syncState.nextSubaddressIndex

                val addrBytes = FialkaNative.xmrSubAddress(
                    keys.spendPub, keys.viewPriv, account = 0, index = index
                )
                val address = String(addrBytes, Charsets.UTF_8)

                // Persist the new address
                addrDao.insert(
                    WalletAddress(
                        coin             = COIN,
                        subaddressIndex  = index,
                        address          = address,
                        conversationId   = conversationId,
                        createdAt        = System.currentTimeMillis()
                    )
                )
                // Increment the index counter
                syncDao.incrementSubaddressIndex(COIN)

                address
            } catch (e: Exception) {
                Log.e(TAG, "getOrGenerateFreshAddress failed", e)
                null
            } finally {
                keys.zeroize()
            }
        }

    /**
     * Get the primary address (account 0, index 0) — used for donations
     * and as the main displayable address.
     *
     * @return  Primary XMR address (95 chars, starts with "4"), or null on error
     */
    suspend fun getPrimaryAddress(): String? = withContext(Dispatchers.IO) {
        val keys = WalletSeedManager.deriveAndGetKeys(context) ?: return@withContext null
        try {
            val addrBytes = FialkaNative.xmrPrimaryAddress(keys.spendPub, keys.viewPub)
            String(addrBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "getPrimaryAddress failed", e)
            null
        } finally {
            keys.zeroize()
        }
    }

    // ── Balance ───────────────────────────────────────────────────────────────

    /**
     * Confirmed unlocked balance in piconero (1 XMR = 1e12 piconero).
     * Only includes transactions with >= [MIN_CONFIRMATIONS] confirmations.
     */
    suspend fun getUnlockedBalancePiconero(): Long = withContext(Dispatchers.IO) {
        val received = txDao.getTotalConfirmedIncoming() ?: 0L
        val sent     = txDao.getTotalConfirmedOutgoing() ?: 0L
        maxOf(0L, received - sent)
    }

    // ── Sync — called by MoneroSyncWorker ─────────────────────────────────────

    /**
     * Full sync cycle:
     * 1. Login to LWS with address + view key
     * 2. Fetch [MoneroLwsClient.LwsAddressInfo] for scanned/blockchain heights
     * 3. Fetch all transactions from LWS
     * 4. Upsert new/updated transactions into Room
     * 5. Update sync state with current heights, flip [WalletSyncState.isInitialSyncDone]
     *
     * @return true if sync completed successfully
     */
    suspend fun syncOnce(): Boolean = withContext(Dispatchers.IO) {
        val keys = WalletSeedManager.deriveAndGetKeys(context) ?: run {
            Log.w(TAG, "syncOnce: no wallet seed")
            return@withContext false
        }

        try {
            val primaryAddr = FialkaNative.xmrPrimaryAddress(keys.spendPub, keys.viewPub)
            val address  = String(primaryAddr, Charsets.UTF_8)
            val viewHex  = keys.viewPriv.joinToString("") { "%02x".format(it) }

            // Step 1 — login (registers/refreshes the account on LWS)
            lws.login(address, viewHex) ?: run {
                Log.w(TAG, "syncOnce: login failed")
                return@withContext false
            }

            // Step 2 — get heights
            val info = lws.getAddressInfo(address, viewHex) ?: run {
                Log.w(TAG, "syncOnce: getAddressInfo failed")
                return@withContext false
            }

            // Step 3 — fetch transactions
            val lwsTxs = lws.getAddressTxs(address, viewHex)

            // Step 4 — upsert into Room
            val now = System.currentTimeMillis()
            val roomTxs = lwsTxs.map { lx -> lx.toRoomTransaction(now, info.blockchainHeight) }
            txDao.insertOrUpdateAll(roomTxs)

            // Step 5 — update sync state
            syncDao.updateProgress(
                coin           = COIN,
                scannedHeight  = info.scannedBlockHeight,
                networkHeight  = info.blockchainHeight,
                syncedAt       = now
            )

            Log.d(TAG, "syncOnce OK: scanned=${info.scannedBlockHeight} / network=${info.blockchainHeight} txs=${lwsTxs.size}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "syncOnce exception", e)
            false
        } finally {
            keys.zeroize()
        }
    }

    // ── Donation address ──────────────────────────────────────────────────────

    /**
     * Derive the deterministic donation subaddress for a specific contact.
     *
     * Uses [FialkaNative.xmrDeriveDonationSubaddress] — the spend key is the
     * hardcoded donation spend public key (not from the user's wallet),
     * and the view key is the donation view private key (also hardcoded in APK).
     *
     * @param accountIdBytes  32-byte account ID of the contact (bs58-decoded)
     * @return                 Donation subaddress string, or null on error
     */
    suspend fun getDonationSubaddress(accountIdBytes: ByteArray): String? =
        withContext(Dispatchers.IO) {
            try {
                val raw = FialkaNative.xmrDeriveDonationSubaddress(
                    DonationConfig.SPEND_PUB,
                    DonationConfig.VIEW_PRIV,
                    accountIdBytes
                )
                String(raw, Charsets.UTF_8)
            } catch (e: Exception) {
                Log.e(TAG, "getDonationSubaddress failed", e)
                null
            }
        }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun MoneroLwsClient.LwsTransaction.toRoomTransaction(
        now: Long,
        networkHeight: Long
    ): WalletTransaction {
        val direction = if (totalReceived > 0L && totalSent == 0L)
            WalletTransaction.DIRECTION_INCOMING
        else
            WalletTransaction.DIRECTION_OUTGOING

        val amount = if (direction == WalletTransaction.DIRECTION_INCOMING)
            totalReceived else totalSent

        val confirmations = if (height <= 0L) 0
        else (networkHeight - height + 1).coerceAtLeast(0L).toInt()

        val status = when {
            mempool || height <= 0L -> WalletTransaction.STATUS_PENDING
            confirmations < MIN_CONFIRMATIONS -> WalletTransaction.STATUS_CONFIRMING
            else -> WalletTransaction.STATUS_CONFIRMED
        }

        return WalletTransaction(
            txId            = hash.ifBlank { id },
            coin            = COIN,
            amountPiconero  = amount,
            feePiconero     = fee,
            direction       = direction,
            status          = status,
            confirmations   = confirmations,
            blockHeight     = height,
            timestamp       = if (timestamp > 0L) timestamp * 1000L else now
        )
    }
}
