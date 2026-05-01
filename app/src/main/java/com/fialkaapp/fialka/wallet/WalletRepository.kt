/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667
 */
package com.fialkaapp.fialka.wallet

import android.content.Context
import android.util.Log
import java.io.File
import com.fialkaapp.fialka.crypto.FialkaNative
import com.fialkaapp.fialka.crypto.WalletSeedManager
import java.security.MessageDigest
import java.text.DateFormat
import java.util.Date
import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import org.json.JSONObject

data class WalletSendDraft(
    val address: String,
    val amount: String,
    val note: String
)

data class WalletLocalEvent(
    val title: String,
    val formattedAt: String
)

data class WalletNodeStatus(
    val online: Boolean,
    val daemonHeight: Long?,
    val targetHeight: Long?,
    val synchronized: Boolean,
    val statusLabel: String
)

data class WalletSnapshot(
    val enabled: Boolean,
    val hasSeed: Boolean,
    val nodeUrl: String,
    val receiveAddress: String?,
    val nodeStatus: WalletNodeStatus,
    // Kept for WalletSettingsFragment compatibility
    val syncEnabled: Boolean,
    val walletEngineStatus: String,
    // Real wallet data (from wallet2 JNI)
    val balancePiconero: Long,          // -1 = not yet available
    val unlockedPiconero: Long,         // -1 = not yet available
    val walletSyncHeight: Long,
    val transactions: List<MoneroWallet.TxInfo>,
    val sendDraft: WalletSendDraft?,
    val lastEvent: WalletLocalEvent?
)

object WalletRepository {

    private const val TAG = "WalletRepository"
    private const val PREFS_NAME = "fialka_wallet_runtime"
    private const val KEY_DRAFT_ADDRESS = "draft_address"
    private const val KEY_DRAFT_AMOUNT = "draft_amount"
    private const val KEY_DRAFT_NOTE = "draft_note"
    private const val KEY_LAST_EVENT_TITLE = "last_event_title"
    private const val KEY_LAST_EVENT_AT = "last_event_at"
    private const val KEY_SYNC_ENABLED = "sync_enabled"

    private const val WALLET_DIR = "xmr_wallet"
    private const val WALLET_FILE = "wallet"

    @Volatile private var receiveAddressCache: String? = null
    @Volatile private var nodeStatusCache: WalletNodeStatus? = null
    @Volatile private var nodeStatusCacheTime: Long = 0L
    private const val NODE_CACHE_TTL_MS = 20_000L

    private data class WalletStateCache(
        val balance: Long,
        val unlocked: Long,
        val walletHeight: Long,
        val txList: List<MoneroWallet.TxInfo>,
        val at: Long
    )
    @Volatile private var walletStateCache: WalletStateCache? = null
    private const val WALLET_CACHE_TTL_MS = 3_000L   // Short TTL — background sync updates state

    /** True while wallet init thread (setDaemon + rescan) is running. Never block on this. */
    @Volatile private var walletInitializing = false

    fun invalidateSeedCache() { receiveAddressCache = null }
    fun invalidateNodeCache() { nodeStatusCache = null; nodeStatusCacheTime = 0L }
    fun invalidateWalletStateCache() { walletStateCache = null }
    fun invalidateAllCaches() { invalidateSeedCache(); invalidateNodeCache(); invalidateWalletStateCache() }

    /** Returns the cached daemon height (0 if not yet fetched). Safe to call from any thread. */
    fun getLastKnownDaemonHeight(): Long = nodeStatusCache?.daemonHeight ?: 0L

    /** True while the wallet init thread is running (setDaemon DIAG loop). */
    fun isWalletInitializing(): Boolean = walletInitializing

