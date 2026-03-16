package com.securechat.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoManager — X25519 + AES-256-GCM crypto module for SecureChat.
 *
 * Identity key: X25519 generated in software JCA, private key stored encrypted
 * in EncryptedSharedPreferences (AES-256-GCM backed by Android Keystore).
 * This is the same model as Signal — hardware security is preserved indirectly.
 *
 * Ephemeral keys: X25519 in software (generated per DH ratchet step, then discarded).
 * Encryption: AES-256-GCM (AEAD) with 12-byte random IV per message.
 * Key derivation: HKDF-SHA256.
 */
object CryptoManager {

    private const val PREFS_FILE = "securechat_identity_keys"
    private const val KEY_PUBLIC = "identity_public_key"
    private const val KEY_PRIVATE = "identity_private_key"

    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val AES_KEY_LENGTH_BYTES = 32

    private const val HKDF_INFO = "SecureChat-v2-message-key"

    private val secureRandom = SecureRandom()

    private lateinit var prefs: SharedPreferences

    /**
     * Must be called once from Application.onCreate() before any crypto operation.
     * Initializes EncryptedSharedPreferences backed by Android Keystore AES-256-GCM.
     */
    fun init(context: Context) {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    // ========================================================================
    // 1. IDENTITY KEY (X25519 in software, encrypted at rest via Keystore)
    // ========================================================================

    fun generateIdentityKeyPair(): String {
        val existing = prefs.getString(KEY_PUBLIC, null)
        if (existing != null) return existing

        val kpg = KeyPairGenerator.getInstance("X25519")
        val keyPair = kpg.generateKeyPair()

        val pubBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        val privBase64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)

        prefs.edit()
            .putString(KEY_PUBLIC, pubBase64)
            .putString(KEY_PRIVATE, privBase64)
            .apply()

        return pubBase64
    }

    fun hasIdentityKey(): Boolean = prefs.contains(KEY_PUBLIC)

    fun getPublicKey(): String? = prefs.getString(KEY_PUBLIC, null)

    /**
     * Returns the raw 32-byte X25519 private key for mnemonic backup.
     */
    fun getIdentityPrivateKeyBytes(): ByteArray {
        val privBase64 = prefs.getString(KEY_PRIVATE, null)
            ?: throw IllegalStateException("Identity key not initialized")
        val pkcs8Bytes = Base64.decode(privBase64, Base64.NO_WRAP)
        // PKCS8 X25519 = 16-byte prefix + 32-byte raw key
        return pkcs8Bytes.copyOfRange(pkcs8Bytes.size - 32, pkcs8Bytes.size)
    }

    /**
     * Restore identity from raw 32-byte private key (from mnemonic).
     * Derives public key via DH with X25519 base point (u=9).
     */
    fun restoreIdentityKey(privateBytes: ByteArray): String {
        require(privateBytes.size == 32) { "Private key must be 32 bytes" }

        // Build PKCS8 private key
        val pkcs8Prefix = byteArrayOf(
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05,
            0x06, 0x03, 0x2b, 0x65, 0x6e, 0x04, 0x22,
            0x04, 0x20
        )
        val kf = KeyFactory.getInstance("X25519")
        val privateKey = kf.generatePrivate(PKCS8EncodedKeySpec(pkcs8Prefix + privateBytes))

        // Derive public key: DH with base point u=9
        val basePoint = ByteArray(32).also { it[0] = 9 }
        val x509Prefix = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b,
            0x65, 0x6e, 0x03, 0x21, 0x00
        )
        val basePointPub = kf.generatePublic(X509EncodedKeySpec(x509Prefix + basePoint))

        val ka = KeyAgreement.getInstance("X25519")
        ka.init(privateKey)
        ka.doPhase(basePointPub, true)
        val publicKeyRaw = ka.generateSecret()

