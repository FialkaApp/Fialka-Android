package com.securechat.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoManager — Isolated crypto module for SecureChat.
 *
 * Responsibilities:
 *  1. Generate EC key pairs (for ECDH key agreement).
 *  2. Perform Diffie-Hellman key exchange to derive a shared secret.
 *  3. Derive a symmetric AES-256 key from the shared secret using HKDF-SHA256.
 *  4. Encrypt / decrypt messages using AES-256-GCM (AEAD).
 *
 * Key storage strategy:
 *  - Private key: stored in Android Keystore (hardware-backed when available).
 *  - Public key: stored as Base64 string in Room.
 *  - Message keys: derived per-message via SymmetricRatchet, zeroed after use.
 *
 * Security guarantees:
 *  - SecureRandom for all IV generation (12 bytes per message, never reused).
 *  - Intermediate key material (shared secrets, derived keys) is zeroed after use.
 *  - Private key never leaves Android Keystore.
 */
object CryptoManager {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "securechat_identity_key"
    private const val EC_CURVE = "secp256r1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val AES_KEY_LENGTH_BYTES = 32 // AES-256

    // HKDF info string for key derivation
    private const val HKDF_INFO = "SecureChat-v1-message-key"

    // Single SecureRandom instance (thread-safe)
    private val secureRandom = SecureRandom()

    // ========================================================================
    // 1. KEY GENERATION
    // ========================================================================

