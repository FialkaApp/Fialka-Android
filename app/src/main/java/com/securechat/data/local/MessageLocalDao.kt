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
