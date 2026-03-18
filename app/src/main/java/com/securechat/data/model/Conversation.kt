package com.securechat.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a conversation between two users.
 * The conversationId is derived from: SHA-256(min(pubKeyA, pubKeyB) + max(pubKeyA, pubKeyB))
 * This ensures both participants compute the same ID.
 */
@Entity(
    tableName = "conversations",
    indices = [Index(value = ["accepted"])]
)
data class Conversation(
    @PrimaryKey
    val conversationId: String,
    val participantPublicKey: String,   // The other participant's public key
    val contactDisplayName: String,
    val lastMessage: String = "",
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val accepted: Boolean = true,       // false = pending invitation, true = active conversation
    val unreadCount: Int = 0,           // Number of unread messages from the other participant
    val sharedFingerprint: String = "", // Shared emoji fingerprint (96-bit, 16 emojis)
    val fingerprintVerified: Boolean = false, // User manually verified the fingerprint
    val ephemeralDuration: Long = 0,   // 0 = off, >0 = duration in ms for new messages
    val dummyTrafficEnabled: Boolean = false  // Per-conversation dummy traffic cover
)
