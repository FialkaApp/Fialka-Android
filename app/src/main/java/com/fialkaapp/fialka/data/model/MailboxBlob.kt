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
 * An opaque encrypted blob stored on the mailbox for a recipient.
 * The mailbox NEVER has the keys to decrypt this — it's just storage.
 */
@Entity(
    tableName = "mailbox_blobs",
    indices = [
        Index(value = ["recipientPubKey"]),
        Index(value = ["expiresAt"])
    ]
)
data class MailboxBlob(
    @PrimaryKey val blobId: String,
    val recipientPubKey: String,
    val senderPubKey: String,
    val data: ByteArray,
    val depositedAt: Long,
    val expiresAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MailboxBlob) return false
        return blobId == other.blobId
    }

    override fun hashCode() = blobId.hashCode()
}
