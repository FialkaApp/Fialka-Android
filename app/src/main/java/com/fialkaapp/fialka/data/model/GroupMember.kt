/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.data.model

import androidx.room.Entity

/**
 * A member of a group.
 * Composite primary key: (groupId, publicKey) — one row per member per group.
 */
@Entity(
    tableName = "group_members",
    primaryKeys = ["groupId", "publicKey"]
)
data class GroupMember(
    val groupId: String,
    val publicKey: String,          // Ed25519 public key — global identity
    val displayName: String,
    val onionAddress: String = "",  // .onion for direct P2P delivery
    val mailboxOnion: String = "",  // mailbox fallback (may be empty)
    val role: String = GroupLocal.ROLE_MEMBER,
    val joinedAt: Long = System.currentTimeMillis()
)
