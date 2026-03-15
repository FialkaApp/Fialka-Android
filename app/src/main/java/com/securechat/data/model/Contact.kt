package com.securechat.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A contact that the user has added.
 * Contains the contact's display name and public key.
 */
@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey
    val contactId: String,          // UUID
    val displayName: String,
    val publicKey: String,          // Base64-encoded X25519 public key
    val verificationStatus: String = "unverified", // "unverified" or "verified"
    val addedAt: Long = System.currentTimeMillis()
)
