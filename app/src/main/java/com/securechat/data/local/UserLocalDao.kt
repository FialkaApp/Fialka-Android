package com.securechat.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.securechat.data.model.UserLocal

@Dao
interface UserLocalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserLocal)

    @Query("SELECT * FROM user_local LIMIT 1")
    suspend fun getUser(): UserLocal?

    @Query("SELECT * FROM user_local LIMIT 1")
    fun getUserLive(): LiveData<UserLocal?>

    @Update
    suspend fun updateUser(user: UserLocal)
}
