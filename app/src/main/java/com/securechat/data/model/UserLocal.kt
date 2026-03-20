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
import androidx.room.PrimaryKey

/**
 * Local user identity.
 * Stores the user's display name and public key.
 * Private key is stored in EncryptedSharedPreferences (Keystore-backed), not here.
 */
@Entity(tableName = "user_local")
data class UserLocal(
    @PrimaryKey
    val userId: String,       // UUID
    val displayName: String,
    val publicKey: String,    // Base64-encoded X25519 public key
    val createdAt: Long = System.currentTimeMillis()
)
