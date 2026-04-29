package com.fialkaapp.fialka.wallet.jni

import android.util.Log

/**
 * Singleton that mirrors C++ Monero::WalletManagerFactory::getWalletManager().
 *
 * Loads libfialka_monero.so on first access.
 */
object WalletManager {

    private const val TAG = "WalletManager"

    var isAvailable: Boolean = false
        private set

    init {
        try {
            System.loadLibrary("fialka_monero")
            isAvailable = true
            Log.i(TAG, "libfialka_monero loaded")
        } catch (e: UnsatisfiedLinkError) {
            isAvailable = false
            Log.e(TAG, "libfialka_monero FAILED: ${e.message}")
        }
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    /**
     * Create a brand-new wallet (generates mnemonic internally).
     * For restore-from-seed use recoveryWallet().
     */
    external fun createWalletJ(
        path: String, password: String, language: String, networkType: Int
    ): Long

    /**
     * Open an existing wallet from disk.
     * Returns Wallet with handle=0 on failure — check wallet.statusWithErrorString().
     */
    external fun openWalletJ(path: String, password: String, networkType: Int): Long

    /**
     * Restore wallet from 25-word mnemonic.
     * @param restoreHeight Block to start scanning from. Use daemonHeight to skip full scan.
     * @param offset        Passphrase/offset (empty string for none).
     */
    external fun recoveryWalletJ(
        path: String, password: String,
        mnemonic: String, offset: String,
        networkType: Int, restoreHeight: Long
    ): Long

    external fun walletExists(path: String): Boolean

    external fun closeJ(wallet: Wallet): Boolean

    external fun getBlockchainHeight(): Long
    external fun getBlockchainTargetHeight(): Long
    external fun setDaemonAddressJ(address: String)

    external fun initLogger(argv0: String, defaultLogBaseName: String)
    external fun setLogLevel(level: Int)

    // ── Kotlin wrappers ───────────────────────────────────────────────────────

    fun createWallet(
        path: String, password: String,
        language: String = "English",
        networkType: Int = 2
    ): Wallet? {
        if (!isAvailable) return null
        val handle = createWalletJ(path, password, language, networkType)
        if (handle == 0L) return null
        val w = Wallet(handle)
        val status = w.statusWithErrorString()
        if (!status.isOk) {
            Log.e(TAG, "createWallet error: ${status.errorString}")
            closeJ(w)
            return null
        }
        return w
    }

    fun openWallet(
        path: String, password: String, networkType: Int = 2
    ): Wallet? {
        if (!isAvailable) return null
        val handle = openWalletJ(path, password, networkType)
        if (handle == 0L) return null
        val w = Wallet(handle)
        val status = w.statusWithErrorString()
        if (!status.isOk) {
            Log.e(TAG, "openWallet error: ${status.errorString}")
            closeJ(w)
            return null
        }
        return w
    }

    /**
     * Restore from mnemonic.
     *
     * NOTE: restoreHeight is passed directly to recoveryWallet() — no DIAG loop,
     * no fast-sync boundary issue. The C++ layer handles it cleanly.
     */
    fun recoveryWallet(
        path: String, password: String,
        mnemonic: String, offset: String = "",
        networkType: Int = 2,
        restoreHeight: Long = 0L
    ): Wallet? {
        if (!isAvailable) return null
        Log.i(TAG, "recoveryWallet restoreHeight=$restoreHeight")
        val handle = recoveryWalletJ(path, password, mnemonic, offset, networkType, restoreHeight)
        if (handle == 0L) return null
        val w = Wallet(handle)
        val status = w.statusWithErrorString()
        if (!status.isOk) {
            Log.e(TAG, "recoveryWallet error: ${status.errorString}")
            closeJ(w)
            return null
        }
        return w
    }

    fun close(wallet: Wallet): Boolean {
        if (!isAvailable) return false
        return closeJ(wallet)
    }
}
