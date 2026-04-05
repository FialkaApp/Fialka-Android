/*
 * Fialka — Post-quantum encrypted messenger
 * Instrumented tests for CryptoManager (AES-GCM, ChaCha20, ML-KEM, ML-DSA, Ed25519)
 */
package com.fialkaapp.fialka.crypto

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CryptoManagerTest {

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CryptoManager.init(context)
        // Ensure identity exists for tests
        if (!CryptoManager.hasIdentity()) {
            CryptoManager.generateIdentity()
        }
    }

    // ========================================================================
    // Identity generation
    // ========================================================================

    @Test
    fun identityIsGenerated() {
        assertTrue(CryptoManager.hasIdentity())
        assertNotNull(CryptoManager.getPublicKey())
        assertNotNull(CryptoManager.getAccountId())
    }

    @Test
    fun publicKeyIsBase64_32Bytes() {
        val pubKey = CryptoManager.getPublicKey()!!
        val decoded = Base64.decode(pubKey, Base64.NO_WRAP)
        // X25519 encoded public key (X.509 SubjectPublicKeyInfo)
        assertTrue(decoded.size >= 32)
    }

    @Test
    fun accountIdIsBase58() {
        val accountId = CryptoManager.getAccountId()!!
        assertTrue(accountId.isNotEmpty())
        // Base58 chars only
        assertTrue(accountId.all { it in "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz" })
    }

    @Test
    fun seedIsReproducible() {
        val seed = CryptoManager.getIdentitySeed()
        assertEquals(32, seed.size)

        // Restore from same seed should give same public key
        val originalPub = CryptoManager.getPublicKey()
        CryptoManager.deleteIdentityKey()
        val restoredPub = CryptoManager.restoreFromSeed(seed.copyOf())
        assertEquals(originalPub, restoredPub)
    }

    // ========================================================================
    // AES-256-GCM encrypt / decrypt
    // ========================================================================

    @Test
    fun aesGcmRoundTrip() {
        val key = CryptoManager.deriveSymmetricKey(ByteArray(32) { 0x42 })
        val plaintext = "Hello Fialka 🔐"

        val encrypted = CryptoManager.encrypt(plaintext, key)
        val decrypted = CryptoManager.decrypt(encrypted, key)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun aesGcmDifferentIvEachTime() {
        val key = CryptoManager.deriveSymmetricKey(ByteArray(32) { 0x01 })

        val enc1 = CryptoManager.encrypt("test", key)
        val enc2 = CryptoManager.encrypt("test", key)

        assertNotEquals(enc1.iv, enc2.iv)
        assertNotEquals(enc1.ciphertext, enc2.ciphertext)
    }

    @Test
    fun aesGcmWrongKeyFails() {
        val key1 = CryptoManager.deriveSymmetricKey(ByteArray(32) { 0x01 })
        val key2 = CryptoManager.deriveSymmetricKey(ByteArray(32) { 0x02 })

        val encrypted = CryptoManager.encrypt("secret", key1)

        try {
            CryptoManager.decrypt(encrypted, key2)
            fail("Should have thrown on wrong key")
        } catch (_: Exception) {
            // Expected
        }
    }

    @Test
    fun aesGcmPaddingIsApplied() {
        val key = CryptoManager.deriveSymmetricKey(ByteArray(32) { 0x03 })

        // Short message should be padded to 256 bucket + GCM tag
        val enc1 = CryptoManager.encrypt("hi", key)
        // Longer message
        val enc2 = CryptoManager.encrypt("x".repeat(200), key)

        val ct1 = Base64.decode(enc1.ciphertext, Base64.NO_WRAP)
        val ct2 = Base64.decode(enc2.ciphertext, Base64.NO_WRAP)

        // Both should be same size (256 bucket + 16 GCM tag)
        assertEquals(ct1.size, ct2.size)
    }

    @Test
    fun aesGcmEmptyStringRoundTrip() {
        val key = CryptoManager.deriveSymmetricKey(ByteArray(32) { 0x04 })
        val encrypted = CryptoManager.encrypt("", key)
        val decrypted = CryptoManager.decrypt(encrypted, key)
        assertEquals("", decrypted)
    }

    @Test
    fun aesGcmUnicodeRoundTrip() {
        val key = CryptoManager.deriveSymmetricKey(ByteArray(32) { 0x05 })
        val unicode = "Привет мир 🇫🇷🇺🇦 日本語 العربية"
        val encrypted = CryptoManager.encrypt(unicode, key)
        assertEquals(unicode, CryptoManager.decrypt(encrypted, key))
    }

    // ========================================================================
    // ChaCha20-Poly1305 encrypt / decrypt
    // ========================================================================

    @Test
    fun chaChaRoundTrip() {
        val key = CryptoManager.deriveSymmetricKey(ByteArray(32) { 0x10 })
        val plaintext = "ChaCha20 test 🛡️"

        val encrypted = CryptoManager.encryptChaCha(plaintext, key)
        val decrypted = CryptoManager.decryptChaCha(encrypted, key)

        assertEquals(plaintext, decrypted)
    }

    @Test
    fun chaChaWrongKeyFails() {
        val key1 = CryptoManager.deriveSymmetricKey(ByteArray(32) { 0x11 })
        val key2 = CryptoManager.deriveSymmetricKey(ByteArray(32) { 0x12 })

        val encrypted = CryptoManager.encryptChaCha("secret", key1)

        try {
            CryptoManager.decryptChaCha(encrypted, key2)
            fail("Should have thrown on wrong key")
        } catch (_: Exception) {
            // Expected
        }
    }

    // ========================================================================
    // X25519 ephemeral DH
    // ========================================================================

    @Test
    fun ephemeralKeyPairGeneration() {
        val kp = CryptoManager.generateEphemeralKeyPair()
        assertTrue(kp.publicKeyBase64.isNotEmpty())
        assertTrue(kp.privateKeyBase64.isNotEmpty())
        assertNotEquals(kp.publicKeyBase64, kp.privateKeyBase64)
    }

    @Test
    fun ephemeralDhAgreement() {
        val kpA = CryptoManager.generateEphemeralKeyPair()
        val kpB = CryptoManager.generateEphemeralKeyPair()

        val ssA = CryptoManager.performEphemeralKeyAgreement(kpA.privateKeyBase64, kpB.publicKeyBase64)
        val ssB = CryptoManager.performEphemeralKeyAgreement(kpB.privateKeyBase64, kpA.publicKeyBase64)

        assertArrayEquals("DH shared secret must be equal on both sides", ssA, ssB)
        assertEquals(32, ssA.size)
    }

    @Test
    fun differentEphemeralKeysDifferentSecrets() {
        val kpA = CryptoManager.generateEphemeralKeyPair()
        val kpB = CryptoManager.generateEphemeralKeyPair()
        val kpC = CryptoManager.generateEphemeralKeyPair()

        val ssAB = CryptoManager.performEphemeralKeyAgreement(kpA.privateKeyBase64, kpB.publicKeyBase64)
        val ssAC = CryptoManager.performEphemeralKeyAgreement(kpA.privateKeyBase64, kpC.publicKeyBase64)

        assertFalse(ssAB.contentEquals(ssAC))
    }

    // ========================================================================
    // ML-KEM-1024 encapsulation / decapsulation
    // ========================================================================

    @Test
    fun mlKemEncapsDecapsRoundTrip() {
        val mlkemPub = CryptoManager.getMLKEMPublicKey()
        assertNotNull("ML-KEM public key should exist after identity init", mlkemPub)

        val (ciphertextB64, sharedSecretEncaps) = CryptoManager.mlkemEncaps(mlkemPub!!)
        val sharedSecretDecaps = CryptoManager.mlkemDecaps(ciphertextB64)

        assertArrayEquals("ML-KEM shared secrets must match", sharedSecretEncaps, sharedSecretDecaps)
        assertEquals(32, sharedSecretEncaps.size)
    }

    @Test
    fun mlKemDifferentEncapsulationsProduceDifferentSecrets() {
        val mlkemPub = CryptoManager.getMLKEMPublicKey()!!

        val (_, ss1) = CryptoManager.mlkemEncaps(mlkemPub)
        val (_, ss2) = CryptoManager.mlkemEncaps(mlkemPub)

        assertFalse("Different encapsulations should produce different secrets", ss1.contentEquals(ss2))
    }

    // ========================================================================
    // ML-DSA-44 sign / verify
    // ========================================================================

    @Test
    fun mlDsaSignVerifyRoundTrip() {
        val data = "handshake data for PQ auth".toByteArray(Charsets.UTF_8)
        val signature = CryptoManager.signHandshakeMlDsa44(data)

        assertTrue(signature.isNotEmpty())

        // Get our own ML-DSA public key to verify
        val mldsaPub = CryptoManager.getMlDsaPublicKey()
        assertNotNull(mldsaPub)

        val valid = CryptoManager.verifyHandshakeMlDsa44(mldsaPub!!, data, signature)
        assertTrue("ML-DSA-44 signature should be valid", valid)
    }

    @Test
    fun mlDsaRejectsTamperedData() {
        val data = "original data".toByteArray(Charsets.UTF_8)
        val signature = CryptoManager.signHandshakeMlDsa44(data)
        val mldsaPub = CryptoManager.getMlDsaPublicKey()!!

        val tamperedData = "tampered data".toByteArray(Charsets.UTF_8)
        val valid = CryptoManager.verifyHandshakeMlDsa44(mldsaPub, tamperedData, signature)
        assertFalse("ML-DSA-44 signature should be invalid for tampered data", valid)
    }

    // ========================================================================
    // Ed25519 sign / verify
    // ========================================================================

    @Test
    fun ed25519SignVerifyRoundTrip() {
        val ciphertext = "encrypted message content"
        val conversationId = "conv-test-123"
        val timestamp = System.currentTimeMillis()

        val signingPub = CryptoManager.getSigningPublicKeyBase64()
        assertTrue(signingPub.isNotEmpty())

        val signature = CryptoManager.signMessage(ciphertext, conversationId, timestamp)
        assertTrue(signature.isNotEmpty())

        val valid = CryptoManager.verifySignature(
            signingPub, ciphertext, conversationId, timestamp, signature
        )
        assertTrue("Ed25519 signature should be valid", valid)
    }

    @Test
    fun ed25519RejectsTamperedCiphertext() {
        val conversationId = "conv-test-456"
        val timestamp = System.currentTimeMillis()

        val signature = CryptoManager.signMessage("original", conversationId, timestamp)
        val signingPub = CryptoManager.getSigningPublicKeyBase64()

        val valid = CryptoManager.verifySignature(
            signingPub, "tampered", conversationId, timestamp, signature
        )
        assertFalse(valid)
    }

    @Test
    fun ed25519RejectsDifferentTimestamp() {
        val conversationId = "conv-test-789"
        val ciphertext = "test message"

        val signature = CryptoManager.signMessage(ciphertext, conversationId, 1000L)
        val signingPub = CryptoManager.getSigningPublicKeyBase64()

        val valid = CryptoManager.verifySignature(
            signingPub, ciphertext, conversationId, 2000L, signature
        )
        assertFalse(valid)
    }

    // ========================================================================
    // File encryption
    // ========================================================================

    @Test
    fun fileEncryptDecryptRoundTrip() {
        val fileBytes = "File content with binary data: \u0000\u0001\u0002".toByteArray()

        val result = CryptoManager.encryptFile(fileBytes)
        assertTrue(result.encryptedBytes.isNotEmpty())
        assertTrue(result.keyBase64.isNotEmpty())
        assertTrue(result.ivBase64.isNotEmpty())

        val decrypted = CryptoManager.decryptFile(result.encryptedBytes, result.keyBase64, result.ivBase64)
        assertArrayEquals(fileBytes, decrypted)
    }

    // ========================================================================
    // Key derivation
    // ========================================================================

    @Test
    fun deriveSymmetricKeyIs256Bits() {
        val key = CryptoManager.deriveSymmetricKey(ByteArray(32) { 0x01 })
        assertEquals("AES", key.algorithm)
        assertEquals(32, key.encoded.size)
    }

    @Test
    fun deriveSymmetricKeyIsDeterministic() {
        val ss1 = ByteArray(32) { 0x42 }
        val ss2 = ByteArray(32) { 0x42 }

        val key1 = CryptoManager.deriveSymmetricKey(ss1)
        val key2 = CryptoManager.deriveSymmetricKey(ss2)

        assertArrayEquals(key1.encoded, key2.encoded)
    }

    @Test
    fun differentSharedSecretsDifferentKeys() {
        val key1 = CryptoManager.deriveSymmetricKey(ByteArray(32) { 0x01 })
        val key2 = CryptoManager.deriveSymmetricKey(ByteArray(32) { 0x02 })

        assertFalse(key1.encoded.contentEquals(key2.encoded))
    }

    // ========================================================================
    // Shared fingerprint
    // ========================================================================

    @Test
    fun sharedFingerprintIsSymmetric() {
        val pubA = CryptoManager.getPublicKey()!!
        val kpB = CryptoManager.generateEphemeralKeyPair()

        val fp1 = CryptoManager.getSharedFingerprint(pubA, kpB.publicKeyBase64)
        val fp2 = CryptoManager.getSharedFingerprint(kpB.publicKeyBase64, pubA)

        assertEquals("Fingerprint must be same regardless of key order", fp1, fp2)
    }

    // ========================================================================
    // Onion address derivation
    // ========================================================================

    @Test
    fun onionAddressIs62CharsWithSuffix() {
        val onion = CryptoManager.getOnionAddress()
        assertTrue(onion.endsWith(".onion"))
        assertEquals(62, onion.length)  // 56 chars + ".onion"
    }

    @Test
    fun computeOnionFromEd25519IsDeterministic() {
        val pub = ByteArray(32) { 0x55.toByte() }
        assertEquals(CryptoManager.computeOnionFromEd25519(pub), CryptoManager.computeOnionFromEd25519(pub))
    }

    @Test
    fun computeOnionFromEd25519DifferentKeysYieldDifferentAddresses() {
        assertNotEquals(
            CryptoManager.computeOnionFromEd25519(ByteArray(32) { 0x01 }),
            CryptoManager.computeOnionFromEd25519(ByteArray(32) { 0x02 })
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun computeOnionFromEd25519RejectsNon32ByteKey() {
        CryptoManager.computeOnionFromEd25519(ByteArray(31))
    }

    // ========================================================================
    // HMAC-SHA256
    // ========================================================================

    @Test
    fun hmacSha256Rfc4231Tc1Vector() {
        // RFC 4231 Test Case 1
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
    fun hmacSha256OutputIs32Bytes() {
        assertEquals(32, CryptoManager.hmacSha256(ByteArray(32), "x".toByteArray()).size)
    }

    @Test
    fun hmacSha256IsDeterministic() {
        val key = ByteArray(32) { 0x55.toByte() }
        val data = "fialka".toByteArray(Charsets.UTF_8)
        assertArrayEquals(CryptoManager.hmacSha256(key, data), CryptoManager.hmacSha256(key, data))
    }

    @Test
    fun hmacSha256DifferentKeysProduceDifferentMacs() {
        val data = "same data".toByteArray(Charsets.UTF_8)
        assertFalse(
            CryptoManager.hmacSha256(ByteArray(32) { 0x01 }, data)
                .contentEquals(CryptoManager.hmacSha256(ByteArray(32) { 0x02 }, data))
        )
    }

    // ========================================================================
    // Conversation ID
    // ========================================================================

    @Test
    fun deriveConversationIdIsSymmetric() {
        assertEquals(
            CryptoManager.deriveConversationId("A", "B"),
            CryptoManager.deriveConversationId("B", "A")
        )
    }

    @Test
    fun deriveConversationIdDifferentInputsYieldDifferentIds() {
        assertNotEquals(
            CryptoManager.deriveConversationId("A", "B"),
            CryptoManager.deriveConversationId("A", "C")
        )
    }

    @Test
    fun deriveConversationIdReturns64CharHex() {
        val id = CryptoManager.deriveConversationId("foo", "bar")
        assertEquals(64, id.length)
        assertTrue(id.matches(Regex("[0-9a-f]{64}")))
    }

    // ========================================================================
    // Shared fingerprint (hex)
    // ========================================================================

    @Test
    fun sharedFingerprintHexIsSymmetricAnd64Chars() {
        val k1 = Base64.encodeToString(ByteArray(44) { 0x55.toByte() }, Base64.NO_WRAP)
        val k2 = Base64.encodeToString(ByteArray(44) { 0x66.toByte() }, Base64.NO_WRAP)
        val hex1 = CryptoManager.getSharedFingerprintHex(k1, k2)
        val hex2 = CryptoManager.getSharedFingerprintHex(k2, k1)
        assertEquals(hex1, hex2)
        assertEquals(64, hex1.length)
        assertTrue(hex1.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun sharedFingerprintHexDifferentPairs() {
        val k1 = Base64.encodeToString(ByteArray(44) { 0x11.toByte() }, Base64.NO_WRAP)
        val k2 = Base64.encodeToString(ByteArray(44) { 0x22.toByte() }, Base64.NO_WRAP)
        val k3 = Base64.encodeToString(ByteArray(44) { 0x33.toByte() }, Base64.NO_WRAP)
        assertNotEquals(
            CryptoManager.getSharedFingerprintHex(k1, k2),
            CryptoManager.getSharedFingerprintHex(k1, k3)
        )
    }

    // ========================================================================
    // Ed25519 → X25519 birational map
    // ========================================================================

    @Test
    fun ed25519ToX25519RawReturns32Bytes() {
        // Use the identity's signing public key (stored as raw 32 bytes, Base64-encoded)
        val edPubB64 = CryptoManager.getSigningPublicKeyBase64()
        val edPub = Base64.decode(edPubB64, Base64.NO_WRAP)
        assertEquals(32, CryptoManager.ed25519PublicKeyToX25519Raw(edPub).size)
    }

    @Test
    fun ed25519ToX25519RawIsDeterministic() {
        val edPubB64 = CryptoManager.getSigningPublicKeyBase64()
        val edPub = Base64.decode(edPubB64, Base64.NO_WRAP)
        assertArrayEquals(
            CryptoManager.ed25519PublicKeyToX25519Raw(edPub),
            CryptoManager.ed25519PublicKeyToX25519Raw(edPub)
        )
    }
}
