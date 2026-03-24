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
package com.fialkaapp.fialka.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.fialkaapp.fialka.data.model.MailboxBlob
import com.fialkaapp.fialka.data.model.MailboxInvite
import com.fialkaapp.fialka.data.model.MailboxMember

@Dao
interface MailboxBlobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(blob: MailboxBlob)

    @Query("SELECT * FROM mailbox_blobs WHERE recipientPubKey = :pubKey ORDER BY depositedAt ASC")
    suspend fun getBlobsForRecipient(pubKey: String): List<MailboxBlob>

    @Query("DELETE FROM mailbox_blobs WHERE blobId = :blobId")
    suspend fun deleteById(blobId: String)

    @Query("DELETE FROM mailbox_blobs WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM mailbox_blobs")
    suspend fun countAll(): Int

    @Query("SELECT COALESCE(SUM(LENGTH(data)), 0) FROM mailbox_blobs")
    suspend fun totalSize(): Long

    @Query("DELETE FROM mailbox_blobs")
    suspend fun deleteAll()
}

@Dao
interface MailboxMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: MailboxMember)

    @Query("SELECT * FROM mailbox_members")
    suspend fun getAll(): List<MailboxMember>

    @Query("SELECT * FROM mailbox_members WHERE pubKey = :pubKey LIMIT 1")
    suspend fun getByPubKey(pubKey: String): MailboxMember?

    @Query("SELECT * FROM mailbox_members WHERE role = 'OWNER' LIMIT 1")
    suspend fun getOwner(): MailboxMember?

    @Query("SELECT COUNT(*) FROM mailbox_members")
    suspend fun count(): Int

    @Query("DELETE FROM mailbox_members WHERE pubKey = :pubKey")
    suspend fun deleteByPubKey(pubKey: String)

    @Query("DELETE FROM mailbox_members")
    suspend fun deleteAll()
}

@Dao
interface MailboxInviteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(invite: MailboxInvite)

    @Query("SELECT * FROM mailbox_invites WHERE code = :code AND used = 0 AND expiresAt > :now LIMIT 1")
    suspend fun getValidInvite(code: String, now: Long = System.currentTimeMillis()): MailboxInvite?

    @Query("UPDATE mailbox_invites SET used = 1, usedByPubKey = :pubKey WHERE code = :code")
    suspend fun markUsed(code: String, pubKey: String)

    @Query("DELETE FROM mailbox_invites WHERE expiresAt < :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM mailbox_invites WHERE used = 0 AND expiresAt > :now")
    suspend fun getActiveInvites(now: Long = System.currentTimeMillis()): List<MailboxInvite>

    @Query("DELETE FROM mailbox_invites")
    suspend fun deleteAll()
}
