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
import com.fialkaapp.fialka.data.model.ContactRequest
import com.fialkaapp.fialka.data.model.P2PMessage
import com.fialkaapp.fialka.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * P2P frame handler for NORMAL mode.
 *
 * Implements [TorTransport.FrameListener] to receive incoming TYPE_MESSAGE,
 * TYPE_CONTACT_REQ, TYPE_KEY_BUNDLE, TYPE_FILE_CHUNK, and TYPE_PING frames
 * from remote peers connecting to our .onion address.
 *
 * Incoming messages are dispatched to ChatRepository for decryption and storage.
 * Contact requests are emitted on a SharedFlow for the UI layer to observe.
 */
object P2PServer : TorTransport.FrameListener {

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _incomingRequests = MutableSharedFlow<ContactRequest>(extraBufferCapacity = 16)
    val incomingRequests: SharedFlow<ContactRequest> = _incomingRequests.asSharedFlow()

    private val _incomingAcceptances = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val incomingAcceptances: SharedFlow<String> = _incomingAcceptances.asSharedFlow()

    private val _fingerprintEvents = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val fingerprintEvents: SharedFlow<Pair<String, String>> = _fingerprintEvents.asSharedFlow()

    private val _ephemeralEvents = MutableSharedFlow<Pair<String, Long>>(extraBufferCapacity = 16)
    val ephemeralEvents: SharedFlow<Pair<String, Long>> = _ephemeralEvents.asSharedFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun start() {
        TorTransport.startServer(frameListener = this)
    }

    fun stop() {
        TorTransport.stopServer()
    }

    // ══════════════════════════════════════════
    //  FRAME DISPATCH
    // ══════════════════════════════════════════

    override suspend fun onFrame(frame: TorTransport.Frame): TorTransport.Frame? {
        return when (frame.type) {
            TorTransport.TYPE_MESSAGE -> handleMessage(frame.payload)
            TorTransport.TYPE_CONTACT_REQ -> handleContactRequest(frame.payload)
            TorTransport.TYPE_KEY_BUNDLE -> handleKeyBundle(frame.payload)
            TorTransport.TYPE_FILE_CHUNK -> handleFileChunk(frame.payload)
            TorTransport.TYPE_PING -> TorTransport.ackOk()
            TorTransport.TYPE_PRESENCE -> handlePresence(frame.payload)
            // Mailbox frames hitting P2P server → explicit rejection
            TorTransport.TYPE_JOIN,
            TorTransport.TYPE_DEPOSIT,
            TorTransport.TYPE_FETCH,
            TorTransport.TYPE_LEAVE,
            TorTransport.TYPE_INVITE_REQ,
            TorTransport.TYPE_REVOKE,
            TorTransport.TYPE_LIST_MEMBERS -> rejectMailboxFrame()
            else -> TorTransport.ackError("unknown frame type")
        }
    }

    /** Return TYPE_JOIN_RESP rejection so mailbox clients get a proper error. */
    private fun rejectMailboxFrame(): TorTransport.Frame {
        val reason = "not a mailbox".toByteArray(Charsets.UTF_8)
        val buf = java.nio.ByteBuffer.allocate(1 + reason.size)
        buf.put(TorTransport.JOIN_REJECTED)
        buf.put(reason)
        return TorTransport.Frame(TorTransport.TYPE_JOIN_RESP, buf.array())
    }

    // ══════════════════════════════════════════
    //  TYPE_MESSAGE (0x01) — encrypted chat message
    // ══════════════════════════════════════════

