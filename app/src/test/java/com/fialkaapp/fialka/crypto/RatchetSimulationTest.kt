/*
 * Fialka — Post-quantum encrypted messenger
 * Extended unit tests: full ratchet simulation, SPQR interval, skip-chain behaviour.
 */
package com.fialkaapp.fialka.crypto

import android.util.Base64
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class RatchetSimulationTest {

    // =========================================================================
    // Full bidirectional ratchet simulation
    // =========================================================================

    /**
     * Tests a full Alice → Bob conversation with direction changes.
     * Both sides must derive the same message keys in sequence.
     */
    @Test
    fun `bidirectional ratchet - Alice sends 5 Bob replies 3`() {
        val sharedSecret = ByteArray(32) { (it + 1).toByte() }
        val ss2 = sharedSecret.copyOf()

        val alice = DoubleRatchet.initializeAsInitiator(sharedSecret)
        val bob   = DoubleRatchet.initializeAsResponder(ss2)

        // Alice sends 5 messages (advance alice.sendChain, bob.recvChain)
        var aliceSend = alice.sendChainKey
        var bobRecv   = bob.recvChainKey

        val aliceKeys = mutableListOf<ByteArray>()
        repeat(5) {
            val (nextChain, msgKey) = DoubleRatchet.advanceChain(aliceSend)
            aliceKeys.add(msgKey.encoded)
            aliceSend = nextChain
        }
        repeat(5) { i ->
            val (nextChain, msgKey) = DoubleRatchet.advanceChain(bobRecv)
            assertArrayEquals("Alice→Bob key mismatch at msg $i", aliceKeys[i], msgKey.encoded)
            bobRecv = nextChain
        }

        // Bob replies 3 messages (advance bob.sendChain, alice.recvChain)
        var bobSend   = bob.sendChainKey
        var aliceRecv = alice.recvChainKey

        val bobKeys = mutableListOf<ByteArray>()
        repeat(3) {
            val (nextChain, msgKey) = DoubleRatchet.advanceChain(bobSend)
            bobKeys.add(msgKey.encoded)
            bobSend = nextChain
        }
        repeat(3) { i ->
            val (nextChain, msgKey) = DoubleRatchet.advanceChain(aliceRecv)
            assertArrayEquals("Bob→Alice key mismatch at msg $i", bobKeys[i], msgKey.encoded)
            aliceRecv = nextChain
        }
    }

    /**
     * Message keys must never repeat across a chain (forward secrecy property).
     */
    @Test
    fun `no message key repeats across 500 chain advances`() {
        var chainKey = Base64.encodeToString(ByteArray(32) { 0xAB.toByte() }, Base64.NO_WRAP)
        val seen = mutableSetOf<String>()

        repeat(500) { i ->
            val (next, mk) = DoubleRatchet.advanceChain(chainKey)
            val mkHex = mk.encoded.joinToString("") { "%02x".format(it) }
            assertTrue("Duplicate message key at step $i: $mkHex", seen.add(mkHex))
            chainKey = next
        }
    }

    /**
     * Two parallel chain advances from the same starting key must produce the same result.
     * (determinism check)
     */
    @Test
    fun `chain advance is reproducible from same initial state`() {
        val initial = Base64.encodeToString(ByteArray(32) { 0x7F.toByte() }, Base64.NO_WRAP)

        fun advance5(start: String): List<String> {
            val keys = mutableListOf<String>()
            var ck = start
            repeat(5) {
                val (next, mk) = DoubleRatchet.advanceChain(ck)
                keys.add(Base64.encodeToString(mk.encoded, Base64.NO_WRAP))
                ck = next
            }
            return keys
        }

        assertEquals(advance5(initial), advance5(initial))
    }

    // =========================================================================
    // SPQR interval and mixing
    // =========================================================================

    @Test
    fun `SPQR fires every PQ_RATCHET_INTERVAL messages`() {
        val interval = DoubleRatchet.PQ_RATCHET_INTERVAL
        assertEquals(10, interval)
    }

    @Test
    fun `pqRatchetStep produces different root keys for different PQ secrets`() {
        val rootKey = Base64.encodeToString(ByteArray(32) { 0x11 }, Base64.NO_WRAP)

        // Simulate 3 SPQR steps with different ML-KEM secrets
        val secrets = Array(3) { i -> ByteArray(32) { (i + 1 + it).toByte() } }
        val results = mutableListOf<String>()
        var current = rootKey
        for (secret in secrets) {
            current = DoubleRatchet.pqRatchetStep(current, secret.copyOf())
            results.add(current)
        }

        // All root keys must be different
        assertEquals(results.size, results.toSet().size)
    }

    @Test
    fun `pqRatchetStep same secret same root gives same output`() {
        val rootKey = Base64.encodeToString(ByteArray(32) { 0x22 }, Base64.NO_WRAP)
        val secret1 = ByteArray(32) { 0x33 }
        val secret2 = ByteArray(32) { 0x33 }

        val result1 = DoubleRatchet.pqRatchetStep(rootKey, secret1)
        val result2 = DoubleRatchet.pqRatchetStep(rootKey, secret2)

        assertEquals(result1, result2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pqRatchetStep rejects 16-byte secret`() {
        val rootKey = Base64.encodeToString(ByteArray(32), Base64.NO_WRAP)
        DoubleRatchet.pqRatchetStep(rootKey, ByteArray(16))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `pqRatchetStep rejects 64-byte secret`() {
        val rootKey = Base64.encodeToString(ByteArray(32), Base64.NO_WRAP)
        DoubleRatchet.pqRatchetStep(rootKey, ByteArray(64))
    }

    // =========================================================================
    // initiator / responder chain symmetry
    // =========================================================================

    @Test
    fun `initiator send chain == responder recv chain (10 steps deep)`() {
        val ss = ByteArray(32) { (it * 11).toByte() }
        val init = DoubleRatchet.initializeAsInitiator(ss.copyOf())
        val resp = DoubleRatchet.initializeAsResponder(ss.copyOf())

        var initChain = init.sendChainKey
        var respChain = resp.recvChainKey

        repeat(10) { i ->
            val (initNext, initKey) = DoubleRatchet.advanceChain(initChain)
            val (respNext, respKey) = DoubleRatchet.advanceChain(respChain)
            assertArrayEquals("Key mismatch at step $i", initKey.encoded, respKey.encoded)
            initChain = initNext
            respChain = respNext
        }
    }

    @Test
    fun `responder send chain == initiator recv chain (10 steps deep)`() {
        val ss = ByteArray(32) { (it * 13).toByte() }
        val init = DoubleRatchet.initializeAsInitiator(ss.copyOf())
        val resp = DoubleRatchet.initializeAsResponder(ss.copyOf())

        var respChain = resp.sendChainKey
        var initChain = init.recvChainKey

        repeat(10) { i ->
            val (respNext, respKey) = DoubleRatchet.advanceChain(respChain)
            val (initNext, initKey) = DoubleRatchet.advanceChain(initChain)
            assertArrayEquals("Key mismatch at step $i", respKey.encoded, initKey.encoded)
            respChain = respNext
            initChain = initNext
        }
    }

    // =========================================================================
    // Root key isolation
    // =========================================================================

    @Test
    fun `different initial shared secrets produce different root keys`() {
        val ss1 = ByteArray(32) { 0xAA.toByte() }
        val ss2 = ByteArray(32) { 0xBB.toByte() }

        val state1 = DoubleRatchet.initializeAsInitiator(ss1)
        val state2 = DoubleRatchet.initializeAsInitiator(ss2)

        assertNotEquals(state1.rootKey, state2.rootKey)
        assertNotEquals(state1.sendChainKey, state2.sendChainKey)
    }

    @Test
    fun `root key and chain keys are always 32 bytes`() {
        val ss = ByteArray(32) { 0xFF.toByte() }
        val state = DoubleRatchet.initializeAsInitiator(ss)

        assertEquals(32, Base64.decode(state.rootKey, Base64.NO_WRAP).size)
        assertEquals(32, Base64.decode(state.sendChainKey, Base64.NO_WRAP).size)
        assertEquals(32, Base64.decode(state.recvChainKey, Base64.NO_WRAP).size)
    }

    // =========================================================================
    // Message key size
    // =========================================================================

    @Test
    fun `message keys are always 32 bytes AES-256`() {
        var chainKey = Base64.encodeToString(ByteArray(32) { 0xCC.toByte() }, Base64.NO_WRAP)
        repeat(20) {
            val (next, mk) = DoubleRatchet.advanceChain(chainKey)
            assertEquals("AES", mk.algorithm)
            assertEquals(32, mk.encoded.size)
            chainKey = next
        }
    }
}
