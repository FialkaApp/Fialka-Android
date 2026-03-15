package com.securechat.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local user identity.
 * Stores the user's display name and public key.
 * Private key is stored in Android Keystore, not here.
 */
@Entity(tableName = "user_local")
data class UserLocal(
    @PrimaryKey
    val userId: String,       // UUID
    val displayName: String,
    val publicKey: String,    // Base64-encoded X25519 public key
    val createdAt: Long = System.currentTimeMillis()
)
