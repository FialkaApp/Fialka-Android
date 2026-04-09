/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 *
 * Pure-JVM unit tests for wallet logic that does NOT require JNI or Android Keystore:
 *   - WalletTransaction piconero ↔ XMR math
 *   - PaymentMessageData round-trip serialization
 *   - DonationConfig: hex decoding + placeholder detection
 *   - DonationManager.isDonationConfigured (key byte check)
 */
package com.fialkaapp.fialka.wallet

import com.fialkaapp.fialka.data.model.WalletTransaction
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class WalletUnitTest {

    // =========================================================================
    // WalletTransaction — piconero ↔ XMR
    // =========================================================================

    @Test
    fun `piconeroToXmr - one full XMR`() {
        val result = WalletTransaction.piconeroToXmr(1_000_000_000_000L)
        assertEquals("1.000000000000", result)
    }

    @Test
    fun `piconeroToXmr - zero`() {
        val result = WalletTransaction.piconeroToXmr(0L)
        assertEquals("0.000000000000", result)
    }

    @Test
    fun `piconeroToXmr - fractional`() {
        // 0.5 XMR = 500_000_000_000 piconero
        val result = WalletTransaction.piconeroToXmr(500_000_000_000L)
        assertEquals("0.500000000000", result)
    }

    @Test
    fun `piconeroToXmr - small amount`() {
        // 1 piconero = 0.000000000001 XMR
        val result = WalletTransaction.piconeroToXmr(1L)
        assertEquals("0.000000000001", result)
    }

    @Test
    fun `piconeroToXmr - large donation amount`() {
        // 10 XMR
        val result = WalletTransaction.piconeroToXmr(10_000_000_000_000L)
        assertEquals("10.000000000000", result)
    }

    @Test
    fun `xmrToPiconero - one XMR`() {
        val result = WalletTransaction.xmrToPiconero("1.0")
        assertEquals(1_000_000_000_000L, result)
    }

    @Test
    fun `xmrToPiconero - half XMR`() {
        val result = WalletTransaction.xmrToPiconero("0.5")
        assertEquals(500_000_000_000L, result)
    }

    @Test
    fun `xmrToPiconero - whole number no decimal`() {
        val result = WalletTransaction.xmrToPiconero("2")
        assertEquals(2_000_000_000_000L, result)
    }

    @Test
    fun `xmrToPiconero - zero`() {
        val result = WalletTransaction.xmrToPiconero("0")
        assertEquals(0L, result)
    }

    @Test
    fun `piconeroToXmr and xmrToPiconero roundtrip`() {
        val amounts = listOf(1L, 999L, 1_000_000L, 1_000_000_000_000L, 42_123_456_789_012L)
        for (piconero in amounts) {
            val xmrStr = WalletTransaction.piconeroToXmr(piconero)
            val back   = WalletTransaction.xmrToPiconero(xmrStr)
            assertEquals("Roundtrip failed for $piconero", piconero, back)
        }
    }

    @Test
    fun `STATUS constants are distinct`() {
        val statuses = listOf(
            WalletTransaction.STATUS_PENDING,
            WalletTransaction.STATUS_CONFIRMING,
            WalletTransaction.STATUS_CONFIRMED,
            WalletTransaction.STATUS_FAILED
        )
        assertEquals(statuses.size, statuses.toSet().size)
    }

    @Test
    fun `DIRECTION constants are distinct`() {
        assertNotEquals(
            WalletTransaction.DIRECTION_INCOMING,
            WalletTransaction.DIRECTION_OUTGOING
        )
    }

    // =========================================================================
    // PaymentMessageData — prefix + round-trip
    // =========================================================================

    @Test
    fun `PaymentMessageData toPlaintext starts with XMR_PAY prefix`() {
        val data = PaymentMessageData(
            amountPiconero = 500_000_000_000L,
            address = "4TestAddressXXX",
            label = "Pizza"
        )
        val text = data.toPlaintext()
        assertTrue("Expected XMR_PAY: prefix", text.startsWith("XMR_PAY:"))
    }

    @Test
    fun `PaymentMessageData round-trip preserves all fields`() {
        val original = PaymentMessageData(
            amountPiconero = 123_456_789_000L,
            address = "4AbcDefGhiJkl90",
            label = "Test payment"
        )
        val roundtripped = PaymentMessageData.fromPlaintext(original.toPlaintext())
        assertNotNull("fromPlaintext should succeed", roundtripped)
        assertEquals(original.amountPiconero, roundtripped!!.amountPiconero)
        assertEquals(original.address, roundtripped.address)
        assertEquals(original.label, roundtripped.label)
    }

    @Test
    fun `PaymentMessageData fromPlaintext returns null for non-payment text`() {
        assertNull(PaymentMessageData.fromPlaintext("Hello world"))
        assertNull(PaymentMessageData.fromPlaintext(""))
        assertNull(PaymentMessageData.fromPlaintext("BTC:someaddress"))
    }

    @Test
    fun `PaymentMessageData amountXmr helper formats correctly`() {
        val data = PaymentMessageData(
            amountPiconero = 1_000_000_000_000L,
            address = "4Test",
            label = null
        )
        // amountXmr should produce non-empty, non-zero string
        assertTrue(data.amountXmr.isNotEmpty())
        assertFalse(data.amountXmr.startsWith("0.000000000000"))
    }

    @Test
    fun `PaymentMessageData shortAddress truncates long address`() {
        val long = "4" + "A".repeat(94)  // 95 chars — standard Monero primary address
        val data = PaymentMessageData(amountPiconero = 1L, address = long, label = null)
        assertTrue("shortAddress should be shorter than full", data.shortAddress.length < long.length)
    }

    // =========================================================================
    // DonationConfig — hex decoding
    // =========================================================================

    @Test
    fun `DonationConfig SPEND_PUB is 32 bytes`() {
        assertEquals(32, DonationConfig.SPEND_PUB.size)
    }

    @Test
    fun `DonationConfig VIEW_PRIV is 32 bytes`() {
        assertEquals(32, DonationConfig.VIEW_PRIV.size)
    }

    @Test
    fun `DonationConfig keys are not identical`() {
        assertFalse(
            "SPEND_PUB and VIEW_PRIV must not be identical",
            DonationConfig.SPEND_PUB.contentEquals(DonationConfig.VIEW_PRIV)
        )
    }

    // =========================================================================
    // DonationManager — placeholder detection
    // =========================================================================

    @Test
    fun `DonationManager isDonationConfigured is true with real keys`() {
        // Real keys were placed in DonationConfig by the developer.
        // This test will FAIL if placeholder all-zero keys are still present,
        // reminding the developer to replace them before release.
        assertTrue(
            "DonationConfig still has placeholder all-zero keys — replace before release!",
            DonationManager.isDonationConfigured
        )
    }
}
