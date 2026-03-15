package com.securechat.data.local

import androidx.room.*
import com.securechat.data.model.RatchetState

@Dao
interface RatchetStateDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(state: RatchetState)

    @Query("SELECT * FROM ratchet_state WHERE conversationId = :conversationId")
    suspend fun getState(conversationId: String): RatchetState?

    @Query("DELETE FROM ratchet_state WHERE conversationId = :conversationId")
    suspend fun deleteState(conversationId: String)
}
