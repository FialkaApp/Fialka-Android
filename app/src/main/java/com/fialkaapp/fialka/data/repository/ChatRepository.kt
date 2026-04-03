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
package com.fialkaapp.fialka.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.crypto.DoubleRatchet
import com.fialkaapp.fialka.data.local.FialkaDatabase
import com.fialkaapp.fialka.data.model.*
import com.fialkaapp.fialka.tor.OutboxManager
import com.fialkaapp.fialka.tor.P2PServer
import com.fialkaapp.fialka.tor.TorTransport
import com.fialkaapp.fialka.util.EphemeralManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * ChatRepository — single source of truth for chat data.
 *
 * Coordinates between:
 *  - Room (local storage)
 *  - Tor P2P transport (.onion direct + mailbox fallback)
 *  - CryptoManager (encryption/decryption)
 *  - DoubleRatchet (X25519 DH ratchet + KDF chains — Perfect Forward Secrecy)
 *
 * Thread safety: ratchet operations are serialized per-conversation via Mutex
 * to prevent race conditions when multiple P2P messages arrive simultaneously.
 */
class ChatRepository(private val appContext: Context) {

    private val db = FialkaDatabase.getInstance(appContext)
    private val userDao = db.userLocalDao()
    private val contactDao = db.contactDao()
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageLocalDao()
    private val ratchetDao = db.ratchetStateDao()
    private val outboxDao = db.outboxDao()

    companion object {
        // Shared across all ChatRepository instances so that the global listener
        // (ConversationsViewModel) and the per-chat listener (ChatViewModel)
        // serialize ratchet operations on the same conversation.
        private val ratchetMutexes = mutableMapOf<String, Mutex>()

        /** Max ratchet steps to try during trial decryption (messageIndex hidden in ciphertext). */
        private const val MAX_RATCHET_SKIP = 100

        /** Opaque prefix for dummy traffic — silently dropped by receiver after decryption. */
        internal const val DUMMY_PREFIX = "\u0007\u001B\u0003"

        /** Prefix for file attachment messages sent via the ratchet. */
        internal const val FILE_PREFIX = "FILE|"

        internal fun getMutex(conversationId: String): Mutex {
            return synchronized(ratchetMutexes) {
                ratchetMutexes.getOrPut(conversationId) { Mutex() }
            }
        }

        internal fun clearMutexes() {
            synchronized(ratchetMutexes) { ratchetMutexes.clear() }
        }

        /** The conversation currently being viewed. Unread count won't increment for it. */
        @Volatile
        var currentlyViewedConversation: String? = null

        /** Timestamp of the last fingerprint event we pushed — used to filter self-echo. */
        @Volatile
        var lastFingerprintPushTimestamp: Long = 0L
    }

    // ========================================================================
    // USER
    // ========================================================================

    suspend fun getUser(): UserLocal? = userDao.getUser()

    fun getUserLive(): LiveData<UserLocal?> = userDao.getUserLive()

    suspend fun createUser(displayName: String): UserLocal {
        val publicKey = CryptoManager.generateIdentity()
        val user = UserLocal(
            userId = UUID.randomUUID().toString(),
            displayName = displayName,
            publicKey = publicKey
        )
        userDao.insertUser(user)
        return user
    }

    suspend fun createUserWithKey(displayName: String, publicKey: String): UserLocal {
        val user = UserLocal(
            userId = UUID.randomUUID().toString(),
            displayName = displayName,
            publicKey = publicKey
        )
        userDao.insertUser(user)
        return user
    }

    suspend fun updateDisplayName(newName: String) {
        val user = userDao.getUser() ?: return
        userDao.updateUser(user.copy(displayName = newName))
    }

    // ========================================================================
    // CONTACTS
    // ========================================================================

    fun getAllContacts(): LiveData<List<Contact>> = contactDao.getAllContacts()

    suspend fun addContact(
        displayName: String,
        publicKey: String,
        signingPublicKey: String? = null,
        mlkemPublicKey: String? = null,
        mldsaPublicKey: String? = null
    ): Contact {
        // Check for duplicate contact by public key
        val existing = contactDao.getContactByPublicKey(publicKey)
        if (existing != null) {
            // Update keys if we now have them but didn't before
            var updated = existing
            if (existing.signingPublicKey == null && signingPublicKey != null) {
                updated = updated.copy(signingPublicKey = signingPublicKey)
            }
            if (existing.mlkemPublicKey == null && mlkemPublicKey != null) {
                updated = updated.copy(mlkemPublicKey = mlkemPublicKey)
            }
            if (existing.mldsaPublicKey == null && mldsaPublicKey != null) {
                updated = updated.copy(mldsaPublicKey = mldsaPublicKey)
            }
            // Compute .onion if we now have signing key but didn't have onion
            if (existing.onionAddress == null && (updated.signingPublicKey ?: signingPublicKey) != null) {
                val sk = updated.signingPublicKey ?: signingPublicKey
                val onion = try {
                    val ed25519Bytes = android.util.Base64.decode(sk, android.util.Base64.NO_WRAP)
                    CryptoManager.computeOnionFromEd25519(ed25519Bytes)
                } catch (_: Exception) { null }
                if (onion != null) updated = updated.copy(onionAddress = onion)
            }
            if (updated !== existing) {
                contactDao.insertContact(updated)
                return updated
            }
            return existing
        }

        // Keys (ML-KEM, ML-DSA, signing) arrive via TYPE_KEY_BUNDLE in-band
        val onionAddress = if (signingPublicKey != null) {
            try {
                val ed25519Bytes = android.util.Base64.decode(signingPublicKey, android.util.Base64.NO_WRAP)
                CryptoManager.computeOnionFromEd25519(ed25519Bytes)
            } catch (_: Exception) { null }
        } else null

        val contact = Contact(
            contactId = UUID.randomUUID().toString(),
            displayName = displayName,
            publicKey = publicKey,
            signingPublicKey = signingPublicKey,
            mlkemPublicKey = mlkemPublicKey,
            mldsaPublicKey = mldsaPublicKey,
            onionAddress = onionAddress
        )
        contactDao.insertContact(contact)
        return contact
    }

    /** Update an existing contact (used by P2PServer when KEY_BUNDLE arrives). */
    suspend fun updateContact(contact: Contact) {
        contactDao.insertContact(contact)  // REPLACE strategy
    }

    suspend fun getContactByPublicKey(publicKey: String): Contact? =
        contactDao.getContactByPublicKey(publicKey)

    /** Update a contact's mailbox onion address (called when received via handshake). */
    suspend fun updateContactMailbox(publicKey: String, mailboxOnion: String) {
        val contact = contactDao.getContactByPublicKey(publicKey) ?: return
        if (contact.mailboxOnion != mailboxOnion) {
            contactDao.insertContact(contact.copy(mailboxOnion = mailboxOnion))
            // Also update any existing conversations with this contact
            val conv = conversationDao.getConversationByParticipantPublicKey(publicKey)
            if (conv != null && conv.participantMailboxOnion != mailboxOnion) {
                conversationDao.updateConversation(conv.copy(participantMailboxOnion = mailboxOnion))
            }
        }
    }

