/*
 * SecureChat — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.securechat.util

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Base64
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URLDecoder
import java.net.URLEncoder

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

    // Fixed 12-byte DER header for X25519 SubjectPublicKeyInfo (identical for every key)
    private val X25519_HEADER = byteArrayOf(
        0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x6E, 0x03, 0x21, 0x00
    )

    /** Strip the known 12-byte X.509 header → 32 raw bytes → Base64 (URL-safe, no padding). */
    private fun x25519ToRaw(fullBase64: String): String {
        val full = Base64.decode(fullBase64, Base64.NO_WRAP)
        require(full.size == 44) { "Expected 44-byte X25519 key, got ${full.size}" }
        return Base64.encodeToString(full.copyOfRange(12, 44), Base64.NO_WRAP)
    }

    /** Restore the 12-byte X.509 header onto a 32-byte raw Base64 key. */
    private fun rawToX25519(rawBase64: String): String {
        val raw = Base64.decode(rawBase64, Base64.NO_WRAP)
        if (raw.size == 44) return rawBase64          // already full X.509 (legacy link)
        require(raw.size == 32) { "Expected 32-byte raw key, got ${raw.size}" }
        return Base64.encodeToString(X25519_HEADER + raw, Base64.NO_WRAP)
    }

    /**
     * Encode a full X.509 Base64 X25519 key as stripped 32-byte Base64.
     * Used by ProfileFragment to display the key without the fixed prefix.
     */
    fun stripX25519Header(fullBase64: String): String = runCatching { x25519ToRaw(fullBase64) }.getOrDefault(fullBase64)

    /**
     * Build a PQXDH v2 deep link from X25519 and ML-KEM public keys.
     * @param x25519PublicKeyBase64  X25519 identity public key (Base64, ~44 chars).
     * @param mlkemPublicKeyBase64   ML-KEM-1024 identity public key (Base64, ~2092 chars). Pass null for classic-only invite.
     * @param displayName            Optional display name to embed so the recipient's form auto-fills.
     */
    fun buildDeepLink(x25519PublicKeyBase64: String, mlkemPublicKeyBase64: String?, displayName: String? = null): String {
        val rawX25519 = runCatching { x25519ToRaw(x25519PublicKeyBase64) }.getOrDefault(x25519PublicKeyBase64)
        val base = if (mlkemPublicKeyBase64 != null) {
            "securechat://invite?v=2&x25519=$rawX25519&mlkem=$mlkemPublicKeyBase64"
        } else {
            "securechat://invite?v=2&x25519=$rawX25519"
        }
        return if (!displayName.isNullOrBlank()) {
            "$base&name=${URLEncoder.encode(displayName, "UTF-8")}"
        } else base
    }

    /**
     * Parse an invite string (scanned QR or received deep link).
     * @return InviteData with x25519 key populated; mlkemKey is null for v1 invites.
     */
    fun parseInvite(raw: String): InviteData? {
        return try {
            when {
                raw.startsWith("securechat://invite?") -> {
                    if (raw.length > 4000) return null  // reject oversized links
                    val seen = mutableSetOf<String>()
                    val params = raw.removePrefix("securechat://invite?")
                        .split("&")
                        .mapNotNull { part ->
                            val idx = part.indexOf('=')
                            if (idx > 0) {
                                val key = part.substring(0, idx)
                                // whitelist only known parameters, reject duplicates
                                if (key !in listOf("v", "x25519", "mlkem", "name")) return@mapNotNull null
                                if (key in seen) return null  // duplicate param → reject
                                seen.add(key)
                                key to part.substring(idx + 1)
                            } else null
                        }
                        .toMap()
                    val x25519 = params["x25519"]?.let { k ->
                        if (k.length > 60) return null  // base64 of 32 bytes ≈ 44 chars
                        runCatching { rawToX25519(k) }.getOrDefault(k)
                    } ?: return null
                    val mlkem = params["mlkem"]?.let { k ->
                        if (k.length > 2500) return null  // ML-KEM-1024 public key ≈ 2092 chars
                        k
                    }
                    val name = params["name"]?.let { n ->
                        if (n.length > 200) return null
                        val decoded = runCatching { URLDecoder.decode(n, "UTF-8") }.getOrNull() ?: return null
                        if (decoded.length > 100) return null
                        // reject control characters
                        if (decoded.any { it.code < 32 }) return null
                        decoded
                    }
                    InviteData(x25519, mlkem, name)
                }
                raw.isNotBlank() && raw.length < 200 -> {
                    // Legacy v1: raw public key only
                    InviteData(x25519PublicKey = raw, mlkemPublicKey = null, displayName = null)
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    data class InviteData(
        val x25519PublicKey: String,
        val mlkemPublicKey: String?,   // null = contact has no ML-KEM key (classic fallback)
        val displayName: String? = null
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

