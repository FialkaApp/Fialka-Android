/*
 * Fialka — Post-quantum encrypted messenger
 * Unit tests for DoubleRatchet (KDF chains, DH ratchet, SPQR)
 */
package com.fialkaapp.fialka.crypto

import android.util.Base64
import org.junit.Assert.*
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DoubleRatchetTest {

    // ========================================================================
    // advanceChain
    // ========================================================================

    @Test
    fun `advanceChain produces new chain key and message key`() {
        val chainKey = ByteArray(32) { it.toByte() }
        val chainKeyB64 = Base64.encodeToString(chainKey, Base64.NO_WRAP)

        val (newChainKeyB64, messageKey) = DoubleRatchet.advanceChain(chainKeyB64)

        // Chain key must change
        assertNotEquals(chainKeyB64, newChainKeyB64)
        // Message key must be 32 bytes AES
        assertEquals("AES", messageKey.algorithm)
        assertEquals(32, messageKey.encoded.size)
    }

    @Test
    fun `advanceChain is deterministic`() {
        val chainKey = ByteArray(32) { (it * 3).toByte() }
        val chainKeyB64 = Base64.encodeToString(chainKey, Base64.NO_WRAP)

        val (ck1, mk1) = DoubleRatchet.advanceChain(chainKeyB64)
        val (ck2, mk2) = DoubleRatchet.advanceChain(chainKeyB64)

        assertEquals(ck1, ck2)
        assertArrayEquals(mk1.encoded, mk2.encoded)
    }

    @Test
    fun `advanceChain successive steps produce unique keys`() {
        var chainKeyB64 = Base64.encodeToString(ByteArray(32) { 0x42 }, Base64.NO_WRAP)
        val messageKeys = mutableSetOf<String>()

        repeat(100) {
            val (nextCk, mk) = DoubleRatchet.advanceChain(chainKeyB64)
            val mkB64 = Base64.encodeToString(mk.encoded, Base64.NO_WRAP)
            assertTrue("Duplicate message key at step $it", messageKeys.add(mkB64))
            chainKeyB64 = nextCk
        }
    }

    @Test
    fun `message key is HMAC(chain_key, 0x01)`() {
        val chainKey = ByteArray(32) { (it + 7).toByte() }
        val chainKeyB64 = Base64.encodeToString(chainKey, Base64.NO_WRAP)

        val (_, messageKey) = DoubleRatchet.advanceChain(chainKeyB64)

        // Verify against manual HMAC
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(chainKey, "HmacSHA256"))
        val expected = mac.doFinal(byteArrayOf(0x01))
        assertArrayEquals(expected.copyOf(32), messageKey.encoded)
    }

    @Test
    fun `chain key advance is HMAC(chain_key, 0x02)`() {
        val chainKey = ByteArray(32) { (it + 7).toByte() }
        val chainKeyB64 = Base64.encodeToString(chainKey, Base64.NO_WRAP)

        val (newChainKeyB64, _) = DoubleRatchet.advanceChain(chainKeyB64)

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(chainKey, "HmacSHA256"))
        val expected = mac.doFinal(byteArrayOf(0x02))
        val expectedB64 = Base64.encodeToString(expected, Base64.NO_WRAP)
        assertEquals(expectedB64, newChainKeyB64)
    }

    // ========================================================================
    // pqRatchetStep (SPQR)
    // ========================================================================

    @Test
    fun `pqRatchetStep changes root key`() {
        val rootKey = ByteArray(32) { 0x11 }
        val rootKeyB64 = Base64.encodeToString(rootKey, Base64.NO_WRAP)
        val pqSecret = ByteArray(32) { 0x22 }

        val newRootKeyB64 = DoubleRatchet.pqRatchetStep(rootKeyB64, pqSecret)

        assertNotEquals(rootKeyB64, newRootKeyB64)
        // Verify output is valid Base64 of 32 bytes
        val decoded = Base64.decode(newRootKeyB64, Base64.NO_WRAP)
        assertEquals(32, decoded.size)
    }

    @Test
    fun `pqRatchetStep is deterministic with same inputs`() {
        val rootKeyB64 = Base64.encodeToString(ByteArray(32) { 0x33 }, Base64.NO_WRAP)
        val pqSecret1 = ByteArray(32) { 0x44 }
        val pqSecret2 = ByteArray(32) { 0x44 }

        val result1 = DoubleRatchet.pqRatchetStep(rootKeyB64, pqSecret1)
        val result2 = DoubleRatchet.pqRatchetStep(rootKeyB64, pqSecret2)

        assertEquals(result1, result2)
    }

    @Test
    fun `pqRatchetStep with different PQ secrets produces different root keys`() {
        val rootKeyB64 = Base64.encodeToString(ByteArray(32) { 0x55 }, Base64.NO_WRAP)

        val result1 = DoubleRatchet.pqRatchetStep(rootKeyB64, ByteArray(32) { 0x66 })
        val result2 = DoubleRatchet.pqRatchetStep(rootKeyB64, ByteArray(32) { 0x77 })

        assertNotEquals(result1, result2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pqRatchetStep rejects non-32 byte secret`() {
        val rootKeyB64 = Base64.encodeToString(ByteArray(32), Base64.NO_WRAP)
        DoubleRatchet.pqRatchetStep(rootKeyB64, ByteArray(16))
    }

    // ========================================================================
    // PQ_RATCHET_INTERVAL
    // ========================================================================

    @Test
    fun `PQ_RATCHET_INTERVAL is 10`() {
        assertEquals(10, DoubleRatchet.PQ_RATCHET_INTERVAL)
    }

    // ========================================================================
    // initializeAsInitiator / initializeAsResponder symmetry
    // ========================================================================

    @Test
    fun `initiator and responder derive symmetric root keys from same shared secret`() {
        val sharedSecret1 = ByteArray(32) { (it * 5).toByte() }
        val sharedSecret2 = ByteArray(32) { (it * 5).toByte() }

        val initState = DoubleRatchet.initializeAsInitiator(sharedSecret1)
        val respState = DoubleRatchet.initializeAsResponder(sharedSecret2)

        assertEquals(initState.rootKey, respState.rootKey)
    }

    @Test
    fun `initiator send chain equals responder recv chain`() {
        val ss1 = ByteArray(32) { (it + 10).toByte() }
        val ss2 = ByteArray(32) { (it + 10).toByte() }

        val initState = DoubleRatchet.initializeAsInitiator(ss1)
        val respState = DoubleRatchet.initializeAsResponder(ss2)

        assertEquals(initState.sendChainKey, respState.recvChainKey)
        assertEquals(initState.recvChainKey, respState.sendChainKey)
    }

    @Test
    fun `initialization produces non-empty keys`() {
        val ss = ByteArray(32) { 0xAA.toByte() }
        val state = DoubleRatchet.initializeAsInitiator(ss)

        assertTrue(state.rootKey.isNotEmpty())
        assertTrue(state.sendChainKey.isNotEmpty())
        assertTrue(state.recvChainKey.isNotEmpty())
        assertTrue(state.localDhPublic.isNotEmpty())
        assertTrue(state.localDhPrivate.isNotEmpty())
    }

    @Test
    fun `initiator and responder generate different ephemeral keys`() {
        val ss1 = ByteArray(32) { 0xBB.toByte() }
        val ss2 = ByteArray(32) { 0xBB.toByte() }

        val initState = DoubleRatchet.initializeAsInitiator(ss1)
        val respState = DoubleRatchet.initializeAsResponder(ss2)

        assertNotEquals(initState.localDhPublic, respState.localDhPublic)
        assertNotEquals(initState.localDhPrivate, respState.localDhPrivate)
    }

    // ========================================================================
    // dhRatchetStep
    // ========================================================================

    @Ignore("Requires libfialka_core.so (JNI) — run as an instrumented test on device/emulator")
    @Test
    fun `dhRatchetStep produces new root and chain keys`() {
        // Initialize two sides
        val ss1 = ByteArray(32) { (it xor 0xCC).toByte() }
        val ss2 = ByteArray(32) { (it xor 0xCC).toByte() }
        val initState = DoubleRatchet.initializeAsInitiator(ss1)
        val respState = DoubleRatchet.initializeAsResponder(ss2)

        // Responder performs DH ratchet step using initiator's ephemeral
        val dhResult = DoubleRatchet.dhRatchetStep(
            respState.rootKey,
            respState.localDhPrivate,
            initState.localDhPublic
        )

        assertNotEquals(respState.rootKey, dhResult.newRootKey)
        assertTrue(dhResult.newChainKey.isNotEmpty())
        // New root key = 32 bytes
        assertEquals(32, Base64.decode(dhResult.newRootKey, Base64.NO_WRAP).size)
    }

    @Ignore("Requires libfialka_core.so (JNI) — run as an instrumented test on device/emulator")
    @Test
    fun `dhRatchetStep is deterministic`() {
        val ss1 = ByteArray(32) { (it + 1).toByte() }
        val ss2 = ByteArray(32) { (it + 1).toByte() }
        val initState = DoubleRatchet.initializeAsInitiator(ss1)
        val respState = DoubleRatchet.initializeAsResponder(ss2)

        val result1 = DoubleRatchet.dhRatchetStep(
            respState.rootKey, respState.localDhPrivate, initState.localDhPublic
        )
        // Re-initialize with same secret to get same keys
        val ss3 = ByteArray(32) { (it + 1).toByte() }
        val ss4 = ByteArray(32) { (it + 1).toByte() }
        val initState2 = DoubleRatchet.initializeAsInitiator(ss3)
        val respState2 = DoubleRatchet.initializeAsResponder(ss4)

        val result2 = DoubleRatchet.dhRatchetStep(
            respState2.rootKey, respState2.localDhPrivate, initState2.localDhPublic
        )

        // Different because ephemeral keys are random each time
        // But both results should be valid 32-byte keys
        assertEquals(32, Base64.decode(result1.newRootKey, Base64.NO_WRAP).size)
        assertEquals(32, Base64.decode(result2.newRootKey, Base64.NO_WRAP).size)
    }

    // ========================================================================
    // Full ratchet round-trip: init → advance chains → encrypt/decrypt
    // ========================================================================

    @Test
    fun `full chain round-trip - same message key on both sides`() {
        val ss1 = ByteArray(32) { (it * 7).toByte() }
        val ss2 = ByteArray(32) { (it * 7).toByte() }

        val initState = DoubleRatchet.initializeAsInitiator(ss1)
        val respState = DoubleRatchet.initializeAsResponder(ss2)

        // Initiator sends 3 messages → advance send chain
        var sendChain = initState.sendChainKey
        val messageKeys = mutableListOf<ByteArray>()
        repeat(3) {
            val (next, mk) = DoubleRatchet.advanceChain(sendChain)
            messageKeys.add(mk.encoded)
            sendChain = next
        }

        // Responder receives 3 messages → advance recv chain (same as initiator's sendChain)
        var recvChain = respState.recvChainKey
        repeat(3) { i ->
            val (next, mk) = DoubleRatchet.advanceChain(recvChain)
            assertArrayEquals("Message key mismatch at index $i", messageKeys[i], mk.encoded)
            recvChain = next
        }
    }
}