    // ========================================================================
    // CONVERSATIONS
    // ========================================================================

    fun getAllConversations(): LiveData<List<Conversation>> = conversationDao.getAllConversations()

    suspend fun getAcceptedConversationsList(): List<Conversation> =
        conversationDao.getAcceptedConversations()

    suspend fun getConversation(conversationId: String): Conversation? =
        conversationDao.getConversationById(conversationId)

    suspend fun createConversation(
        contactPublicKey: String,
        contactName: String,
        accepted: Boolean = true,
        conversationId: String? = null
    ): Conversation {
        val myPublicKey = userDao.getUser()?.publicKey
            ?: throw IllegalStateException("User not initialized")

        // Use provided ID, or look up existing one for this contact, or generate a random UUID
        val finalConversationId = conversationId
            ?: conversationDao.getConversationByParticipantPublicKey(contactPublicKey)?.conversationId
            ?: java.util.UUID.randomUUID().toString()

        // Check if conversation already exists
        val existing = conversationDao.getConversationById(finalConversationId)
        if (existing != null) return existing

        // Compute shared emoji fingerprint (96-bit, same on both sides)
        val sharedFingerprint = CryptoManager.getSharedFingerprint(myPublicKey, contactPublicKey)

        // Resolve contact's .onion address for P2P delivery
        val contact = contactDao.getContactByPublicKey(contactPublicKey)
        val onionAddress = contact?.onionAddress ?: ""
        val mailboxOnion = contact?.mailboxOnion ?: ""

        val conversation = Conversation(
            conversationId = finalConversationId,
            participantPublicKey = contactPublicKey,
            contactDisplayName = contactName,
            accepted = accepted,
            sharedFingerprint = sharedFingerprint,
            participantOnionAddress = onionAddress,
            participantMailboxOnion = mailboxOnion
        )
        conversationDao.insertConversation(conversation)

        // Initialize ratchet state for this conversation
        initializeRatchet(finalConversationId, myPublicKey, contactPublicKey)

        return conversation
    }

    private suspend fun updateConversationLastMessage(conversationId: String, message: String) {
        val conversation = conversationDao.getConversationById(conversationId) ?: return
        conversationDao.updateConversation(
            conversation.copy(
                lastMessage = message,
                lastMessageTimestamp = System.currentTimeMillis()
            )
        )
    }

    // ========================================================================
    // RATCHET INITIALIZATION
    // ========================================================================

    private suspend fun initializeRatchet(
        conversationId: String,
        myPublicKey: String,
        contactPublicKey: String
    ) {
        val ssClassic = CryptoManager.performKeyAgreement(contactPublicKey)
        val isInitiator = myPublicKey < contactPublicKey

        // Look up contact's ML-KEM key from the local DB (populated during addContact)
        val contact = contactDao.getContactByPublicKey(contactPublicKey)
        val remoteMlkemKey = contact?.mlkemPublicKey

        val ratchetState: RatchetState

        if (isInitiator) {
            if (remoteMlkemKey != null) {
                // PQXDH initiator path: encapsulate now, but start with CLASSIC chains.
                // Both sides must begin with the same classic secret so messages are
                // decryptable regardless of who sends first. The combined (PQ) root is
                // installed later — on send (initiator) or on receive (responder) — and
                // only affects the rootKey. Active send/recv chains stay classic until
                // the next natural DH ratchet step derives post-quantum chains from the
                // upgraded root.
                val (kemCt, ssPQ) = CryptoManager.mlkemEncaps(remoteMlkemKey)
                val ssPQBase64 = android.util.Base64.encodeToString(ssPQ, android.util.Base64.NO_WRAP)
                ssPQ.fill(0)
                val init = DoubleRatchet.initializeAsInitiator(ssClassic)
                ssClassic.fill(0)
                ratchetState = RatchetState(
                    conversationId = conversationId,
                    rootKey = init.rootKey,
                    sendChainKey = init.sendChainKey,
                    recvChainKey = init.recvChainKey,
                    sendIndex = 0,
                    recvIndex = 0,
                    localDhPublic = init.localDhPublic,
                    localDhPrivate = init.localDhPrivate,
                    remoteDhPublic = init.remoteDhPublic,
                    remoteMlkemPublicKey = remoteMlkemKey,
                    pqxdhInitialized = false,
                    pendingKemCiphertext = kemCt,
                    // Store ssPQ so the deferred PQXDH rootKey upgrade in sendMessage
                    // can recompute combined = performKeyAgreement() + ssPQ
                    pendingClassicSecret = ssPQBase64
                )
            } else {
                // Classic-only initiator fallback (contact hasn't published ML-KEM key)
                val init = DoubleRatchet.initializeAsInitiator(ssClassic)
                ssClassic.fill(0)
                ratchetState = RatchetState(
                    conversationId = conversationId,
                    rootKey = init.rootKey,
                    sendChainKey = init.sendChainKey,
                    recvChainKey = init.recvChainKey,
                    sendIndex = 0,
                    recvIndex = 0,
                    localDhPublic = init.localDhPublic,
                    localDhPrivate = init.localDhPrivate,
                    remoteDhPublic = init.remoteDhPublic,
                    pqxdhInitialized = true  // no ML-KEM → mark done
                )
            }
        } else {
            // Responder path: store ssClassic bytes — will combine with ssPQ when first
            // message carrying kemCiphertext arrives and re-initialize all chain keys then.
            val ssClassicBase64 = android.util.Base64.encodeToString(ssClassic, android.util.Base64.NO_WRAP)
            val init = DoubleRatchet.initializeAsResponder(ssClassic)
            ssClassic.fill(0)
            ratchetState = RatchetState(
                conversationId = conversationId,
                rootKey = init.rootKey,
                sendChainKey = init.sendChainKey,
                recvChainKey = init.recvChainKey,
                sendIndex = 0,
                recvIndex = 0,
                localDhPublic = init.localDhPublic,
                localDhPrivate = init.localDhPrivate,
                remoteDhPublic = init.remoteDhPublic,
                pendingClassicSecret = ssClassicBase64
            )
        }

        ratchetDao.insertOrUpdate(ratchetState)
    }

    private suspend fun getOrCreateRatchetState(
        conversationId: String,
        contactPublicKey: String
    ): RatchetState {
        val existing = ratchetDao.getState(conversationId)
        if (existing != null) return existing

        val myPublicKey = userDao.getUser()?.publicKey
            ?: throw IllegalStateException("User not initialized")
        initializeRatchet(conversationId, myPublicKey, contactPublicKey)
        return ratchetDao.getState(conversationId)!!
    }

    // ========================================================================
    // MESSAGES (with PFS ratchet — mutex-protected)
    // ========================================================================