    /** Main entry — must be called from Dispatchers.IO. */
    fun getSnapshot(context: Context): WalletSnapshot {
        val configuredNodeUrl = WalletPreferences.getNodeUrl(context)
        val runtimeNodeUrl = resolveRuntimeNodeUrl(configuredNodeUrl)

        val receiveAddress = receiveAddressCache
            ?: buildReceiveAddress(context).also { receiveAddressCache = it }

        val now = System.currentTimeMillis()
        val nodeStatus = nodeStatusCache
            .takeIf { it != null && (now - nodeStatusCacheTime) < NODE_CACHE_TTL_MS }
            ?: checkNodeStatus(context).also {
                nodeStatusCache = it; nodeStatusCacheTime = System.currentTimeMillis()
            }

        val walletState = if (nodeStatus.online) {
            getOrRefreshWalletState(context, runtimeNodeUrl)
        } else {
            walletStateCache  // return stale cache if node offline
        }

        val engineStatus = when {
            !MoneroWallet.isAvailable -> "⚠️ libfialka_monero non disponible sur cet ABI"
            !WalletSeedManager.hasWalletSeed(context) -> "⚠️ Aucune seed wallet"
            !MoneroWallet.isOpen -> "⚠️ Wallet non ouvert"
            else -> "✅ Wallet2 actif · hauteur ${walletState?.walletHeight ?: 0}"
        }

        return WalletSnapshot(
            enabled = WalletPreferences.isWalletEnabled(context),
            hasSeed = WalletSeedManager.hasWalletSeed(context),
            nodeUrl = runtimeNodeUrl,
            receiveAddress = receiveAddress,
            nodeStatus = nodeStatus,
            syncEnabled = isSyncEnabled(context),
            walletEngineStatus = engineStatus,
            balancePiconero = walletState?.balance ?: -1L,
            unlockedPiconero = walletState?.unlocked ?: -1L,
            walletSyncHeight = walletState?.walletHeight ?: 0L,
            transactions = walletState?.txList ?: emptyList(),
            sendDraft = getSendDraft(context),
            lastEvent = getLastLocalEvent(context)
        )
    }

    private fun getOrRefreshWalletState(context: Context, nodeUrl: String): WalletStateCache? {
        if (!MoneroWallet.isAvailable) return null
        if (!WalletSeedManager.hasWalletSeed(context)) return null

        // Short TTL: background sync updates state continuously, just read it frequently.
        val now = System.currentTimeMillis()
        val cached = walletStateCache
        if (cached != null && (now - cached.at) < WALLET_CACHE_TTL_MS) return cached

        return try {
            if (!MoneroWallet.isOpen) {
                // setDaemon() blocks ~25s (JNI DIAG loop) + rescanFromHeight() blocks more.
                // Launch init on a daemon thread so this function returns immediately —
                // the sync polling loop will pick up wallet state once init is done.
                if (!walletInitializing) {
                    walletInitializing = true
                    Thread({
                        try {
                            initWalletBlocking(context, nodeUrl)
                        } catch (e: Exception) {
                            Log.e(TAG, "Wallet init thread error", e)
                        } finally {
                            walletInitializing = false
                        }
                    }, "wallet-init").apply { isDaemon = true }.start()
                    Log.i(TAG, "Wallet init started on background thread")
                }
                return walletStateCache  // null or stale — UI polls until wallet is open
            }

            if (MoneroWallet.currentDaemonUrl != nodeUrl) {
                MoneroWallet.setDaemon(nodeUrl)
                MoneroWallet.connectToDaemon()
                MoneroWallet.startRefreshAsync()
            }

            // Read current state from wallet2's in-memory state.
            val state = WalletStateCache(
                balance = MoneroWallet.getBalance(),
                unlocked = MoneroWallet.getUnlockedBalance(),
                walletHeight = MoneroWallet.getBlockchainHeight(),
                txList = MoneroWallet.getHistory(),
                at = System.currentTimeMillis()
            )
            walletStateCache = state
            state
        } catch (e: Exception) {
            Log.e(TAG, "Wallet engine error", e)
            null
        }
    }

