/*
 * Fialka — Post-quantum encrypted messenger
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
package com.fialkaapp.fialka.data.model

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
    val signatureValid: Boolean? = null,  // null = no signature, true = valid, false = invalid
    val isOneShot: Boolean = false,       // true = image visible once then deleted
    val oneShotOpened: Boolean = false,   // true = one-shot image was already viewed
    // Delivery status: 0=SENT (direct P2P), 1=MAILBOX (via mailbox deposit), 2=FAILED, 3=PENDING (in outbox), 4=SENDING (in flight)
    val deliveryStatus: Int = DELIVERY_SENDING
) {
    companion object {
        const val DELIVERY_SENT = 0     // Delivered directly via P2P
        const val DELIVERY_MAILBOX = 1  // Delivered via mailbox deposit
        const val DELIVERY_FAILED = 2   // Both direct + mailbox failed, in outbox
        const val DELIVERY_PENDING = 3  // Queued in outbox, delivery in progress
        const val DELIVERY_SENDING = 4  // Currently being sent (show spinner) — migrated to PENDING on restart
    }
}
