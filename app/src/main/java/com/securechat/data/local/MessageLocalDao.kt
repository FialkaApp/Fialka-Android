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
package com.securechat.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.securechat.data.model.MessageLocal

@Dao
interface MessageLocalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageLocal)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessagesForConversation(conversationId: String): LiveData<List<MessageLocal>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(conversationId: String): MessageLocal?

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)

    @Query("DELETE FROM messages WHERE localId = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND senderPublicKey = :senderKey AND timestamp = :timestamp")
    suspend fun messageExists(conversationId: String, senderKey: String, timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND isMine = 0 AND timestamp = :timestamp")
    suspend fun receivedMessageExists(conversationId: String, timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND plaintext != '' AND isMine = 0")
    suspend fun countReceivedMessages(conversationId: String): Int

    @Query("DELETE FROM messages WHERE expiresAt > 0 AND expiresAt < :now")
    suspend fun deleteExpiredMessages(now: Long = System.currentTimeMillis())

    @Query("SELECT * FROM messages WHERE expiresAt > 0 AND expiresAt < :now")
    suspend fun getExpiredMessages(now: Long = System.currentTimeMillis()): List<MessageLocal>

    @Query("UPDATE messages SET expiresAt = :expiresAt WHERE localId = :messageId")
    suspend fun setExpiresAt(messageId: String, expiresAt: Long)

    @Query("UPDATE messages SET oneShotOpened = 1, localFilePath = NULL WHERE localId = :messageId")
    suspend fun markOneShotOpened(messageId: String)

    @Query("UPDATE messages SET oneShotOpened = 1 WHERE localId = :messageId")
    suspend fun flagOneShotOpened(messageId: String)

    @Query("SELECT * FROM messages WHERE localId = :messageId LIMIT 1")
    suspend fun getMessageById(messageId: String): MessageLocal?

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId AND timestamp = :timestamp LIMIT 1")
    suspend fun getMessageByTimestamp(conversationId: String, timestamp: Long): MessageLocal?

    /**
     * Activate ephemeral timers for received messages that have been "read" (chat opened).
     * Sets expiresAt = now + ephemeralDuration for received messages where:
     *  - isMine = false (received)
     *  - ephemeralDuration > 0 (ephemeral is enabled)
     *  - expiresAt = 0 (timer not yet started — hasn't been read)
     */
    @Query("UPDATE messages SET expiresAt = :now + ephemeralDuration WHERE conversationId = :conversationId AND isMine = 0 AND ephemeralDuration > 0 AND expiresAt = 0")
    suspend fun activateEphemeralTimersForRead(conversationId: String, now: Long)
}
