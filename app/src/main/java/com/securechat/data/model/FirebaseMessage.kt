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
    val kemCiphertext: String = "", // Base64 ML-KEM-768 ciphertext (first message only, PQXDH)
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
        return map
    }
}
