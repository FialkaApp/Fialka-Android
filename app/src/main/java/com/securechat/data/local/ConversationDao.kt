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

    @Update
    suspend fun updateConversation(conversation: Conversation)

    @Delete
    suspend fun deleteConversation(conversation: Conversation)
}
