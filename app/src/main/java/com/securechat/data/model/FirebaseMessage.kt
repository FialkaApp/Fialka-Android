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
package com.securechat.data.model

/**
 * Firebase message model — metadata-hardened.
 * Only ciphertext + minimal routing info transit on Firebase.
 *
 * Removed from wire format (V1.1 metadata hardening):
 *  - senderPublicKey: was leaking identity-key-level info (unnecessary in 1-to-1)
 *  - messageIndex: now embedded inside AES-GCM ciphertext (trial decryption)
 *
 * Remaining fields:
 *  - ciphertext: Base64 AES-256-GCM (contains "index|plaintext")
 *  - iv: Base64 12-byte nonce
 *  - createdAt: server timestamp (needed for ordering + TTL cleanup)
 *  - senderUid: Firebase anonymous UID (needed for Cloud Function push routing)
 */
data class FirebaseMessage(
    val ciphertext: String = "",
    val iv: String = "",
    val createdAt: Long = 0L,
    val senderUid: String = "",
    val ephemeralKey: String = "",   // Base64 X25519 DH public key (Double Ratchet)
    val signature: String = "",     // Base64 Ed25519 signature (64 bytes)
    val kemCiphertext: String = "", // Base64 ML-KEM-1024 ciphertext (first message only, PQXDH)
    val cipherSuite: Int = 0,      // 0 = AES-256-GCM (default), 1 = ChaCha20-Poly1305
    @Transient val firebaseKey: String = ""  // Local-only: Firebase node key for delete-after-delivery
) {
    fun toMap(): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "ciphertext" to ciphertext,
            "iv" to iv,
            "createdAt" to createdAt,
            "senderUid" to senderUid
        )
        if (ephemeralKey.isNotEmpty()) {
            map["ephemeralKey"] = ephemeralKey
        }
        if (signature.isNotEmpty()) {
            map["signature"] = signature
        }
        if (kemCiphertext.isNotEmpty()) {
            map["kemCiphertext"] = kemCiphertext
        }
        if (cipherSuite != 0) {
            map["cipherSuite"] = cipherSuite
        }
        return map
    }
}
