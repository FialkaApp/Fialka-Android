package com.securechat.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.securechat.util.DeviceSecurityManager
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.SecretWithEncapsulation
import org.bouncycastle.pqc.crypto.mlkem.MLKEMExtractor
import org.bouncycastle.pqc.crypto.mlkem.MLKEMGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyGenerationParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMKeyPairGenerator
import org.bouncycastle.pqc.crypto.mlkem.MLKEMParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPrivateKeyParameters
import org.bouncycastle.pqc.crypto.mlkem.MLKEMPublicKeyParameters
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
    private const val KEY_MLKEM_PUBLIC  = "identity_mlkem_public_key"
    private const val KEY_MLKEM_PRIVATE = "identity_mlkem_private_key"

    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val AES_KEY_LENGTH_BYTES = 32

    private const val HKDF_INFO = "SecureChat-v2-message-key"
    private const val INBOX_HKDF_INFO = "SecureChat-Inbox-v1"

    private val secureRandom = SecureRandom()

    private lateinit var prefs: SharedPreferences

    /**
     * Must be called once from Application.onCreate() before any crypto operation.
     * Initializes EncryptedSharedPreferences backed by Android Keystore AES-256-GCM.
     */
    fun init(context: Context) {
        val profile = DeviceSecurityManager.getSecurityProfile(context.applicationContext)
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setRequestStrongBoxBacked(profile.isStrongBoxAvailable)
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
        clearSigningKeyCache()
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

    // Padding buckets (bytes). 2-byte length header included in bucket.
    private val PADDING_BUCKETS = intArrayOf(256, 1024, 4096, 16384)

    /**
     * Pad plaintext into fixed-size buckets to prevent traffic analysis.
     * Format: [2 bytes big-endian real length][UTF-8 plaintext][random padding to bucket boundary]
     */
    private fun padPlaintext(plaintextBytes: ByteArray): ByteArray {
        val payloadSize = 2 + plaintextBytes.size  // 2-byte header + content
        val bucket = PADDING_BUCKETS.firstOrNull { it >= payloadSize } ?: payloadSize
        val padded = ByteArray(bucket)
        // Write real length as 2-byte big-endian header
        padded[0] = ((plaintextBytes.size shr 8) and 0xFF).toByte()
        padded[1] = (plaintextBytes.size and 0xFF).toByte()
        // Copy plaintext after header
        plaintextBytes.copyInto(padded, destinationOffset = 2)
        // Fill remaining bytes with random data (not zeros — avoids pattern)
        if (bucket > payloadSize) {
            val randomPad = ByteArray(bucket - payloadSize)
            secureRandom.nextBytes(randomPad)
            randomPad.copyInto(padded, destinationOffset = payloadSize)
            randomPad.fill(0)
        }
        return padded
    }

    /**
     * Strip padding to recover original plaintext bytes.
     */
    private fun unpadPlaintext(padded: ByteArray): ByteArray {
        val realLength = ((padded[0].toInt() and 0xFF) shl 8) or (padded[1].toInt() and 0xFF)
        require(realLength >= 0 && realLength <= padded.size - 2) { "Invalid padding header" }
        return padded.copyOfRange(2, 2 + realLength)
    }

    fun encrypt(plaintext: String, key: SecretKey): EncryptedData {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plaintextBytes = plaintext.toByteArray(Charsets.UTF_8)
        val paddedBytes = padPlaintext(plaintextBytes)
        plaintextBytes.fill(0)
        val ciphertextBytes = cipher.doFinal(paddedBytes)
        paddedBytes.fill(0)

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
        val paddedBytes = cipher.doFinal(ciphertextBytes)
        val plaintextBytes = unpadPlaintext(paddedBytes)
        val plaintext = String(plaintextBytes, Charsets.UTF_8)

        iv.fill(0)
        ciphertextBytes.fill(0)
        paddedBytes.fill(0)
        plaintextBytes.fill(0)
        return plaintext
    }

    // ========================================================================
    // 6. FILE ENCRYPTION (AES-256-GCM, one-shot key per file)
    // ========================================================================

    data class FileEncryptionResult(
        val encryptedBytes: ByteArray,
        val keyBase64: String,    // Random AES-256 key (to embed in E2E message)
        val ivBase64: String      // IV used for this file
    )

    /**
     * Encrypt raw file bytes with a fresh random AES-256-GCM key.
     * Returns the ciphertext + key + IV (key/IV are sent via the ratchet).
     */
    fun encryptFile(fileBytes: ByteArray): FileEncryptionResult {
        val keyBytes = ByteArray(32)
        secureRandom.nextBytes(keyBytes)
        val key = SecretKeySpec(keyBytes, "AES")
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val encrypted = cipher.doFinal(fileBytes)

        val result = FileEncryptionResult(
            encryptedBytes = encrypted,
            keyBase64 = Base64.encodeToString(keyBytes, Base64.NO_WRAP),
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
        keyBytes.fill(0)
        iv.fill(0)
        return result
    }

    /**
     * Decrypt file bytes using the provided key and IV.
     */
    fun decryptFile(encryptedBytes: ByteArray, keyBase64: String, ivBase64: String): ByteArray {
        val keyBytes = Base64.decode(keyBase64, Base64.NO_WRAP)
        val iv = Base64.decode(ivBase64, Base64.NO_WRAP)
        val key = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val decrypted = cipher.doFinal(encryptedBytes)

        keyBytes.fill(0)
        iv.fill(0)
        return decrypted
    }

    // ========================================================================
    // 7. UTILITIES
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
    // 7. INBOX PAYLOAD ENCRYPTION (ECIES: ephemeral X25519 + HKDF + AES-GCM)
    // Encrypts contact request fields so only the recipient can read them.
    // Format (base64): [44-byte X.509 ephemPub][12-byte IV][AES-GCM ciphertext+tag]
    // ========================================================================

    /**
     * Encrypt a contact request payload for inbox delivery.
     * No senderPublicKey, displayName or conversationId is stored in cleartext.
     */
    fun encryptInboxPayload(plaintext: ByteArray, recipientPublicKeyBase64: String): String {
        val kpg = KeyPairGenerator.getInstance("X25519")
        val ephemKP = kpg.generateKeyPair()
        val kf = KeyFactory.getInstance("X25519")
        val recipientPub = kf.generatePublic(
            X509EncodedKeySpec(Base64.decode(recipientPublicKeyBase64, Base64.NO_WRAP))
        )
        val ka = KeyAgreement.getInstance("X25519")
        ka.init(ephemKP.private)
        ka.doPhase(recipientPub, true)
        val dhSecret = ka.generateSecret()

        val aesKeyBytes = hkdfExtractExpand(dhSecret, INBOX_HKDF_INFO.toByteArray(Charsets.UTF_8))
        dhSecret.fill(0)

        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(aesKeyBytes, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext)
        aesKeyBytes.fill(0)

        val combined = ephemKP.public.encoded + iv + ciphertext  // 44 + 12 + n bytes
        iv.fill(0)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /**
     * Decrypt an inbox payload using our own identity X25519 private key.
     */
    fun decryptInboxPayload(encryptedBase64: String): ByteArray {
        val combined = Base64.decode(encryptedBase64, Base64.NO_WRAP)
        require(combined.size > 44 + 12 + 16) { "Invalid encrypted inbox payload" }

        val ephemPubBytes = combined.copyOfRange(0, 44)
        val iv = combined.copyOfRange(44, 56)
        val ciphertext = combined.copyOfRange(56, combined.size)

        val kf = KeyFactory.getInstance("X25519")
        val ephemPub = kf.generatePublic(X509EncodedKeySpec(ephemPubBytes))
        val ka = KeyAgreement.getInstance("X25519")
        ka.init(getIdentityPrivateKey())
        ka.doPhase(ephemPub, true)
        val dhSecret = ka.generateSecret()

        val aesKeyBytes = hkdfExtractExpand(dhSecret, INBOX_HKDF_INFO.toByteArray(Charsets.UTF_8))
        dhSecret.fill(0)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKeyBytes, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)
        aesKeyBytes.fill(0)
        return plaintext
    }

    /** HKDF-SHA256 extract+expand → 32 output bytes. Does NOT zero ikm. */
    private fun hkdfExtractExpand(ikm: ByteArray, info: ByteArray): ByteArray {
        val prk = hmacSha256(ByteArray(32), ikm)
        val expandInput = ByteArray(info.size + 1)
        System.arraycopy(info, 0, expandInput, 0, info.size)
        expandInput[expandInput.size - 1] = 0x01
        val okm = hmacSha256(prk, expandInput)
        prk.fill(0)
        expandInput.fill(0)
        return okm.copyOf(32)
    }

    // ========================================================================
    // 8. EMOJI FINGERPRINT (96-bit)
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

    /**
     * Derive a per-conversation pseudonymous sender ID.
     * HMAC-SHA256(conversationId, uid) truncated to 32 hex chars.
     * Prevents cross-conversation UID correlation on Firebase.
     */
    fun hashSenderUid(conversationId: String, uid: String): String {
        val hash = hmacSha256(
            conversationId.toByteArray(Charsets.UTF_8),
            uid.toByteArray(Charsets.UTF_8)
        )
        return hash.take(16).joinToString("") { "%02x".format(it) }
    }

    // ========================================================================
    // 8. ED25519 MESSAGE SIGNING
    // ========================================================================

    private const val KEY_SIGNING_PUBLIC = "signing_public_key"
    private const val KEY_SIGNING_PRIVATE = "signing_private_key"

    /** Cached signing key pair — generated once, reused for all signatures. */
    @Volatile
    private var cachedSigningPrivate: Ed25519PrivateKeyParameters? = null
    @Volatile
    private var cachedSigningPublic: Ed25519PublicKeyParameters? = null

    /**
     * Get or generate an Ed25519 signing key pair.
     * Uses BouncyCastle lightweight API directly — bypasses JCA provider lookup
     * which fails on Android because the platform's stripped BC takes priority.
     */
    fun getOrDeriveSigningKeyPair(): KeyPair {
        // Return cached as JCA KeyPair for API compatibility
        val priv = cachedSigningPrivate
        val pub = cachedSigningPublic
        if (priv != null && pub != null) {
            return toJcaKeyPair(priv, pub)
        }

        synchronized(this) {
            val priv2 = cachedSigningPrivate
            val pub2 = cachedSigningPublic
            if (priv2 != null && pub2 != null) {
                return toJcaKeyPair(priv2, pub2)
            }

            // 1. Try loading from EncryptedSharedPreferences
            val storedPub = prefs.getString(KEY_SIGNING_PUBLIC, null)
            val storedPriv = prefs.getString(KEY_SIGNING_PRIVATE, null)
            if (storedPub != null && storedPriv != null) {
                try {
                    val privBytes = Base64.decode(storedPriv, Base64.NO_WRAP)
                    val pubBytes = Base64.decode(storedPub, Base64.NO_WRAP)
                    val privateKey = Ed25519PrivateKeyParameters(privBytes, 0)
                    val publicKey = Ed25519PublicKeyParameters(pubBytes, 0)
                    cachedSigningPrivate = privateKey
                    cachedSigningPublic = publicKey
                    return toJcaKeyPair(privateKey, publicKey)
                } catch (e: Exception) {
                    Log.w("BC", "Stored Ed25519 keys invalid, regenerating", e)
                    prefs.edit().remove(KEY_SIGNING_PUBLIC).remove(KEY_SIGNING_PRIVATE).apply()
                }
            }

            // 2. Generate new Ed25519 keypair via BC lightweight API
            Log.d("BC", "Generating new Ed25519 keypair via BC lightweight API")
            val generator = Ed25519KeyPairGenerator()
            generator.init(Ed25519KeyGenerationParameters(SecureRandom()))
            val keyPairBC = generator.generateKeyPair()
            val privateKey = keyPairBC.private as Ed25519PrivateKeyParameters
            val publicKey = keyPairBC.public as Ed25519PublicKeyParameters

            // 3. Persist raw key bytes in EncryptedSharedPreferences
            prefs.edit()
                .putString(KEY_SIGNING_PUBLIC, Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP))
                .putString(KEY_SIGNING_PRIVATE, Base64.encodeToString(privateKey.encoded, Base64.NO_WRAP))
                .apply()

            cachedSigningPrivate = privateKey
            cachedSigningPublic = publicKey
            return toJcaKeyPair(privateKey, publicKey)
        }
    }

    /** Convert BC lightweight params to a JCA KeyPair (for API compatibility). */
    private fun toJcaKeyPair(priv: Ed25519PrivateKeyParameters, pub: Ed25519PublicKeyParameters): KeyPair {
        // Create a dummy KeyPair — callers only need the encoded bytes
        val pubEncoded = pub.encoded
        val privEncoded = priv.encoded
        val pubKey = object : PublicKey {
            override fun getAlgorithm() = "Ed25519"
            override fun getFormat() = "RAW"
            override fun getEncoded() = pubEncoded
        }
        val privKey = object : PrivateKey {
            override fun getAlgorithm() = "Ed25519"
            override fun getFormat() = "RAW"
            override fun getEncoded() = privEncoded
        }
        return KeyPair(pubKey, privKey)
    }

    /** Get the Ed25519 signing public key as Base64 (X509 encoded). */
    fun getSigningPublicKeyBase64(): String {
        val keyPair = getOrDeriveSigningKeyPair()
        return Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
    }

    /** Clear cached signing key pair and stored keys (call on logout/account delete). */
    fun clearSigningKeyCache() {
        cachedSigningPrivate = null
        cachedSigningPublic = null
        prefs.edit()
            .remove(KEY_SIGNING_PUBLIC)
            .remove(KEY_SIGNING_PRIVATE)
            .apply()
    }

    /**
     * Sign message data: ciphertext || conversationId || createdAt (big-endian 8 bytes).
     * Returns Base64-encoded Ed25519 signature (64 bytes).
     */
    fun signMessage(
        ciphertextBase64: String,
        conversationId: String,
        createdAt: Long
    ): String {
        val keyPair = getOrDeriveSigningKeyPair()
        val dataToSign = buildSignedData(ciphertextBase64, conversationId, createdAt)

        val privBytes = keyPair.private.encoded
        val privateKey = Ed25519PrivateKeyParameters(privBytes, 0)
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        signer.update(dataToSign, 0, dataToSign.size)
        val signatureBytes = signer.generateSignature()
        val signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)

        dataToSign.fill(0)
        return signatureBase64
    }

    /**
     * Verify an Ed25519 signature on received message data.
     * Returns true if valid, false otherwise.
     */
    fun verifySignature(
        signingPublicKeyBase64: String,
        ciphertextBase64: String,
        conversationId: String,
        createdAt: Long,
        signatureBase64: String
    ): Boolean {
        return try {
            val pubKeyBytes = Base64.decode(signingPublicKeyBase64, Base64.NO_WRAP)
            val publicKey = Ed25519PublicKeyParameters(pubKeyBytes, 0)

            val dataToVerify = buildSignedData(ciphertextBase64, conversationId, createdAt)
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)

            val verifier = Ed25519Signer()
            verifier.init(false, publicKey)
            verifier.update(dataToVerify, 0, dataToVerify.size)
            val result = verifier.verifySignature(signatureBytes)
            dataToVerify.fill(0)
            result
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Build the data blob that is signed: ciphertext || conversationId || createdAt.
     * createdAt is encoded as big-endian 8 bytes for anti-replay protection.
     */
    private fun buildSignedData(
        ciphertextBase64: String,
        conversationId: String,
        createdAt: Long
    ): ByteArray {
        val ciphertextBytes = ciphertextBase64.toByteArray(Charsets.UTF_8)
        val conversationBytes = conversationId.toByteArray(Charsets.UTF_8)
        val timestampBytes = ByteArray(8)
        for (i in 0..7) {
            timestampBytes[7 - i] = ((createdAt shr (i * 8)) and 0xFF).toByte()
        }
        val result = ByteArray(ciphertextBytes.size + conversationBytes.size + 8)
        System.arraycopy(ciphertextBytes, 0, result, 0, ciphertextBytes.size)
        System.arraycopy(conversationBytes, 0, result, ciphertextBytes.size, conversationBytes.size)
        System.arraycopy(timestampBytes, 0, result, ciphertextBytes.size + conversationBytes.size, 8)
        return result
    }

    data class EncryptedData(
        val ciphertext: String,
        val iv: String
    )

    // ========================================================================
    // 9. ML-KEM-768 IDENTITY KEY (PQXDH — post-quantum key encapsulation)
    //
    // Uses BouncyCastle 1.78+ lightweight API directly (NOT via JCA provider).
    // Same pattern as Ed25519: raw bytes stored in EncryptedSharedPreferences.
    // ========================================================================

    /**
     * Generate & persist an ML-KEM-768 identity key pair.
     * Idempotent: returns existing public key if already generated.
     * @return Base64 public key (~1580 chars).
     */
    fun generateMLKEMIdentityKeyPair(): String {
        val existing = prefs.getString(KEY_MLKEM_PUBLIC, null)
        if (existing != null) return existing

        val generator = MLKEMKeyPairGenerator()
        generator.init(MLKEMKeyGenerationParameters(secureRandom, MLKEMParameters.ml_kem_768))
        val keyPair = generator.generateKeyPair()
        val pub = keyPair.public as MLKEMPublicKeyParameters
        val priv = keyPair.private as MLKEMPrivateKeyParameters

        val pubBase64  = Base64.encodeToString(pub.encoded, Base64.NO_WRAP)
        val privBase64 = Base64.encodeToString(priv.encoded, Base64.NO_WRAP)

        prefs.edit()
            .putString(KEY_MLKEM_PUBLIC, pubBase64)
            .putString(KEY_MLKEM_PRIVATE, privBase64)
            .apply()

        return pubBase64
    }

    /** Returns the stored ML-KEM-768 public key as Base64, or null if not yet generated. */
    fun getMLKEMPublicKey(): String? = prefs.getString(KEY_MLKEM_PUBLIC, null)

    /**
     * ML-KEM-768 encapsulation (initiator side).
     * @param recipientPublicKeyBase64 Recipient's ML-KEM public key (Base64).
     * @return Pair of (ciphertextBase64, sharedSecretBytes). Zero-wipe secret after use.
     */
    fun mlkemEncaps(recipientPublicKeyBase64: String): Pair<String, ByteArray> {
        val pubBytes = Base64.decode(recipientPublicKeyBase64, Base64.NO_WRAP)
        val recipientPub = MLKEMPublicKeyParameters(MLKEMParameters.ml_kem_768, pubBytes)

        val genResult: SecretWithEncapsulation = MLKEMGenerator(secureRandom).generateEncapsulated(recipientPub)
        val ciphertextBase64 = Base64.encodeToString(genResult.encapsulation, Base64.NO_WRAP)
        val sharedSecret = genResult.secret.copyOf()
        genResult.destroy()
        return Pair(ciphertextBase64, sharedSecret)
    }

    /**
     * ML-KEM-768 decapsulation (recipient side).
     * @param ciphertextBase64 KEM ciphertext received in the first message (Base64).
     * @return sharedSecretBytes. Zero-wipe after use.
     */
    fun mlkemDecaps(ciphertextBase64: String): ByteArray {
        val privBase64 = prefs.getString(KEY_MLKEM_PRIVATE, null)
            ?: throw IllegalStateException("ML-KEM identity key not initialized")

        val privBytes = Base64.decode(privBase64, Base64.NO_WRAP)
        val privateKey = MLKEMPrivateKeyParameters(MLKEMParameters.ml_kem_768, privBytes)
        privBytes.fill(0)

        val ciphertextBytes = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        val kemExtractor = MLKEMExtractor(privateKey)
        return kemExtractor.extractSecret(ciphertextBytes)
    }

    /**
     * Derive PQXDH root key by combining X25519 and ML-KEM shared secrets.
     * HKDF-SHA256(ssClassic || ssPQ) — both secrets are zeroed after derivation.
     */
    fun deriveRootKeyPQXDH(ssClassic: ByteArray, ssPQ: ByteArray): SecretKey {
        val combined = ssClassic + ssPQ
        ssClassic.fill(0)
        ssPQ.fill(0)
        val key = deriveSymmetricKey(combined)  // deriveSymmetricKey already zeros combined
        return key
    }
}
