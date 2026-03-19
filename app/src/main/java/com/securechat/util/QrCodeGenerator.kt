package com.securechat.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Utility to generate QR code bitmaps from text content.
 *
 * Supports two modes:
 *  - Legacy: raw X25519 public key Base64 (v1 — existing contacts)
 *  - PQXDH : deep link `securechat://invite?v=2&x25519=<key>&mlkem=<key>` (v2)
 *
 * For PQXDH deep links (~1650 chars) we force QR Version 40 with error correction L
 * to maximise data capacity (~2953 chars). If encoding fails (e.g. ZXing version
 * constraint unavailable), the caller should fall back to sharing the deep link as text.
 */
object QrCodeGenerator {

    /**
     * Build a PQXDH v2 deep link from X25519 and ML-KEM public keys.
     * @param x25519PublicKeyBase64  X25519 identity public key (Base64, ~44 chars).
     * @param mlkemPublicKeyBase64   ML-KEM-768 identity public key (Base64, ~1580 chars). Pass null for classic-only invite.
     */
    fun buildDeepLink(x25519PublicKeyBase64: String, mlkemPublicKeyBase64: String?): String {
        return if (mlkemPublicKeyBase64 != null) {
            "securechat://invite?v=2&x25519=$x25519PublicKeyBase64&mlkem=$mlkemPublicKeyBase64"
        } else {
            // Short link — ML-KEM key is fetched from Firebase by the recipient when adding contact
            "securechat://invite?v=2&x25519=$x25519PublicKeyBase64"
        }
    }

    /**
     * Parse an invite string (scanned QR or received deep link).
     * @return InviteData with x25519 key populated; mlkemKey is null for v1 invites.
     */
    fun parseInvite(raw: String): InviteData? {
        return when {
            raw.startsWith("securechat://invite?") -> {
                val params = raw.removePrefix("securechat://invite?")
                    .split("&")
                    .mapNotNull { part ->
                        val idx = part.indexOf('=')
                        if (idx > 0) part.substring(0, idx) to part.substring(idx + 1) else null
                    }
                    .toMap()
                val x25519 = params["x25519"] ?: return null
                val mlkem  = params["mlkem"]
                InviteData(x25519, mlkem)
            }
            raw.isNotBlank() -> {
                // Legacy v1: raw public key only
                InviteData(x25519PublicKey = raw, mlkemPublicKey = null)
            }
            else -> null
        }
    }

    data class InviteData(
        val x25519PublicKey: String,
        val mlkemPublicKey: String?   // null = contact has no ML-KEM key (classic fallback)
    )

    /**
     * Generate a QR code bitmap from the given text.
     *
     * For PQXDH deep links (content length > 200 chars) we automatically switch to
     * Version 40 / Error-Correction L to fit the larger payload.
     *
     * @param content The text to encode (use [buildDeepLink] to produce PQXDH format).
     * @param size    Width/height in pixels (default 512).
     * @return A Bitmap containing the QR code, or null if the content is too large to encode.
     */
    fun generate(content: String, size: Int = 512): Bitmap? {
        val useLargeVersion = content.length > 200

        val hints: Map<EncodeHintType, Any> = if (useLargeVersion) {
            mapOf(
                EncodeHintType.MARGIN           to 1,
                EncodeHintType.CHARACTER_SET    to "ISO-8859-1",  // binary mode — max capacity
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L,
                EncodeHintType.QR_VERSION       to 40
            )
        } else {
            mapOf(
                EncodeHintType.MARGIN        to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
        }

        return try {
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            null  // caller should display "too large, use text share" fallback
        }
    }
}

