/*
 * Fialka — Secure P2P Chat
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */

package com.fialkaapp.fialka.crypto

import android.util.Base64
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

/**
 * JNI bridge to `libfialka_core.so` (Rust).
 *
 * All external functions receive/return raw [ByteArray].
 * Helper extensions on this object reconstruct the data-class types used by [CryptoManager].
 *
 * Wire formats match ffi/mod.rs exactly:
 *   encryptAes / encryptChaCha  → iv/nonce(12) || ciphertext
 *   encryptFile                 → key(32) || iv(12) || ciphertext
 *   x25519GenerateEphemeral     → priv(32) || pub(32)
 *   mlkemKeygenFromSeed         → ek(1568) || dk(3168)
 *   mlkemEncaps                 → ct(1568) || ss(32)
 *   mldsaKeygenFromSeed         → vk(1312) || sk(2560)
 *   identityDerive              → ed_pub(32) || x25519_pub(32) || x25519_priv(32)
 *                                  || mlkem_ek(1568) || mlkem_dk(3168)
 *                                  || mldsa_vk(1312) || mldsa_sk(2560) = 8704 bytes
 *   ratchetInitAsInitiator/Responder → root(32)||send_chain(32)||recv_chain(32)
 *                                       ||dh_priv(32)||dh_pub(32) = 160 bytes
 *   ratchetDhStep               → new_root(32) || new_chain(32) = 64 bytes
 *   ratchetAdvanceChain         → new_chain(32) || msg_key(32) = 64 bytes
 *   ed25519Verify / mldsaVerify → [1] = valid, [0] = invalid
 */
object FialkaNative {

    init {
        System.loadLibrary("fialka_core")
    }

    // ── KDF ──────────────────────────────────────────────────────────────────

    external fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray
    external fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray
    external fun hkdfZeroSalt(ikm: ByteArray, info: ByteArray): ByteArray

    // ── AES-256-GCM ──────────────────────────────────────────────────────────

    /** Returns iv(12) || ciphertext */
    external fun encryptAes(plaintext: ByteArray, key: ByteArray): ByteArray

    /** iv and ct are separate arrays. Returns UTF-8 plaintext bytes. */
    external fun decryptAes(iv: ByteArray, ct: ByteArray, key: ByteArray): ByteArray

    // ── ChaCha20-Poly1305 ────────────────────────────────────────────────────

    /** Returns nonce(12) || ciphertext */
    external fun encryptChaCha(plaintext: ByteArray, key: ByteArray): ByteArray
    external fun decryptChaCha(nonce: ByteArray, ct: ByteArray, key: ByteArray): ByteArray

    // ── File encryption ──────────────────────────────────────────────────────

    /** Returns key(32) || iv(12) || ciphertext */
    external fun encryptFile(fileBytes: ByteArray): ByteArray
    external fun decryptFile(ct: ByteArray, key: ByteArray, iv: ByteArray): ByteArray

    // ── Ed25519 ──────────────────────────────────────────────────────────────

    external fun ed25519Sign(seed: ByteArray, data: ByteArray): ByteArray
    /** Returns [1] if valid, [0] otherwise */
    external fun ed25519Verify(pub32: ByteArray, data: ByteArray, sig: ByteArray): ByteArray
    external fun ed25519BuildSignedData(ctUtf8: ByteArray, convIdUtf8: ByteArray, tsMs: Long): ByteArray

    // ── X25519 ───────────────────────────────────────────────────────────────

    /** Returns priv(32) || pub(32) */
    external fun x25519GenerateEphemeral(): ByteArray
    external fun x25519Dh(localPriv: ByteArray, remotePub: ByteArray): ByteArray
    external fun ed25519ToX25519Raw(edPub: ByteArray): ByteArray

    // ── Identity ─────────────────────────────────────────────────────────────

    /**
     * Returns 8704-byte bundle:
     * ed_pub(32) || x25519_pub(32) || x25519_priv(32)
     * || mlkem_ek(1568) || mlkem_dk(3168) || mldsa_vk(1312) || mldsa_sk(2560)
     */
    external fun identityDerive(seed: ByteArray): ByteArray
    external fun computeOnion(edPub: ByteArray): ByteArray
    external fun deriveAccountId(edPub: ByteArray): ByteArray

    // ── ML-KEM-1024 ──────────────────────────────────────────────────────────

    /** Returns ek(1568) || dk(3168) */
    external fun mlkemKeygenFromSeed(seed64: ByteArray): ByteArray
    /** Returns ct(1568) || ss(32) */
    external fun mlkemEncaps(ek: ByteArray): ByteArray
    external fun mlkemDecaps(dk: ByteArray, ct: ByteArray): ByteArray

    // ── ML-DSA-44 ────────────────────────────────────────────────────────────

    /** Returns vk(1312) || sk(2560) */
    external fun mldsaKeygenFromSeed(seed32: ByteArray): ByteArray
    external fun mldsaSign(sk: ByteArray, data: ByteArray): ByteArray
    /** Returns [1] if valid, [0] otherwise */
    external fun mldsaVerify(vk: ByteArray, data: ByteArray, sig: ByteArray): ByteArray

    // ── Ratchet ──────────────────────────────────────────────────────────────

    external fun deriveRootKeyPqxdh(ssClassic: ByteArray, ssPq: ByteArray): ByteArray

    /** Returns root(32)||send_chain(32)||recv_chain(32)||dh_priv(32)||dh_pub(32) = 160 bytes */
    external fun ratchetInitAsInitiator(identitySecret: ByteArray): ByteArray
    external fun ratchetInitAsResponder(identitySecret: ByteArray): ByteArray

