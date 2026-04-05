/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.fialkaapp.fialka.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.fialkaapp.fialka.util.DeviceSecurityManager
import com.fialkaapp.fialka.util.FialkaSecurePrefs
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * CryptoManager — "1 Seed → Everything" identity + E2E crypto module for Fialka.
 *
 * Identity: Ed25519 seed (32 bytes, BIP-39 24 words) is the master secret.
 *   seed → Ed25519 keypair  (signing)
 *   seed → X25519 keypair   (DH — birational, same scalar via SHA-512)
 *   seed → ML-KEM-1024      (PQXDH — HKDF deterministic KeyGen)
 *   seed → ML-DSA-44        (PQ handshake auth — HKDF deterministic KeyGen)
 *   seed → Account ID        (SHA3-256 → Base58)
 *
 * All keys stored encrypted in EncryptedSharedPreferences (AES-256-GCM
 * backed by Android Keystore). Same model as Signal.
 *
 * Ephemeral keys: X25519 in software (generated per DH ratchet step, then discarded).
 * Encryption: AES-256-GCM (AEAD) with 12-byte random IV per message.
 * Key derivation: HKDF-SHA256.
 */
object CryptoManager {

    private const val PREFS_FILE = "fialka_identity_keys"
    private const val KEY_ED25519_SEED = "identity_ed25519_seed"
    private const val KEY_ACCOUNT_ID = "identity_account_id"
    private const val KEY_PUBLIC = "identity_public_key"
    private const val KEY_PRIVATE = "identity_private_key"
    private const val KEY_MLKEM_PUBLIC  = "identity_mlkem_public_key"
    private const val KEY_MLKEM_PRIVATE = "identity_mlkem_private_key"
    private const val KEY_MLDSA_PUBLIC  = "identity_mldsa_public_key"
    private const val KEY_MLDSA_PRIVATE = "identity_mldsa_private_key"
    /**
     * Set to true once the user successfully completes seed verification.
     * hasIdentity() only returns true after this flag is set — prevents
     * bypassing onboarding by killing the app before verification completes.
     */
    private const val KEY_SEED_VERIFIED      = "identity_seed_verified"
    /** Three comma-separated word indexes (0-based) chosen at identity generation time. Fixed for life. */
    private const val KEY_SEED_PROMPT_INDEXES = "identity_seed_prompt_indexes"

    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private const val AES_KEY_LENGTH_BYTES = 32
private const val HKDF_INFO = "Fialka-v2-message-key"
    private const val INBOX_HKDF_INFO = "Fialka-Inbox-v1"

    private val secureRandom = SecureRandom()

    private lateinit var prefs: SharedPreferences

    /**
     * Must be called once from Application.onCreate() before any crypto operation.
     * Initializes EncryptedSharedPreferences backed by Android Keystore AES-256-GCM.
     */
    fun init(context: Context) {
        val profile = DeviceSecurityManager.getSecurityProfile(context.applicationContext)
        prefs = FialkaSecurePrefs.open(
            context.applicationContext,
            PREFS_FILE,
            strongBox = profile.isStrongBoxAvailable
        )
    }

    // ========================================================================
    // 1. IDENTITY — "1 Seed → Everything"
    //
    // Ed25519 seed (32 bytes) is the master secret from which all keys derive:
    //   seed → Ed25519 keypair  (signing + identity)
    //   seed → X25519 keypair   (DH — via SHA-512(seed)[0..31] = same scalar)
    //   seed → ML-KEM-1024      (PQXDH — via HKDF deterministic KeyGen)
    //   seed → Account ID        (SHA3-256(Ed25519 pub) → Base58)
    //
    // BIP-39 24 words encode this seed. Restoring = re-deriving everything.
    // ========================================================================

