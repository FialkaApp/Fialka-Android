/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.wallet

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.fialkaapp.fialka.tor.TorManager
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URL

/**
 * HTTP JSON client for a Monero Light Wallet Server (LWS / OpenMonero protocol).
 *
 * ### Tor routing
 * Every request goes through the SOCKS5 proxy on 127.0.0.1:9050 (TorManager.socksPort).
 * This is handled explicitly per-connection (not global ProxySelector) so wallet traffic
 * is always isolated even if the global selector is re-set.
 *
 * ### LWS API (OpenMonero / mymonero-core protocol v1)
 * All endpoints are JSON over HTTP POST:
 * - `POST /login`            register/refresh account, get server info
 * - `POST /get_address_info` balance, scanned height, blockchain height
 * - `POST /get_address_txs`  transaction list with amounts
 * - `POST /submit_raw_tx`    broadcast signed transaction (Phase 6 — send)
 *
 * ### Server configuration
 * Default community server: `http://lws.monerolws.com` (clearnet .onion forthcoming).
 * User can override via [setServerUrl]. Stored in plain SharedPreferences (not sensitive).
 *
 * @param context Application context (for prefs + TorManager port)
 */
class MoneroLwsClient(private val context: Context) {

    // ── Configuration ────────────────────────────────────────────────────────

    companion object {
        private const val TAG = "MoneroLwsClient"
        private const val PREFS_NAME = "fialka_lws_config"
        private const val KEY_SERVER_URL = "lws_server_url"

        /**
         * Default LWS instance. Community-operated, open-source.
         * Users can change this in wallet settings (Phase 5).
         * Using .onion address when one is published by the operator.
         */
        private const val DEFAULT_SERVER_URL = "http://node.moneroworld.com:8082"

        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS    = 60_000
    }

