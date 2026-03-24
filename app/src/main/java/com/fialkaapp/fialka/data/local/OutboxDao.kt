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
import com.fialkaapp.fialka.data.model.OutboxMessage

@Dao
interface OutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: OutboxMessage)

    @Query("SELECT * FROM outbox WHERE status = :status AND nextRetryAt <= :now ORDER BY createdAt ASC")
    suspend fun getPendingMessages(
        status: Int = OutboxMessage.STATUS_PENDING,
        now: Long = System.currentTimeMillis()
    ): List<OutboxMessage>

    @Query("UPDATE outbox SET status = :status, retryCount = retryCount + 1, nextRetryAt = :nextRetryAt WHERE id = :id")
    suspend fun updateRetry(id: String, status: Int, nextRetryAt: Long)

    @Query("UPDATE outbox SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: Int)

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM outbox WHERE status = :status")
    suspend fun deleteByStatus(status: Int)

    @Query("SELECT COUNT(*) FROM outbox WHERE status = :status")
    suspend fun countByStatus(status: Int): Int

    @Query("DELETE FROM outbox WHERE retryCount >= 50")
    suspend fun deleteExhaustedRetries()
}
