/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.crypto

import android.content.Context
import com.fialkaapp.fialka.util.FialkaSecurePrefs
import java.math.BigInteger
import java.security.SecureRandom

/**
 * Separate, secure XMR wallet seed manager.
 *
 * IMPORTANT:
 * - This seed is independent from the Fialka messaging seed.
 * - Stored encrypted with Android Keystore (FialkaSecurePrefs).
 */
object WalletSeedManager {

    private const val PREFS_FILE = "fialka_wallet_keys"
    private const val KEY_WALLET_SPEND_KEY = "wallet_xmr_spend_key"

    private val random = SecureRandom()

    // ed25519 subgroup order l:
    // 2^252 + 27742317777372353535851937790883648493
    private val L: BigInteger =
        BigInteger.ONE.shiftLeft(252).add(BigInteger("27742317777372353535851937790883648493"))

    fun hasWalletSeed(context: Context): Boolean =
        walletPrefs(context).getString(KEY_WALLET_SPEND_KEY, null) != null

    /**
     * Generate a new private spend key, store it encrypted, and return 25-word mnemonic.
     */
    fun generateNewMnemonic(context: Context): String {
        val spendKey = generateSpendKey()
        return try {
            persist(context, spendKey)
            MoneroMnemonic.init(context)
            MoneroMnemonic.seedToMnemonic(spendKey).joinToString(" ")
        } finally {
            spendKey.fill(0)
        }
    }

    /**
     * Import from native Monero 25-word mnemonic.
     */
    fun importFromMnemonic(context: Context, mnemonic25words: String): Boolean {
        val words = mnemonic25words.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.size != 25) return false

        MoneroMnemonic.init(context)
        val spendKey = MoneroMnemonic.mnemonicToSeed(words) ?: return false
        return try {
            persist(context, spendKey)
            true
        } finally {
            spendKey.fill(0)
        }
    }

    fun getMnemonic(context: Context): String? {
        val spendKey = loadSpendKey(context) ?: return null
        return try {
            MoneroMnemonic.init(context)
            MoneroMnemonic.seedToMnemonic(spendKey).joinToString(" ")
        } finally {
            spendKey.fill(0)
        }
    }

    fun loadSpendKey(context: Context): ByteArray? {
        val hex = walletPrefs(context).getString(KEY_WALLET_SPEND_KEY, null) ?: return null
        if (hex.length != 64) return null
        return ByteArray(32) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    fun wipeWallet(context: Context) {
        walletPrefs(context).edit().remove(KEY_WALLET_SPEND_KEY).apply()
    }

    private fun persist(context: Context, spendKey: ByteArray) {
        val hex = spendKey.joinToString("") { "%02x".format(it) }
        walletPrefs(context).edit().putString(KEY_WALLET_SPEND_KEY, hex).apply()
    }

    private fun walletPrefs(context: Context): FialkaSecurePrefs.Prefs =
        FialkaSecurePrefs.open(context.applicationContext, PREFS_FILE, strongBox = false)

    private fun generateSpendKey(): ByteArray {
        val raw = ByteArray(32)
        random.nextBytes(raw)

        // Reduce mod l to produce a canonical scalar.
        val scalar = littleEndianToBigInt(raw).mod(L)
        raw.fill(0)

        var out = bigIntToLittleEndian32(scalar)
        if (out.all { it == 0.toByte() }) {
            out.fill(0)
            out = ByteArray(32).also {
                do {
                    random.nextBytes(it)
                } while (it.all { b -> b == 0.toByte() })
            }
        }
        return out
    }

    private fun littleEndianToBigInt(bytes: ByteArray): BigInteger {
        val be = bytes.reversedArray()
        return BigInteger(1, be)
    }

    private fun bigIntToLittleEndian32(value: BigInteger): ByteArray {
        val be = value.toByteArray()
        val padded = ByteArray(32)
        val src = if (be.size > 32) be.copyOfRange(be.size - 32, be.size) else be
        System.arraycopy(src, 0, padded, 32 - src.size, src.size)
        return padded.reversedArray()
    }
}
