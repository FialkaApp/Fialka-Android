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
    val recvIndex: Int = 0          // Next expected receiving message index
)
