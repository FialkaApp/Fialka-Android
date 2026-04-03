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
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.data.local.FialkaDatabase
import com.fialkaapp.fialka.data.model.MessageLocal
import com.fialkaapp.fialka.data.model.OutboxMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import com.fialkaapp.fialka.tor.TorState
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

    /** Result of a send attempt */
    enum class DeliveryResult { DIRECT, MAILBOX, QUEUED }

    private var retryJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var appContext: Context

    fun init(context: Context) {
        appContext = context.applicationContext
        // Migrate any messages stuck in SENDING (from a previous crash mid-send) to PENDING
        scope.launch {
            try {
                FialkaDatabase.getInstance(appContext).messageLocalDao()
                    .migrateSendingToPending()
            } catch (_: Exception) {}
        }
    }

    fun start() {
        if (retryJob != null) return
        retryJob = scope.launch {
            while (isActive) {
                try { processOutbox() } catch (e: Exception) {
                    android.util.Log.e("OutboxManager", "processOutbox failed: ${e.message}", e)
                }
                delay(RETRY_INTERVAL_MS)
            }
        }
        // TAP: broadcast presence every time the onion is (re)published
        scope.launch {
            var lastPublished = false
            TorManager.state.collect { state ->
                val isPublished = state is TorState.ONION_PUBLISHED
                if (isPublished && !lastPublished) {
                    broadcastPresence()
                }
                lastPublished = isPublished
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
        payload: ByteArray,
        fallbackOnion: String? = null,
        messageLocalId: String? = null
    ) {
        val dao = FialkaDatabase.getInstance(appContext).outboxDao()
        val msg = OutboxMessage(
            id = UUID.randomUUID().toString(),
            destinationOnion = destinationOnion,
            frameType = frameType.toInt(),
            payload = payload,
            createdAt = System.currentTimeMillis(),
            fallbackOnion = fallbackOnion,
            messageLocalId = messageLocalId
        )
        dao.insert(msg)
    }

    /**
     * Send a frame immediately via direct P2P, then try DEPOSIT on mailbox, then queue.
     * @return the delivery result: DIRECT, MAILBOX, or QUEUED.
     */
    suspend fun sendOrQueue(
        onionAddress: String,
        frame: TorTransport.Frame,
        fallbackOnion: String? = null,
        messageLocalId: String? = null
    ): DeliveryResult {
        // 1. Try direct P2P
        val response = TorTransport.sendFrame(onionAddress, frame = frame)
        if (response != null &&
            response.type == TorTransport.TYPE_ACK &&
            response.payload.isNotEmpty() &&
            response.payload[0] == TorTransport.ACK_OK
        ) {
            return DeliveryResult.DIRECT
        }

        // 2. Try DEPOSIT on recipient's mailbox immediately
        if (!fallbackOnion.isNullOrEmpty()) {
            try {
                val recipientEd25519 = resolveRecipientEd25519(onionAddress)
                if (recipientEd25519 != null) {
                    val deposited = MailboxClientManager.depositToMailbox(
                        fallbackOnion, recipientEd25519, frame.type, frame.payload
                    )
                    if (deposited) return DeliveryResult.MAILBOX
                }
            } catch (_: Exception) {}
        }

        // 3. Both failed — queue for background retry
        enqueue(onionAddress, frame.type, frame.payload, fallbackOnion, messageLocalId)
        return DeliveryResult.QUEUED
    }

    private suspend fun processOutbox() {
        val dao = FialkaDatabase.getInstance(appContext).outboxDao()
        val messageDao = FialkaDatabase.getInstance(appContext).messageLocalDao()
        dao.deleteExhaustedRetries()

        val pending = dao.getPendingMessages()
        for (msg in pending) {
            // ✅ Plus de STATUS_SENDING — évite les messages orphelins après crash

            // Try direct P2P delivery first
            val frame = TorTransport.Frame(msg.frameType.toByte(), msg.payload)
            val response = TorTransport.sendFrame(msg.destinationOnion, frame = frame)

            if (response != null &&
                response.type == TorTransport.TYPE_ACK &&
                response.payload.isNotEmpty() &&
                response.payload[0] == TorTransport.ACK_OK
            ) {
                dao.deleteById(msg.id)
                // Update linked message status to SENT (direct)
                msg.messageLocalId?.let {
                    try { messageDao.updateDeliveryStatus(it, MessageLocal.DELIVERY_SENT) } catch (_: Exception) {}
                }
                continue
            }

            // Direct P2P failed — try DEPOSIT on recipient's mailbox if available
            if (!msg.fallbackOnion.isNullOrEmpty()) {
                try {
                    val recipientEd25519 = resolveRecipientEd25519(msg.destinationOnion)
                    if (recipientEd25519 != null) {
                        val deposited = MailboxClientManager.depositToMailbox(
                            msg.fallbackOnion,
                            recipientEd25519,
                            msg.frameType.toByte(),
                            msg.payload
                        )
                        if (deposited) {
                            dao.deleteById(msg.id)
                            // Update linked message status to MAILBOX
                            msg.messageLocalId?.let {
                                try { messageDao.updateDeliveryStatus(it, MessageLocal.DELIVERY_MAILBOX) } catch (_: Exception) {}
                            }
                            continue
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("OutboxManager", "Mailbox deposit failed for ${msg.id}: ${e.message}")
                }
            }

            // Both failed — schedule retry with backoff
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

    /**
     * TAP — "I'm online" broadcast.
     * Sends a TYPE_PRESENCE frame to every accepted contact's .onion.
     * Also embeds our mailboxOnion so contacts can update their fallback address.
     * Fire-and-forget — not queued, best-effort only.
     * After broadcast, immediately flushes outbox entries for each reachable contact.
     */
    fun broadcastPresence() {
        scope.launch {
            try {
                val db = FialkaDatabase.getInstance(appContext)
                val conversations = db.conversationDao().getAcceptedConversations()
                val myMailbox = MailboxClientManager.getMailboxOnion()
                val myPubKey = CryptoManager.getPublicKey() ?: return@launch
                val mySigningPub = CryptoManager.getSigningPublicKeyBase64()

                val presencePayload = JSONObject().apply {
                    put("senderPublicKey", myPubKey)
                    put("signingPublicKey", mySigningPub)
                    if (myMailbox != null) put("mailboxOnion", myMailbox)
                }.toString().toByteArray(Charsets.UTF_8)

                for (conv in conversations) {
                    val onion = conv.participantOnionAddress.ifEmpty { continue }
                    try {
                        val frame = TorTransport.Frame(TorTransport.TYPE_PRESENCE, presencePayload)
                        val resp = TorTransport.sendFrame(onion, frame = frame)
                        if (resp?.type == TorTransport.TYPE_ACK) {
                            // Contact is online — flush outbox for them immediately
                            processOutboxForContact(onion)
                        }
                    } catch (_: Exception) {}
                }
            } catch (e: Exception) {
                android.util.Log.w("OutboxManager", "broadcastPresence failed: ${e.message}")
            }
        }
    }

    /**
     * Immediately retry all pending outbox messages destined for [contactOnion].
     * Called when we receive a PRESENCE frame (contact came online) or after a
     * successful broadcastPresence() ACK (we came online and contact responded).
     */
    suspend fun processOutboxForContact(contactOnion: String) {
        try {
            val dao = FialkaDatabase.getInstance(appContext).outboxDao()
            val messageDao = FialkaDatabase.getInstance(appContext).messageLocalDao()
            val pending = dao.getPendingMessagesForOnion(contactOnion)
            for (msg in pending) {
                val frame = TorTransport.Frame(msg.frameType.toByte(), msg.payload)
                val response = TorTransport.sendFrame(msg.destinationOnion, frame = frame)
                if (response != null &&
                    response.type == TorTransport.TYPE_ACK &&
                    response.payload.isNotEmpty() &&
                    response.payload[0] == TorTransport.ACK_OK
                ) {
                    dao.deleteById(msg.id)
                    msg.messageLocalId?.let {
                        try { messageDao.updateDeliveryStatus(it, MessageLocal.DELIVERY_SENT) } catch (_: Exception) {}
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("OutboxManager", "processOutboxForContact($contactOnion) failed: ${e.message}")
        }
    }

    /**
     * Resolve the Ed25519 public key (raw 32 bytes) for a recipient identified by .onion address.
     * Returns null — and skips mailbox deposit — if signingPublicKey is absent (X25519 fallback
     * would corrupt the mailbox member lookup which expects raw Ed25519).
     */
    private suspend fun resolveRecipientEd25519(onionAddress: String): ByteArray? {
        return try {
            val db = FialkaDatabase.getInstance(appContext)
            val contact = db.contactDao().getContactByOnionAddress(onionAddress) ?: run {
                android.util.Log.w("OutboxManager", "resolveRecipientEd25519: contact introuvable pour $onionAddress")
                return null
            }
            // Must be the raw Ed25519 signing key (32 bytes) — X25519 would corrupt the mailbox member lookup
            val keyB64 = contact.signingPublicKey ?: run {
                android.util.Log.w("OutboxManager", "resolveRecipientEd25519: signingPublicKey absent pour $onionAddress — mailbox deposit ignoré")
                return null
            }
            android.util.Base64.decode(keyB64, android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("OutboxManager", "resolveRecipientEd25519 failed: ${e.message}")
            null
        }
    }
}