    /** Returns new_root(32) || new_chain(32) = 64 bytes */
    external fun ratchetDhStep(root: ByteArray, localPriv: ByteArray, remotePub: ByteArray): ByteArray

    /** Returns new_chain(32) || msg_key(32) = 64 bytes */
    external fun ratchetAdvanceChain(chainKey: ByteArray): ByteArray

    external fun ratchetPqStep(rootKey: ByteArray, pqSs: ByteArray): ByteArray

    // ── Kotlin helper extensions ─────────────────────────────────────────────

    /**
     * Wrap [encryptAes] result into [CryptoManager.EncryptedData].
     * iv(12) || ct → ciphertext=Base64(ct), iv=Base64(iv[0..12])
     */
    fun encryptAesAsData(plaintext: String, key: SecretKey): CryptoManager.EncryptedData {
        val raw = encryptAes(plaintext.toByteArray(Charsets.UTF_8), key.encoded)
        val iv = raw.copyOfRange(0, 12)
        val ct = raw.copyOfRange(12, raw.size)
        return CryptoManager.EncryptedData(
            ciphertext = Base64.encodeToString(ct, Base64.NO_WRAP),
            iv         = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    /**
     * Wrap [encryptChaCha] result into [CryptoManager.EncryptedData].
     * nonce(12) || ct → ciphertext=Base64(ct), iv=Base64(nonce)
     */
    fun encryptChaChaAsData(plaintext: String, key: SecretKey): CryptoManager.EncryptedData {
        val raw = encryptChaCha(plaintext.toByteArray(Charsets.UTF_8), key.encoded)
        val nonce = raw.copyOfRange(0, 12)
        val ct    = raw.copyOfRange(12, raw.size)
        return CryptoManager.EncryptedData(
            ciphertext = Base64.encodeToString(ct, Base64.NO_WRAP),
            iv         = Base64.encodeToString(nonce, Base64.NO_WRAP)
        )
    }

    /**
     * Decrypt [CryptoManager.EncryptedData] (AES-GCM) using Rust.
     */
    fun decryptAesFromData(data: CryptoManager.EncryptedData, key: SecretKey): String {
        val iv = Base64.decode(data.iv, Base64.NO_WRAP)
        val ct = Base64.decode(data.ciphertext, Base64.NO_WRAP)
        return decryptAes(iv, ct, key.encoded).toString(Charsets.UTF_8)
    }

    /**
     * Decrypt [CryptoManager.EncryptedData] (ChaCha20) using Rust.
     */
    fun decryptChaChaFromData(data: CryptoManager.EncryptedData, key: SecretKey): String {
        val nonce = Base64.decode(data.iv, Base64.NO_WRAP)
        val ct    = Base64.decode(data.ciphertext, Base64.NO_WRAP)
        return decryptChaCha(nonce, ct, key.encoded).toString(Charsets.UTF_8)
    }

    /**
     * Wrap [encryptFile] result into [CryptoManager.FileEncryptionResult].
     * key(32) || iv(12) || ct
     */
    fun encryptFileAsResult(fileBytes: ByteArray): CryptoManager.FileEncryptionResult {
        val raw  = encryptFile(fileBytes)
        val key  = raw.copyOfRange(0, 32)
        val iv   = raw.copyOfRange(32, 44)
        val ct   = raw.copyOfRange(44, raw.size)
        return CryptoManager.FileEncryptionResult(
            encryptedBytes = ct,
            keyBase64      = Base64.encodeToString(key, Base64.NO_WRAP),
            ivBase64       = Base64.encodeToString(iv, Base64.NO_WRAP)
        )
    }

    /**
     * Wrap [x25519GenerateEphemeral] result into [CryptoManager.X25519KeyPair].
     * priv(32) || pub(32)
     */
    fun generateEphemeralX25519(): CryptoManager.X25519KeyPair {
        val raw = x25519GenerateEphemeral()
        return CryptoManager.X25519KeyPair(
            publicKeyBase64  = Base64.encodeToString(raw.copyOfRange(32, 64), Base64.NO_WRAP),
            privateKeyBase64 = Base64.encodeToString(raw.copyOfRange(0, 32), Base64.NO_WRAP)
        )
    }

    /**
     * Wrap [mlkemEncaps] result: ct(1568) || ss(32)
     * Returns Pair(ciphertextBase64, sharedSecretBytes)
     */
    fun mlkemEncapsResult(ek: ByteArray): Pair<String, ByteArray> {
        val raw = mlkemEncaps(ek)
        val ct = raw.copyOfRange(0, 1568)
        val ss = raw.copyOfRange(1568, 1600)
        return Pair(Base64.encodeToString(ct, Base64.NO_WRAP), ss)
    }

    /**
     * Derive AES SecretKey from HKDF for use with CryptoManager symmetric operations.
     */
    fun deriveSymmetricKey(sharedSecret: ByteArray): SecretKey {
        val keyBytes = hkdfZeroSalt(sharedSecret, "Fialka-symmetric-key".toByteArray())
        return SecretKeySpec(keyBytes, "AES")
    }

    /** Returns true if the Rust ed25519Verify result indicates a valid signature. */
    fun isEd25519Valid(result: ByteArray): Boolean = result.isNotEmpty() && result[0] == 1.toByte()

    /** Returns true if the Rust mldsaVerify result indicates a valid signature. */
    fun isMlDsaValid(result: ByteArray): Boolean = result.isNotEmpty() && result[0] == 1.toByte()
}