    private val prefs: SharedPreferences
        get() = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Current configured server URL (trailing slash stripped). */
    val serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL)
                     ?.trimEnd('/') ?: DEFAULT_SERVER_URL

    /** Persist a custom LWS server URL. Pass null to reset to default. */
    fun setServerUrl(url: String?) {
        if (url == null) {
            prefs.edit().remove(KEY_SERVER_URL).apply()
        } else {
            prefs.edit().putString(KEY_SERVER_URL, url.trimEnd('/')).apply()
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Register or refresh an account on the LWS server.
     *
     * LWS needs the primary spend public key and view private key to scan
     * the blockchain on the client's behalf.
     *
     * @param address       Primary XMR address (95 chars, starts with "4")
     * @param viewKey       View private key (64-char hex)
     * @param createAccount If true, server will create account if it doesn't exist
     * @return              [LwsLoginResult] with server info, null on failure
     */
    suspend fun login(
        address: String,
        viewKey: String,
        createAccount: Boolean = true
    ): LwsLoginResult? {
        val body = JSONObject().apply {
            put("address", address)
            put("view_key", viewKey)
            put("create_account", createAccount)
            put("generated_locally", true)
        }
        val resp = post("/login", body) ?: return null
        return try {
            LwsLoginResult(
                newAddress       = resp.optBoolean("new_address", false),
                generatedLocally = resp.optBoolean("generated_locally", true),
                startHeight      = resp.optLong("start_height", 0L)
            )
        } catch (e: Exception) {
            Log.e(TAG, "login parse error", e)
            null
        }
    }

    /**
     * Get address balance and sync progress from LWS.
     *
     * @param address       Primary XMR address
     * @param viewKey       View private key (64-char hex)
     * @return              [LwsAddressInfo] or null on failure
     */
    suspend fun getAddressInfo(address: String, viewKey: String): LwsAddressInfo? {
        val body = JSONObject().apply {
            put("address", address)
            put("view_key", viewKey)
        }
        val resp = post("/get_address_info", body) ?: return null
        return try {
            LwsAddressInfo(
                totalReceived    = resp.optLong("total_received", 0L),
                totalSent        = resp.optLong("total_sent", 0L),
                lockedFunds      = resp.optLong("locked_funds", 0L),
                scannedBlockHeight = resp.optLong("scanned_block_height", 0L),
                scannedBlockTimestamp = resp.optLong("scanned_block_timestamp", 0L),
                transactionHeight  = resp.optLong("transaction_height", 0L),
                blockchainHeight   = resp.optLong("blockchain_height", 0L)
            )
        } catch (e: Exception) {
            Log.e(TAG, "getAddressInfo parse error", e)
            null
        }
    }

    /**
     * Fetch all transactions for the address from the LWS server.
     *
     * @param address   Primary XMR address
     * @param viewKey   View private key (64-char hex)
     * @return          List of [LwsTransaction], empty list on failure or no txs
     */
    suspend fun getAddressTxs(address: String, viewKey: String): List<LwsTransaction> {
        val body = JSONObject().apply {
            put("address", address)
            put("view_key", viewKey)
        }
        val resp = post("/get_address_txs", body) ?: return emptyList()
        return try {
            val arr = resp.optJSONArray("transactions") ?: return emptyList()
            buildList {
                for (i in 0 until arr.length()) {
                    val tx = arr.getJSONObject(i)
                    add(
                        LwsTransaction(
                            id             = tx.optString("id", ""),
                            hash           = tx.optString("hash", ""),
                            timestamp      = tx.optLong("timestamp", 0L),
                            totalReceived  = tx.optLong("total_received", 0L),
                            totalSent      = tx.optLong("total_sent", 0L),
                            fee            = tx.optLong("fee", 0L),
                            unlockTime     = tx.optLong("unlock_time", 0L),
                            height         = tx.optLong("height", 0L),
                            // height==0 means unconfirmed/mempool
                            coinbase       = tx.optBoolean("coinbase", false),
                            mempool        = tx.optLong("height", -1L) == 0L
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAddressTxs parse error", e)
            emptyList()
        }
    }

    // ── Internal HTTP machinery ───────────────────────────────────────────────

    /**
     * Execute a JSON POST through Tor SOCKS5 proxy.
     * Returns the parsed top-level JSONObject, or null if the request failed.
     *
     * Uses explicit per-connection SOCKS5 proxy (not global ProxySelector) for safety:
     * If Tor is not yet connected, this call would fail rather than leak to clearnet.
     */
    @Suppress("BlockingMethodInNonBlockingContext")
    private fun post(path: String, body: JSONObject): JSONObject? {
        val torPort = TorManager.socksPort
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", torPort))

        return try {
            val url = URL("$serverUrl$path")
            val conn = url.openConnection(proxy) as HttpURLConnection
            conn.apply {
                requestMethod = "POST"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout    = READ_TIMEOUT_MS
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Fialka/1.0 (Android)")
                doOutput = true
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { w ->
                w.write(body.toString())
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                Log.w(TAG, "LWS $path → HTTP $code")
                conn.disconnect()
                return null
            }

            val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).readText()
            conn.disconnect()
            JSONObject(raw)
        } catch (e: Exception) {
            Log.e(TAG, "LWS POST $path failed", e)
            null
        }
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    data class LwsLoginResult(
        val newAddress: Boolean,
        val generatedLocally: Boolean,
        val startHeight: Long
    )

    data class LwsAddressInfo(
        /** Total piconero ever received (all confirmed transactions). */
        val totalReceived: Long,
        /** Total piconero ever sent (all confirmed transactions). */
        val totalSent: Long,
        /** Piconero locked (outputs < 10 confirmations). */
        val lockedFunds: Long,
        /** Highest block the server has scanned for this address. */
        val scannedBlockHeight: Long,
        val scannedBlockTimestamp: Long,
        /** Highest block that has a transaction for this address. */
        val transactionHeight: Long,
        /** Current tip of the Monero blockchain. */
        val blockchainHeight: Long
    ) {
        /** Unlocked balance in piconero. */
        val unlockedBalance: Long get() = totalReceived - totalSent - lockedFunds
        /** Total balance (including locked) in piconero. */
        val totalBalance: Long get() = totalReceived - totalSent
    }

    data class LwsTransaction(
        val id: String,
        val hash: String,
        val timestamp: Long,
        val totalReceived: Long,
        val totalSent: Long,
        val fee: Long,
        val unlockTime: Long,
        val height: Long,
        val coinbase: Boolean,
        val mempool: Boolean
    )
}
