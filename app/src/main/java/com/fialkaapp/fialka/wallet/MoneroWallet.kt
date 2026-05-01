/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 *
 * MoneroWallet — compatibility facade over WalletManager + Wallet (wallet/jni/).
 *
 * All methods are BLOCKING — must be called from a background thread (Dispatchers.IO).
 *
 * Migration note: previously backed by libmonero_jni.so (BUILD=16) with custom DIAG loop.
 * Now backed by libfialka_monero.so built from official Monero v0.18.3.4 source.
 * setDaemon() no longer runs a blocking DIAG loop — it calls wallet->init() non-blocking.
 * Sync is driven entirely by startRefreshAsync() â†’ wallet->startRefresh().
 */
package com.fialkaapp.fialka.wallet

import android.util.Log
import com.fialkaapp.fialka.wallet.jni.WalletManager
import com.fialkaapp.fialka.wallet.jni.Wallet

object MoneroWallet {

    private const val TAG = "MoneroWallet"

    /** True if libfialka_monero loaded successfully. */
    val isAvailable: Boolean get() = WalletManager.isAvailable

    /** True if a wallet is currently open. */
    val isOpen: Boolean get() = _wallet != null

    /** The daemon URL currently connected, or empty. */
    var currentDaemonUrl: String = ""
        private set

    @Volatile private var _wallet: Wallet? = null

    // â”€â”€ Wallet lifecycle â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Restore wallet from 25-word mnemonic and write to [path].
     * Maps to WalletManager.recoveryWallet().
     */
    fun createWallet(
        path: String,
        password: String,
        mnemonic: String,
        networkType: Int,
        restoreHeight: Long
    ): Boolean {
        if (!isAvailable) return false
        _wallet?.let { WalletManager.close(it) }
        val w = WalletManager.recoveryWallet(path, password, mnemonic, "", networkType, restoreHeight)
        _wallet = w
        if (w == null) Log.e(TAG, "createWallet (recovery) failed")
        return w != null
    }

    /** Open an existing wallet from disk. */
    fun openWallet(path: String, password: String, networkType: Int): Boolean {
        if (!isAvailable) return false
        _wallet?.let { WalletManager.close(it) }
        val w = WalletManager.openWallet(path, password, networkType)
        _wallet = w
        if (w == null) Log.e(TAG, "openWallet failed")
        return w != null
    }

    /** Save wallet state and close. */
    fun closeWallet() {
        val w = _wallet ?: return
        w.store()
        WalletManager.close(w)
        _wallet = null
        currentDaemonUrl = ""
    }

    // â”€â”€ Connection â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Connect to daemon. NON-BLOCKING. No DIAG loop.
     * Previously nativeSetDaemon() triggered a blocking 1-pass DIAG refresh —
     * that caused the status=1 infinite loop at block 2100000. This does not.
     */
    fun setDaemon(url: String, proxy: String = ""): Boolean {
        val w = _wallet ?: return false
        val ok = w.init(url, proxy)
        if (ok) currentDaemonUrl = url
        return ok
    }

    /** Connect to daemon (same as setDaemon without proxy). */
    fun connectToDaemon(): Boolean = setDaemon(currentDaemonUrl)

    /** Connection status: 0=disconnected, 1=connected, 2=wrong_version. */
    fun connectionStatus(): Int = _wallet?.getConnectionStatusJ() ?: 0

    // â”€â”€ Sync â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Synchronous single-pass refresh. */
    fun refresh(): Boolean = _wallet?.refresh() ?: false

    /** Start non-blocking background sync loop. */
    fun startRefreshAsync() { _wallet?.startRefresh() }

    /** Stop background sync loop. */
    fun stopRefresh() { _wallet?.pauseRefresh() }

    /** Block height the wallet has synced to. */
    fun getBlockchainHeight(): Long = _wallet?.getBlockChainHeight() ?: 0L

    /** Daemon's current tip height. */
    fun getDaemonBlockchainHeight(): Long = _wallet?.getDaemonBlockChainHeight() ?: 0L

    /** True if wallet has caught up with daemon. */
    fun isSynchronized(): Boolean = _wallet?.isSynchronizedJ() ?: false

    /** Save wallet state to disk. */
    fun store(): Boolean = _wallet?.store() ?: false

    // â”€â”€ Balance â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** Total balance in piconero. -1 if wallet not open. */
    fun getBalance(accountIndex: Int = 0): Long = _wallet?.getBalance(accountIndex) ?: -1L

    /** Spendable balance in piconero. -1 if wallet not open. */
    fun getUnlockedBalance(accountIndex: Int = 0): Long =
        _wallet?.getUnlockedBalance(accountIndex) ?: -1L

    // â”€â”€ Addresses â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    fun getAddress(accountIndex: Int = 0, subaddressIndex: Int = 0): String =
        _wallet?.getAddressJ(accountIndex, subaddressIndex) ?: ""

    // â”€â”€ Transactions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /**
     * Send a transaction.
     * Creates, signs and broadcasts. Returns [SendResult].
     */
    fun sendTransaction(
        destAddress: String,
        amountPiconero: Long,
        priority: Int = 0,
        accountIndex: Int = 0
    ): SendResult {
        val w = _wallet ?: return SendResult(false, error = "Wallet non ouvert")
        return try {
            val tx = w.createTransaction(destAddress, amountPiconero, priority, accountIndex)
            if (!tx.isOk) {
                return SendResult(false, error = tx.getErrorString())
            }
            val committed = tx.commit("", false)
            val txId = tx.txId ?: ""
            val fee = tx.getFee()
            w.disposeTransaction(tx)
            if (committed) SendResult(true, txId = txId, fee = fee)
            else SendResult(false, error = tx.getErrorString())
        } catch (e: Exception) {
            Log.e(TAG, "sendTransaction failed", e)
            SendResult(false, error = e.message ?: "Erreur inconnue")
        }
    }

    /** Full transaction history as a list of [TxInfo]. */
    fun getHistory(): List<TxInfo> {
        val w = _wallet ?: return emptyList()
        return try {
            w.getHistory().getAll().map { info ->
                TxInfo(
                    txId          = info.txId,
                    direction     = info.direction,
                    amount        = info.amount,
                    fee           = info.fee,
                    height        = info.blockHeight,
                    timestamp     = info.timestamp,
                    confirmations = info.confirmations,
                    label         = info.subaddressLabel,
                    paymentId     = info.paymentId
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getHistory failed", e)
            emptyList()
        }
    }

    /** Get last error from wallet status. */
    fun getLastError(): String =
        _wallet?.statusWithErrorString()?.errorString ?: "Biblioth\u00e8que non charg\u00e9e"

    // â”€â”€ Stubs (no longer needed with new JNI) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    /** No-op. Rescan from height is set via recoveryWallet(restoreHeight). */
    fun rescanFromHeight(height: Long): Boolean {
        _wallet?.rescanBlockchainAsync()
        return true
    }

    // â”€â”€ Data classes â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    data class SendResult(
        val success: Boolean,
        val txId: String = "",
        val fee: Long = 0L,
        val error: String = ""
    )

    data class TxInfo(
        val txId: String,
        val direction: Int,         // 0 = incoming, 1 = outgoing
        val amount: Long,           // piconero
        val fee: Long,              // piconero
        val height: Long,           // 0 if pending/unconfirmed
        val timestamp: Long,        // epoch seconds
        val confirmations: Long,
        val label: String,
        val paymentId: String
    ) {
        val isIncoming: Boolean get() = direction == 0
        val isPending: Boolean get() = height == 0L
    }
}