    fun getMessages(conversationId: String): LiveData<List<MessageLocal>> =
        messageDao.getMessagesForConversation(conversationId)

    /**
     * Send a message with Perfect Forward Secrecy.
     * Protected by a per-conversation Mutex to prevent ratchet desynchronization.
     *
     * Order of operations (atomic w.r.t. ratchet state):
     * 1. Advance send chain → get unique message key
     * 2. Encrypt with AES-256-GCM
     * 3. Send via Tor P2P (.onion direct or mailbox store-and-forward)
     * 4. ONLY on success: persist new ratchet state + save message locally
     */
    suspend fun sendMessage(conversationId: String, plaintext: String): MessageLocal {
        val user = userDao.getUser()
            ?: throw IllegalStateException("User not initialized")
        val conversation = conversationDao.getConversationById(conversationId)
            ?: throw IllegalStateException("Conversation not found")

        return getMutex(conversationId).withLock {
            // 1. Get ratchet state
            var ratchetState = getOrCreateRatchetState(conversationId, conversation.participantPublicKey)

            // 2. DH ratchet step: if we have the remote's DH public key but no sendChainKey yet,
            //    or if we need to generate a fresh ephemeral for a new sending turn
            if (ratchetState.sendChainKey.isEmpty() && ratchetState.remoteDhPublic.isNotEmpty()) {
                // We know the remote's DH key — perform DH ratchet to derive send chain
                val newEphemeral = CryptoManager.generateEphemeralKeyPair()
                val dhResult = DoubleRatchet.dhRatchetStep(
                    ratchetState.rootKey, newEphemeral.privateKeyBase64, ratchetState.remoteDhPublic
                )
                ratchetState = ratchetState.copy(
                    rootKey = dhResult.newRootKey,
                    sendChainKey = dhResult.newChainKey,
                    sendIndex = 0,
                    localDhPublic = newEphemeral.publicKeyBase64,
                    localDhPrivate = newEphemeral.privateKeyBase64
                )
                ratchetDao.insertOrUpdate(ratchetState)
            }

            // 3. Advance symmetric send chain → get unique message key
            val (newSendChainKey, messageKey) = DoubleRatchet.advanceChain(ratchetState.sendChainKey)
            val messageIndex = ratchetState.sendIndex

            // 3b. SPQR — periodic ML-KEM re-encapsulation for continuous PQ healing
            var spqrKemCiphertext = ""
            var newPqRatchetCounter = ratchetState.pqRatchetCounter + 1
            if (ratchetState.pqxdhInitialized
                && newPqRatchetCounter >= DoubleRatchet.PQ_RATCHET_INTERVAL
                && ratchetState.remoteMlkemPublicKey.isNotEmpty()
            ) {
                try {
                    val (kemCt, ssPQ) = CryptoManager.mlkemEncaps(ratchetState.remoteMlkemPublicKey)
                    val newRootKey = DoubleRatchet.pqRatchetStep(ratchetState.rootKey, ssPQ)
                    ratchetState = ratchetState.copy(rootKey = newRootKey)
                    spqrKemCiphertext = kemCt
                    newPqRatchetCounter = 0
                } catch (e: Exception) {
                }
            }

            // 4. Embed messageIndex in plaintext for metadata privacy, then encrypt
            val augmentedPlaintext = "$messageIndex|$plaintext"
            val encryptedData = CryptoManager.encrypt(augmentedPlaintext, messageKey)

            // 5. Sign ciphertext with Ed25519 (anti-forgery + anti-replay)
            val createdAt = System.currentTimeMillis()
            val signature = try {
                CryptoManager.signMessage(encryptedData.ciphertext, conversationId, createdAt)
            } catch (_: Exception) { "" }

            // 6. Build wire message — include ephemeral DH public key for Double Ratchet
            //    and KEM ciphertext on first message (PQXDH initiator) or SPQR re-key
            //    ML-DSA-44 signature on handshake message for PQ authentication
            val kemToSend = ratchetState.pendingKemCiphertext.ifEmpty { spqrKemCiphertext }
            val mldsaSignature = if (ratchetState.pendingKemCiphertext.isNotEmpty()) {
                try {
                    val handshakeData = (ratchetState.pendingKemCiphertext + ratchetState.localDhPublic)
                        .toByteArray(Charsets.UTF_8)
                    CryptoManager.signHandshakeMlDsa44(handshakeData)
                } catch (_: Exception) { "" }
            } else ""

            // ── Send via Tor P2P (.onion direct or mailbox fallback) ──
            var recipientOnion = conversation.participantOnionAddress
            if (recipientOnion.isEmpty()) {
                // Conversation was created before we had the .onion — look it up now
                val contact = contactDao.getContactByPublicKey(conversation.participantPublicKey)
                recipientOnion = contact?.onionAddress ?: ""
                if (recipientOnion.isNotEmpty()) {
                    conversationDao.updateConversation(conversation.copy(participantOnionAddress = recipientOnion))
                }
            }
            val messageJson = org.json.JSONObject().apply {
                put("ciphertext", encryptedData.ciphertext)
                put("iv", encryptedData.iv)
                put("createdAt", createdAt)
                put("ephemeralKey", ratchetState.localDhPublic)
                put("signature", signature)
                put("kemCiphertext", kemToSend)
                put("mldsaSignature", mldsaSignature)
                put("conversationId", conversationId)
                // Inclure notre mailboxOnion pour que le destinataire puisse nous joindre
                // même si on est offline, et mettre à jour sa DB si on a changé de mailbox
                val myMailbox = com.fialkaapp.fialka.tor.MailboxClientManager.getMailboxOnion()
                if (myMailbox != null) put("senderMailboxOnion", myMailbox)
            }
            val frame = TorTransport.Frame(
                TorTransport.TYPE_MESSAGE,
                messageJson.toString().toByteArray(Charsets.UTF_8)
            )

            // Generate localId now so OutboxManager can link back to it
            val localId = UUID.randomUUID().toString()
            val mailboxOnion = conversation.participantMailboxOnion.ifEmpty { null }
            val deliveryResult = OutboxManager.sendOrQueue(
                recipientOnion, frame, mailboxOnion, localId
            )

            // Map delivery result to MessageLocal status
            val deliveryStatus = when (deliveryResult) {
                OutboxManager.DeliveryResult.DIRECT -> MessageLocal.DELIVERY_SENT
                OutboxManager.DeliveryResult.MAILBOX -> MessageLocal.DELIVERY_MAILBOX
                OutboxManager.DeliveryResult.QUEUED -> MessageLocal.DELIVERY_PENDING
            }

            // 7. Send succeeded → persist ratchet state; clear pendingKemCiphertext
            //    If this was the first message carrying kemCiphertext (PQXDH initiator),
            //    also upgrade the rootKey to the combined (classic+PQ) secret. Only the
            //    rootKey changes — current send/recv chains stay intact so in-flight
            //    messages remain decryptable until the next DH ratchet step naturally
            //    derives post-quantum chains from the upgraded root.
            val wasPqxdhSend = ratchetState.pendingKemCiphertext.isNotEmpty()
                    && !ratchetState.pqxdhInitialized
                    && ratchetState.pendingClassicSecret.isNotEmpty()

            if (wasPqxdhSend) {
                val ssClassicFresh = CryptoManager.performKeyAgreement(conversation.participantPublicKey)
                val ssPQBytes = android.util.Base64.decode(
                    ratchetState.pendingClassicSecret, android.util.Base64.NO_WRAP
                )
                val combined = ssClassicFresh + ssPQBytes
                ssClassicFresh.fill(0)
                ssPQBytes.fill(0)
                // Derive the combined rootKey (same derivation both sides will use)
                val pqInit = DoubleRatchet.initializeAsInitiator(combined)
                // combined is zeroed inside initializeAsInitiator
                ratchetDao.insertOrUpdate(
                    ratchetState.copy(
                        sendChainKey = newSendChainKey,
                        sendIndex = messageIndex + 1,
                        pendingKemCiphertext = "",
                        rootKey = pqInit.rootKey,
                        pqxdhInitialized = true,
                        pendingClassicSecret = "",
                        pqRatchetCounter = 0
                    )
                )
            } else {
                ratchetDao.insertOrUpdate(
                    ratchetState.copy(
                        sendChainKey = newSendChainKey,
                        sendIndex = messageIndex + 1,
                        pendingKemCiphertext = "",
                        pqRatchetCounter = newPqRatchetCounter
                    )
                )
            }

            // 8. Save plaintext locally (with ephemeral timing if enabled)
            val ephDuration = conversation.ephemeralDuration
            val expiresAt = if (ephDuration > 0) System.currentTimeMillis() + ephDuration else 0L
            val localMessage = MessageLocal(
                localId = localId,
                conversationId = conversationId,
                senderPublicKey = user.publicKey,
                plaintext = plaintext,
                isMine = true,
                ephemeralDuration = ephDuration,
                expiresAt = expiresAt,
                signatureValid = true,  // Own messages are implicitly valid
                deliveryStatus = deliveryStatus
            )
            messageDao.insertMessage(localMessage)
            updateConversationLastMessage(conversationId, plaintext)

            localMessage
        }
    }