    private suspend fun handleMessage(payload: ByteArray): TorTransport.Frame {
        return try {
            val json = JSONObject(String(payload, Charsets.UTF_8))
            val conversationId = json.getString("conversationId")

            val wireMessage = P2PMessage(
                ciphertext = json.getString("ciphertext"),
                iv = json.getString("iv"),
                createdAt = json.getLong("createdAt"),
                ephemeralKey = json.optString("ephemeralKey", ""),
                signature = json.optString("signature", ""),
                kemCiphertext = json.optString("kemCiphertext", ""),
                mldsaSignature = json.optString("mldsaSignature", "")
            )

            val repository = ChatRepository(appContext)
            val received = repository.receiveMessage(conversationId, wireMessage)

            // Mettre à jour le mailboxOnion de l'expéditeur si fourni
            // Cela permet de le joindre via mailbox même s'il a rejoint la mailbox après le handshake
            val senderMailboxOnion = json.optString("senderMailboxOnion", "")
            if (senderMailboxOnion.isNotEmpty() && received != null) {
                try {
                    val conv = repository.getConversation(conversationId)
                    if (conv != null && conv.participantMailboxOnion != senderMailboxOnion) {
                        repository.updateContactMailbox(conv.participantPublicKey, senderMailboxOnion)
                        android.util.Log.i("P2PServer", "mailboxOnion mis à jour pour conv $conversationId → $senderMailboxOnion")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("P2PServer", "updateContactMailbox failed: ${e.message}")
                }
            }

            TorTransport.ackOk()
        } catch (_: Exception) {
            TorTransport.ackError("malformed message")
        }
    }

    // ══════════════════════════════════════════
    //  TYPE_CONTACT_REQ (0x06) — incoming contact request
    // ══════════════════════════════════════════

    private fun handleContactRequest(payload: ByteArray): TorTransport.Frame {
        return try {
            val json = JSONObject(String(payload, Charsets.UTF_8))

            // Handle acceptance notifications first — they don't have senderDisplayName
            if (json.optBoolean("accepted", false)) {
                val acceptedConvId = json.getString("conversationId")
                val senderMailbox = if (json.has("mailboxOnion")) json.getString("mailboxOnion") else null
                // Save sender's mailbox onion if provided
                if (senderMailbox != null) {
                    val senderPubKey = json.optString("senderPublicKey", "")
                    if (senderPubKey.isNotEmpty()) {
                        scope.launch {
                            try {
                                val repo = ChatRepository(appContext)
                                repo.updateContactMailbox(senderPubKey, senderMailbox)
                            } catch (_: Exception) {}
                        }
                    }
                }
                scope.launch { _incomingAcceptances.emit(acceptedConvId) }
                return TorTransport.ackOk()
            }

            val request = ContactRequest(
                senderPublicKey = json.getString("senderPublicKey"),
                senderDisplayName = json.getString("senderDisplayName"),
                conversationId = json.getString("conversationId"),
                createdAt = json.getLong("createdAt"),
                senderSigningPublicKey = if (json.has("senderSigningPublicKey")) json.getString("senderSigningPublicKey") else null,
                senderMailboxOnion = if (json.has("mailboxOnion")) json.getString("mailboxOnion") else null
            )

            scope.launch { _incomingRequests.emit(request) }

            TorTransport.ackOk()
        } catch (_: Exception) {
            TorTransport.ackError("malformed contact request")
        }
    }

    // ══════════════════════════════════════════
    //  TYPE_KEY_BUNDLE (0x03) — ML-KEM + ML-DSA key exchange
    // ══════════════════════════════════════════

    private suspend fun handleKeyBundle(payload: ByteArray): TorTransport.Frame {
        return try {
            val json = JSONObject(String(payload, Charsets.UTF_8))
            val senderPublicKey = json.getString("senderPublicKey")
            val mlkemPublicKey = json.optString("mlkemPublicKey", "")
            val mldsaPublicKey = json.optString("mldsaPublicKey", "")
            val signingPublicKey = json.optString("signingPublicKey", "")
            val mailboxOnion = json.optString("mailboxOnion", "")

            // Update the contact's keys in local DB
            val repository = ChatRepository(appContext)
            val contact = repository.getContactByPublicKey(senderPublicKey)
            if (contact != null) {
                var updated = contact
                if (mlkemPublicKey.isNotEmpty() && contact.mlkemPublicKey == null) {
                    updated = updated.copy(mlkemPublicKey = mlkemPublicKey)
                }
                if (mldsaPublicKey.isNotEmpty() && contact.mldsaPublicKey == null) {
                    updated = updated.copy(mldsaPublicKey = mldsaPublicKey)
                }
                if (signingPublicKey.isNotEmpty() && contact.signingPublicKey == null) {
                    updated = updated.copy(signingPublicKey = signingPublicKey)
                }
                if (updated !== contact) {
                    repository.updateContact(updated)
                }
                // Update mailbox address if sender included it
                if (mailboxOnion.isNotEmpty()) {
                    try { repository.updateContactMailbox(senderPublicKey, mailboxOnion) } catch (_: Exception) {}
                }
            }

            TorTransport.ackOk()
        } catch (_: Exception) {
            TorTransport.ackError("malformed key bundle")
        }
    }

    // ══════════════════════════════════════════
    //  TYPE_PRESENCE (0x13) — contact came online
    // ══════════════════════════════════════════

    /**
     * Handles incoming TAP / presence heartbeat.
     * The sender tells us they are online plus optionally their current mailboxOnion.
     * We:
     *  1. Update the contact's mailboxOnion in DB if it changed.
     *  2. Flush our outbox for this contact immediately (no need to wait 60s).
     *  3. Return ACK_OK so the sender knows we received it.
     */
    private suspend fun handlePresence(payload: ByteArray): TorTransport.Frame {
        try {
            if (payload.isNotEmpty()) {
                val json = JSONObject(String(payload, Charsets.UTF_8))
                val senderPublicKey = json.optString("senderPublicKey", "")
                val mailboxOnion = json.optString("mailboxOnion", "")
                val signingPublicKey = json.optString("signingPublicKey", "")

                if (senderPublicKey.isNotEmpty()) {
                    val repository = ChatRepository(appContext)
                    // Update mailbox address if provided and changed
                    if (mailboxOnion.isNotEmpty()) {
                        try { repository.updateContactMailbox(senderPublicKey, mailboxOnion) } catch (_: Exception) {}
                    }
                    // Update signing key if we didn't have it yet
                    if (signingPublicKey.isNotEmpty()) {
                        val contact = repository.getContactByPublicKey(senderPublicKey)
                        if (contact != null && contact.signingPublicKey == null) {
                            try { repository.updateContact(contact.copy(signingPublicKey = signingPublicKey)) } catch (_: Exception) {}
                        }
                    }
                    // Resolve their onion and flush outbox for them
                    val contact = repository.getContactByPublicKey(senderPublicKey)
                    val onion = contact?.onionAddress ?: ""
                    if (onion.isNotEmpty()) {
                        scope.launch { OutboxManager.processOutboxForContact(onion) }
                    }
                }
            }
        } catch (_: Exception) {}
        return TorTransport.ackOk()
    }

    // ══════════════════════════════════════════
    //  TYPE_FILE_CHUNK (0x02) — encrypted file data
    // ══════════════════════════════════════════

    /** Pending file chunks keyed by "conversationId:fileName" */
    private val pendingFileChunks = java.util.concurrent.ConcurrentHashMap<String, ByteArray>()

    /**
     * Pending file metadata — registered when metadata arrives before the chunk.
     * Keyed by "conversationId:fileName", value = (messageLocalId, keyBase64, ivBase64, isOneShot)
     */
    private val pendingFileMetadata = java.util.concurrent.ConcurrentHashMap<String, PendingFileMeta>()

    data class PendingFileMeta(
        val messageLocalId: String,
        val conversationId: String,
        val fileName: String,
        val keyBase64: String,
        val ivBase64: String,
        val isOneShot: Boolean
    )

    private fun handleFileChunk(payload: ByteArray): TorTransport.Frame {
        return try {
            val json = JSONObject(String(payload, Charsets.UTF_8))
            val conversationId = json.getString("conversationId")
            val fileName = json.getString("fileName")
            val dataBase64 = json.getString("data")
            val key = "$conversationId:$fileName"
            val encryptedBytes = android.util.Base64.decode(dataBase64, android.util.Base64.NO_WRAP)

            // Check if metadata already arrived and is waiting for this chunk
            val meta = pendingFileMetadata.remove(key)
            if (meta != null) {
                // Metadata arrived first — finalize the message now
                scope.launch {
                    try {
                        val repository = ChatRepository(appContext)
                        repository.finalizeFileMessage(meta.messageLocalId, meta.conversationId, meta.fileName, encryptedBytes, meta.keyBase64, meta.ivBase64, meta.isOneShot)
                    } catch (_: Exception) {}
                }
            } else {
                // Chunk arrived first — store it for when metadata arrives
                pendingFileChunks[key] = encryptedBytes
            }
            TorTransport.ackOk()
        } catch (_: Exception) {
            TorTransport.ackError("malformed file chunk")
        }
    }

    /**
     * Consume a pending file chunk (returns encrypted bytes, or null if not yet arrived).
     */
    fun consumePendingFileChunk(conversationId: String, fileName: String): ByteArray? {
        val key = "$conversationId:$fileName"
        return pendingFileChunks.remove(key)
    }

    /**
     * Register that metadata arrived but file chunk hasn't yet.
     * When the chunk arrives later, handleFileChunk will finalize the message.
     */
    fun registerPendingFileMetadata(meta: PendingFileMeta) {
        val key = "${meta.conversationId}:${meta.fileName}"
        pendingFileMetadata[key] = meta
    }

    /**
     * Send a contact request to a remote peer's .onion via TYPE_CONTACT_REQ.
     */
    suspend fun sendContactRequest(
        recipientOnion: String,
        senderPublicKey: String,
        senderDisplayName: String,
        conversationId: String,
        senderSigningPublicKey: String?
    ): Boolean {
        val json = JSONObject().apply {
            put("senderPublicKey", senderPublicKey)
            put("senderDisplayName", senderDisplayName)
            put("conversationId", conversationId)
            put("createdAt", System.currentTimeMillis())
            if (senderSigningPublicKey != null) {
                put("senderSigningPublicKey", senderSigningPublicKey)
            }
            val mailbox = MailboxClientManager.getMailboxOnion()
            if (mailbox != null) put("mailboxOnion", mailbox)
        }
        val frame = TorTransport.Frame(
            TorTransport.TYPE_CONTACT_REQ,
            json.toString().toByteArray(Charsets.UTF_8)
        )
        OutboxManager.sendOrQueue(recipientOnion, frame)
        return true
    }

    /**
     * Send an acceptance notification to the contact's .onion.
     */
    suspend fun sendAcceptance(recipientOnion: String, conversationId: String, myPublicKey: String): Boolean {
        val json = JSONObject().apply {
            put("conversationId", conversationId)
            put("senderPublicKey", myPublicKey)
            put("accepted", true)
            put("createdAt", System.currentTimeMillis())
            val mailbox = MailboxClientManager.getMailboxOnion()
            if (mailbox != null) put("mailboxOnion", mailbox)
        }
        val frame = TorTransport.Frame(
            TorTransport.TYPE_CONTACT_REQ,
            json.toString().toByteArray(Charsets.UTF_8)
        )
        OutboxManager.sendOrQueue(recipientOnion, frame)
        return true
    }

    /**
     * Send a KEY_BUNDLE containing ML-KEM + ML-DSA public keys via Tor P2P.
     */
    suspend fun sendKeyBundle(
        recipientOnion: String,
        senderPublicKey: String,
        mlkemPublicKey: String?,
        mldsaPublicKey: String?,
        signingPublicKey: String?
    ): Boolean {
        val json = JSONObject().apply {
            put("senderPublicKey", senderPublicKey)
            if (mlkemPublicKey != null) put("mlkemPublicKey", mlkemPublicKey)
            if (mldsaPublicKey != null) put("mldsaPublicKey", mldsaPublicKey)
            if (signingPublicKey != null) put("signingPublicKey", signingPublicKey)
        }
        val frame = TorTransport.Frame(
            TorTransport.TYPE_KEY_BUNDLE,
            json.toString().toByteArray(Charsets.UTF_8)
        )
        OutboxManager.sendOrQueue(recipientOnion, frame)
        return true
    }

    /**
     * Send a fingerprint verification event via TYPE_MESSAGE with special metadata.
     */
    suspend fun sendFingerprintEvent(recipientOnion: String, conversationId: String, event: String): Boolean {
        val json = JSONObject().apply {
            put("conversationId", conversationId)
            put("type", "fingerprint")
            put("event", event)
        }
        val frame = TorTransport.Frame(
            TorTransport.TYPE_MESSAGE,
            json.toString().toByteArray(Charsets.UTF_8)
        )
        OutboxManager.sendOrQueue(recipientOnion, frame)
        return true
    }

    /**
     * Send an ephemeral duration change via TYPE_MESSAGE with special metadata.
     */
    suspend fun sendEphemeralDuration(recipientOnion: String, conversationId: String, durationMs: Long): Boolean {
        val json = JSONObject().apply {
            put("conversationId", conversationId)
            put("type", "ephemeral")
            put("duration", durationMs)
        }
        val frame = TorTransport.Frame(
            TorTransport.TYPE_MESSAGE,
            json.toString().toByteArray(Charsets.UTF_8)
        )
        OutboxManager.sendOrQueue(recipientOnion, frame)
        return true
    }
}
