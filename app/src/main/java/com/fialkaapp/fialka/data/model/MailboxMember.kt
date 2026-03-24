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
import androidx.room.PrimaryKey

/**
 * An authorized member of a mailbox.
 * The first member to JOIN is automatically the OWNER.
 */
@Entity(tableName = "mailbox_members")
data class MailboxMember(
    @PrimaryKey val pubKey: String,
    val role: String,
    val joinedAt: Long,
    val lastSeenAt: Long = 0
) {
    companion object {
        const val ROLE_OWNER = "OWNER"
        const val ROLE_MEMBER = "MEMBER"
    }
}