    /**
     * Send a dummy message on a conversation to mask real traffic patterns.
     * Uses the real Double Ratchet (indistinguishable from real messages on the wire).
     * Receiver detects the DUMMY_PREFIX after decryption and silently drops it.
     */
    suspend fun sendDummyMessage(conversationId: String) {
        try {
            // Variable padding (5-200 bytes) so ciphertext length mimics real messages
            val paddingSize = 5 + java.security.SecureRandom().nextInt(196)
            val randomPadding = ByteArray(paddingSize).also { java.security.SecureRandom().nextBytes(it) }
            val dummyPlaintext = DUMMY_PREFIX + android.util.Base64.encodeToString(randomPadding, android.util.Base64.NO_WRAP)
            sendMessage(conversationId, dummyPlaintext)
            // Delete the locally saved dummy message (we don't want it in the UI)
            val lastMsg = messageDao.getLastMessage(conversationId)
            if (lastMsg != null && lastMsg.plaintext.startsWith(DUMMY_PREFIX)) {
                messageDao.deleteMessageById(lastMsg.localId)
                // Restore the previous real message as lastMessage on the conversation
                val prevMsg = messageDao.getLastMessage(conversationId)
                if (prevMsg != null) {
                    updateConversationLastMessage(conversationId, prevMsg.plaintext)
                }
            }
        } catch (_: Exception) {
            // Dummy failures are silent — they must never disrupt real messaging
        }
    }

    /**
     * Send a file with E2E encryption via Tor P2P.
     * 1. Encrypt file locally with a random AES-256-GCM key
     * 2. Send metadata (key + IV + filename + size) as a ratcheted message
     * 3. Send encrypted file bytes via TYPE_FILE_CHUNK frames
     *
     * The receiver decrypts locally and stores the plaintext file.
     */
    suspend fun sendFile(
        conversationId: String,
        fileBytes: ByteArray,
        fileName: String,
        isOneShot: Boolean = false
    ): MessageLocal {
        // 1. Encrypt file client-side
        val encResult = CryptoManager.encryptFile(fileBytes)

        // 2. Build metadata plaintext: FILE|inline|key|iv|filename|size|oneshot
        val oneShotFlag = if (isOneShot) "1" else "0"
        val metadata = "${FILE_PREFIX}inline|${encResult.keyBase64}|${encResult.ivBase64}|${fileName}|${fileBytes.size}|${oneShotFlag}"

        // 3. Send metadata via the normal ratchet pipeline (E2E encrypted)
        val sentMessage = sendMessage(conversationId, metadata)

        // 4. Send encrypted file bytes via Tor P2P
        val conversation = conversationDao.getConversationById(conversationId)
        if (conversation != null) {
            val recipientOnion = conversation.participantOnionAddress
            val fileJson = org.json.JSONObject().apply {
                put("conversationId", conversationId)
                put("fileName", fileName)
                put("data", android.util.Base64.encodeToString(encResult.encryptedBytes, android.util.Base64.NO_WRAP))
            }
            val frame = TorTransport.Frame(
                TorTransport.TYPE_FILE_CHUNK,
                fileJson.toString().toByteArray(Charsets.UTF_8)
            )
            OutboxManager.sendOrQueue(recipientOnion, frame, conversation.participantMailboxOnion.ifEmpty { null })
        }

        // 5. Save the decrypted file locally
        val localFile = saveFileLocally(conversationId, fileName, fileBytes)

        // 6. Update the local message with file info
        val displayPrefix = if (isOneShot) "\uD83D\uDD25" else "\uD83D\uDCCE"  // 🔥 or 📎
        val fileMessage = sentMessage.copy(
            plaintext = "$displayPrefix $fileName",
            fileName = fileName,
            fileSize = fileBytes.size.toLong(),
            localFilePath = localFile.absolutePath,
            isOneShot = isOneShot
        )
        messageDao.insertMessage(fileMessage)  // REPLACE because same localId
        updateConversationLastMessage(conversationId, fileMessage.plaintext)

        return fileMessage
    }

    /**
     * Save decrypted file to app-private storage.
     * Path: /data/data/com.fialka/files/received_files/{conversationId}/{fileName}
     */
    private fun saveFileLocally(
        conversationId: String,
        fileName: String,
        fileBytes: ByteArray
    ): java.io.File {
        val dir = java.io.File(appContext.filesDir, "received_files/$conversationId")
        dir.mkdirs()
        // Sanitize filename to prevent path traversal
        val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val file = java.io.File(dir, safeName)
        file.writeBytes(fileBytes)
        return file
    }

