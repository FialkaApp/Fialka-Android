package com.securechat.crypto

import android.util.Base64
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * Full Double Ratchet — X25519 DH ratchet + symmetric KDF chains.
 *
 * On every direction change (Alice→Bob then Bob→Alice), a new DH exchange
 * happens with fresh ephemeral keys. This "heals" the session: even if a
 * chain key is compromised, the next DH step produces a new root key that
 * the attacker cannot derive.
 *
 * DH Ratchet step:
 *   dh_secret = X25519(our_new_ephemeral_private, their_latest_ephemeral_public)
 *   (new_root_key, new_chain_key) = HKDF(root_key, dh_secret)
 *
 * Symmetric chain step (per message):
 *   message_key = HMAC-SHA256(chain_key, 0x01)
 *   chain_key'  = HMAC-SHA256(chain_key, 0x02)
 */
object DoubleRatchet {

    private const val AES_KEY_LENGTH = 32

    // ========================================================================
    // 1. INITIAL SETUP (from identity ECDH shared secret)
    // ========================================================================

    /**
     * Bootstrap the ratchet from the identity-key ECDH shared secret.
     * Called once when the conversation is created.
     *
     * Both sides derive rootKey + initial send/recv chains from the identity
     * ECDH shared secret. Each side also generates an ephemeral keypair.
     * The ephemeral public keys are exchanged via real messages.
     *
     * @param sharedSecret  Raw bytes from DH(identity_A, identity_B)
     * @return InitialRatchetState with all the keys needed
     */
    data class InitialRatchetState(
        val rootKey: String,          // Base64
        val sendChainKey: String,     // Base64
        val recvChainKey: String,     // Base64
        val localDhPublic: String,    // Base64
        val localDhPrivate: String,   // Base64
        val remoteDhPublic: String    // Base64 (empty for initiator initially)
    )

    fun initializeAsInitiator(identitySharedSecret: ByteArray): InitialRatchetState {
        // Derive root key + initial chains from identity DH shared secret
        val rootKey = hkdfExpand(identitySharedSecret, "SecureChat-DR-root".toByteArray())
        val sendChainKey = hkdfExpand(rootKey, "SecureChat-DR-chain-init-send".toByteArray())
        val recvChainKey = hkdfExpand(rootKey, "SecureChat-DR-chain-init-recv".toByteArray())
        identitySharedSecret.fill(0)

        // Generate first ephemeral keypair for DH ratchet
        val ephemeral = CryptoManager.generateEphemeralKeyPair()

        val result = InitialRatchetState(
            rootKey = Base64.encodeToString(rootKey, Base64.NO_WRAP),
            sendChainKey = Base64.encodeToString(sendChainKey, Base64.NO_WRAP),
            recvChainKey = Base64.encodeToString(recvChainKey, Base64.NO_WRAP),
            localDhPublic = ephemeral.publicKeyBase64,
            localDhPrivate = ephemeral.privateKeyBase64,
            remoteDhPublic = ""
        )
        rootKey.fill(0)
        sendChainKey.fill(0)
        recvChainKey.fill(0)
        return result
    }

    fun initializeAsResponder(identitySharedSecret: ByteArray): InitialRatchetState {
        // Derive root key + initial chains (swapped vs initiator)
        val rootKey = hkdfExpand(identitySharedSecret, "SecureChat-DR-root".toByteArray())
        // Responder's recv = initiator's send, responder's send = initiator's recv
        val recvChainKey = hkdfExpand(rootKey, "SecureChat-DR-chain-init-send".toByteArray())
        val sendChainKey = hkdfExpand(rootKey, "SecureChat-DR-chain-init-recv".toByteArray())
        identitySharedSecret.fill(0)

        // Generate ephemeral keypair (ready for when we send)
        val ephemeral = CryptoManager.generateEphemeralKeyPair()

        val result = InitialRatchetState(
            rootKey = Base64.encodeToString(rootKey, Base64.NO_WRAP),
            sendChainKey = Base64.encodeToString(sendChainKey, Base64.NO_WRAP),
            recvChainKey = Base64.encodeToString(recvChainKey, Base64.NO_WRAP),
            localDhPublic = ephemeral.publicKeyBase64,
            localDhPrivate = ephemeral.privateKeyBase64,
            remoteDhPublic = ""
        )
        rootKey.fill(0)
        sendChainKey.fill(0)
        recvChainKey.fill(0)
        return result
    }

