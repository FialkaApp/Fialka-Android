/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.crypto

import android.content.Context
import android.content.SharedPreferences
import com.fialkaapp.fialka.util.DeviceSecurityManager
import com.fialkaapp.fialka.util.FialkaSecurePrefs

/**
 * Public keys derived from the XMR wallet seed.
 *
 * All fields are raw 32-byte little-endian scalars / curve points.
 *
 * **IMPORTANT: call [zeroize] immediately after use.**
 * Never store an instance of this class beyond the callsite scope.
 */
data class XmrKeys(
    val spendPub: ByteArray,   // 32 bytes
    val viewPub: ByteArray,    // 32 bytes
    val viewPriv: ByteArray    // 32 bytes — treat as sensitive
) {
    /**
     * Overwrite all key material with zeros.
     * Must be called once the caller no longer needs the keys.
     */
    fun zeroize() {
        spendPub.fill(0)
        viewPub.fill(0)
        viewPriv.fill(0)
    }

    override fun equals(other: Any?): Boolean = false   // prevent accidental equality leaks
    override fun hashCode(): Int = System.identityHashCode(this)
}

/**
 * Manages the XMR wallet seed lifecycle.
 *
 * ### Seed isolation
 * The XMR wallet seed is completely independent of the Ed25519 identity seed:
 * - Identity seed: stored under `fialka_identity_keys` / `identity_ed25519_seed`
 * - Wallet seed:   stored under [PREFS_FILE] / [KEY_WALLET_SEED]
 *
 * ### Storage security
 * Both seeds use [FialkaSecurePrefs] (EncryptedSharedPreferences backed by
 * Android Keystore AES-256-GCM). The raw 32-byte seed is stored as a
 * hex-encoded string (constant length, no padding side-channels).
 *
 * ### Monero mnemonic format
 * The native Monero 25-word mnemonic (NOT BIP-39) is decoded/encoded via
 * [MoneroMnemonic] (Phase 3). This class just stores and retrieves the raw seed.
 */
object WalletSeedManager {

    private const val PREFS_FILE = "fialka_wallet_keys"
    private const val KEY_WALLET_SEED = "wallet_xmr_seed"

    private const val FEATURE_PREFS = "fialka_feature_flags"
    private const val KEY_WALLET_ENABLED = "wallet_enabled"

    // ── Wallet feature flag ─────────────────────────────────────────────────

    /** Returns true if the user has opted in to the wallet feature. */
    fun isWalletEnabled(context: Context): Boolean =
        featurePrefs(context).getBoolean(KEY_WALLET_ENABLED, false)

    /**
     * Enable or disable the wallet feature.
     * Disabling does NOT delete the seed — it only hides the UI.
     */
    fun setWalletEnabled(context: Context, enabled: Boolean) {
        featurePrefs(context).edit().putBoolean(KEY_WALLET_ENABLED, enabled).apply()
    }

    // ── Seed existence ──────────────────────────────────────────────────────

    /** Returns true if a wallet seed has already been generated or imported. */
    fun hasWalletSeed(context: Context): Boolean =
        walletPrefs(context).getString(KEY_WALLET_SEED, null) != null

    // ── Seed generation ─────────────────────────────────────────────────────

    /**
     * Generate a fresh 32-byte XMR wallet seed via the Rust RNG ([FialkaNative.xmrGenerateSeed])
     * and persist it securely.
     *
     * Throws [IllegalStateException] if a seed already exists — call [hasWalletSeed] first.
     * The seed bytes are overwritten with zeros immediately after persisting.
     */
    fun generateAndStoreNewSeed(context: Context) {
        check(!hasWalletSeed(context)) {
            "WalletSeedManager: a seed already exists — wipe it before generating a new one"
        }

        var seed: ByteArray? = null
        try {
            seed = FialkaNative.xmrGenerateSeed()
            require(seed.size == 32) { "xmrGenerateSeed returned ${seed.size} bytes (expected 32)" }
            persist(context, seed)
        } finally {
            seed?.fill(0)
        }
    }

    // ── Seed import ─────────────────────────────────────────────────────────

    /**
     * Import an existing wallet from a 25-word native Monero mnemonic.
     *
     * The mnemonic is decoded to a 32-byte seed via [MoneroMnemonic.toSeed].
     * Returns `false` if the mnemonic is invalid (wrong word count, unknown words,
     * bad checksum). The existing seed (if any) is NOT overwritten on failure.
     *
     * NOTE: Mnemonic decoding is implemented in Phase 3 (MoneroMnemonic.kt).
     */
    fun importFromMnemonic(context: Context, mnemonic25words: String): Boolean {
        val words = mnemonic25words.trim().split(Regex("\\s+"))
        if (words.size != 25) return false

        var seed: ByteArray? = null
        return try {
            seed = MoneroMnemonic.mnemonicToSeed(words) ?: return false
            persist(context, seed)
            true
        } finally {
            seed?.fill(0)
        }
    }

    // ── Key derivation ──────────────────────────────────────────────────────

    /**
     * Load the stored seed and derive the XMR key triple via [FialkaNative.xmrDeriveKeys].
     *
     * Returns `null` if no seed is stored.
     *
     * **The returned [XmrKeys] must be [XmrKeys.zeroize]d immediately after use.**
     * Even though viewPriv is "only" the view key, it can be used to identify all
     * incoming transactions — treat it as sensitive material.
     */
    fun deriveAndGetKeys(context: Context): XmrKeys? {
        var seed: ByteArray? = null
        var raw96: ByteArray? = null
        try {
            seed = loadSeed(context) ?: return null
            raw96 = FialkaNative.xmrDeriveKeys(seed)
            require(raw96.size == 96) {
                "xmrDeriveKeys returned ${raw96.size} bytes (expected 96)"
            }
            return XmrKeys(
                spendPub = raw96.copyOfRange(0, 32),
                viewPub  = raw96.copyOfRange(32, 64),
                viewPriv = raw96.copyOfRange(64, 96)
            )
        } finally {
            seed?.fill(0)
            raw96?.fill(0)
        }
    }

    // ── Seed deletion ───────────────────────────────────────────────────────

    /**
     * Wipe the stored seed and disable the wallet feature.
     *
     * This is destructive. Caller must ensure the user has backed up the
     * 25-word mnemonic before calling this method.
     */
    fun wipeWallet(context: Context) {
        walletPrefs(context).edit()
            .remove(KEY_WALLET_SEED)
            .apply()
        setWalletEnabled(context, false)
    }

    // ── Internal helpers ────────────────────────────────────────────────────

    private fun persist(context: Context, seed: ByteArray) {
        val hex = seed.joinToString("") { "%02x".format(it) }
        walletPrefs(context).edit()
            .putString(KEY_WALLET_SEED, hex)
            .apply()
    }

    /**
     * Load the stored 32-byte seed from prefs.
     * Returns null if no seed is stored yet.
     * **Caller must zero the returned ByteArray after use.**
     */
    private fun loadSeed(context: Context): ByteArray? {
        val hex = walletPrefs(context).getString(KEY_WALLET_SEED, null) ?: return null
        require(hex.length == 64) { "Stored seed has unexpected length ${hex.length}" }
        return ByteArray(32) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun walletPrefs(context: Context): FialkaSecurePrefs.Prefs {
        val profile = DeviceSecurityManager.getSecurityProfile(context.applicationContext)
        return FialkaSecurePrefs.open(
            context.applicationContext,
            PREFS_FILE,
            strongBox = profile.isStrongBoxAvailable
        )
    }

    private fun featurePrefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(FEATURE_PREFS, Context.MODE_PRIVATE)
}