    /**
     * Parse and process a received file attachment message.
     * File data arrives via TYPE_FILE_CHUNK (stored in pendingFileChunks by P2PServer).
     * Format: FILE|inline|keyBase64|ivBase64|fileName|fileSize
     *
     * Two-phase insert:
     * 1. Insert a placeholder immediately (shows download progress in UI).
     * 2. Update on success (localFilePath set) or failure (retry metadata in plaintext).
     */
    private suspend fun handleReceivedFile(
        conversationId: String,
        conversation: Conversation,
        decryptedPlaintext: String,
        timestamp: Long,
        signatureValid: Boolean?
    ): MessageLocal? {
        // Parse: strip FILE_PREFIX, then split remaining by |
        val payload = decryptedPlaintext.removePrefix(FILE_PREFIX)
        val parts = payload.split("|", limit = 6)
        if (parts.size < 5) return null

        val source = parts[0]      // "inline" for P2P
        val keyBase64 = parts[1]
        val ivBase64 = parts[2]
        val fileName = parts[3]
        val fileSize = parts[4].toLongOrNull() ?: 0L
        val isOneShot = parts.getOrNull(5) == "1"

        val ephDuration = conversation.ephemeralDuration
        val isCurrentlyViewing = currentlyViewedConversation == conversationId
        val expiresAt = if (ephDuration > 0 && isCurrentlyViewing) {
            System.currentTimeMillis() + ephDuration
        } else { 0L }

        val localId = UUID.randomUUID().toString()

        // Phase 1: Insert downloading placeholder (localFilePath = null → UI shows progress)
        val placeholder = MessageLocal(
            localId = localId,
            conversationId = conversationId,
            senderPublicKey = conversation.participantPublicKey,
            plaintext = "⏳ $fileName",
            timestamp = timestamp,
            isMine = false,
            ephemeralDuration = ephDuration,
            expiresAt = expiresAt,
            fileName = fileName,
            fileSize = fileSize,
            signatureValid = signatureValid,
            isOneShot = isOneShot
        )
        messageDao.insertMessage(placeholder)
        if (currentlyViewedConversation != conversationId) {
            conversationDao.incrementUnreadCount(conversationId)
        }

        // Phase 2: Decrypt file data from P2PServer pending chunks
        return try {
            val encryptedBytes = P2PServer.consumePendingFileChunk(conversationId, fileName)
            if (encryptedBytes != null) {
                val decryptedBytes = CryptoManager.decryptFile(encryptedBytes, keyBase64, ivBase64)
                val localFile = saveFileLocally(conversationId, fileName, decryptedBytes)

                val displayPrefix = if (isOneShot) "\uD83D\uDD25" else "\uD83D\uDCCE"
                val finalMessage = placeholder.copy(
                    plaintext = "$displayPrefix $fileName",
                    localFilePath = localFile.absolutePath
                )
                messageDao.insertMessage(finalMessage)  // REPLACE by same localId
                updateConversationLastMessage(conversationId, finalMessage.plaintext)
                finalMessage
            } else {
                // File chunk not arrived yet — register metadata so P2PServer auto-finalizes when chunk arrives
                P2PServer.registerPendingFileMetadata(
                    P2PServer.PendingFileMeta(
                        messageLocalId = localId,
                        conversationId = conversationId,
                        fileName = fileName,
                        keyBase64 = keyBase64,
                        ivBase64 = ivBase64,
                        isOneShot = isOneShot
                    )
                )
                placeholder
            }
        } catch (e: Exception) {
            val errorMessage = placeholder.copy(
                plaintext = "⚠️ Échec : $fileName"
            )
            messageDao.insertMessage(errorMessage)  // REPLACE by same localId
            updateConversationLastMessage(conversationId, "⚠️ Échec : $fileName")
            errorMessage
        }
    }

    /**
     * Retry a failed file download.
     * In P2P mode, files are received inline — retry asks P2PServer for pending chunk.
     */
    suspend fun retryFileDownload(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        val fileName = message.fileName ?: return
        if (!message.plaintext.startsWith("⚠️")) return

        // Update to downloading state
        val downloading = message.copy(plaintext = "⏳ $fileName")
        messageDao.insertMessage(downloading)

        try {
            val encryptedBytes = P2PServer.consumePendingFileChunk(message.conversationId, fileName)
            if (encryptedBytes != null) {
                // For retry, the file chunk is already available — save directly
                val localFile = saveFileLocally(message.conversationId, fileName, encryptedBytes)

                val finalMessage = message.copy(
                    plaintext = "\uD83D\uDCCE $fileName",
                    localFilePath = localFile.absolutePath
                )
                messageDao.insertMessage(finalMessage)
                updateConversationLastMessage(message.conversationId, finalMessage.plaintext)
            } else {
                // No pending chunk — leave as error
                val errorMessage = message.copy(plaintext = "⚠️ Échec : $fileName")
                messageDao.insertMessage(errorMessage)
            }
        } catch (e: Exception) {
            val errorMessage = message.copy(plaintext = "⚠️ Échec : $fileName")
            messageDao.insertMessage(errorMessage)
        }
    }

    /**
     * Called by P2PServer when a file chunk arrives AFTER the metadata placeholder was already created.
     * Decrypts the file, saves it locally, and updates the placeholder message.
     */
    suspend fun finalizeFileMessage(
        messageLocalId: String,
        conversationId: String,
        fileName: String,
        encryptedBytes: ByteArray,
        keyBase64: String,
        ivBase64: String,
        isOneShot: Boolean
    ) {
        val message = messageDao.getMessageById(messageLocalId) ?: return
        try {
            val decryptedBytes = CryptoManager.decryptFile(encryptedBytes, keyBase64, ivBase64)
            val localFile = saveFileLocally(conversationId, fileName, decryptedBytes)
            val displayPrefix = if (isOneShot) "\uD83D\uDD25" else "\uD83D\uDCCE"
            val finalMessage = message.copy(
                plaintext = "$displayPrefix $fileName",
                localFilePath = localFile.absolutePath
            )
            messageDao.insertMessage(finalMessage)
            updateConversationLastMessage(conversationId, finalMessage.plaintext)
        } catch (_: Exception) {
            val errorMessage = message.copy(plaintext = "⚠️ Échec : $fileName")
            messageDao.insertMessage(errorMessage)
            updateConversationLastMessage(conversationId, "⚠️ Échec : $fileName")
        }
    }

    /**
     * Mark a one-shot message as opened: immediately flag in DB (prevents re-viewing),
     * then delay file deletion so the viewer app has time to load it.
     */
    suspend fun markOneShotOpened(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        if (!message.isOneShot || message.oneShotOpened) return
        // Immediately flag as opened in DB — even if user leaves, it stays locked
        messageDao.flagOneShotOpened(messageId)
        // Wait for viewer app to load the file
        kotlinx.coroutines.delay(5000)
        // Now delete the physical file and clear the path
        message.localFilePath?.let { path ->
            try { java.io.File(path).delete() } catch (_: Exception) { }
        }
        messageDao.markOneShotOpened(messageId)
    }

