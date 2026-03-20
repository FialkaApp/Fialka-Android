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
    val dummyTrafficEnabled: Boolean = false,  // Per-conversation dummy traffic cover
    // Timestamp of the last successfully decrypted incoming message.
    // Used as the Firebase listener lower-bound on restart so already-seen messages
    // (including ones we failed to decrypt) are not re-fetched and don't corrupt the ratchet.
    val lastDeliveredAt: Long = 0L
)
