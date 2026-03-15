package com.securechat.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a conversation between two users.
 * The conversationId is derived from: SHA-256(min(pubKeyA, pubKeyB) + max(pubKeyA, pubKeyB))
 * This ensures both participants compute the same ID.
 */
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey
    val conversationId: String,
    val participantPublicKey: String,   // The other participant's public key
    val contactDisplayName: String,
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)