    /**
     * Resend a failed message — reset outbox retry and mark as pending.
     */
    suspend fun resendMessage(messageId: String) {
        messageDao.updateDeliveryStatus(messageId, MessageLocal.DELIVERY_PENDING)
        outboxDao.resetRetryForMessage(messageId)
    }

    /**
     * Decrypt a received message with Perfect Forward Secrecy.
     * Uses trial decryption since messageIndex is embedded in the ciphertext
     * (metadata hardening — observer cannot see ratchet position).
     *
     * Protected by a per-conversation Mutex to prevent ratchet desynchronization
     * when multiple P2P messages arrive simultaneously.
     */
    suspend fun receiveMessage(
        conversationId: String,
        p2pMessage: P2PMessage
    ): MessageLocal? {

        return getMutex(conversationId).withLock {

            // Skip duplicates — check by conversationId + timestamp (received only)
            val exists = messageDao.receivedMessageExists(
                conversationId,
                p2pMessage.createdAt
            )
            if (exists > 0) return@withLock null

            // Get conversation to find the contact's public key
            val conversation = conversationDao.getConversationById(conversationId)
                ?: return@withLock null

            // Get ratchet state
            var ratchetState = getOrCreateRatchetState(conversationId, conversation.participantPublicKey)

            // DH ratchet step: if remote sent a new ephemeral key, perform DH ratchet
            val remoteEphemeral = p2pMessage.ephemeralKey

            // Always store the remote ephemeral if present
            if (remoteEphemeral.isNotEmpty() && remoteEphemeral != ratchetState.remoteDhPublic) {
                val previousRemote = ratchetState.remoteDhPublic
                ratchetState = ratchetState.copy(remoteDhPublic = remoteEphemeral)

                // DH ratchet only if we already knew a previous remote ephemeral AND it changed
                if (previousRemote.isNotEmpty()) {
                    val dhResult = DoubleRatchet.dhRatchetStep(
                        ratchetState.rootKey, ratchetState.localDhPrivate, remoteEphemeral
                    )
                    ratchetState = ratchetState.copy(
                        rootKey = dhResult.newRootKey,
                        recvChainKey = dhResult.newChainKey,
                        recvIndex = 0,
                        sendChainKey = ""  // Force DH ratchet on next send (healing)
                    )
                }
                ratchetDao.insertOrUpdate(ratchetState)
            }

            // Trial decryption: messageIndex is embedded in ciphertext as "index|plaintext"
            var tempChainKey = ratchetState.recvChainKey
            var decryptedPlaintext: String? = null
            var finalChainKey: String? = null
            var foundIndex = -1

            for (skip in 0..MAX_RATCHET_SKIP) {
                val (nextChainKey, messageKey) = DoubleRatchet.advanceChain(tempChainKey)
                try {
                    val decrypted = CryptoManager.decrypt(
                        CryptoManager.EncryptedData(
                            ciphertext = p2pMessage.ciphertext,
                            iv = p2pMessage.iv
                        ),
                        messageKey
                    )
                    // Parse embedded "messageIndex|plaintext"
                    val sep = decrypted.indexOf('|')
                    if (sep > 0) {
                        val idx = decrypted.substring(0, sep).toIntOrNull()
                        if (idx != null && idx == ratchetState.recvIndex + skip) {
                            decryptedPlaintext = decrypted.substring(sep + 1)
                            finalChainKey = nextChainKey
                            foundIndex = idx
                            break
                        }
                    }
                    // Decrypted but index mismatch — continue trying
                    tempChainKey = nextChainKey
                } catch (_: Exception) {
                    tempChainKey = nextChainKey
                }
            }

            // Update ratchet only on successful decryption
            if (finalChainKey != null) {
                ratchetState = ratchetState.copy(
                    recvChainKey = finalChainKey,
                    recvIndex = foundIndex + 1
                )
                ratchetDao.insertOrUpdate(ratchetState)

                // PQXDH deferred upgrade (responder side):
                // Now that the message carrying kemCiphertext has been successfully
                // decrypted with the classic chain, upgrade rootKey to the combined
                // (classic + PQ) secret. Only the rootKey changes — the current
                // send/recv chains stay intact. The next DH ratchet step will
                // naturally derive post-quantum chains from the upgraded root.
                if (!ratchetState.pqxdhInitialized
                    && p2pMessage.kemCiphertext.isNotEmpty()
                    && ratchetState.pendingClassicSecret.isNotEmpty()
                ) {
                    try {
                        val classicBytes = android.util.Base64.decode(
                            ratchetState.pendingClassicSecret, android.util.Base64.NO_WRAP
                        )
                        val ssPQ = CryptoManager.mlkemDecaps(p2pMessage.kemCiphertext)
                        val combined = classicBytes + ssPQ
                        classicBytes.fill(0)
                        ssPQ.fill(0)
                        val pqInit = DoubleRatchet.initializeAsResponder(combined)
                        // combined is zeroed inside initializeAsResponder
                        ratchetState = ratchetState.copy(
                            rootKey = pqInit.rootKey,
                            pqxdhInitialized = true,
                            pendingClassicSecret = ""
                        )
                        ratchetDao.insertOrUpdate(ratchetState)
                    } catch (e: Exception) {
                    }
                }

                // SPQR — periodic ML-KEM re-encapsulation (receiver side):
                // If PQXDH is already complete and this message carries a fresh kemCiphertext,
                // it's a SPQR re-key: decapsulate and mix into rootKey.
                if (ratchetState.pqxdhInitialized
                    && p2pMessage.kemCiphertext.isNotEmpty()
                ) {
                    try {
                        val ssPQ = CryptoManager.mlkemDecaps(p2pMessage.kemCiphertext)
                        val newRootKey = DoubleRatchet.pqRatchetStep(ratchetState.rootKey, ssPQ)
                        ratchetState = ratchetState.copy(
                            rootKey = newRootKey,
                            pqRatchetCounter = 0
                        )
                        ratchetDao.insertOrUpdate(ratchetState)
                    } catch (e: Exception) {
                    }
                }

                // Delete-after-delivery not needed — P2P messages are ephemeral on the wire
            }

            // Verify ML-DSA-44 handshake signature (PQ authentication on first PQXDH message)
            if (p2pMessage.mldsaSignature.isNotEmpty() && p2pMessage.kemCiphertext.isNotEmpty()) {
                val contact0 = contactDao.getContactByPublicKey(conversation.participantPublicKey)
                val mldsaKey = contact0?.mldsaPublicKey
                if (mldsaKey != null) {
                    val handshakeData = (p2pMessage.kemCiphertext + p2pMessage.ephemeralKey)
                        .toByteArray(Charsets.UTF_8)
                    val mldsaValid = CryptoManager.verifyHandshakeMlDsa44(mldsaKey, handshakeData, p2pMessage.mldsaSignature)
                    if (!mldsaValid) {
                    }
                }
            }

            // Verify Ed25519 signature (after ratchet advance — ratchet must ALWAYS progress)
            val contact = contactDao.getContactByPublicKey(conversation.participantPublicKey)

            // Keys arrive via TYPE_KEY_BUNDLE in-band — no remote fetch
            val signingKey = contact?.signingPublicKey
            val signatureValid: Boolean? = if (p2pMessage.signature.isNotEmpty() && signingKey != null) {
                CryptoManager.verifySignature(
                    signingKey,
                    p2pMessage.ciphertext,
                    conversationId,
                    p2pMessage.createdAt,
                    p2pMessage.signature
                )
            } else if (p2pMessage.signature.isNotEmpty()) {
                // Signature present but no signing key known — can't verify
                null
            } else {
                // No signature on message
                null
            }

            // Silently drop dummy traffic messages (used to mask real activity patterns)
            if (decryptedPlaintext != null && decryptedPlaintext.startsWith(DUMMY_PREFIX)) {
                return@withLock null
            }

            // Handle file attachment messages
            if (decryptedPlaintext != null && decryptedPlaintext.startsWith(FILE_PREFIX)) {
                return@withLock handleReceivedFile(
                    conversationId, conversation, decryptedPlaintext,
                    p2pMessage.createdAt, signatureValid
                )
            }

            // Save locally (with ephemeral timing if conversation has it enabled)
            val ephDuration = conversation.ephemeralDuration
            val isCurrentlyViewing = currentlyViewedConversation == conversationId
            val expiresAt = if (ephDuration > 0 && isCurrentlyViewing) {
                System.currentTimeMillis() + ephDuration
            } else {
                0L  // Timer will start when chat is opened (activateEphemeralTimers)
            }
            val localMessage = MessageLocal(
                localId = UUID.randomUUID().toString(),
                conversationId = conversationId,
                senderPublicKey = conversation.participantPublicKey,
                plaintext = decryptedPlaintext ?: "[Échec du déchiffrement]",
                timestamp = p2pMessage.createdAt,
                isMine = false,
                ephemeralDuration = ephDuration,
                expiresAt = expiresAt,
                signatureValid = signatureValid
            )
            messageDao.insertMessage(localMessage)
            updateConversationLastMessage(conversationId, localMessage.plaintext)
            if (currentlyViewedConversation != conversationId) {
                conversationDao.incrementUnreadCount(conversationId)
            }

            localMessage
        }
    }

