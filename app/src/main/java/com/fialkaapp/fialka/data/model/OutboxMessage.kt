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
 * Outbox queue entry — stores frames that failed to deliver via TorTransport
 * and need to be retried later. Used in NORMAL mode only.
 */
@Entity(
    tableName = "outbox",
    indices = [
        Index(value = ["status", "nextRetryAt"]),
        Index(value = ["destinationOnion"])
    ]
)
data class OutboxMessage(
    @PrimaryKey val id: String,
    val destinationOnion: String,
    val frameType: Int,
    val payload: ByteArray,
    val createdAt: Long,
    val retryCount: Int = 0,
    val nextRetryAt: Long = 0,
    val status: Int = STATUS_PENDING,
    val fallbackOnion: String? = null,  // Recipient's mailbox .onion for DEPOSIT fallback
    val messageLocalId: String? = null  // Links back to MessageLocal for delivery status updates
) {
    companion object {
        const val STATUS_PENDING = 0
        const val STATUS_SENDING = 1
        const val STATUS_DELIVERED = 2
        const val STATUS_FAILED = 3
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OutboxMessage) return false
        return id == other.id
    }

    override fun hashCode() = id.hashCode()
}
