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
package com.fialkaapp.fialka.tor

import android.content.Context
import com.fialkaapp.fialka.data.local.FialkaDatabase
import com.fialkaapp.fialka.data.model.OutboxMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.min

/**
 * Background retry loop for outbox messages in NORMAL mode.
 * Periodically attempts to deliver queued frames via TorTransport.
 * Exponential backoff on failure, max 50 retries per message.
 */
object OutboxManager {

    private const val RETRY_INTERVAL_MS = 60_000L
    private const val MAX_RETRY_DELAY_MS = 30 * 60_000L

    private var retryJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun start() {
        if (retryJob != null) return
        retryJob = scope.launch {
            while (isActive) {
                try { processOutbox() } catch (_: Exception) {}
                delay(RETRY_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        retryJob?.cancel()
        retryJob = null
    }

    /**
     * Queue a frame for later delivery.
     */
    suspend fun enqueue(
        destinationOnion: String,
        frameType: Byte,
        payload: ByteArray
    ) {
        val dao = FialkaDatabase.getInstance(appContext).outboxDao()
        val msg = OutboxMessage(
            id = UUID.randomUUID().toString(),
            destinationOnion = destinationOnion,
            frameType = frameType.toInt(),
            payload = payload,
            createdAt = System.currentTimeMillis()
        )
        dao.insert(msg)
    }

    /**
     * Send a frame immediately, falling back to outbox if delivery fails.
     * @return true if delivered now, false if queued for retry.
     */
    suspend fun sendOrQueue(
        onionAddress: String,
        frame: TorTransport.Frame
    ): Boolean {
        val response = TorTransport.sendFrame(onionAddress, frame = frame)
        if (response != null &&
            response.type == TorTransport.TYPE_ACK &&
            response.payload.isNotEmpty() &&
            response.payload[0] == TorTransport.ACK_OK
        ) {
            return true
        }
        enqueue(onionAddress, frame.type, frame.payload)
        return false
    }

    private suspend fun processOutbox() {
        val dao = FialkaDatabase.getInstance(appContext).outboxDao()
        dao.deleteExhaustedRetries()

        val pending = dao.getPendingMessages()
        for (msg in pending) {
            dao.updateStatus(msg.id, OutboxMessage.STATUS_SENDING)

            val frame = TorTransport.Frame(msg.frameType.toByte(), msg.payload)
            val response = TorTransport.sendFrame(msg.destinationOnion, frame = frame)

            if (response != null &&
                response.type == TorTransport.TYPE_ACK &&
                response.payload.isNotEmpty() &&
                response.payload[0] == TorTransport.ACK_OK
            ) {
                dao.deleteById(msg.id)
            } else {
                val backoff = min(
                    RETRY_INTERVAL_MS * (1L shl min(msg.retryCount, 10)),
                    MAX_RETRY_DELAY_MS
                )
                dao.updateRetry(
                    msg.id,
                    OutboxMessage.STATUS_PENDING,
                    System.currentTimeMillis() + backoff
                )
            }
        }
    }
}
