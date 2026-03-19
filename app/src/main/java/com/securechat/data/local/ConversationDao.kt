package com.securechat.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.securechat.data.model.Conversation

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation)

    @Query("SELECT * FROM conversations ORDER BY lastMessageTimestamp DESC")
    fun getAllConversations(): LiveData<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE conversationId = :conversationId LIMIT 1")
    suspend fun getConversationById(conversationId: String): Conversation?

    @Query("SELECT * FROM conversations WHERE participantPublicKey = :publicKey LIMIT 1")
    suspend fun getConversationByParticipantPublicKey(publicKey: String): Conversation?

    @Query("SELECT * FROM conversations WHERE accepted = 1")
    suspend fun getAcceptedConversations(): List<Conversation>

    @Query("SELECT * FROM conversations")
    suspend fun getAllConversationsList(): List<Conversation>

    @Update
    suspend fun updateConversation(conversation: Conversation)

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE conversationId = :conversationId")
    suspend fun incrementUnreadCount(conversationId: String)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE conversationId = :conversationId")
    suspend fun resetUnreadCount(conversationId: String)

    @Query("UPDATE conversations SET fingerprintVerified = :verified WHERE conversationId = :conversationId")
    suspend fun updateFingerprintVerified(conversationId: String, verified: Boolean)

    @Query("UPDATE conversations SET ephemeralDuration = :duration WHERE conversationId = :conversationId")
    suspend fun updateEphemeralDuration(conversationId: String, duration: Long)

    @Query("UPDATE conversations SET dummyTrafficEnabled = :enabled WHERE conversationId = :conversationId")
    suspend fun updateDummyTraffic(conversationId: String, enabled: Boolean)

    @Query("SELECT * FROM conversations WHERE accepted = 1 AND dummyTrafficEnabled = 1")
    suspend fun getConversationsWithDummyTraffic(): List<Conversation>

    @Delete
    suspend fun deleteConversation(conversation: Conversation)
}