    /**
     * Generate an EC key pair and store the private key in Android Keystore.
     * Returns the public key as a Base64-encoded string.
     *
     * If a key already exists with the alias, it will be returned without
     * generating a new one.
     */
    fun generateIdentityKeyPair(): String {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

        // If key already exists, return existing public key
        if (keyStore.containsAlias(KEY_ALIAS)) {
            val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
            return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        }

        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )

        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_AGREE_KEY
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec(EC_CURVE))
            .build()

        keyPairGenerator.initialize(parameterSpec)
        val keyPair = keyPairGenerator.generateKeyPair()

        return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    }

    /**
     * Check if an identity key pair already exists in the Keystore.
     */
    fun hasIdentityKey(): Boolean {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.containsAlias(KEY_ALIAS)
    }

    /**
     * Get the local public key from Keystore as Base64.
     * Returns null if no key exists.
     */
    fun getPublicKey(): String? {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) return null
        val publicKey = keyStore.getCertificate(KEY_ALIAS).publicKey
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    /**
     * Delete the identity key pair from Android Keystore.
     * Called during account reset to ensure no key material remains.
     */
    fun deleteIdentityKey() {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    /**
     * Get the private key from Android Keystore.
     * This key never leaves the Keystore in practice.
     */
    private fun getPrivateKey(): PrivateKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return keyStore.getKey(KEY_ALIAS, null) as PrivateKey
    }

    // ========================================================================
    // 2. DIFFIE-HELLMAN KEY EXCHANGE
    // ========================================================================

    /**
     * Perform ECDH key agreement between local private key and a remote public key.
     * Returns the raw shared secret bytes.
     *
     * @param remotePublicKeyBase64 The other party's public key (Base64-encoded X.509 format).
     */
    fun performKeyAgreement(remotePublicKeyBase64: String): ByteArray {
        val remotePublicKeyBytes = Base64.decode(remotePublicKeyBase64, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("EC")
        val remotePublicKey = keyFactory.generatePublic(X509EncodedKeySpec(remotePublicKeyBytes))

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(getPrivateKey())
        keyAgreement.doPhase(remotePublicKey, true)

        return keyAgreement.generateSecret()
    }

    // ========================================================================
    // 3. KEY DERIVATION (HKDF-SHA256)
    // ========================================================================

    /**
     * Derive an AES-256 key from a shared secret using HKDF-SHA256.
     * The shared secret is zeroed after derivation.
     */
    fun deriveSymmetricKey(sharedSecret: ByteArray): SecretKey {
        // HKDF-Extract: PRK = HMAC-SHA256(salt="", IKM=sharedSecret)
        val salt = ByteArray(32) // zero salt
        val prk = hmacSha256(salt, sharedSecret)

        // Zero the shared secret — no longer needed
        sharedSecret.fill(0)

        // HKDF-Expand: OKM = HMAC-SHA256(PRK, info || 0x01)
        val info = HKDF_INFO.toByteArray(Charsets.UTF_8)
        val expandInput = ByteArray(info.size + 1)
        System.arraycopy(info, 0, expandInput, 0, info.size)
        expandInput[expandInput.size - 1] = 0x01

        val okm = hmacSha256(prk, expandInput)

        // Zero intermediate key material
        prk.fill(0)
        expandInput.fill(0)

        val key = SecretKeySpec(okm, 0, AES_KEY_LENGTH_BYTES, "AES")
        okm.fill(0)
        return key
    }

    /**
     * Derive a conversation symmetric key from a remote public key.
     * Combines ECDH + HKDF in one call. Used as legacy fallback only.
     */
    fun deriveConversationKey(remotePublicKeyBase64: String): SecretKey {
        val sharedSecret = performKeyAgreement(remotePublicKeyBase64)
        return deriveSymmetricKey(sharedSecret) // sharedSecret zeroed inside
    }

    // ========================================================================
    // 4. AES-256-GCM ENCRYPTION / DECRYPTION
    // ========================================================================

    /**
     * Encrypt plaintext using AES-256-GCM.
     * Uses SecureRandom for IV generation — guaranteed unique per message.
     *
     * @param plaintext The message to encrypt.
     * @param key The AES-256 symmetric key (will be used then should be discarded by caller).
     * @return EncryptedData containing ciphertext (Base64) and IV (Base64).
     */
    fun encrypt(plaintext: String, key: SecretKey): EncryptedData {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)

        // Generate random IV using the singleton SecureRandom
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, key, gcmSpec)

        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val ciphertextBytes = cipher.doFinal(plaintextBytes)

        // Zero plaintext bytes
        plaintextBytes.fill(0)

        val result = EncryptedData(
            ciphertext = Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )

        // Zero IV and ciphertext buffers
        iv.fill(0)
        ciphertextBytes.fill(0)

        return result
    }

    /**
     * Decrypt ciphertext using AES-256-GCM.
     *
     * @param encryptedData The encrypted data (ciphertext + IV, both Base64).
     * @param key The AES-256 symmetric key (will be used then should be discarded by caller).
     * @return Decrypted plaintext string.
     */
    fun decrypt(encryptedData: EncryptedData, key: SecretKey): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)

        val iv = Base64.decode(encryptedData.iv, Base64.NO_WRAP)
        val ciphertextBytes = Base64.decode(encryptedData.ciphertext, Base64.NO_WRAP)

        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, gcmSpec)

        val plaintextBytes = cipher.doFinal(ciphertextBytes)
        val plaintext = String(plaintextBytes, Charsets.UTF_8)

        // Zero intermediate buffers
        iv.fill(0)
        ciphertextBytes.fill(0)
        plaintextBytes.fill(0)

        return plaintext
    }

    // ========================================================================
    // 5. UTILITIES
    // ========================================================================

    /**
     * Validate that a Base64 string represents a valid EC public key.
     */
    fun isValidPublicKey(publicKeyBase64: String): Boolean {
        return try {
            val keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val keyFactory = KeyFactory.getInstance("EC")
            keyFactory.generatePublic(X509EncodedKeySpec(keyBytes))
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Derive a conversation ID from two public keys.
     * conversationId = SHA-256( min(pubKeyA, pubKeyB) + max(pubKeyA, pubKeyB) )
     * Encoded as hex string.
     */
    fun deriveConversationId(pubKeyA: String, pubKeyB: String): String {
        val sorted = listOf(pubKeyA, pubKeyB).sorted()
        val combined = (sorted[0] + sorted[1]).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(combined)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * HMAC-SHA256 implementation.
     */
    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    /**
     * Data class holding encrypted message data.
     */
    data class EncryptedData(
        val ciphertext: String, // Base64
        val iv: String          // Base64
    )
}
