package com.securechat.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores the Double Ratchet state for a conversation.
 *
 * Each conversation has:
 *  - A root key that evolves with each ratchet step
 *  - A sending chain key for encrypting outgoing messages
 *  - A receiving chain key for decrypting incoming messages
 *  - Counters to track message indices (for ordering and skipped messages)
 *
 * All keys are stored as Base64 strings.
 */
@Entity(tableName = "ratchet_state")
data class RatchetState(
    @PrimaryKey
    val conversationId: String,
    val rootKey: String,            // Base64 — current root key
    val sendChainKey: String,       // Base64 — current sending chain key
    val recvChainKey: String,       // Base64 — current receiving chain key
    val sendIndex: Int = 0,         // Next sending message index
    val recvIndex: Int = 0,         // Next expected receiving message index
    // --- Double Ratchet DH state ---
    val localDhPublic: String = "",   // Base64 — our current ephemeral DH public key
    val localDhPrivate: String = "",  // Base64 — our current ephemeral DH private key
    val remoteDhPublic: String = "",  // Base64 — their latest ephemeral DH public key
    // --- PQXDH (ML-KEM-768) state ---
    val remoteMlkemPublicKey: String = "",  // Base64 — recipient's ML-KEM public key (stored on init)
    val pqxdhInitialized: Boolean = false,  // true once the first PQXDH exchange is complete
    // Initiator: KEM ciphertext to attach to first outgoing message (cleared after send)
    val pendingKemCiphertext: String = "",
    // Responder: raw X25519 shared secret (Base64) held until first kemCiphertext arrives
    val pendingClassicSecret: String = ""
)
