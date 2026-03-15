package com.securechat.crypto

import android.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Symmetric Double Ratchet implementation for Perfect Forward Secrecy.
 *
 * Based on the Signal Protocol's symmetric ratchet (KDF chains):
 *  - Each message uses a UNIQUE message key derived from a chain key.
 *  - After deriving a message key, the chain key advances (one-way).
 *  - Old chain keys are deleted → compromising current state doesn't reveal past messages.
 *
 * Chain advancement:
 *   chain_key[n+1] = HMAC-SHA256(chain_key[n], 0x02)
 *   message_key[n] = HMAC-SHA256(chain_key[n], 0x01)
 *
 * Root key ratchet (when initializing):
 *   (root_key', send_chain_key) = HKDF(root_key, ECDH_shared_secret, "send")
 *   (root_key'', recv_chain_key) = HKDF(root_key, ECDH_shared_secret, "recv")
 */
object SymmetricRatchet {

    private const val AES_KEY_LENGTH = 32

    /**
     * Derive the initial ratchet state from an ECDH shared secret.
     *
     * The initiator (lexicographically smaller public key) gets:
     *   send_chain = derived with "SecureChat-send"
     *   recv_chain = derived with "SecureChat-recv"
     *
     * The responder gets the opposite assignment so they match.
     *
     * @param sharedSecret Raw ECDH shared secret bytes
     * @param isInitiator true if this user's public key is lexicographically smaller
     * @return Triple of (rootKey, sendChainKey, recvChainKey) as Base64
     */
    fun initializeChains(
        sharedSecret: ByteArray,
        isInitiator: Boolean
    ): Triple<String, String, String> {
        // Derive root key
        val rootKey = hkdfExpand(sharedSecret, "SecureChat-root-key".toByteArray())

        // Zero the shared secret — no longer needed
        sharedSecret.fill(0)

        // Derive two chain keys from root
        val chainA = hkdfExpand(rootKey, "SecureChat-chain-A".toByteArray())
        val chainB = hkdfExpand(rootKey, "SecureChat-chain-B".toByteArray())

        val sendChain = if (isInitiator) chainA else chainB
        val recvChain = if (isInitiator) chainB else chainA

        val result = Triple(
            Base64.encodeToString(rootKey, Base64.NO_WRAP),
            Base64.encodeToString(sendChain, Base64.NO_WRAP),
            Base64.encodeToString(recvChain, Base64.NO_WRAP)
        )

        // Zero intermediate key material
        rootKey.fill(0)
        chainA.fill(0)
        chainB.fill(0)

        return result
    }

    /**
     * Advance a chain key and derive a message key.
     * The old chain key bytes are zeroed after derivation.
     *
     * @param chainKeyBase64 Current chain key (Base64)
     * @return Pair of (newChainKey as Base64, messageKey as SecretKey)
     */
    fun advanceChain(chainKeyBase64: String): Pair<String, SecretKey> {
        val chainKey = Base64.decode(chainKeyBase64, Base64.NO_WRAP)

        // Derive message key: HMAC(chain_key, 0x01)
        val messageKeyBytes = hmacSha256(chainKey, byteArrayOf(0x01))
        val messageKey = SecretKeySpec(messageKeyBytes, 0, AES_KEY_LENGTH, "AES")

        // Advance chain key: HMAC(chain_key, 0x02)
        val newChainKey = hmacSha256(chainKey, byteArrayOf(0x02))
        val newChainKeyBase64 = Base64.encodeToString(newChainKey, Base64.NO_WRAP)

        // Zero old key material
        chainKey.fill(0)
        messageKeyBytes.fill(0)
        newChainKey.fill(0)

        return Pair(newChainKeyBase64, messageKey)
    }

    /**
     * Advance a chain key multiple times and return the final message key.
     * Used to catch up on skipped messages.
     *
     * advanceChainBy(chain, 0) = advance once (same as advanceChain)
     * advanceChainBy(chain, 2) = skip 2 intermediate keys, advance 3 times total
     *
     * @param chainKeyBase64 Starting chain key
     * @param skip Number of extra messages to skip (0 = no skip, just advance once)
     * @return Pair of (newChainKey as Base64, messageKey as SecretKey for the last step)
     */
    fun advanceChainBy(chainKeyBase64: String, skip: Int): Pair<String, SecretKey> {
        var currentChain = chainKeyBase64
        var messageKey: SecretKey? = null
        // advance (skip + 1) times: skip intermediate + get target
        for (i in 0..skip) {
            val (newChain, mk) = advanceChain(currentChain)
            currentChain = newChain
            messageKey = mk
        }
        return Pair(currentChain, messageKey!!)
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /**
     * Simplified HKDF-Expand for single-block output (32 bytes).
     */
    private fun hkdfExpand(ikm: ByteArray, info: ByteArray): ByteArray {
        // Extract: PRK = HMAC(salt=zeros, ikm)
        val salt = ByteArray(32)
        val prk = hmacSha256(salt, ikm)

        // Expand: output = HMAC(PRK, info || 0x01)
        val expandInput = ByteArray(info.size + 1)
        System.arraycopy(info, 0, expandInput, 0, info.size)
        expandInput[expandInput.size - 1] = 0x01
        return hmacSha256(prk, expandInput)
    }
}