    // ========================================================================
    // MESSAGE LISTENING (Tor P2P — handled by P2PServer)
    // ========================================================================

    /**
     * Mark a conversation as read (reset unread count to 0).
     * Called when the user opens a chat.
     */
    suspend fun markConversationRead(conversationId: String) {
        conversationDao.resetUnreadCount(conversationId)
    }

    // ========================================================================
    // CONTACT REQUESTS (via Tor P2P)
    // ========================================================================

    /**
     * Send a contact request to the recipient via Tor P2P.
     */
    /**
     * Envoie une demande de contact vers le pair distant via Tor P2P.
     * @return true si la demande a été envoyée, false si les prérequis manquent.
     * @throws IllegalStateException si l'utilisateur local n'est pas initialisé.
     */
    suspend fun sendContactRequest(contactPublicKey: String): Boolean {
        val user = userDao.getUser()
            ?: throw IllegalStateException("Utilisateur non initialisé — impossible d'envoyer la demande de contact")

        val conversationId = conversationDao.getConversationByParticipantPublicKey(contactPublicKey)?.conversationId
            ?: run {
                android.util.Log.w("ChatRepository", "sendContactRequest: conversation introuvable pour $contactPublicKey")
                return false
            }

        val signingPublicKey = try {
            CryptoManager.getSigningPublicKeyBase64()
        } catch (e: Exception) {
            android.util.Log.w("ChatRepository", "sendContactRequest: clé de signature indisponible: ${e.message}")
            null
        }

        val contact = contactDao.getContactByPublicKey(contactPublicKey)
        val recipientOnion = contact?.onionAddress

        if (recipientOnion.isNullOrEmpty()) {
            android.util.Log.e("ChatRepository",
                "sendContactRequest: onionAddress manquant pour $contactPublicKey — " +
                "contact trouvé: ${contact != null}, signingKey présente: ${contact?.signingPublicKey != null}")
            return false
        }

        P2PServer.sendContactRequest(
            recipientOnion = recipientOnion,
            senderPublicKey = user.publicKey,
            senderDisplayName = user.displayName,
            conversationId = conversationId,
            senderSigningPublicKey = signingPublicKey
        )
        return true
    }

    /**
     * Incoming contact requests arrive via P2PServer.incomingRequests SharedFlow.
     * ViewModel collects that flow directly — no repository wrapper needed.
     */

    /**
     * Accept an incoming contact request:
     * 1. Add the sender as a contact
     * 2. Create the conversation + initialize ratchet (accepted = true)
     * 3. Notify the sender via Tor P2P that the request was accepted
     * 4. Send our key bundle so they get our ML-KEM / ML-DSA / signing keys
     * Returns the created Conversation.
     */
    suspend fun acceptContactRequest(request: ContactRequest): Conversation {
        // Add contact (with signing key from the request if available)
        addContact(request.senderDisplayName, request.senderPublicKey, request.senderSigningPublicKey)

        // Save sender's mailbox onion if provided
        if (request.senderMailboxOnion != null) {
            updateContactMailbox(request.senderPublicKey, request.senderMailboxOnion)
        }

        // Create conversation using the ID sent by the initiator
        val conversation = createConversation(
            request.senderPublicKey,
            request.senderDisplayName,
            accepted = true,
            conversationId = request.conversationId
        )

        // Notify sender that we accepted (via Tor P2P)
        val contact = contactDao.getContactByPublicKey(request.senderPublicKey)
        val recipientOnion = contact?.onionAddress
        val myPublicKey = getUser()?.publicKey ?: ""
        if (recipientOnion != null) {
            P2PServer.sendAcceptance(recipientOnion, conversation.conversationId, myPublicKey)

            // Send our key bundle so the initiator gets ML-KEM + ML-DSA + signing keys
            val mySigningKey = CryptoManager.getSigningPublicKeyBase64()
            val myMlkemKey = CryptoManager.getMLKEMPublicKey()
            val myMldsaKey = CryptoManager.getMlDsaPublicKey()
            P2PServer.sendKeyBundle(
                recipientOnion,
                senderPublicKey = myPublicKey,
                mlkemPublicKey = myMlkemKey,
                mldsaPublicKey = myMldsaKey,
                signingPublicKey = mySigningKey
            )
        }

        return conversation
    }

    /**
     * Check if a contact request has already been accepted (conversation exists).
     */
    suspend fun isContactRequestAlreadyAccepted(conversationId: String): Boolean {
        return conversationDao.getConversationById(conversationId) != null
    }

    /**
     * Return all conversationIds where accepted = false (outgoing pending invites).
     */
    suspend fun getPendingConversationIds(): List<String> =
        conversationDao.getPendingConversationIds()

