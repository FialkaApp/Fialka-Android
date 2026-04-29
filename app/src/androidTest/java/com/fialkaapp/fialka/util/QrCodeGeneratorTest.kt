/*
 * Fialka — Post-quantum encrypted messenger
 * Instrumented tests for QrCodeGenerator (generate, buildDeepLink, parseInvite).
 */
package com.fialkaapp.fialka.util

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.fialkaapp.fialka.crypto.CryptoManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QrCodeGeneratorTest {

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        CryptoManager.init(context)
        if (!CryptoManager.hasIdentity()) {
            CryptoManager.generateIdentity()
        }
    }

    // ========================================================================
    // generate() — bitmap generation (regression for forced QR_VERSION=40 bug)
    // ========================================================================

    @Test
    fun generate_shortContent_returnsNonNullBitmap() {
        // v3 deep link is ~80 chars — must produce a readable QR, not a 177×177 monster
        val ed = Base64.encodeToString(ByteArray(32) { 0x42 }, Base64.NO_WRAP)
        val link = QrCodeGenerator.buildDeepLinkV3(ed, "Alice")
        assertTrue("v3 link should be < 120 chars", link.length < 120)

        val bitmap = QrCodeGenerator.generate(link, 400)
        assertNotNull("generate() must not return null for v3 link", bitmap)
        assertEquals(400, bitmap!!.width)
        assertEquals(400, bitmap.height)
    }

    @Test
    fun generate_longContent_returnsNonNullBitmap() {
        // v2 link with ML-KEM key is 200+ chars — this was the bug trigger
        val mlkemPub = CryptoManager.getMLKEMPublicKey()
        assertNotNull(mlkemPub)
        val x25519 = CryptoManager.getPublicKey()!!
        val link = QrCodeGenerator.buildDeepLink(x25519, mlkemPub, "Bob")
        assertTrue("v2 link should be > 200 chars", link.length > 200)

        val bitmap = QrCodeGenerator.generate(link, 512)
        assertNotNull("generate() must not return null for v2 link (>200 chars)", bitmap)
        assertEquals(512, bitmap!!.width)
        assertEquals(512, bitmap.height)
    }

    @Test
    fun generate_defaultSize_is512() {
        val bitmap = QrCodeGenerator.generate("fialka://invite?v=3&ed=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
        assertNotNull(bitmap)
        assertEquals(512, bitmap!!.width)
        assertEquals(512, bitmap.height)
    }

    @Test
    fun generate_differentContents_produceDifferentBitmaps() {
        val b1 = QrCodeGenerator.generate("content_A", 256)
        val b2 = QrCodeGenerator.generate("content_B", 256)
        assertNotNull(b1); assertNotNull(b2)
        // Sample center pixel — QR data zone differs for different content
        var differ = false
        outer@ for (x in 100..155) {
            for (y in 100..155) {
                if (b1!!.getPixel(x, y) != b2!!.getPixel(x, y)) {
                    differ = true
                    break@outer
                }
            }
        }
        assertTrue("Different content must produce different QR bitmaps", differ)
    }

    // ========================================================================
    // buildDeepLinkV3
    // ========================================================================

    @Test
    fun buildDeepLinkV3_withName_containsAllParams() {
        val ed = Base64.encodeToString(ByteArray(32) { 0x01 }, Base64.NO_WRAP)
        val link = QrCodeGenerator.buildDeepLinkV3(ed, "TestUser")
        assertTrue(link.startsWith("fialka://invite?v=3&ed="))
        assertTrue(link.contains("name="))
        assertTrue(link.contains("TestUser"))
    }

    @Test
    fun buildDeepLinkV3_withoutName_noNameParam() {
        val ed = Base64.encodeToString(ByteArray(32) { 0x02 }, Base64.NO_WRAP)
        val link = QrCodeGenerator.buildDeepLinkV3(ed)
        assertFalse(link.contains("name="))
    }

    // ========================================================================
    // parseInvite — v3 round-trip (requires JNI for Ed25519→X25519 derivation)
    // ========================================================================

    @Test
    fun parseInvite_v3_roundTrip() {
        val edPubBase64 = CryptoManager.getSigningPublicKeyBase64()
        val link = QrCodeGenerator.buildDeepLinkV3(edPubBase64, "Fialka User")

        val parsed = QrCodeGenerator.parseInvite(link)
        assertNotNull("parseInvite must return non-null for a valid v3 link", parsed)
        assertEquals(edPubBase64, parsed!!.ed25519PublicKey)
        assertEquals("Fialka User", parsed.displayName)
        // x25519 must be the derivation of the ed25519 key
        assertNotNull(parsed.x25519PublicKey)
        assertTrue(parsed.x25519PublicKey.isNotEmpty())
    }

    @Test
    fun parseInvite_v3_noName_returnsNullDisplayName() {
        val edPubBase64 = CryptoManager.getSigningPublicKeyBase64()
        val link = QrCodeGenerator.buildDeepLinkV3(edPubBase64)
        val parsed = QrCodeGenerator.parseInvite(link)
        assertNotNull(parsed)
        assertNull(parsed!!.displayName)
    }

    // ========================================================================
    // parseInvite — v1 legacy (no JNI)
    // ========================================================================

    @Test
    fun parseInvite_v1_legacyRawKey_isAccepted() {
        val rawKey = "A".repeat(44)  // 44-char base64 — within the <200 char v1 path
        val parsed = QrCodeGenerator.parseInvite(rawKey)
        assertNotNull(parsed)
        assertEquals(rawKey, parsed!!.x25519PublicKey)
        assertNull(parsed.mlkemPublicKey)
    }

    // ========================================================================
    // parseInvite — rejection of malformed / malicious inputs
    // ========================================================================

    @Test
    fun parseInvite_oversizedLink_isRejected() {
        val link = "fialka://invite?v=3&ed=" + "A".repeat(4000)
        assertNull("Oversized link must be rejected", QrCodeGenerator.parseInvite(link))
    }

    @Test
    fun parseInvite_duplicateParam_isRejected() {
        val ed = Base64.encodeToString(ByteArray(32) { 0x03 }, Base64.NO_WRAP)
        val link = "fialka://invite?v=3&ed=$ed&ed=$ed"
        assertNull("Duplicate 'ed' param must be rejected", QrCodeGenerator.parseInvite(link))
    }

    @Test
    fun parseInvite_controlCharInName_isRejected() {
        val ed = Base64.encodeToString(ByteArray(32) { 0x04 }, Base64.NO_WRAP)
        val link = "fialka://invite?v=3&ed=$ed&name=Evil%00Null"
        assertNull("Name with control char must be rejected", QrCodeGenerator.parseInvite(link))
    }

    @Test
    fun parseInvite_unknownParam_isIgnored() {
        val ed = Base64.encodeToString(ByteArray(32) { 0x05 }, Base64.NO_WRAP)
        // 'foo=bar' is not in the whitelist — should be silently dropped, rest parses OK
        val link = "fialka://invite?v=3&ed=$ed&foo=bar"
        val parsed = QrCodeGenerator.parseInvite(link)
        // Unknown param is stripped, v3 still requires 'ed' which is present
        assertNotNull(parsed)
    }

    @Test
    fun parseInvite_blankString_returnsNull() {
        assertNull(QrCodeGenerator.parseInvite(""))
        assertNull(QrCodeGenerator.parseInvite("   "))
    }
}
