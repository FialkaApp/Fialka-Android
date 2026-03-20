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

    @Query("UPDATE conversations SET lastDeliveredAt = :timestamp WHERE conversationId = :conversationId AND :timestamp > lastDeliveredAt")
    suspend fun updateLastDeliveredAt(conversationId: String, timestamp: Long)

    @Query("SELECT * FROM conversations WHERE accepted = 1 AND dummyTrafficEnabled = 1")
    suspend fun getConversationsWithDummyTraffic(): List<Conversation>

    @Query("SELECT conversationId FROM conversations WHERE accepted = 0")
    suspend fun getPendingConversationIds(): List<String>

    @Delete
    suspend fun deleteConversation(conversation: Conversation)
}
