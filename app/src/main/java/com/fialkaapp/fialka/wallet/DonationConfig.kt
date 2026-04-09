/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 *
 * IMPORTANT — Do NOT change these keys before the first mainnet release.
 * They are embedded in the APK and changing them would break existing donation tracking.
 * The corresponding view-only wallet can be shared publicly (open-source transparency).
 *
 * How it works:
 *   - SPEND_PUB: donation wallet's spend public key (32 bytes, hex-decoded)
 *   - VIEW_PRIV: donation wallet's view private key (32 bytes, hex-decoded)
 *
 * Subaddresses are derived deterministically per-contact via:
 *   FialkaNative.xmrDeriveDonationSubaddress(SPEND_PUB, VIEW_PRIV, contactIdBytes)
 *
 * Setup:
 *   1. Generate a fresh Monero wallet (cold, air-gapped) — this is the donation wallet.
 *   2. Export its spend_pub and view_priv (Feather Wallet → Tools → Wallet Keys).
 *   3. Replace the placeholder hex strings below with the real keys.
 *   4. Include the primary address and view_key in README/DONATIONS.md for public auditing.
 *
 * WARNING: VIEW_PRIV allows scanning all incoming transactions — keep it in the APK only
 * for subaddress derivation. The spend key MUST stay cold and offline at all times.
 */
package com.fialkaapp.fialka.wallet

/**
 * Donation wallet keys — embedded in APK for deterministic subaddress derivation.
 *
 * Replace [SPEND_PUB_HEX] and [VIEW_PRIV_HEX] with the real donation wallet keys
 * before the first public release.
 */
object DonationConfig {

    /** Decode a lowercase hex string to a ByteArray. */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun hexToBytes(hex: String): ByteArray =
        ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }


    /**
     * Donation wallet spend **public** key — 64 hex chars (32 bytes).
     * TODO: Replace with real donation wallet spend_pub before release.
     */
    private const val SPEND_PUB_HEX =
        "25bf4e55bd298ea9362676cadb04323c5e3a1e90f3723edbcf44f860d48028ad"

    /**
     * Donation wallet view **private** key — 64 hex chars (32 bytes).
     * This key allows scanning for incoming transactions (read-only).
     * TODO: Replace with real donation wallet view_priv before release.
     */
    private const val VIEW_PRIV_HEX =
        "a0f0c2f047ca7bb81aecb22dd53f3a33204ec32a56e6abe3010f60d3f19d1802"

    /** Spend public key as a 32-byte array. */
    val SPEND_PUB: ByteArray by lazy { hexToBytes(SPEND_PUB_HEX) }

    /** View private key as a 32-byte array. */
    val VIEW_PRIV: ByteArray by lazy { hexToBytes(VIEW_PRIV_HEX) }
}