    /**
     * Mark a conversation as accepted locally.
     * Called when we receive an acceptance notification via P2PServer.incomingAcceptances.
     */
    suspend fun markConversationAccepted(conversationId: String) {
        val conversation = conversationDao.getConversationById(conversationId) ?: return
        if (!conversation.accepted) {
            conversationDao.updateConversation(conversation.copy(accepted = true))
        }
    }

    // ========================================================================
    // FINGERPRINT VERIFICATION
    // ========================================================================

    /**
     * Mark a conversation's fingerprint as verified/unverified.
     * Verification state is LOCAL-ONLY — each participant decides independently.
     * A notification event is sent via Tor P2P so the other side sees a message.
     */
    suspend fun verifyFingerprint(conversationId: String, verified: Boolean) {
        conversationDao.updateFingerprintVerified(conversationId, verified)
        insertFingerprintInfoMessage(conversationId, verified, isLocal = true)
        val ts = System.currentTimeMillis()
        lastFingerprintPushTimestamp = ts

        // Notify remote via Tor P2P
        val conversation = conversationDao.getConversationById(conversationId) ?: return
        val contact = contactDao.getContactByPublicKey(conversation.participantPublicKey)
        val recipientOnion = contact?.onionAddress
        if (recipientOnion != null) {
            val event = "${if (verified) "verified" else "unverified"}:$ts"
            P2PServer.sendFingerprintEvent(recipientOnion, conversationId, event)
        }
    }

    /**
     * Insert a local-only info message when fingerprint verification changes.
     * [isLocal] = true  → "Vous avez vérifié …"
     * [isLocal] = false → "Votre contact a vérifié …"
     */
    suspend fun insertFingerprintInfoMessage(conversationId: String, verified: Boolean, isLocal: Boolean) {
        val text = if (verified) {
            if (isLocal) "🔐 Vous avez vérifié l'empreinte de sécurité"
            else "🔐 Votre contact a vérifié l'empreinte de sécurité"
        } else {
            if (isLocal) "🔓 Vous avez retiré la vérification de l'empreinte"
            else "🔓 Votre contact a retiré la vérification de l'empreinte"
        }
        val infoMessage = MessageLocal(
            localId = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderPublicKey = "",
            plaintext = text,
            timestamp = System.currentTimeMillis(),
            isMine = false,
            isInfoMessage = true
        )
        messageDao.insertMessage(infoMessage)
    }

    /**
     * Delete a conversation and all its messages from the local database.
     */
    suspend fun deleteConversation(conversationId: String) {
        messageDao.deleteMessagesForConversation(conversationId)
        val conversation = conversationDao.getConversationById(conversationId) ?: return
        conversationDao.deleteConversation(conversation)
    }

    suspend fun getConversationIdByContactPublicKey(publicKey: String): String? =
        conversationDao.getConversationByParticipantPublicKey(publicKey)?.conversationId

    /**
     * Delete a stale conversation, its messages, ratchet state, and the contact.
     * Used when re-adding a contact whose account was reset.
     */
    suspend fun deleteStaleConversation(conversationId: String, contact: Contact) {
        messageDao.deleteMessagesForConversation(conversationId)
        val conversation = conversationDao.getConversationById(conversationId)
        if (conversation != null) {
            conversationDao.deleteConversation(conversation)
        }
        ratchetDao.deleteState(conversationId)
        contactDao.deleteContact(contact)
        synchronized(ratchetMutexes) {
            ratchetMutexes.remove(conversationId)
        }
    }

    // ========================================================================
    // EPHEMERAL MESSAGES
    // ========================================================================

    /**
     * Set ephemeral duration for a conversation.
     * Writes to local DB and notifies the remote participant via Tor P2P.
     * Also inserts a local info message visible in the chat.
     */
    suspend fun setEphemeralDuration(conversationId: String, durationMs: Long) {
        conversationDao.updateEphemeralDuration(conversationId, durationMs)
        insertEphemeralInfoMessage(conversationId, durationMs)

        // Notify remote via Tor P2P
        val conversation = conversationDao.getConversationById(conversationId) ?: return
        val contact = contactDao.getContactByPublicKey(conversation.participantPublicKey)
        val recipientOnion = contact?.onionAddress
        if (recipientOnion != null) {
            P2PServer.sendEphemeralDuration(recipientOnion, conversationId, durationMs)
        }
    }

    suspend fun setDummyTraffic(conversationId: String, enabled: Boolean) {
        conversationDao.updateDummyTraffic(conversationId, enabled)
    }

    suspend fun getConversationsWithDummyTraffic(): List<Conversation> =
        conversationDao.getConversationsWithDummyTraffic()

    /**
     * Sync ephemeral duration to local DB only (no remote write).
     * Used when receiving a remote change via P2PServer to avoid infinite write loop.
     */
    suspend fun syncEphemeralDurationLocally(conversationId: String, durationMs: Long) {
        conversationDao.updateEphemeralDuration(conversationId, durationMs)
    }

    /**
     * Insert a local-only info message when ephemeral setting changes.
     * Does NOT reveal any username — just the action and duration.
     */
    suspend fun insertEphemeralInfoMessage(conversationId: String, durationMs: Long) {
        val text = if (durationMs > 0) {
            "⏱ Les messages éphémères ont été activés sur ${EphemeralManager.getLabelForDuration(durationMs)}"
        } else {
            "⏱ Les messages éphémères ont été désactivés"
        }
        val infoMessage = MessageLocal(
            localId = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderPublicKey = "",
            plaintext = text,
            timestamp = System.currentTimeMillis(),
            isMine = false,
            isInfoMessage = true
        )
        messageDao.insertMessage(infoMessage)
    }

    /** Delete all expired ephemeral messages. */
    suspend fun deleteExpiredMessages() {
        messageDao.deleteExpiredMessages()
    }

    /**
     * Activate ephemeral timers on received messages that haven't been read yet.
     * Called when the user opens the chat — this is when the "read" timer starts.
     * Only affects received messages (isMine = false) that have a duration but no expiresAt.
     */
    suspend fun activateEphemeralTimers(conversationId: String) {
        messageDao.activateEphemeralTimersForRead(conversationId, System.currentTimeMillis())
    }

    /** Set expiresAt on a message (for received ephemeral messages). */
    suspend fun setMessageExpiresAt(messageId: String, expiresAt: Long) {
        messageDao.setExpiresAt(messageId, expiresAt)
    }

    // ========================================================================
    // RESET / DELETE ACCOUNT
    // ========================================================================

    /**
     * Delete all local data and cryptographic material:
     * 1. Clear all Room tables (user, contacts, conversations, messages, ratchet state)
     * 2. Delete the identity key pair from EncryptedSharedPreferences
     */
    suspend fun resetAccount() {
        // Clear local data
        clearMutexes()
        db.clearAllTables()
        CryptoManager.deleteIdentityKey()
    }
}
