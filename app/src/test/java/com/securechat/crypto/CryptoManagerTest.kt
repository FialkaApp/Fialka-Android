package com.securechat.crypto

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import java.util.Base64

/**
 * Unit tests for CryptoManager logic (pure JVM, no Android Keystore).
 *
 * Tests the core cryptographic operations:
 *  1. ECDH key agreement produces shared secrets
 *  2. HKDF key derivation
 *  3. AES-256-GCM encrypt/decrypt roundtrip
 *  4. Conversation ID derivation (deterministic + commutative)
 *  5. ECDSA signature sign/verify
 */
@RunWith(JUnit4::class)
class CryptoManagerTest {

    private val secureRandom = SecureRandom()

    // ========================================================================
    // Helper: JVM-compatible crypto (mirrors CryptoManager without Keystore)
    // ========================================================================

    private fun generateECKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    private fun deriveAesKey(sharedSecret: ByteArray): SecretKeySpec {
        val salt = ByteArray(32)
        val prk = hmacSha256(salt, sharedSecret)
        val info = "SecureChat-v1-message-key".toByteArray(Charsets.UTF_8)
        val expandInput = ByteArray(info.size + 1)
        System.arraycopy(info, 0, expandInput, 0, info.size)
        expandInput[expandInput.size - 1] = 0x01
        val okm = hmacSha256(prk, expandInput)
        return SecretKeySpec(okm, 0, 32, "AES")
    }

    private fun encryptAesGcm(plaintext: ByteArray, key: SecretKeySpec): Pair<ByteArray, ByteArray> {
        val iv = ByteArray(12)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return Pair(cipher.doFinal(plaintext), iv)
    }