    /**
     * JNI fast-sync hard boundary: the wallet2 JNI (BUILD=16) has a checkpoint embedded at
     * exactly block 2100000. Passing refreshFromH=2100000 to createWallet causes the DIAG loop
     * to attempt full block scan starting at 2100000, which always returns status=1 because the
     * synthetic fast-sync header at the boundary doesn't match the real block data.
     * Any refreshFromH ABOVE this boundary works correctly.
     */
    private const val JNI_FAST_SYNC_BOUNDARY = 2_100_000L

    /**
     * Heavy wallet initialization — runs on a background thread, NEVER call from UI.
     *
     * setDaemon() triggers the JNI DIAG loop (1 blocking refresh pass).
     * The DIAG loop does fast-sync then full block scan starting at refreshFromH.
     * refreshFromH must be strictly ABOVE JNI_FAST_SYNC_BOUNDARY to avoid status=1.
     */
    private fun initWalletBlocking(context: Context, nodeUrl: String) {
        val dir = File(context.filesDir, WALLET_DIR).also { it.mkdirs() }
        val walletPath = File(dir, WALLET_FILE).absolutePath
        val password = deriveWalletPassword(context) ?: return

        if (File("$walletPath.keys").exists()) {
            val opened = MoneroWallet.openWallet(walletPath, password, WalletPreferences.getNetworkType(context))
            if (!opened) {
                Log.e(TAG, "openWallet failed: ${MoneroWallet.getLastError()}")
                return
            }
            Log.i(TAG, "Wallet opened from disk")
        } else {
            val mnemonic = WalletSeedManager.getMnemonic(context) ?: return
            val requestedH = WalletPreferences.getRestoreHeight(context)
            // Must be strictly above the JNI fast-sync boundary. At exactly 2100000 the DIAG
            // loop always gets status=1 (synthetic header mismatch). Use 2100001 as minimum.
            val safeH = if (requestedH <= JNI_FAST_SYNC_BOUNDARY) JNI_FAST_SYNC_BOUNDARY + 1L else requestedH
            Log.i(TAG, "Creating wallet (net=${WalletPreferences.getNetworkType(context)}, requestedH=$requestedH safeH=$safeH)")
            val created = MoneroWallet.createWallet(walletPath, password, mnemonic, WalletPreferences.getNetworkType(context), safeH)
            if (!created) {
                Log.e(TAG, "createWallet failed: ${MoneroWallet.getLastError()}")
                return
            }
        }

        // init() connects to daemon non-blocking (no DIAG loop).
        // With the new JNI, setDaemon() calls wallet->init() directly.
        Log.i(TAG, "setDaemon (non-blocking init)…")
        MoneroWallet.setDaemon(nodeUrl)
        Log.i(TAG, "setDaemon done, walletH=${MoneroWallet.getBlockchainHeight()}")

        MoneroWallet.startRefreshAsync()
        Log.i(TAG, "Wallet init complete — startRefreshAsync running")

        // Invalidate the receive address cache so the next getSnapshot() call picks up the
        // canonical wallet2 address instead of the Rust-derived one.
        invalidateSeedCache()

        // Persist wallet state so next launch opens at current height, not walletH=1.
        MoneroWallet.store()
        Log.i(TAG, "Wallet state saved to disk (walletH=${MoneroWallet.getBlockchainHeight()})")
    }

    /** Save wallet state to disk. Call from IO thread. Safe to call at any time. */
    fun storeWallet() {
        if (!MoneroWallet.isOpen) return
        try {
            MoneroWallet.store()
            Log.i(TAG, "storeWallet: saved (walletH=${MoneroWallet.getBlockchainHeight()})")
        } catch (e: Exception) {
            Log.e(TAG, "storeWallet failed", e)
        }
    }

    /** Restart the wallet2 auto-refresh thread. Call when status=1 is detected (reorg/stuck). */
    fun restartRefresh() {
        if (!MoneroWallet.isOpen) return
        try {
            Log.i(TAG, "restartRefresh: stopping+restarting auto-refresh (stuck detection)")
            MoneroWallet.stopRefresh()
            MoneroWallet.startRefreshAsync()
        } catch (e: Exception) {
            Log.e(TAG, "restartRefresh failed", e)
        }
    }

