package com.securechat.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local message entity stored in Room.
 * The plaintext is stored locally (encrypted via Room/SQLCipher in a future version).
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["conversationId", "timestamp"]),
        Index(value = ["expiresAt"])
    ]
)
data class MessageLocal(
    @PrimaryKey
    val localId: String,              // UUID
    val conversationId: String,
    val senderPublicKey: String,
    val plaintext: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMine: Boolean,              // true if sent by this user
    val ephemeralDuration: Long = 0,  // 0 = permanent, >0 = duration in ms
    val expiresAt: Long = 0,          // 0 = permanent, >0 = epoch ms when it should be deleted
    val isInfoMessage: Boolean = false, // true for system info messages (e.g. ephemeral toggled)
    // File attachment fields (null/empty = text-only message)
    val fileName: String? = null,         // Original file name
    val fileSize: Long = 0,               // File size in bytes
    val localFilePath: String? = null,    // Path to decrypted file on device
    val signatureValid: Boolean? = null   // null = no signature, true = valid, false = invalid
)
