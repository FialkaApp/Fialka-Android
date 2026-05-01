/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.fialkaapp.fialka.wallet

import android.content.Context

/**
 * Wallet feature flags and basic runtime configuration.
 *
 * IMPORTANT:
 * - Wallet is disabled by default.
 * - Messaging seed and wallet seed MUST remain fully separate.
 */
object WalletPreferences {

    private const val PREFS_NAME = "fialka_wallet"
    private const val KEY_ENABLED = "wallet_enabled"
    private const val KEY_NODE_URL = "wallet_node_url"
    private const val KEY_CREATED = "wallet_created"
    private const val KEY_RESTORE_HEIGHT = "wallet_restore_height"
    private const val KEY_NETWORK_TYPE = "wallet_network_type"

    /** Monero network constants — 0 = Mainnet, 2 = Stagenet */
    const val MAINNET = 0
    const val STAGENET = 2

    private const val DEFAULT_STAGENET_NODE_URL = "http://127.0.0.1:38081"
    private const val DEFAULT_MAINNET_NODE_URL  = "http://127.0.0.1:18081"
    // Recent stagenet height — wallet only scans the last ~5000 blocks instead of 2.1M.
    // Always set explicitly via setRestoreHeight() before createWallet/importFromMnemonic.
    private const val DEFAULT_STAGENET_RESTORE_HEIGHT = 2_100_000L
    private const val DEFAULT_MAINNET_RESTORE_HEIGHT  = 3_300_000L

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isWalletEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setWalletEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getNetworkType(context: Context): Int =
        prefs(context).getInt(KEY_NETWORK_TYPE, STAGENET)

    fun setNetworkType(context: Context, type: Int) {
        prefs(context).edit().putInt(KEY_NETWORK_TYPE, type).apply()
    }

    fun isStagenet(context: Context): Boolean = getNetworkType(context) == STAGENET

    /** Returns "Stagenet" or "Mainnet" */
    fun getNetworkLabel(context: Context): String =
        if (isStagenet(context)) "Stagenet" else "Mainnet"

    fun getNodeUrl(context: Context): String {
        val default = if (isStagenet(context)) DEFAULT_STAGENET_NODE_URL else DEFAULT_MAINNET_NODE_URL
        return prefs(context).getString(KEY_NODE_URL, default).orEmpty()
    }

    fun setNodeUrl(context: Context, nodeUrl: String) {
        prefs(context).edit().putString(KEY_NODE_URL, nodeUrl.trim()).apply()
    }

    fun isWalletCreated(context: Context): Boolean =
        prefs(context).getBoolean(KEY_CREATED, false)

    fun setWalletCreated(context: Context, created: Boolean) {
        prefs(context).edit().putBoolean(KEY_CREATED, created).apply()
    }

    fun getRestoreHeight(context: Context): Long {
        val default = if (isStagenet(context)) DEFAULT_STAGENET_RESTORE_HEIGHT else DEFAULT_MAINNET_RESTORE_HEIGHT
        return prefs(context).getLong(KEY_RESTORE_HEIGHT, default)
    }

    fun setRestoreHeight(context: Context, height: Long) {
        prefs(context).edit().putLong(KEY_RESTORE_HEIGHT, height).apply()
    }
}
