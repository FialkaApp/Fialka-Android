package com.fialkaapp.fialka.wallet.jni

import android.util.Log

/**
 * Wraps a C++ Monero::Wallet* pointer.
 *
 * NEVER use on the main thread — all methods are blocking JNI calls.
 *
 * Lifecycle:
 *   val wallet = WalletManager.openWallet(...) or WalletManager.recoveryWallet(...)
 *   wallet.init(daemonUrl, ...)          // connect, NON-blocking, NO DIAG loop
 *   wallet.setListener(myListener)       // register callbacks
 *   wallet.startRefresh()                // background sync starts here
 *   ...
 *   WalletManager.close(wallet)          // closes wallet, frees handle
 */
class Wallet(@JvmField val handle: Long) {

    // listenerHandle is set by the JNI layer (setListenerJ returns the MyWalletListener* ptr)
    @JvmField
    var listenerHandle: Long = 0L

    val isValid: Boolean get() = handle != 0L

    // ── Seed / keys ───────────────────────────────────────────────────────────

    external fun getSeed(seedOffset: String): String
    external fun getSecretViewKey(): String
    external fun getSecretSpendKey(): String

    // ── Address ───────────────────────────────────────────────────────────────

    external fun getAddressJ(accountIndex: Int, addressIndex: Int): String
    fun getAddress(accountIndex: Int = 0, addressIndex: Int = 0) =
        getAddressJ(accountIndex, addressIndex)

    // ── Status ────────────────────────────────────────────────────────────────

    external fun statusWithErrorString(): WalletStatus

    // ── Persistence ───────────────────────────────────────────────────────────

    external fun store(path: String): Boolean
    fun store() = store("")

    // ── Connection ────────────────────────────────────────────────────────────

    /**
     * Connect to daemon. NON-BLOCKING. Replaces the old nativeSetDaemon() DIAG loop.
     * Returns true on success.
     */
    external fun initJ(
        daemonAddress: String,
        upperTransactionSizeLimit: Long,
        daemonUsername: String,
        daemonPassword: String,
        proxyAddress: String
    ): Boolean

    fun init(
        daemonAddress: String,
        proxy: String = "",
        daemonUsername: String = "",
        daemonPassword: String = ""
    ): Boolean {
        Log.i("Wallet", "init() daemonAddress=$daemonAddress")
        return initJ(daemonAddress, 0L, daemonUsername, daemonPassword, proxy)
    }

    external fun getConnectionStatusJ(): Int

    // ── Restore height ───────────────────────────────────────────────────────

    external fun setRestoreHeight(height: Long)
    external fun getRestoreHeight(): Long

    // ── Balance ───────────────────────────────────────────────────────────────

    external fun getBalance(accountIndex: Int): Long
    external fun getUnlockedBalance(accountIndex: Int): Long
    fun balance(accountIndex: Int = 0) = getBalance(accountIndex)
    fun unlockedBalance(accountIndex: Int = 0) = getUnlockedBalance(accountIndex)

    // ── Height ────────────────────────────────────────────────────────────────

    external fun getBlockChainHeight(): Long
    external fun getDaemonBlockChainHeight(): Long
    external fun getDaemonBlockChainTargetHeight(): Long
    external fun isSynchronizedJ(): Boolean
    fun isSynchronized() = isSynchronizedJ()

    // ── Sync ──────────────────────────────────────────────────────────────────

    /** Start background refresh loop (non-blocking). */
    external fun startRefresh()

    /** Pause background refresh loop. */
    external fun pauseRefresh()

    /** Synchronous single-pass refresh. */
    external fun refresh(): Boolean

    /** Async single-pass refresh. */
    external fun refreshAsync()

    /** Full rescan from genesis (async). */
    external fun rescanBlockchainAsyncJ()
    fun rescanBlockchainAsync() = rescanBlockchainAsyncJ()

    // ── Listener ──────────────────────────────────────────────────────────────

    external fun setListenerJ(listener: WalletListener?): Long
    fun setListener(listener: WalletListener?) {
        listenerHandle = setListenerJ(listener)
    }

    // ── Transactions ──────────────────────────────────────────────────────────

    external fun getHistoryJ(): Long
    fun getHistory(): TransactionHistory = TransactionHistory(getHistoryJ())

    external fun createTransactionJ(
        dstAddr: String,
        paymentId: String,
        amount: Long,
        mixinCount: Int,
        priority: Int,
        accountIndex: Int
    ): Long

    fun createTransaction(
        dstAddr: String,
        amountPiconero: Long,
        priority: Int = 0,
        accountIndex: Int = 0,
        paymentId: String = "",
        mixinCount: Int = 0
    ): PendingTransaction = PendingTransaction(
        createTransactionJ(dstAddr, paymentId, amountPiconero, mixinCount, priority, accountIndex)
    )

    external fun disposeTransaction(pendingTransaction: PendingTransaction)

    // ── Static helpers ────────────────────────────────────────────────────────

    companion object {
        external fun isAddressValid(address: String, networkType: Int): Boolean
        external fun getDisplayAmount(amount: Long): String
        external fun getAmountFromString(amount: String): Long
        external fun getMaximumAllowedAmount(): Long
    }
}
