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

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId AND plaintext != '' AND isMine = 0")
    suspend fun countReceivedMessages(conversationId: String): Int
}