    fun isSyncEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SYNC_ENABLED, false)

    fun setSyncEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SYNC_ENABLED, enabled).apply()
    }

    fun checkNodeStatus(context: Context): WalletNodeStatus {
        val configuredNodeUrl = WalletPreferences.getNodeUrl(context)
        val runtimeNodeUrl = resolveRuntimeNodeUrl(configuredNodeUrl)
        return try {
            val rpcUrl = runtimeNodeUrl.trimEnd('/') + "/json_rpc"
            val connection = (URL(rpcUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4_000
                readTimeout = 4_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            val payload = """{"jsonrpc":"2.0","id":"0","method":"get_info"}""".toByteArray(Charsets.UTF_8)
            connection.outputStream.use { it.write(payload) }
            val code = connection.responseCode
            if (code !in 200..299) {
                return WalletNodeStatus(false, null, null, false, "Nœud hors ligne (HTTP $code)")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val result = JSONObject(body).optJSONObject("result")
                ?: return WalletNodeStatus(false, null, null, false, "Réponse nœud invalide")
            val height = result.optLong("height", -1L).takeIf { it >= 0L }
            val target = result.optLong("target_height", -1L).takeIf { it >= 0L }
            val synchronized = result.optBoolean("synchronized", false) ||
                (height != null && target != null && target > 0L && height >= target)
            val label = if (synchronized) "Nœud en ligne · sync OK" else "Nœud en ligne · syncing…"
            WalletNodeStatus(true, height, target, synchronized, label)
        } catch (_: Exception) {
            WalletNodeStatus(false, null, null, false, "Nœud hors ligne / injoignable")
        }
    }

    fun validateAddress(context: Context, address: String): Boolean {
        return try {
            FialkaNative.xmrValidateAddress(address.toByteArray(Charsets.UTF_8), WalletPreferences.getNetworkType(context)) > 0
        } catch (_: Throwable) {
            false
        }
    }

    fun saveSendDraft(context: Context, draft: WalletSendDraft) {
        prefs(context).edit()
            .putString(KEY_DRAFT_ADDRESS, draft.address.trim())
            .putString(KEY_DRAFT_AMOUNT, draft.amount.trim())
            .putString(KEY_DRAFT_NOTE, draft.note.trim())
            .apply()
    }

    fun clearSendDraft(context: Context) {
        prefs(context).edit()
            .remove(KEY_DRAFT_ADDRESS)
            .remove(KEY_DRAFT_AMOUNT)
            .remove(KEY_DRAFT_NOTE)
            .apply()
    }

    fun getSendDraft(context: Context): WalletSendDraft? {
        val address = prefs(context).getString(KEY_DRAFT_ADDRESS, null)?.trim().orEmpty()
        val amount = prefs(context).getString(KEY_DRAFT_AMOUNT, null)?.trim().orEmpty()
        val note = prefs(context).getString(KEY_DRAFT_NOTE, null)?.trim().orEmpty()
        if (address.isEmpty() && amount.isEmpty() && note.isEmpty()) return null
        return WalletSendDraft(address = address, amount = amount, note = note)
    }

    fun recordLocalEvent(context: Context, title: String) {
        prefs(context).edit()
            .putString(KEY_LAST_EVENT_TITLE, title)
            .putLong(KEY_LAST_EVENT_AT, System.currentTimeMillis())
            .apply()
    }

    /** Format piconero as "0.000000 XMR". Returns "-- XMR" if negative. */
    fun formatXmr(piconero: Long): String {
        if (piconero < 0) return "-- XMR"
        return "%.6f XMR".format(piconero / 1_000_000_000_000.0)
    }

    private fun buildReceiveAddress(context: Context): String? {
        // Priority 1: if wallet2 is open, use its address directly — this is the canonical address
        // that matches the official Monero GUI wallet for the same mnemonic.
        if (MoneroWallet.isOpen) {
            val addr = MoneroWallet.getAddress()
            if (addr.isNotEmpty()) return addr
        }

        // Priority 2: wallet not open yet — derive from spend key via Rust FFI as best-effort.
        // NOTE: This path may produce a DIFFERENT address than wallet2 in some configurations.
        // It should only be used as a temporary placeholder before wallet2 finishes initialising.
        val spendKey = WalletSeedManager.loadSpendKey(context) ?: return null
        return try {
            val keys = FialkaNative.xmrDeriveKeys(spendKey)
            if (keys.size < 96) return null
            val spendPub = keys.copyOfRange(0, 32)
            val viewPub = keys.copyOfRange(32, 64)
            val addrBytes = FialkaNative.xmrPrimaryAddress(spendPub, viewPub, WalletPreferences.getNetworkType(context))
            String(addrBytes, Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        } finally {
            spendKey.fill(0)
        }
    }

    private fun deriveWalletPassword(context: Context): String? {
        val spendKey = WalletSeedManager.loadSpendKey(context) ?: return null
        return try {
            val hash = MessageDigest.getInstance("SHA-256").digest(spendKey)
            hash.take(16).joinToString("") { "%02x".format(it) }
        } finally {
            spendKey.fill(0)
        }
    }

    private fun getLastLocalEvent(context: Context): WalletLocalEvent? {
        val title = prefs(context).getString(KEY_LAST_EVENT_TITLE, null) ?: return null
        val ts = prefs(context).getLong(KEY_LAST_EVENT_AT, 0L)
        val formatted = if (ts > 0L)
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(ts))
        else "-"
        return WalletLocalEvent(title = title, formattedAt = formatted)
    }

    private fun resolveRuntimeNodeUrl(configuredUrl: String): String {
        return try {
            val parsed = URI(configuredUrl)
            val host = parsed.host?.lowercase().orEmpty()
            if (isProbablyEmulator() && (host == "127.0.0.1" || host == "localhost")) {
                URI(parsed.scheme, parsed.userInfo, "10.0.2.2", parsed.port,
                    parsed.path, parsed.query, parsed.fragment).toString()
            } else configuredUrl
        } catch (_: Exception) { configuredUrl }
    }

    private fun isProbablyEmulator(): Boolean {
        val fp = android.os.Build.FINGERPRINT.lowercase()
        val model = android.os.Build.MODEL.lowercase()
        val mfr = android.os.Build.MANUFACTURER.lowercase()
        val brand = android.os.Build.BRAND.lowercase()
        val device = android.os.Build.DEVICE.lowercase()
        val product = android.os.Build.PRODUCT.lowercase()
        return fp.contains("generic") || fp.contains("emulator") ||
            model.contains("emulator") || model.contains("sdk") ||
            mfr.contains("genymotion") ||
            (brand.startsWith("generic") && device.startsWith("generic")) ||
            product.contains("sdk")
    }

    /**
     * Wipe all wallet data: closes the JNI wallet, deletes files on disk,
     * erases the encrypted seed, and clears all preferences and caches.
     * Call from IO thread (never on main thread).
     */
    fun deleteWallet(context: Context) {
        // 1. Close JNI wallet if open
        try { if (MoneroWallet.isOpen) MoneroWallet.closeWallet() } catch (_: Exception) {}
        // 2. Erase encrypted spend key
        WalletSeedManager.wipeWallet(context)
        // 3. Delete wallet2 files on disk
        try {
            File(context.filesDir, WALLET_DIR).listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {}
        // 4. Clear wallet enabled / created flags
        WalletPreferences.setWalletEnabled(context, false)
        WalletPreferences.setWalletCreated(context, false)
        // 5. Clear runtime prefs (draft, last event, sync flag)
        prefs(context).edit().clear().apply()
        // 6. Invalidate all in-memory caches
        invalidateAllCaches()
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