    // PKCS8 prefix for X25519 raw 32-byte private key (RFC 8410)
    private val X25519_PKCS8_PREFIX = byteArrayOf(
        0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05,
        0x06, 0x03, 0x2b, 0x65, 0x6e, 0x04, 0x22,
        0x04, 0x20
    )
    // X509 prefix for X25519 raw 32-byte public key
    private val X25519_X509_PREFIX = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b,
        0x65, 0x6e, 0x03, 0x21, 0x00
    )

    /**
     * Generate a brand-new identity from a fresh random Ed25519 seed.
     * Derives: Ed25519, X25519, ML-KEM-1024, Account ID — all from 1 seed.
     * @return X25519 public key (Base64) for backward compat with current protocol.
     */
    fun generateIdentity(): String {
        val existingSeed = prefs.getString(KEY_ED25519_SEED, null)
        if (existingSeed != null) return prefs.getString(KEY_PUBLIC, null)!!

        val seed = ByteArray(32)
        secureRandom.nextBytes(seed)
        try {
            val publicKey = deriveAndStoreAllKeys(seed)
            // Generate and persist the 3 fixed word positions used for verification.
            // These never change — same words every time the screen is opened.
            val indexes = (0 until 24).shuffled(secureRandom).take(3).sorted()
            prefs.edit().putString(KEY_SEED_PROMPT_INDEXES, indexes.joinToString(",")).apply()
            return publicKey
        } finally {
            seed.fill(0)
        }
    }

    /**
     * Restore identity from a 32-byte Ed25519 seed (decoded from BIP-39 mnemonic).
     * Deterministically re-derives all keys — Ed25519, X25519, ML-KEM, Account ID.
     * @return X25519 public key (Base64).
     */
    fun restoreFromSeed(seed: ByteArray): String {
        require(seed.size == 32) { "Ed25519 seed must be 32 bytes" }
        val key = deriveAndStoreAllKeys(seed)
        // Restoring from seed proves knowledge — mark as verified immediately.
        markSeedVerified()
        return key
    }

    /** True only when seed exists AND the user has completed seed verification. */
    fun hasIdentity(): Boolean =
        prefs.getString(KEY_ED25519_SEED, null) != null && isSeedVerified()

    /** True when keys are generated but the user hasn't verified the seed phrase yet. */
    fun hasPendingIdentity(): Boolean =
        prefs.getString(KEY_ED25519_SEED, null) != null && !isSeedVerified()

    fun isSeedVerified(): Boolean = prefs.getBoolean(KEY_SEED_VERIFIED, false)

    /** Called after the user successfully enters the 3 verification words (or skips with warning). */
    fun markSeedVerified() {
        prefs.edit().putBoolean(KEY_SEED_VERIFIED, true).apply()
    }

    /**
     * Returns the 3 fixed word positions (0-based) chosen at identity creation.
     * Always returns the same indexes — never reshuffled — so the user always
     * sees the same words when they return to the verification screen.
     */
    fun getSeedPromptIndexes(): List<Int> {
        val stored = prefs.getString(KEY_SEED_PROMPT_INDEXES, null) ?: return emptyList()
        return stored.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    fun getPublicKey(): String? = prefs.getString(KEY_PUBLIC, null)

    /**
     * Returns the raw 32-byte Ed25519 seed for BIP-39 mnemonic backup.
     * This is the MASTER SECRET from which all keys derive.
     */
    fun getIdentitySeed(): ByteArray {
        val seedBase64 = prefs.getString(KEY_ED25519_SEED, null)
            ?: throw IllegalStateException("Identity not initialized")
        return Base64.decode(seedBase64, Base64.NO_WRAP)
    }

    /** Account ID: SHA3-256(Ed25519 pubkey) → Base58. */
    fun getAccountId(): String? = prefs.getString(KEY_ACCOUNT_ID, null)

    /** Raw 32-byte Ed25519 public key (BC encoded = raw bytes, no ASN.1). */
    fun getEd25519PublicKeyRaw(): ByteArray {
        val pubBase64 = prefs.getString(KEY_SIGNING_PUBLIC, null)
            ?: throw IllegalStateException("Identity not initialized")
        return Base64.decode(pubBase64, Base64.NO_WRAP)
    }

    /**
     * Tor v3 .onion address derived from the local Ed25519 public key.
     * Delegates to [computeOnionFromEd25519] with our own identity key.
     */
    fun getOnionAddress(): String = computeOnionFromEd25519(getEd25519PublicKeyRaw())

    /**
     * Compute a Tor v3 .onion address from ANY Ed25519 public key.
     *
     * Spec: rend-spec-v3 §6 "Encoding onion addresses"
     *   checksum = SHA3-256(".onion checksum" || pubkey || version)[0..1]
     *   version  = 0x03
     *   address  = base32(pubkey || checksum || version).lowercase() + ".onion"
     *   Result:  56 chars + ".onion"
     *
     * @param ed25519PubBytes 32-byte Ed25519 public key (raw, no ASN.1).
     */
    fun computeOnionFromEd25519(ed25519PubBytes: ByteArray): String {
        require(ed25519PubBytes.size == 32) { "Ed25519 public key must be 32 bytes" }
        return FialkaNative.computeOnion(ed25519PubBytes).toString(Charsets.UTF_8)
    }

    /**
     * Convert an Ed25519 public key to its X25519 equivalent via the birational
     * map between twisted Edwards25519 and Montgomery/Curve25519.
     *
     * Math: u = (1 + y) / (1 - y) mod p,  where p = 2²⁵⁵ − 19
     * and y is the y-coordinate encoded in the 32-byte Ed25519 public key.
     *
     * Same conversion as libsodium's crypto_sign_ed25519_pk_to_curve25519.
     *
     * @param ed25519PubBytes 32-byte Ed25519 public key (raw, no ASN.1).
     * @return 32-byte raw X25519 public key (little-endian u-coordinate).
     */
    fun ed25519PublicKeyToX25519Raw(ed25519PubBytes: ByteArray): ByteArray {
        require(ed25519PubBytes.size == 32) { "Ed25519 public key must be 32 bytes" }
        return FialkaNative.ed25519ToX25519Raw(ed25519PubBytes)
    }

    /**
     * Convert an Ed25519 public key to a full X25519 X.509-encoded public key
     * (Base64), compatible with [Contact.publicKey] and [performKeyAgreement].
     *
     * @param ed25519PubBytes 32-byte Ed25519 public key (raw, no ASN.1).
     * @return Base64-encoded X25519 public key (44-byte X.509 SubjectPublicKeyInfo).
     */
    fun ed25519PublicKeyToX25519(ed25519PubBytes: ByteArray): String {
        val rawX25519 = ed25519PublicKeyToX25519Raw(ed25519PubBytes)
        return Base64.encodeToString(X25519_X509_PREFIX + rawX25519, Base64.NO_WRAP)
    }

    /**
     * 64-byte expanded Ed25519 secret key for Tor ADD_ONION.
     *
     * Format: SHA-512(seed) with first 32 bytes clamped per Ed25519 spec.
     * Tor control protocol expects: "ED25519-V3:<base64 of 64 bytes>".
     *
     * The expanded key encodes the same keypair as our identity —
     * so the resulting .onion matches [getOnionAddress] exactly.
     */
    fun getExpandedEd25519KeyForTor(): ByteArray {
        val seed = getIdentitySeed()
        val expanded = MessageDigest.getInstance("SHA-512").digest(seed)
        // Clamp scalar (first 32 bytes) per RFC 8032 §5.1.5
        expanded[0] = (expanded[0].toInt() and 248).toByte()
        expanded[31] = (expanded[31].toInt() and 63).toByte()
        expanded[31] = (expanded[31].toInt() or 64).toByte()
        seed.fill(0)
        return expanded
    }

    /**
     * Core derivation: 1 Ed25519 seed → all keys + Account ID (via Rust fialka-core).
     * Called by both generateIdentity() and restoreFromSeed().
     *
     * Bundle from Rust (8704 bytes):
     *   ed_pub(32) || x25519_pub(32) || x25519_priv(32)
     *   || mlkem_ek(1568) || mlkem_dk(3168)
     *   || mldsa_vk(1312) || mldsa_sk(2560)
     */
    private fun deriveAndStoreAllKeys(seed: ByteArray): String {
        val bundle = FialkaNative.identityDerive(seed)

        // Parse the 8704-byte bundle
        var off = 0
        val edPub        = bundle.copyOfRange(off, off + 32).also { off += 32 }
        val x25519PubRaw = bundle.copyOfRange(off, off + 32).also { off += 32 }
        val x25519PrivRaw= bundle.copyOfRange(off, off + 32).also { off += 32 }
        val mlkemEk      = bundle.copyOfRange(off, off + 1568).also { off += 1568 }
        val mlkemDk      = bundle.copyOfRange(off, off + 3168).also { off += 3168 }
        val mldsaVk      = bundle.copyOfRange(off, off + 1312).also { off += 1312 }
        val mldsaSk      = bundle.copyOfRange(off, off + 2560)

        // Wrap X25519 raw keys into JCA encoding for storage format compatibility
        val x25519PubJca  = X25519_X509_PREFIX + x25519PubRaw
        val x25519PrivJca = X25519_PKCS8_PREFIX + x25519PrivRaw

        val x25519PubBase64  = Base64.encodeToString(x25519PubJca, Base64.NO_WRAP)
        val x25519PrivBase64 = Base64.encodeToString(x25519PrivJca, Base64.NO_WRAP)

        val accountId = FialkaNative.deriveAccountId(edPub).toString(Charsets.UTF_8)

        prefs.edit()
            .putString(KEY_ED25519_SEED, Base64.encodeToString(seed, Base64.NO_WRAP))
            .putString(KEY_PUBLIC, x25519PubBase64)
            .putString(KEY_PRIVATE, x25519PrivBase64)
            .putString(KEY_SIGNING_PUBLIC, Base64.encodeToString(edPub, Base64.NO_WRAP))
            .putString(KEY_SIGNING_PRIVATE, Base64.encodeToString(seed, Base64.NO_WRAP))
            .putString(KEY_MLKEM_PUBLIC, Base64.encodeToString(mlkemEk, Base64.NO_WRAP))
            .putString(KEY_MLKEM_PRIVATE, Base64.encodeToString(mlkemDk, Base64.NO_WRAP))
            .putString(KEY_MLDSA_PUBLIC, Base64.encodeToString(mldsaVk, Base64.NO_WRAP))
            .putString(KEY_MLDSA_PRIVATE, Base64.encodeToString(mldsaSk, Base64.NO_WRAP))
            .putString(KEY_ACCOUNT_ID, accountId)
            .apply()

        // Cache raw ed25519 pub bytes (no BC objects needed)
        cachedSigningPublicBytes = edPub
        x25519PrivRaw.fill(0)

        return x25519PubBase64
    }

    fun deleteIdentityKey() {
        prefs.edit()
            .remove(KEY_ED25519_SEED)
            .remove(KEY_ACCOUNT_ID)
            .remove(KEY_PUBLIC)
            .remove(KEY_PRIVATE)
            .remove(KEY_MLKEM_PUBLIC)
            .remove(KEY_MLKEM_PRIVATE)
            .remove(KEY_MLDSA_PUBLIC)
            .remove(KEY_MLDSA_PRIVATE)
            .apply()
        clearSigningKeyCache()
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
    // 3. DIFFIE-HELLMAN (X25519 via Rust fialka-core)
    // ========================================================================

    private fun getIdentityPrivateKeyRaw(): ByteArray {
        val privBase64 = prefs.getString(KEY_PRIVATE, null)
            ?: throw IllegalStateException("Identity key not initialized")
        val full = Base64.decode(privBase64, Base64.NO_WRAP)
        // Strip JCA PKCS8 prefix (16 bytes) to get raw 32-byte X25519 private key
        return full.copyOfRange(X25519_PKCS8_PREFIX.size, X25519_PKCS8_PREFIX.size + 32)
    }

    /** DH with identity private key and a remote public key. */
    fun performKeyAgreement(remotePublicKeyBase64: String): ByteArray {
        val privRaw = getIdentityPrivateKeyRaw()
        val pubFull = Base64.decode(remotePublicKeyBase64, Base64.NO_WRAP)
        val pubRaw = pubFull.copyOfRange(X25519_X509_PREFIX.size, X25519_X509_PREFIX.size + 32)
        val secret = FialkaNative.x25519Dh(privRaw, pubRaw)
        privRaw.fill(0)
        return secret
    }

    /** DH with an ephemeral private key (software) and a remote public key. */
    fun performEphemeralKeyAgreement(
        localPrivateKeyBase64: String,
        remotePublicKeyBase64: String
    ): ByteArray {
        val privFull = Base64.decode(localPrivateKeyBase64, Base64.NO_WRAP)
        val pubFull = Base64.decode(remotePublicKeyBase64, Base64.NO_WRAP)
        val privRaw = privFull.copyOfRange(X25519_PKCS8_PREFIX.size, X25519_PKCS8_PREFIX.size + 32)
        val pubRaw = pubFull.copyOfRange(X25519_X509_PREFIX.size, X25519_X509_PREFIX.size + 32)
        val secret = FialkaNative.x25519Dh(privRaw, pubRaw)
        privRaw.fill(0)
        return secret
    }

    // ========================================================================
    // 4. KEY DERIVATION (HKDF-SHA256)
    // ========================================================================

    fun deriveSymmetricKey(sharedSecret: ByteArray): SecretKey {
        val keyBytes = FialkaNative.hkdfZeroSalt(sharedSecret, HKDF_INFO.toByteArray(Charsets.UTF_8))
        sharedSecret.fill(0)
        return SecretKeySpec(keyBytes, "AES")
    }

    // ========================================================================
    // 5. AES-256-GCM ENCRYPTION / DECRYPTION
    // ========================================================================

    fun encrypt(plaintext: String, key: SecretKey): EncryptedData {
        val raw = FialkaNative.encryptAes(plaintext.toByteArray(Charsets.UTF_8), key.encoded)
        val iv = raw.copyOfRange(0, 12)
        val ct = raw.copyOfRange(12, raw.size)
        return EncryptedData(
            ciphertext = Base64.encodeToString(ct, Base64.NO_WRAP),
            iv = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    fun decrypt(encryptedData: EncryptedData, key: SecretKey): String {
        val iv = Base64.decode(encryptedData.iv, Base64.NO_WRAP)
        val ct = Base64.decode(encryptedData.ciphertext, Base64.NO_WRAP)
        return FialkaNative.decryptAes(iv, ct, key.encoded).toString(Charsets.UTF_8)
    }

    // ========================================================================
    // 5b. ChaCha20-Poly1305 ENCRYPTION (alternative cipher for devices without AES-NI)
    // ========================================================================

    /**
     * Encrypt with ChaCha20-Poly1305 (via Rust fialka-core).
     * Same padding as AES-GCM — transparent to the Double Ratchet.
     */
    fun encryptChaCha(plaintext: String, key: SecretKey): EncryptedData {
        val raw = FialkaNative.encryptChaCha(plaintext.toByteArray(Charsets.UTF_8), key.encoded)
        val nonce = raw.copyOfRange(0, 12)
        val ct = raw.copyOfRange(12, raw.size)
        return EncryptedData(
            ciphertext = Base64.encodeToString(ct, Base64.NO_WRAP),
            iv = Base64.encodeToString(nonce, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypt with ChaCha20-Poly1305 (via Rust fialka-core).
     */
    fun decryptChaCha(encryptedData: EncryptedData, key: SecretKey): String {
        val nonce = Base64.decode(encryptedData.iv, Base64.NO_WRAP)
        val ct = Base64.decode(encryptedData.ciphertext, Base64.NO_WRAP)
        return FialkaNative.decryptChaCha(nonce, ct, key.encoded).toString(Charsets.UTF_8)
    }

    /**
     * Detect if the device has hardware AES acceleration.
     * Returns true if AES-NI is available (newer ARM processors have ARMv8 AES extension).
     * On devices without it, ChaCha20-Poly1305 is faster.
     */
    fun hasHardwareAes(): Boolean {
        return try {
            // ARMv8 Crypto Extension is standard on API 33+ (minSdk 33)
            // Android's JCA AES-GCM uses hardware acceleration transparently
            // If Cipher init takes < 1ms, hardware is present
            val testKey = SecretKeySpec(ByteArray(32), "AES")
            val start = System.nanoTime()
            val c = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            c.init(Cipher.ENCRYPT_MODE, testKey, GCMParameterSpec(128, ByteArray(12)))
            c.doFinal(ByteArray(64))
            val elapsed = System.nanoTime() - start
            elapsed < 5_000_000 // < 5ms → hardware AES available
        } catch (_: Exception) {
            false
        }
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
     * Encrypt raw file bytes with a fresh random AES-256-GCM key (via Rust).
     * Returns the ciphertext + key + IV (key/IV are sent via the ratchet).
     */
    fun encryptFile(fileBytes: ByteArray): FileEncryptionResult {
        val raw = FialkaNative.encryptFile(fileBytes)   // key(32) || iv(12) || ct
        val key = raw.copyOfRange(0, 32)
        val iv  = raw.copyOfRange(32, 44)
        val ct  = raw.copyOfRange(44, raw.size)
        return FileEncryptionResult(
            encryptedBytes = ct,
            keyBase64 = Base64.encodeToString(key, Base64.NO_WRAP),
            ivBase64  = Base64.encodeToString(iv,  Base64.NO_WRAP)
        )
    }

    /**
     * Decrypt file bytes using the provided key and IV (via Rust).
     */
    fun decryptFile(encryptedBytes: ByteArray, keyBase64: String, ivBase64: String): ByteArray {
        val key = Base64.decode(keyBase64, Base64.NO_WRAP)
        val iv  = Base64.decode(ivBase64,  Base64.NO_WRAP)
        return FialkaNative.decryptFile(encryptedBytes, key, iv)
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
        val combined = (sorted[0] + "|" + sorted[1]).toByteArray(Charsets.UTF_8)
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

        val aesKeyBytes = FialkaNative.hkdfZeroSalt(dhSecret, INBOX_HKDF_INFO.toByteArray(Charsets.UTF_8))
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

        val privRaw = getIdentityPrivateKeyRaw()
        val ephemPubRaw = ephemPubBytes.copyOfRange(X25519_X509_PREFIX.size, X25519_X509_PREFIX.size + 32)
        val dhSecret = FialkaNative.x25519Dh(privRaw, ephemPubRaw)
        privRaw.fill(0)

        val aesKeyBytes = FialkaNative.hkdfZeroSalt(dhSecret, INBOX_HKDF_INFO.toByteArray(Charsets.UTF_8))
        dhSecret.fill(0)

        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKeyBytes, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        val plaintext = cipher.doFinal(ciphertext)
        aesKeyBytes.fill(0)
        return plaintext
    }



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

    /** Returns the raw SHA-256 hex of the shared fingerprint (deterministic, safe for QR). */
    fun getSharedFingerprintHex(myPubKeyBase64: String, contactPubKeyBase64: String): String {
        val sorted = listOf(myPubKeyBase64, contactPubKeyBase64).sorted()
        val combined = (sorted[0] + sorted[1]).toByteArray(Charsets.UTF_8)
        val hash = MessageDigest.getInstance("SHA-256").digest(combined)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = FialkaNative.hmacSha256(key, data)

    /**
     * Derive a per-conversation pseudonymous sender ID.
     * HMAC-SHA256(conversationId, uid) truncated to 32 hex chars.
     * Prevents cross-conversation UID correlation (metadata hardening).
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

    /** Cached Ed25519 public key bytes (32 bytes raw). */
    @Volatile
    private var cachedSigningPublicBytes: ByteArray? = null

    /**
     * Get the Ed25519 signing key pair as a JCA KeyPair.
     * Private = seed bytes (32 bytes). Public = raw Ed25519 pub (32 bytes).
     * Compatible with TorTransport which uses .encoded on both.
     */
    fun getOrDeriveSigningKeyPair(): KeyPair {
        val cached = cachedSigningPublicBytes
        val seed = getIdentitySeed()
        val pubBytes = cached ?: run {
            val sp = prefs.getString(KEY_SIGNING_PUBLIC, null)
                ?: Base64.encodeToString(FialkaNative.identityDerive(seed).copyOfRange(0, 32), Base64.NO_WRAP)
            Base64.decode(sp, Base64.NO_WRAP).also { cachedSigningPublicBytes = it }
        }
        return makeSigningKeyPair(seed, pubBytes)
    }

    private fun makeSigningKeyPair(seedBytes: ByteArray, pubBytes: ByteArray): KeyPair {
        val pubKey = object : PublicKey {
            override fun getAlgorithm() = "Ed25519"
            override fun getFormat() = "RAW"
            override fun getEncoded() = pubBytes
        }
        val privKey = object : PrivateKey {
            override fun getAlgorithm() = "Ed25519"
            override fun getFormat() = "RAW"
            override fun getEncoded() = seedBytes
        }
        return KeyPair(pubKey, privKey)
    }

    /** Get the Ed25519 signing public key as Base64. */
    fun getSigningPublicKeyBase64(): String =
        prefs.getString(KEY_SIGNING_PUBLIC, null)
            ?: throw IllegalStateException("Identity not initialized")

    /** Clear cached signing key pair and stored keys (call on logout/account delete). */
    fun clearSigningKeyCache() {
        cachedSigningPublicBytes = null
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
        val seed = getIdentitySeed()
        val dataToSign = buildSignedData(ciphertextBase64, conversationId, createdAt)
        val signatureBytes = FialkaNative.ed25519Sign(seed, dataToSign)
        seed.fill(0)
        dataToSign.fill(0)
        return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
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
            val pubKeyBytes   = Base64.decode(signingPublicKeyBase64, Base64.NO_WRAP)
            val dataToVerify  = buildSignedData(ciphertextBase64, conversationId, createdAt)
            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            val result = FialkaNative.ed25519Verify(pubKeyBytes, dataToVerify, signatureBytes)
            dataToVerify.fill(0)
            result.isNotEmpty() && result[0] == 1.toByte()
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
    // 9. ML-KEM-1024 IDENTITY KEY (PQXDH — post-quantum key encapsulation)
    //
    // ML-KEM keypair is derived deterministically from Ed25519 seed during
    // generateIdentity() / restoreFromSeed(). No standalone generation needed.
    // ========================================================================

    /**
     * Returns the stored ML-KEM-1024 public key (generated during identity setup).
     * @return Base64 public key (~2092 chars).
     */
    fun generateMLKEMIdentityKeyPair(): String {
        return prefs.getString(KEY_MLKEM_PUBLIC, null)
            ?: throw IllegalStateException("Identity not initialized — call generateIdentity() first")
    }

    /** Returns the stored ML-KEM-1024 public key as Base64, or null if not yet generated. */
    fun getMLKEMPublicKey(): String? = prefs.getString(KEY_MLKEM_PUBLIC, null)

    /**
     * ML-KEM-1024 encapsulation (initiator side).
     * @param recipientPublicKeyBase64 Recipient's ML-KEM public key (Base64).
     * @return Pair of (ciphertextBase64, sharedSecretBytes). Zero-wipe secret after use.
     */
    fun mlkemEncaps(recipientPublicKeyBase64: String): Pair<String, ByteArray> {
        val ek = Base64.decode(recipientPublicKeyBase64, Base64.NO_WRAP)
        return FialkaNative.mlkemEncapsResult(ek)
    }

    /**
     * ML-KEM-1024 decapsulation (recipient side).
     * @param ciphertextBase64 KEM ciphertext received in the first message (Base64).
     * @return sharedSecretBytes. Zero-wipe after use.
     */
    fun mlkemDecaps(ciphertextBase64: String): ByteArray {
        val dk = Base64.decode(
            prefs.getString(KEY_MLKEM_PRIVATE, null)
                ?: throw IllegalStateException("ML-KEM identity key not initialized"),
            Base64.NO_WRAP
        )
        val ct = Base64.decode(ciphertextBase64, Base64.NO_WRAP)
        return FialkaNative.mlkemDecaps(dk, ct)
    }

    /**
     * Derive PQXDH root key by combining X25519 and ML-KEM shared secrets.
     * HKDF-SHA256(ssClassic || ssPQ) — both secrets are zeroed after derivation.
     */
    fun deriveRootKeyPQXDH(ssClassic: ByteArray, ssPQ: ByteArray): SecretKey {
        require(ssClassic.size == 32) { "X25519 shared secret must be 32 bytes" }
        require(ssPQ.size == 32) { "ML-KEM shared secret must be 32 bytes" }
        val rootBytes = FialkaNative.deriveRootKeyPqxdh(ssClassic, ssPQ)
        ssClassic.fill(0)
        ssPQ.fill(0)
        return SecretKeySpec(rootBytes, "AES")
    }

    // ========================================================================
    // 10. ML-DSA-44 HANDSHAKE AUTHENTICATION (post-quantum signature)
    //
    // ML-DSA-44 keypair is derived deterministically from Ed25519 seed during
    // generateIdentity() / restoreFromSeed(). Used ONLY during session
    // establishment (handshake) — not per-message (2420-byte sig is too heavy).
    // Ed25519 continues to sign every message; ML-DSA-44 adds PQ auth on the
    // first PQXDH handshake message.
    // ========================================================================

    /** Returns the stored ML-DSA-44 public key as Base64, or null if not yet generated. */
    fun getMlDsaPublicKey(): String? = prefs.getString(KEY_MLDSA_PUBLIC, null)

    /**
     * Sign handshake data with ML-DSA-44 (PQ authentication).
     * Called by the PQXDH initiator on the first message (kemCiphertext + ephemeralKey).
     * @param data Raw bytes to sign (e.g. kemCiphertext || ephemeralKey).
     * @return Base64-encoded ML-DSA-44 signature (2420 bytes raw).
     */
    fun signHandshakeMlDsa44(data: ByteArray): String {
        val sk = Base64.decode(
            prefs.getString(KEY_MLDSA_PRIVATE, null)
                ?: throw IllegalStateException("ML-DSA-44 key not initialized — call generateIdentity() first"),
            Base64.NO_WRAP
        )
        return Base64.encodeToString(FialkaNative.mldsaSign(sk, data), Base64.NO_WRAP)
    }

    /**
     * Verify a ML-DSA-44 handshake signature from the remote peer.
     */
    fun verifyHandshakeMlDsa44(pubKeyBase64: String, data: ByteArray, signatureBase64: String): Boolean {
        return try {
            val vk  = Base64.decode(pubKeyBase64,    Base64.NO_WRAP)
            val sig = Base64.decode(signatureBase64, Base64.NO_WRAP)
            val res = FialkaNative.mldsaVerify(vk, data, sig)
            res.isNotEmpty() && res[0] == 1.toByte()
        } catch (_: Exception) {
            false
        }
    }

    // ========================================================================
    // 11. IDENTITY HELPERS — remaining pure-Kotlin utilities
    // ========================================================================

}
