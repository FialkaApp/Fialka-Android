/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.ui.wallet

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.viewModelScope
import com.fialkaapp.fialka.crypto.WalletSeedManager
import com.fialkaapp.fialka.data.model.WalletTransaction
import com.fialkaapp.fialka.data.model.WalletSyncState
import com.fialkaapp.fialka.wallet.MoneroLwsClient
import com.fialkaapp.fialka.wallet.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WalletHomeViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WalletRepository(app)
    private val client = MoneroLwsClient(app)

    /** All transactions, ordered by timestamp descending (emitted from DB). */
    val transactions: LiveData<List<WalletTransaction>> = repo.observeTransactions()

    /** Current sync state (block heights, last sync timestamp). */
    val syncState: LiveData<WalletSyncState?> = repo.observeSyncState()

    /**
     * Unlocked balance derived from confirmed incoming minus confirmed outgoing,
     * in piconero. Convert to XMR via [WalletTransaction.piconeroToXmr].
     */
    val balance: LiveData<Long> = transactions.map { list ->
        var bal = 0L
        for (tx in list) {
            if (tx.status == WalletTransaction.STATUS_CONFIRMED ||
                tx.status == WalletTransaction.STATUS_CONFIRMING) {
                bal += when (tx.direction) {
                    WalletTransaction.DIRECTION_INCOMING -> tx.amountPiconero
                    WalletTransaction.DIRECTION_OUTGOING -> -(tx.amountPiconero + tx.feePiconero)
                    else -> 0L
                }
            }
        }
        maxOf(0L, bal)
    }

    /** Primary Monero address; loaded once lazily on first receive-tab open. */
    val primaryAddress = MutableLiveData<String?>(null)

    /** True while a manual sync request is in progress. */
    val syncRunning = MutableLiveData(false)

    /** Human-readable result of the last manual sync (null = no result yet). */
    val syncResult = MutableLiveData<String?>(null)

    // ── Address loading ───────────────────────────────────────────────────────

    /** Load primary address if not already loaded. */
    fun loadPrimaryAddress() {
        if (primaryAddress.value != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val addr = repo.getPrimaryAddress()
            withContext(Dispatchers.Main) { primaryAddress.value = addr }
        }
    }

    // ── Manual sync ───────────────────────────────────────────────────────────

    /** Trigger one sync cycle immediately (runs on IO dispatcher). */
    fun triggerSync() {
        if (syncRunning.value == true) return
        syncRunning.value = true
        syncResult.value = null
        viewModelScope.launch(Dispatchers.IO) {
            val ok = try {
                repo.syncOnce()
            } catch (e: Exception) {
                false
            }
            withContext(Dispatchers.Main) {
                syncRunning.value = false
                syncResult.value = if (ok) null else "Sync failed — check server URL or Tor"
            }
        }
    }

    // ── Server URL ────────────────────────────────────────────────────────────

    /** Current configured LWS server URL. */
    fun getServerUrl(): String = client.serverUrl

    /** Persist a new server URL. Pass null to reset to default. */
    fun setServerUrl(url: String?) {
        client.setServerUrl(url?.takeIf { it.isNotBlank() })
    }

    // ── Wallet wipe ───────────────────────────────────────────────────────────

    /**
     * Irreversibly delete the wallet seed from the device.
     * WorkManager sync job is cancelled. DB wallet tables are NOT wiped
     * (historical data is harmless without the keys).
     */
    fun wipeWallet() {
        WalletSeedManager.wipeWallet(getApplication())
        com.fialkaapp.fialka.wallet.MoneroSyncWorker.cancel(getApplication())
    }
}
