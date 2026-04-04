/*
 * Fialka — Post-quantum encrypted messenger
 * Unit tests for CryptoManager — pure functions only (no Android Keystore).
 *
 * Tests cover: AES-256-GCM, ChaCha20-Poly1305, HMAC-SHA256, Ed25519 verify,
 * X25519 DH, file crypto, fingerprints, .onion derivation, birational map.
 *
 * Functions that require CryptoManager.init() (Android Keystore, signMessage,
 * restoreFromSeed) are tested via instrumented tests (androidTest/).
 */
package com.fialkaapp.fialka.crypto

import android.util.Base64
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class CryptoManagerTest {

    companion object {
        private fun testKey(fill: Byte = 0x42) = SecretKeySpec(ByteArray(32) { fill }, "AES")

        /**
         * Mirror of CryptoManager.buildSignedData (private) — replicated here
         * to produce reference signatures without accessing Keystore.
         * Format: ciphertextUTF8 || conversationIdUTF8 || createdAt_bigEndian8
         */
        private fun buildSignedData(ct: String, convId: String, ts: Long): ByteArray {
            val ctB = ct.toByteArray(Charsets.UTF_8)
            val cB = convId.toByteArray(Charsets.UTF_8)
            val tsB = ByteArray(8) { i -> ((ts shr ((7 - i) * 8)) and 0xFF).toByte() }
            return ctB + cB + tsB
        }

        private fun signWithBc(seed: ByteArray, ct: String, convId: String, ts: Long): String {
            val priv = Ed25519PrivateKeyParameters(seed, 0)
            val data = buildSignedData(ct, convId, ts)
            val signer = Ed25519Signer()
            signer.init(true, priv)
            signer.update(data, 0, data.size)
            return Base64.encodeToString(signer.generateSignature(), Base64.NO_WRAP)
        }

        private fun pubKeyBase64(seed: ByteArray): String {
            val priv = Ed25519PrivateKeyParameters(seed, 0)
            return Base64.encodeToString(priv.generatePublicKey().encoded, Base64.NO_WRAP)
        }
    }

    // =========================================================================
    // HMAC-SHA256
    // =========================================================================

    @Test
    fun `hmacSha256 RFC4231 TC1 vector`() {
        val result = CryptoManager.hmacSha256(
            ByteArray(20) { 0x0b.toByte() },
            "Hi There".toByteArray(Charsets.UTF_8)
        )
        assertEquals(
            "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
            result.joinToString("") { "%02x".format(it) }
        )
    }

    @Test
    fun `hmacSha256 output is always 32 bytes`() {
        assertEquals(32, CryptoManager.hmacSha256(ByteArray(32), "x".toByteArray()).size)
    }

    @Test
    fun `hmacSha256 is deterministic`() {
        val key = ByteArray(32) { 0x55.toByte() }
        val data = "fialka".toByteArray(Charsets.UTF_8)
        assertArrayEquals(CryptoManager.hmacSha256(key, data), CryptoManager.hmacSha256(key, data))
    }

    @Test
    fun `hmacSha256 different keys produce different MACs`() {
        val data = "same data".toByteArray(Charsets.UTF_8)
        assertFalse(
            CryptoManager.hmacSha256(ByteArray(32) { 0x01 }, data)
                .contentEquals(CryptoManager.hmacSha256(ByteArray(32) { 0x02 }, data))
        )
    }

    // =========================================================================
    // AES-256-GCM
    // =========================================================================

    @Test
    fun `AES encrypt then decrypt recovers plaintext`() {
        val key = testKey()
        val pt = "Hello Fialka — secret"
        assertEquals(pt, CryptoManager.decrypt(CryptoManager.encrypt(pt, key), key))
    }

    @Test
    fun `AES encrypt produces different IV each time`() {
        val key = testKey()
        assertNotEquals(CryptoManager.encrypt("test", key).iv, CryptoManager.encrypt("test", key).iv)
    }

    @Test(expected = Exception::class)
    fun `AES decrypt with wrong key throws`() {
        CryptoManager.decrypt(CryptoManager.encrypt("secret", testKey(0x11)), testKey(0x22))
    }

    @Test
    fun `AES padding short text bucket is 256 bytes`() {
        // payloadSize = 2 + 2 = 4 → bucket 256 → ciphertext = 256 + 16 tag = 272 bytes
        assertEquals(272, Base64.decode(CryptoManager.encrypt("hi", testKey()).ciphertext, Base64.NO_WRAP).size)
    }

    @Test
    fun `AES padding medium text bucket is 1024 bytes`() {
        // 300 chars > 254 (256-2) → bucket 1024 → ciphertext = 1024 + 16 = 1040 bytes
        assertEquals(1040, Base64.decode(CryptoManager.encrypt("A".repeat(300), testKey()).ciphertext, Base64.NO_WRAP).size)
    }

    @Test
    fun `AES padding large text bucket is 4096 bytes`() {
        // 1100 chars > 1022 (1024-2) → bucket 4096 → ciphertext = 4096 + 16 = 4112 bytes
        assertEquals(4112, Base64.decode(CryptoManager.encrypt("B".repeat(1100), testKey()).ciphertext, Base64.NO_WRAP).size)
    }

    @Test
    fun `AES roundtrip empty string`() {
        assertEquals("", CryptoManager.decrypt(CryptoManager.encrypt("", testKey()), testKey()))
    }

    @Test
    fun `AES roundtrip unicode`() {
        val text = "Fialka 🔐🛡️ — post-quantum"
        assertEquals(text, CryptoManager.decrypt(CryptoManager.encrypt(text, testKey()), testKey()))
    }

    // =========================================================================
    // ChaCha20-Poly1305
    // =========================================================================

    @Test
    fun `ChaCha20 encrypt then decrypt recovers plaintext`() {
        val key = testKey(0x77)
        val text = "ChaCha20-Poly1305 test"
        assertEquals(text, CryptoManager.decryptChaCha(CryptoManager.encryptChaCha(text, key), key))
    }

    @Test
    fun `ChaCha20 produces different nonce each time`() {
        val key = testKey(0x33)
        assertNotEquals(CryptoManager.encryptChaCha("x", key).iv, CryptoManager.encryptChaCha("x", key).iv)
    }

    @Test(expected = Exception::class)
    fun `ChaCha20 decrypt with wrong key throws`() {
        CryptoManager.decryptChaCha(CryptoManager.encryptChaCha("secret", testKey(0x55)), testKey(0x66))
    }

    @Test
    fun `ChaCha20 and AES produce different ciphertexts`() {
        val key = testKey()
        assertNotEquals(CryptoManager.encrypt("same", key).ciphertext, CryptoManager.encryptChaCha("same", key).ciphertext)
    }

    // =========================================================================
    // File encryption
    // =========================================================================

    @Test
    fun `encryptFile then decryptFile recovers bytes`() {
        val bytes = ByteArray(1024) { (it and 0xFF).toByte() }
        val r = CryptoManager.encryptFile(bytes)
        assertArrayEquals(bytes, CryptoManager.decryptFile(r.encryptedBytes, r.keyBase64, r.ivBase64))
    }

    @Test
    fun `encryptFile generates unique key per call`() {
        val bytes = ByteArray(64) { 0x01 }
        val r1 = CryptoManager.encryptFile(bytes)
        val r2 = CryptoManager.encryptFile(bytes)
        assertNotEquals(r1.keyBase64, r2.keyBase64)
        assertNotEquals(r1.ivBase64, r2.ivBase64)
    }

    @Test
    fun `encryptFile key is 32 bytes`() {
        assertEquals(32, Base64.decode(CryptoManager.encryptFile(ByteArray(64)).keyBase64, Base64.NO_WRAP).size)
    }

    @Test(expected = Exception::class)
    fun `decryptFile with wrong key throws`() {
        val r = CryptoManager.encryptFile(ByteArray(128) { 0x42 })
        CryptoManager.decryptFile(r.encryptedBytes, Base64.encodeToString(ByteArray(32), Base64.NO_WRAP), r.ivBase64)
    }

    // =========================================================================
    // Ed25519 — verifySignature (pure: no Keystore needed)
    // =========================================================================

    @Test
    fun `verifySignature accepts valid BC-generated signature`() {
        val seed = ByteArray(32) { 0xAB.toByte() }
        val ct = "base64ct=="; val conv = "conv-uuid"; val ts = 1700000000000L
        assertTrue(CryptoManager.verifySignature(pubKeyBase64(seed), ct, conv, ts, signWithBc(seed, ct, conv, ts)))
    }

    @Test
    fun `verifySignature fails for tampered ciphertext`() {
        val seed = ByteArray(32) { 0x11.toByte() }
        val ct = "ct=="; val conv = "conv"; val ts = 1700000000001L
        assertFalse(CryptoManager.verifySignature(pubKeyBase64(seed), "tampered==", conv, ts, signWithBc(seed, ct, conv, ts)))
    }

    @Test
    fun `verifySignature fails for tampered conversationId`() {
        val seed = ByteArray(32) { 0x22.toByte() }
        val ct = "ct"; val conv = "real"; val ts = 1700000000002L
        assertFalse(CryptoManager.verifySignature(pubKeyBase64(seed), ct, "fake", ts, signWithBc(seed, ct, conv, ts)))
    }

    @Test
    fun `verifySignature fails for tampered timestamp`() {
        val seed = ByteArray(32) { 0x33.toByte() }
        val ct = "ct"; val conv = "conv"; val ts = 1700000000003L
        assertFalse(CryptoManager.verifySignature(pubKeyBase64(seed), ct, conv, ts + 1, signWithBc(seed, ct, conv, ts)))
    }

    @Test
    fun `verifySignature fails for wrong public key`() {
        val seed1 = ByteArray(32) { 0x44.toByte() }
        val seed2 = ByteArray(32) { 0x55.toByte() }
        val ct = "ct"; val conv = "conv"; val ts = 1700000000004L
        assertFalse(CryptoManager.verifySignature(pubKeyBase64(seed2), ct, conv, ts, signWithBc(seed1, ct, conv, ts)))
    }

    @Test
    fun `verifySignature fails for corrupted signature`() {
        val seed = ByteArray(32) { 0x66.toByte() }
        val ct = "ct"; val conv = "conv"; val ts = 1700000000005L
        val sigBytes = Base64.decode(signWithBc(seed, ct, conv, ts), Base64.NO_WRAP)
        sigBytes[0] = (sigBytes[0].toInt() xor 0xFF).toByte()
        assertFalse(CryptoManager.verifySignature(pubKeyBase64(seed), ct, conv, ts, Base64.encodeToString(sigBytes, Base64.NO_WRAP)))
    }

    @Test
    fun `verifySignature returns false for garbage signature`() {
        assertFalse(CryptoManager.verifySignature(pubKeyBase64(ByteArray(32) { 0x77.toByte() }), "ct", "conv", 0L, "not-base64!!!"))
    }

    // =========================================================================
    // X25519 Ephemeral DH
    // =========================================================================

    @Test
    fun `generateEphemeralKeyPair returns distinct keypairs`() {
        val kp1 = CryptoManager.generateEphemeralKeyPair()
        val kp2 = CryptoManager.generateEphemeralKeyPair()
        assertNotEquals(kp1.publicKeyBase64, kp2.publicKeyBase64)
    }

    @Test
    fun `performEphemeralKeyAgreement DH is commutative`() {
        val kpA = CryptoManager.generateEphemeralKeyPair()
        val kpB = CryptoManager.generateEphemeralKeyPair()
        assertArrayEquals(
            CryptoManager.performEphemeralKeyAgreement(kpA.privateKeyBase64, kpB.publicKeyBase64),
            CryptoManager.performEphemeralKeyAgreement(kpB.privateKeyBase64, kpA.publicKeyBase64)
        )
    }

    @Test
    fun `performEphemeralKeyAgreement returns 32 bytes`() {
        val kpA = CryptoManager.generateEphemeralKeyPair()
        val kpB = CryptoManager.generateEphemeralKeyPair()
        assertEquals(32, CryptoManager.performEphemeralKeyAgreement(kpA.privateKeyBase64, kpB.publicKeyBase64).size)
    }

    @Test
    fun `performEphemeralKeyAgreement different peers yield different secrets`() {
        val kpA = CryptoManager.generateEphemeralKeyPair()
        val kpB = CryptoManager.generateEphemeralKeyPair()
        val kpC = CryptoManager.generateEphemeralKeyPair()
        assertFalse(
            CryptoManager.performEphemeralKeyAgreement(kpA.privateKeyBase64, kpB.publicKeyBase64)
                .contentEquals(CryptoManager.performEphemeralKeyAgreement(kpA.privateKeyBase64, kpC.publicKeyBase64))
        )
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    @Test
    fun `deriveConversationId is symmetric`() {
        assertEquals(
            CryptoManager.deriveConversationId("A", "B"),
            CryptoManager.deriveConversationId("B", "A")
        )
    }

    @Test
    fun `deriveConversationId different inputs yield different IDs`() {
        assertNotEquals(CryptoManager.deriveConversationId("A", "B"), CryptoManager.deriveConversationId("A", "C"))
    }

    @Test
    fun `deriveConversationId returns 64-char lowercase hex`() {
        val id = CryptoManager.deriveConversationId("foo", "bar")
        assertEquals(64, id.length)
        assertTrue(id.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `getSharedFingerprint is symmetric`() {
        val k1 = Base64.encodeToString(ByteArray(44) { 0x11.toByte() }, Base64.NO_WRAP)
        val k2 = Base64.encodeToString(ByteArray(44) { 0x22.toByte() }, Base64.NO_WRAP)
        assertEquals(CryptoManager.getSharedFingerprint(k1, k2), CryptoManager.getSharedFingerprint(k2, k1))
    }

    @Test
    fun `getSharedFingerprint returns 4 space-separated groups`() {
        val k1 = Base64.encodeToString(ByteArray(44) { 0x33.toByte() }, Base64.NO_WRAP)
        val k2 = Base64.encodeToString(ByteArray(44) { 0x44.toByte() }, Base64.NO_WRAP)
        assertEquals(4, CryptoManager.getSharedFingerprint(k1, k2).split(" ").size)
    }

    @Test
    fun `getSharedFingerprintHex is symmetric and 64 hex chars`() {
        val k1 = Base64.encodeToString(ByteArray(44) { 0x55.toByte() }, Base64.NO_WRAP)
        val k2 = Base64.encodeToString(ByteArray(44) { 0x66.toByte() }, Base64.NO_WRAP)
        val hex1 = CryptoManager.getSharedFingerprintHex(k1, k2)
        val hex2 = CryptoManager.getSharedFingerprintHex(k2, k1)
        assertEquals(hex1, hex2)
        assertEquals(64, hex1.length)
        assertTrue(hex1.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `getSharedFingerprintHex different pairs yield different results`() {
        val k1 = Base64.encodeToString(ByteArray(44) { 0x11.toByte() }, Base64.NO_WRAP)
        val k2 = Base64.encodeToString(ByteArray(44) { 0x22.toByte() }, Base64.NO_WRAP)
        val k3 = Base64.encodeToString(ByteArray(44) { 0x33.toByte() }, Base64.NO_WRAP)
        assertNotEquals(CryptoManager.getSharedFingerprintHex(k1, k2), CryptoManager.getSharedFingerprintHex(k1, k3))
    }

    // =========================================================================
    // Tor .onion derivation
    // =========================================================================

    @Test
    fun `computeOnionFromEd25519 returns valid 62-char onion address`() {
        val onion = CryptoManager.computeOnionFromEd25519(ByteArray(32) { (it + 1).toByte() })
        assertEquals(62, onion.length)
        assertTrue(onion.endsWith(".onion"))
        assertTrue(onion.substring(0, 56).matches(Regex("[a-z2-7]{56}")))
    }

    @Test
    fun `computeOnionFromEd25519 is deterministic`() {
        val pub = ByteArray(32) { 0x55.toByte() }
        assertEquals(CryptoManager.computeOnionFromEd25519(pub), CryptoManager.computeOnionFromEd25519(pub))
    }

    @Test
    fun `computeOnionFromEd25519 different keys different addresses`() {
        assertNotEquals(
            CryptoManager.computeOnionFromEd25519(ByteArray(32) { 0x01 }),
            CryptoManager.computeOnionFromEd25519(ByteArray(32) { 0x02 })
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `computeOnionFromEd25519 rejects non-32-byte key`() {
        CryptoManager.computeOnionFromEd25519(ByteArray(31))
    }

    // =========================================================================
    // Ed25519 → X25519 birational map
    // =========================================================================

    @Test
    fun `ed25519PublicKeyToX25519Raw returns 32 bytes`() {
        val seed = ByteArray(32) { 0x11.toByte() }
        val pub = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        assertEquals(32, CryptoManager.ed25519PublicKeyToX25519Raw(pub).size)
    }

    @Test
    fun `ed25519PublicKeyToX25519Raw is deterministic`() {
        val seed = ByteArray(32) { 0x22.toByte() }
        val pub = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        assertArrayEquals(CryptoManager.ed25519PublicKeyToX25519Raw(pub), CryptoManager.ed25519PublicKeyToX25519Raw(pub))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ed25519PublicKeyToX25519Raw rejects non-32-byte key`() {
        CryptoManager.ed25519PublicKeyToX25519Raw(ByteArray(33))
    }

    @Test
    fun `ed25519PublicKeyToX25519 returns 44-byte X509 public key`() {
        val seed = ByteArray(32) { 0x33.toByte() }
        val pub = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded
        assertEquals(44, Base64.decode(CryptoManager.ed25519PublicKeyToX25519(pub), Base64.NO_WRAP).size)
    }

    // =========================================================================
    // isValidPublicKey
    // =========================================================================

    @Test
    fun `isValidPublicKey accepts ephemeral public key`() {
        assertTrue(CryptoManager.isValidPublicKey(CryptoManager.generateEphemeralKeyPair().publicKeyBase64))
    }

    @Test
    fun `isValidPublicKey rejects garbage`() {
        assertFalse(CryptoManager.isValidPublicKey("not-a-key"))
        assertFalse(CryptoManager.isValidPublicKey(""))
        assertFalse(CryptoManager.isValidPublicKey(Base64.encodeToString(ByteArray(10), Base64.NO_WRAP)))
    }
}