    private fun decryptAesGcm(ciphertext: ByteArray, iv: ByteArray, key: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun deriveConversationId(pubKeyA: String, pubKeyB: String): String {
        val sorted = listOf(pubKeyA, pubKeyB).sorted()
        val combined = (sorted[0] + sorted[1]).toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(combined)
        return digest.joinToString("") { "%02x".format(it) }
    }

    // ========================================================================
    // TESTS: ECDH Key Agreement
    // ========================================================================

    @Test
    fun `ECDH produces same shared secret on both sides`() {
        val alice = generateECKeyPair()
        val bob = generateECKeyPair()

        // Alice computes shared secret with Bob's public key
        val kaAlice = KeyAgreement.getInstance("ECDH")
        kaAlice.init(alice.private)
        kaAlice.doPhase(bob.public, true)
        val secretAlice = kaAlice.generateSecret()

        // Bob computes shared secret with Alice's public key
        val kaBob = KeyAgreement.getInstance("ECDH")
        kaBob.init(bob.private)
        kaBob.doPhase(alice.public, true)
        val secretBob = kaBob.generateSecret()

        assertArrayEquals("ECDH shared secret should match", secretAlice, secretBob)
    }

    @Test
    fun `ECDH with different partners produces different secrets`() {
        val alice = generateECKeyPair()
        val bob = generateECKeyPair()
        val eve = generateECKeyPair()

        val kaAliceBob = KeyAgreement.getInstance("ECDH")
        kaAliceBob.init(alice.private)
        kaAliceBob.doPhase(bob.public, true)
        val secretAliceBob = kaAliceBob.generateSecret()

        val kaAliceEve = KeyAgreement.getInstance("ECDH")
        kaAliceEve.init(alice.private)
        kaAliceEve.doPhase(eve.public, true)
        val secretAliceEve = kaAliceEve.generateSecret()

        assertFalse(
            "Different ECDH partners should produce different secrets",
            secretAliceBob.contentEquals(secretAliceEve)
        )
    }

    // ========================================================================
    // TESTS: AES-256-GCM Encryption
    // ========================================================================

    @Test
    fun `AES-GCM encrypt-decrypt roundtrip`() {
        val sharedSecret = ByteArray(32) { it.toByte() }
        val key = deriveAesKey(sharedSecret)

        val plaintext = "Hello, SecureChat! 🔐"
        val (ciphertext, iv) = encryptAesGcm(plaintext.toByteArray(), key)
        val decrypted = decryptAesGcm(ciphertext, iv, key)

        assertEquals(plaintext, String(decrypted))
    }

    @Test
    fun `AES-GCM with wrong key fails`() {
        val key1 = deriveAesKey(ByteArray(32) { 0x01 })
        val key2 = deriveAesKey(ByteArray(32) { 0x02 })

        val plaintext = "Secret message"
        val (ciphertext, iv) = encryptAesGcm(plaintext.toByteArray(), key1)

        try {
            decryptAesGcm(ciphertext, iv, key2)
            fail("Decryption with wrong key should throw")
        } catch (e: Exception) {
            // Expected: AEADBadTagException
        }
    }

    @Test
    fun `AES-GCM ciphertext differs from plaintext`() {
        val key = deriveAesKey(ByteArray(32) { 0xAA.toByte() })
        val plaintext = "This is a test message"
        val (ciphertext, _) = encryptAesGcm(plaintext.toByteArray(), key)

        assertFalse(
            "Ciphertext should differ from plaintext",
            ciphertext.contentEquals(plaintext.toByteArray())
        )
    }

    @Test
    fun `AES-GCM each encryption produces different ciphertext (random IV)`() {
        val key = deriveAesKey(ByteArray(32) { 0xBB.toByte() })
        val plaintext = "Same plaintext"

        val (ct1, iv1) = encryptAesGcm(plaintext.toByteArray(), key)
        val (ct2, iv2) = encryptAesGcm(plaintext.toByteArray(), key)

        assertFalse("IVs should differ", iv1.contentEquals(iv2))
        // Ciphertext will differ because IVs differ
        assertFalse("Ciphertexts should differ", ct1.contentEquals(ct2))
    }

    @Test
    fun `AES-GCM handles empty plaintext`() {
        val key = deriveAesKey(ByteArray(32) { 0xCC.toByte() })
        val (ciphertext, iv) = encryptAesGcm(ByteArray(0), key)
        val decrypted = decryptAesGcm(ciphertext, iv, key)
        assertEquals(0, decrypted.size)
    }

    @Test
    fun `AES-GCM handles large message`() {
        val key = deriveAesKey(ByteArray(32) { 0xDD.toByte() })
        val plaintext = "A".repeat(100_000)
        val (ciphertext, iv) = encryptAesGcm(plaintext.toByteArray(), key)
        val decrypted = String(decryptAesGcm(ciphertext, iv, key))
        assertEquals(plaintext, decrypted)
    }

    // ========================================================================
    // TESTS: Conversation ID
    // ========================================================================

    @Test
    fun `conversation ID is deterministic`() {
        val keyA = "pubKeyAlice123"
        val keyB = "pubKeyBob456"

        val id1 = deriveConversationId(keyA, keyB)
        val id2 = deriveConversationId(keyA, keyB)

        assertEquals("Same keys should produce same conversation ID", id1, id2)
    }

    @Test
    fun `conversation ID is commutative`() {
        val keyA = "pubKeyAlice123"
        val keyB = "pubKeyBob456"

        val idAB = deriveConversationId(keyA, keyB)
        val idBA = deriveConversationId(keyB, keyA)

        assertEquals("Conversation ID should be order-independent", idAB, idBA)
    }

    @Test
    fun `conversation ID is hex and 64 chars (SHA-256)`() {
        val id = deriveConversationId("key1", "key2")

        assertEquals("SHA-256 hex should be 64 chars", 64, id.length)
        assertTrue("Should be valid hex", id.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `different key pairs produce different conversation IDs`() {
        val id1 = deriveConversationId("keyA", "keyB")
        val id2 = deriveConversationId("keyA", "keyC")

        assertNotEquals("Different keys should produce different IDs", id1, id2)
    }

    // ========================================================================
    // TESTS: ECDSA Signature (JVM-level, mirrors CryptoManager logic)
    // ========================================================================

    @Test
    fun `ECDSA sign and verify roundtrip`() {
        val keyPair = generateECKeyPair()
        val plaintext = "Hello, signed message!"
        val messageIndex = 42

        val dataToSign = (plaintext + messageIndex.toString()).toByteArray()

        val signer = java.security.Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(dataToSign)
        val signature = signer.sign()

        val verifier = java.security.Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(keyPair.public)
        verifier.update(dataToSign)

        assertTrue("Valid signature should verify", verifier.verify(signature))
    }

    @Test
    fun `ECDSA signature fails with wrong public key`() {
        val alice = generateECKeyPair()
        val eve = generateECKeyPair()
        val data = "message0".toByteArray()

        val signer = java.security.Signature.getInstance("SHA256withECDSA")
        signer.initSign(alice.private)
        signer.update(data)
        val signature = signer.sign()

        // Verify with Eve's key — should fail
        val verifier = java.security.Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(eve.public)
        verifier.update(data)

        assertFalse("Signature should fail with wrong key", verifier.verify(signature))
    }

    @Test
    fun `ECDSA signature fails with tampered data`() {
        val keyPair = generateECKeyPair()
        val original = "original message42".toByteArray()

        val signer = java.security.Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(original)
        val signature = signer.sign()

        // Tamper with data
        val tampered = "tampered message42".toByteArray()
        val verifier = java.security.Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(keyPair.public)
        verifier.update(tampered)

        assertFalse("Tampered data should fail verification", verifier.verify(signature))
    }

    @Test
    fun `ECDSA public key survives X509 encoding roundtrip`() {
        val keyPair = generateECKeyPair()
        val encoded = keyPair.public.encoded

        val keyFactory = KeyFactory.getInstance("EC")
        val decoded = keyFactory.generatePublic(X509EncodedKeySpec(encoded))

        assertEquals("Key should survive encoding roundtrip", keyPair.public, decoded)
    }

    // ========================================================================
    // TESTS: HKDF Key Derivation
    // ========================================================================

    @Test
    fun `HKDF produces 32-byte keys`() {
        val key = deriveAesKey(ByteArray(32) { 0x55 })
        assertEquals("AES-256 key should be 32 bytes", 32, key.encoded.size)
        assertEquals("Algorithm should be AES", "AES", key.algorithm)
    }

    @Test
    fun `HKDF is deterministic`() {
        val secret = ByteArray(32) { 0x77 }
        val key1 = deriveAesKey(secret.copyOf())
        val key2 = deriveAesKey(secret.copyOf())
        assertArrayEquals("Same input should produce same key", key1.encoded, key2.encoded)
    }

    @Test
    fun `HKDF different inputs produce different keys`() {
        val key1 = deriveAesKey(ByteArray(32) { 0x01 })
        val key2 = deriveAesKey(ByteArray(32) { 0x02 })
        assertFalse("Different inputs should produce different keys", key1.encoded.contentEquals(key2.encoded))
    }
}
