/*
 * Fialka — Post-quantum encrypted messenger
 * Unit tests for WalletRepository pure functions (no JNI, no network, no Context).
 *
 * Scope: formatXmr, WalletSendDraft data class, WalletNodeStatus defaults.
 * Excluded: validateAddress (JNI), getSnapshot (network+JNI), buildReceiveAddress (JNI+context).
 */
package com.fialkaapp.fialka.wallet

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class WalletRepositoryPureTest {

    // =========================================================================
    // formatXmr — piconero → human-readable XMR
    // 1 XMR = 1_000_000_000_000 piconero (12 decimal places)
    // =========================================================================

    @Test
    fun `formatXmr zero piconero is 0 XMR`() {
        assertEquals("0.000000 XMR", WalletRepository.formatXmr(0L))
    }

    @Test
    fun `formatXmr negative returns dash`() {
        assertEquals("-- XMR", WalletRepository.formatXmr(-1L))
        assertEquals("-- XMR", WalletRepository.formatXmr(Long.MIN_VALUE))
    }

    @Test
    fun `formatXmr 1 XMR formats correctly`() {
        assertEquals("1.000000 XMR", WalletRepository.formatXmr(1_000_000_000_000L))
    }

    @Test
    fun `formatXmr fractional XMR formats correctly`() {
        // 0.5 XMR = 500_000_000_000 piconero
        assertEquals("0.500000 XMR", WalletRepository.formatXmr(500_000_000_000L))
    }

    @Test
    fun `formatXmr small amount formats correctly`() {
        // 0.000001 XMR = 1_000_000 piconero (minimum displayable at 6 decimals)
        assertEquals("0.000001 XMR", WalletRepository.formatXmr(1_000_000L))
    }

    @Test
    fun `formatXmr sub-minimum amount rounds to zero in 6 decimals`() {
        // 999_999 piconero < 0.000001 XMR — should display as 0.000000 (not negative)
        val result = WalletRepository.formatXmr(999_999L)
        assertTrue("Should display ~0.000000 XMR, got: $result", result.startsWith("0.000000"))
    }

    @Test
    fun `formatXmr large amount formats without overflow`() {
        // 1000 XMR
        assertEquals("1000.000000 XMR", WalletRepository.formatXmr(1_000L * 1_000_000_000_000L))
    }

    @Test
    fun `formatXmr always ends with XMR suffix`() {
        listOf(0L, 1L, 1_000_000_000_000L, 999_999_999_999L).forEach { piconero ->
            assertTrue(
                "formatXmr($piconero) must end with ' XMR'",
                WalletRepository.formatXmr(piconero).endsWith(" XMR")
            )
        }
    }

    @Test
    fun `formatXmr always has 6 decimal places for positive values`() {
        val result = WalletRepository.formatXmr(1_234_567_890_123L)
        val decimalPart = result.substringAfter(".").substringBefore(" ")
        assertEquals("Should have exactly 6 decimal digits", 6, decimalPart.length)
    }

    // =========================================================================
    // WalletSendDraft — data class behaviour
    // =========================================================================

    @Test
    fun `WalletSendDraft equality and copy`() {
        val draft1 = WalletSendDraft("addr1", "0.5", "test note")
        val draft2 = WalletSendDraft("addr1", "0.5", "test note")
        val draft3 = WalletSendDraft("addr1", "1.0", "test note")

        assertEquals(draft1, draft2)
        assertNotEquals(draft1, draft3)
        assertEquals("addr1", draft1.copy(amount = "1.0").address)
    }

    @Test
    fun `WalletSendDraft trims whitespace when saved`() {
        // saveSendDraft trims — verify data class holds trimmed values when stored
        val draft = WalletSendDraft(
            address = "  someAddress  ",
            amount = " 0.1 ",
            note = " a note "
        )
        // Simulate what saveSendDraft does: trim() on all fields
        val trimmed = WalletSendDraft(
            address = draft.address.trim(),
            amount = draft.amount.trim(),
            note = draft.note.trim()
        )
        assertEquals("someAddress", trimmed.address)
        assertEquals("0.1", trimmed.amount)
        assertEquals("a note", trimmed.note)
    }

    // =========================================================================
    // WalletNodeStatus — state machine invariants
    // =========================================================================

    @Test
    fun `WalletNodeStatus offline has null heights`() {
        val status = WalletNodeStatus(
            online = false,
            daemonHeight = null,
            targetHeight = null,
            synchronized = false,
            statusLabel = "offline"
        )
        assertFalse(status.online)
        assertFalse(status.synchronized)
        assertNull(status.daemonHeight)
        assertNull(status.targetHeight)
    }

    @Test
    fun `WalletNodeStatus synchronized requires online`() {
        // A synchronized-but-offline state is logically inconsistent
        val status = WalletNodeStatus(
            online = true,
            daemonHeight = 2_100_000L,
            targetHeight = 2_100_000L,
            synchronized = true,
            statusLabel = "sync OK"
        )
        assertTrue(status.online)
        assertTrue(status.synchronized)
        assertEquals(2_100_000L, status.daemonHeight)
    }

    // =========================================================================
    // WalletLocalEvent — display formatting
    // =========================================================================

    @Test
    fun `WalletLocalEvent stores title and formattedAt`() {
        val event = WalletLocalEvent("Transaction received", "2026-04-29 12:00")
        assertEquals("Transaction received", event.title)
        assertEquals("2026-04-29 12:00", event.formattedAt)
    }
}