    // ========================================================================
    // 2. DH RATCHET STEP
    // ========================================================================

    data class DhRatchetResult(
        val newRootKey: String,       // Base64
        val newChainKey: String       // Base64
    )

    /**
     * Perform one DH ratchet step:
     *   dh_out = X25519(localPrivate, remotePubKey)
     *   (newRootKey, newChainKey) = KDF_RK(rootKey, dh_out)
     */
    fun dhRatchetStep(
        rootKeyBase64: String,
        localDhPrivateBase64: String,
        remoteDhPublicBase64: String
    ): DhRatchetResult {
        // DH exchange
        val dhSecret = CryptoManager.performEphemeralKeyAgreement(
            localDhPrivateBase64, remoteDhPublicBase64
        )

        val rootKey = Base64.decode(rootKeyBase64, Base64.NO_WRAP)

        // KDF_RK: derive new root key + chain key from (rootKey, dhSecret)
        val combined = ByteArray(rootKey.size + dhSecret.size)
        System.arraycopy(rootKey, 0, combined, 0, rootKey.size)
        System.arraycopy(dhSecret, 0, combined, rootKey.size, dhSecret.size)

        val newRootKey = hkdfExpand(combined, "SecureChat-DR-root-ratchet".toByteArray())
        val newChainKey = hkdfExpand(combined, "SecureChat-DR-chain-ratchet".toByteArray())

        // Zero everything
        rootKey.fill(0)
        dhSecret.fill(0)
        combined.fill(0)

        val result = DhRatchetResult(
            newRootKey = Base64.encodeToString(newRootKey, Base64.NO_WRAP),
            newChainKey = Base64.encodeToString(newChainKey, Base64.NO_WRAP)
        )
        newRootKey.fill(0)
        newChainKey.fill(0)
        return result
    }

    // ========================================================================
    // 3. SYMMETRIC CHAIN STEP (same as before — KDF chain)
    // ========================================================================

    /**
     * Advance a chain key and derive a message key.
     *   message_key = HMAC(chain_key, 0x01)
     *   chain_key'  = HMAC(chain_key, 0x02)
     */
    fun advanceChain(chainKeyBase64: String): Pair<String, SecretKey> {
        val chainKey = Base64.decode(chainKeyBase64, Base64.NO_WRAP)

        val messageKeyBytes = hmacSha256(chainKey, byteArrayOf(0x01))
        val messageKey = SecretKeySpec(messageKeyBytes, 0, AES_KEY_LENGTH, "AES")

        val newChainKey = hmacSha256(chainKey, byteArrayOf(0x02))
        val newChainKeyBase64 = Base64.encodeToString(newChainKey, Base64.NO_WRAP)

        chainKey.fill(0)
        messageKeyBytes.fill(0)
        newChainKey.fill(0)

        return Pair(newChainKeyBase64, messageKey)
    }

    // ========================================================================
    // Internal helpers
    // ========================================================================

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun hkdfExpand(ikm: ByteArray, info: ByteArray): ByteArray {
        val salt = ByteArray(32)
        val prk = hmacSha256(salt, ikm)
        val expandInput = ByteArray(info.size + 1)
        System.arraycopy(info, 0, expandInput, 0, info.size)
        expandInput[expandInput.size - 1] = 0x01
        return hmacSha256(prk, expandInput)
    }
}
