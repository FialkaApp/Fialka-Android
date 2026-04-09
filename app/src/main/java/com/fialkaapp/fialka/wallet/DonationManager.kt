/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.wallet

import android.util.Log
import com.fialkaapp.fialka.crypto.FialkaNative

/**
 * Derives deterministic Monero donation subaddresses from the hardcoded
 * [DonationConfig] keys.
 *
 * ### Why per-account subaddresses?
 * Each caller receives a unique subaddress derived from [accountId], so the
 * project can acknowledge specific donors without linking pseudonyms or
 * conversation metadata — the subaddress is derived purely from the contactId,
 * which is already public within the conversation.
 *
 * ### Placeholder guard
 * When the APK still contains all-zero placeholder keys (pre-release),
 * [isDonationConfigured] returns false and all derivation methods return null.
 * The UI hides the donation section in that case.
 *
 * ### Usage
 * - **Profile screen** (own profile): call [getAppDonationAddress] to display
 *   a stable "support Fialka" QR with the fixed [APP_ACCOUNT_ID].
 * - **Conversation profile**: call [getDonationAddressForId] with the
 *   [conversationId] bytes so each contact gets a unique subaddress.
 */
object DonationManager {

    private const val TAG = "DonationManager"

    /**
     * Fixed account identifier used for the project-wide donation address
     * shown on the user's own profile screen ("support the developer").
     */
    private const val APP_ACCOUNT_ID = "fialka-project-donation-v1"

    /**
     * True when the APK has been set up with real donation wallet keys.
     * False when running with placeholder all-zero keys (pre-release build).
     */
    val isDonationConfigured: Boolean by lazy {
        DonationConfig.SPEND_PUB.any { it != 0.toByte() } &&
        DonationConfig.VIEW_PRIV.any { it != 0.toByte() }
    }

    /**
     * Derive the stable project donation address shown on the own profile screen.
     * Uses the fixed [APP_ACCOUNT_ID] so the address never changes between installs.
     *
     * @return Primary donation subaddress (95-char Monero address), or null
     *         if keys are placeholder / JNI derivation fails.
     */
    fun getAppDonationAddress(): String? {
        if (!isDonationConfigured) return null
        return deriveForId(APP_ACCOUNT_ID)
    }

    /**
     * Derive a unique donation subaddress for a specific conversation.
     * Each [conversationId] maps to a different subaddress (index-space collision
     * is negligible — the index is hashed, not sequential).
     *
     * @param conversationId The Room conversation ID (UUID string).
     * @return Unique subaddress for this conversation, or null on failure.
     */
    fun getDonationAddressForConversation(conversationId: String): String? {
        if (!isDonationConfigured) return null
        return deriveForId(conversationId)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun deriveForId(id: String): String? {
        return try {
            val accountIdBytes = id.toByteArray(Charsets.UTF_8)
            val result = FialkaNative.xmrDeriveDonationSubaddress(
                DonationConfig.SPEND_PUB,
                DonationConfig.VIEW_PRIV,
                accountIdBytes
            )
            if (result.isEmpty()) null else String(result, Charsets.UTF_8)
        } catch (e: Throwable) {
            Log.e(TAG, "deriveForId($id) failed", e)
            null
        }
    }
}
