/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A group conversation.
 *
 * Security model:
 *   - groupKey (AES-256, 32 bytes Base64) is the shared symmetric key for message content.
 *   - Each invitation transports groupKey via the recipient's existing Double Ratchet channel
 *     (E2E encrypted, PFS-protected).
 *   - Every group message is additionally signed with the sender's Ed25519 key so receivers
 *     can verify authenticity even though groupKey is shared.
 */
@Entity(tableName = "groups")
data class GroupLocal(
    @PrimaryKey
    val groupId: String,                // UUID — stable identifier for this group
    val name: String,                   // Display name, editable by OWNER/ADMIN
    val groupKey: String,               // Base64 AES-256 symmetric key (32 bytes)
    val createdAt: Long = System.currentTimeMillis(),
    val createdByPubKey: String,        // publicKey of the creator
    val myRole: String = ROLE_MEMBER,   // "OWNER", "ADMIN", or "MEMBER"
    val memberCount: Int = 1,           // Cached member count for display
    val lastMessage: String = "",       // Last message preview
    val lastMessageTimestamp: Long = System.currentTimeMillis(),
    val unreadCount: Int = 0,
    /**
     * Invite policy: who can invite new members.
     * INVITE_ADMIN_ONLY (default) — only OWNER/ADMIN can invite.
     * INVITE_ALL — any member can invite.
     */
    val invitePolicy: String = INVITE_ADMIN_ONLY
) {
    companion object {
        const val ROLE_OWNER  = "OWNER"
        const val ROLE_ADMIN  = "ADMIN"
        const val ROLE_MEMBER = "MEMBER"

        const val INVITE_ADMIN_ONLY = "INVITE_ADMIN_ONLY"
        const val INVITE_ALL        = "INVITE_ALL"
    }
}
