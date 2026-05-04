/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.fialkaapp.fialka.data.model.GroupLocal
import com.fialkaapp.fialka.data.model.GroupMember

@Dao
interface GroupDao {

    // ── Groups ────────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupLocal)

    @Query("SELECT * FROM groups ORDER BY lastMessageTimestamp DESC")
    fun getAllGroups(): LiveData<List<GroupLocal>>

    @Query("SELECT * FROM groups ORDER BY lastMessageTimestamp DESC")
    suspend fun getAllGroupsList(): List<GroupLocal>

    @Query("SELECT * FROM `groups` WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroupById(groupId: String): GroupLocal?

    @Query("UPDATE `groups` SET lastMessage = :msg, lastMessageTimestamp = :ts WHERE groupId = :groupId")
    suspend fun updateLastMessage(groupId: String, msg: String, ts: Long)

    @Query("UPDATE `groups` SET unreadCount = unreadCount + 1 WHERE groupId = :groupId")
    suspend fun incrementUnread(groupId: String)

    @Query("UPDATE `groups` SET unreadCount = 0 WHERE groupId = :groupId")
    suspend fun clearUnread(groupId: String)

    @Query("UPDATE `groups` SET memberCount = :count WHERE groupId = :groupId")
    suspend fun updateMemberCount(groupId: String, count: Int)

    @Query("UPDATE `groups` SET name = :name WHERE groupId = :groupId")
    suspend fun updateGroupName(groupId: String, name: String)

    @Query("DELETE FROM `groups` WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)

    // ── Members ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMember)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<GroupMember>)

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    suspend fun getMembersForGroup(groupId: String): List<GroupMember>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId")
    fun getMembersLive(groupId: String): LiveData<List<GroupMember>>

    @Query("SELECT * FROM group_members WHERE groupId = :groupId AND publicKey = :pubKey LIMIT 1")
    suspend fun getMember(groupId: String, pubKey: String): GroupMember?

    @Query("DELETE FROM group_members WHERE groupId = :groupId AND publicKey = :pubKey")
    suspend fun removeMember(groupId: String, pubKey: String)

    @Query("DELETE FROM group_members WHERE groupId = :groupId")
    suspend fun removeAllMembers(groupId: String)

    @Query("SELECT COUNT(*) FROM group_members WHERE groupId = :groupId")
    suspend fun getMemberCount(groupId: String): Int

    @Query("UPDATE group_members SET role = :role WHERE groupId = :groupId AND publicKey = :pubKey")
    suspend fun updateRole(groupId: String, pubKey: String, role: String)

    /** Update my own cached role in the groups table (synced when I receive PROMOTE/DEMOTE). */
    @Query("UPDATE `groups` SET myRole = :role WHERE groupId = :groupId")
    suspend fun updateMyRole(groupId: String, role: String)

    /** Update group invite policy. */
    @Query("UPDATE `groups` SET invitePolicy = :policy WHERE groupId = :groupId")
    suspend fun updateInvitePolicy(groupId: String, policy: String)
}
