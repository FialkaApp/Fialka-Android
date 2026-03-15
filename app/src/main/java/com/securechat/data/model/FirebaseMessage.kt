package com.securechat.data.model

/**
 * Firebase message model.
 * This is what gets stored on Firebase — only ciphertext, never plaintext.
 *
 * Fields:
 *  - senderPublicKey: identifies who sent the message
 *  - ciphertext: Base64-encoded AES-256-GCM encrypted content
 *  - iv: Base64-encoded initialization vector / nonce
 *  - messageIndex: ratchet chain index (for PFS key derivation)
 *  - createdAt: server timestamp
 */
data class FirebaseMessage(
    val senderPublicKey: String = "",
    val ciphertext: String = "",
    val iv: String = "",
    val messageIndex: Int = 0,
    val createdAt: Long = 0L
) {
    fun toMap(): Map<String, Any> = mapOf(
        "senderPublicKey" to senderPublicKey,
        "ciphertext" to ciphertext,
        "iv" to iv,
        "messageIndex" to messageIndex,
        "createdAt" to createdAt
    )
}
