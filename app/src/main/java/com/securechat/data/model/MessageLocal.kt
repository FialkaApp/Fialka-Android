package com.securechat.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local message entity stored in Room.
 * The plaintext is stored locally (encrypted via Room/SQLCipher in a future version).
 */
@Entity(tableName = "messages")
data class MessageLocal(
    @PrimaryKey
    val localId: String,              // UUID
    val conversationId: String,
    val senderPublicKey: String,
    val plaintext: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isMine: Boolean,              // true if sent by this user
    val ephemeralDuration: Long = 0,  // 0 = permanent, >0 = duration in ms
    val expiresAt: Long = 0           // 0 = permanent, >0 = epoch ms when it should be deleted
)
