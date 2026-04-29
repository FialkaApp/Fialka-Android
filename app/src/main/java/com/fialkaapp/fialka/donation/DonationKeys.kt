/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667
 *
 * DONATION WALLET — View-only keys embedded in the APK.
 *
 * How it works:
 *   - Each user gets a UNIQUE Monero subaddress, derived deterministically from
 *     their AccountID using SHA-256(accountId)[0..3] as the subaddress index.
 *   - All subaddresses converge to the SAME donation wallet (your wallet).
 *   - The private view key lets the app (and anyone who decompiles it) SEE
 *     incoming transactions but NOT spend them. The spend key stays offline.
 *
 * Setup (one-time):
 *   1. Create a dedicated Monero wallet (stagenet for testing, mainnet for prod).
 *   2. Run:  monero-wallet-cli --wallet-file <wallet> --command export_raw_key
 *      Or open the wallet and run: export_raw_key
 *   3. Copy the 32-byte hex values below.
 *   4. Switch NETWORK_TYPE to 0 (mainnet) when going live.
 *
 * IMPORTANT: These are the DONATION wallet keys, completely separate from
 * user wallets. Never put a user's spend key here.
 */
package com.fialkaapp.fialka.donation

object DonationKeys {

    /**
     * Public spend key of the donation wallet (32 bytes, hex-encoded).
     * Replace with your real donation wallet's public spend key.
     */
    const val SPEND_PUB_HEX =
        "7bf6b9c141116ca66211bec9ca5afe7f30d8f17acca7c0af43deda8fb85775a1"
    //   ^^^^^ REPLACE WITH YOUR REAL DONATION WALLET PUBLIC SPEND KEY ^^^^^

    /**
     * Private view key of the donation wallet (32 bytes, hex-encoded).
     * Safe to embed — can only VIEW transactions, not spend.
     * Replace with your real donation wallet's private view key.
     */
    const val VIEW_PRIV_HEX =
        "59967010c2fb89cedbddfe06480eb0bfee5d1848ada64f850e723c3cc524c90d"
    //   ^^^^^ REPLACE WITH YOUR REAL DONATION WALLET PRIVATE VIEW KEY ^^^^^

    /**
     * Network type matching WalletRepository.NETWORK_TYPE.
     * 0 = Mainnet, 1 = Testnet, 2 = Stagenet
     */
    const val NETWORK_TYPE = 2  // Stagenet — change to 0 for production

    /** Decode hex string to ByteArray. */
    fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Odd hex length" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    val spendPubBytes: ByteArray get() = hexToBytes(SPEND_PUB_HEX)
    val viewPrivBytes: ByteArray get() = hexToBytes(VIEW_PRIV_HEX)

    /** Returns true only if placeholder keys have been replaced. */
    val isConfigured: Boolean get() =
        SPEND_PUB_HEX != "0000000000000000000000000000000000000000000000000000000000000001" ||
        VIEW_PRIV_HEX != "0000000000000000000000000000000000000000000000000000000000000001"
}
