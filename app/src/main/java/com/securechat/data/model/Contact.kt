package com.securechat.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A contact that the user has added.
 * Contains the contact's display name and public key.
 */
@Entity(
    tableName = "contacts",
    indices = [Index(value = ["publicKey"])]
)
data class Contact(
    @PrimaryKey
    val contactId: String,          // UUID
    val displayName: String,
    val publicKey: String,          // Base64-encoded X25519 public key
    val verificationStatus: String = "unverified", // "unverified" or "verified"
    val addedAt: Long = System.currentTimeMillis(),
    val signingPublicKey: String? = null,  // Base64 Ed25519 public key for signature verification
    val mlkemPublicKey: String? = null     // Base64 ML-KEM-768 public key for PQXDH
)