        // Encode public key as X509
        val publicKey = kf.generatePublic(X509EncodedKeySpec(x509Prefix + publicKeyRaw))
        val pubBase64 = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
        val privBase64 = Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP)

        prefs.edit()
            .putString(KEY_PUBLIC, pubBase64)
            .putString(KEY_PRIVATE, privBase64)
            .apply()

        return pubBase64
    }

    fun deleteIdentityKey() {
        prefs.edit()
            .remove(KEY_PUBLIC)
            .remove(KEY_PRIVATE)
            .apply()
    }

    private fun getIdentityPrivateKey(): PrivateKey {
        val privBase64 = prefs.getString(KEY_PRIVATE, null)
            ?: throw IllegalStateException("Identity key not initialized")
        val kf = KeyFactory.getInstance("X25519")
        return kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(privBase64, Base64.NO_WRAP)))
    }

    // ========================================================================
    // 2. EPHEMERAL DH KEYS (X25519 in software — for Double Ratchet)
    // ========================================================================

    data class X25519KeyPair(
        val publicKeyBase64: String,
        val privateKeyBase64: String
    )

    fun generateEphemeralKeyPair(): X25519KeyPair {
        val kpg = KeyPairGenerator.getInstance("X25519")
        val keyPair = kpg.generateKeyPair()
        return X25519KeyPair(
            publicKeyBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP),
            privateKeyBase64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
        )
    }

    // ========================================================================
    // 3. DIFFIE-HELLMAN (X25519)
    // ========================================================================

    /** DH with identity private key (Keystore) and a remote public key. */
    fun performKeyAgreement(remotePublicKeyBase64: String): ByteArray {
        val remotePubBytes = Base64.decode(remotePublicKeyBase64, Base64.NO_WRAP)
        val kf = KeyFactory.getInstance("X25519")
        val remotePub = kf.generatePublic(X509EncodedKeySpec(remotePubBytes))

        val ka = KeyAgreement.getInstance("X25519")
        ka.init(getIdentityPrivateKey())
        ka.doPhase(remotePub, true)
        return ka.generateSecret()
    }

    /** DH with an ephemeral private key (software) and a remote public key. */
    fun performEphemeralKeyAgreement(
        localPrivateKeyBase64: String,
        remotePublicKeyBase64: String
    ): ByteArray {
        val privBytes = Base64.decode(localPrivateKeyBase64, Base64.NO_WRAP)
        val kf = KeyFactory.getInstance("X25519")
        val localPriv = kf.generatePrivate(PKCS8EncodedKeySpec(privBytes))
        privBytes.fill(0)  // Zero private key material immediately

        val remotePub = kf.generatePublic(
            X509EncodedKeySpec(Base64.decode(remotePublicKeyBase64, Base64.NO_WRAP))
        )

        val ka = KeyAgreement.getInstance("X25519")
        ka.init(localPriv)
        ka.doPhase(remotePub, true)
        return ka.generateSecret()
    }

    // ========================================================================
    // 4. KEY DERIVATION (HKDF-SHA256)
    // ========================================================================

    fun deriveSymmetricKey(sharedSecret: ByteArray): SecretKey {
        val salt = ByteArray(32)
        val prk = hmacSha256(salt, sharedSecret)
        sharedSecret.fill(0)

        val info = HKDF_INFO.toByteArray(Charsets.UTF_8)
        val expandInput = ByteArray(info.size + 1)
        System.arraycopy(info, 0, expandInput, 0, info.size)
        expandInput[expandInput.size - 1] = 0x01
        val okm = hmacSha256(prk, expandInput)

        prk.fill(0)
        expandInput.fill(0)

        val key = SecretKeySpec(okm, 0, AES_KEY_LENGTH_BYTES, "AES")
        okm.fill(0)
        return key
    }

    // ========================================================================
    // 5. AES-256-GCM ENCRYPTION / DECRYPTION
    // ========================================================================

    fun encrypt(plaintext: String, key: SecretKey): EncryptedData {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val ciphertextBytes = cipher.doFinal(plaintextBytes)
        plaintextBytes.fill(0)

        val result = EncryptedData(
            ciphertext = Base64.encodeToString(ciphertextBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
        iv.fill(0)
        ciphertextBytes.fill(0)
        return result
    }

    fun decrypt(encryptedData: EncryptedData, key: SecretKey): String {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val iv = Base64.decode(encryptedData.iv, Base64.NO_WRAP)
        val ciphertextBytes = Base64.decode(encryptedData.ciphertext, Base64.NO_WRAP)

        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plaintextBytes = cipher.doFinal(ciphertextBytes)
        val plaintext = String(plaintextBytes, Charsets.UTF_8)

        iv.fill(0)
        ciphertextBytes.fill(0)
        plaintextBytes.fill(0)
        return plaintext
    }

    // ========================================================================
    // 6. UTILITIES
    // ========================================================================

    fun isValidPublicKey(publicKeyBase64: String): Boolean {
        return try {
            val keyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val kf = KeyFactory.getInstance("X25519")
            kf.generatePublic(X509EncodedKeySpec(keyBytes))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun deriveConversationId(pubKeyA: String, pubKeyB: String): String {
        val sorted = listOf(pubKeyA, pubKeyB).sorted()
        val combined = (sorted[0] + sorted[1]).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(combined)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ========================================================================
    // 7. EMOJI FINGERPRINT (96-bit)
    // ========================================================================

    private val EMOJI_PALETTE = listOf(
        "🔥", "🐱", "🦄", "🍕", "🌟", "🚀", "💎", "⚡",
        "🎸", "📱", "🔔", "🎉", "🌈", "🐶", "🎯", "🍀",
        "🦋", "🌺", "🍒", "🎵", "🐠", "🌙", "🍭", "🎨",
        "🦊", "🌊", "🍩", "🎪", "🐧", "🌻", "🍋", "🎲",
        "🦁", "🌴", "🍇", "🎹", "🐸", "🌸", "🍬", "🎭",
        "🦉", "🌵", "🍎", "🎺", "🐝", "🌾", "🍫", "🎻",
        "🦈", "🌽", "🍑", "🎼", "🐙", "🌿", "🍓", "🎮",
        "🦜", "🍄", "🍊", "🎳", "🐢", "🌰", "🍈", "🎧"
    )

    fun getSharedFingerprint(myPubKeyBase64: String, contactPubKeyBase64: String): String {
        val sorted = listOf(myPubKeyBase64, contactPubKeyBase64).sorted()
        val combined = (sorted[0] + sorted[1]).toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(combined)
        val emojis = (0 until 16).map { i -> EMOJI_PALETTE[hash[i].toInt() and 0x3F] }
        return emojis.chunked(4).joinToString(" ") { it.joinToString("") }
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    data class EncryptedData(
        val ciphertext: String,
        val iv: String
    )
}
