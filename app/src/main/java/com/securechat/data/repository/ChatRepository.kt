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
package com.securechat.data.repository

import android.content.Context
import android.util.Log
import androidx.lifecycle.LiveData
import com.securechat.crypto.CryptoManager
import com.securechat.crypto.DoubleRatchet
import com.securechat.data.local.SecureChatDatabase
import com.securechat.data.model.*
import com.securechat.data.remote.FirebaseRelay
import com.securechat.util.EphemeralManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * ChatRepository — single source of truth for chat data.
 *
 * Coordinates between:
 *  - Room (local storage)
 *  - Firebase (remote relay)
 *  - CryptoManager (encryption/decryption)
 *  - DoubleRatchet (X25519 DH ratchet + KDF chains — Perfect Forward Secrecy)
 *
 * Thread safety: ratchet operations are serialized per-conversation via Mutex
 * to prevent race conditions when multiple Firebase messages arrive simultaneously.
 */
class ChatRepository(private val appContext: Context) {

    private val db = SecureChatDatabase.getInstance(appContext)
    private val userDao = db.userLocalDao()
    private val contactDao = db.contactDao()
    private val conversationDao = db.conversationDao()
    private val messageDao = db.messageLocalDao()
    private val ratchetDao = db.ratchetStateDao()

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

        /**
         * Tracks Firebase keys already processed by any listener to prevent
         * double-processing when both ConversationsViewModel (global) and
         * ChatViewModel (per-chat) listeners receive the same message.
         */
        private val processedFirebaseKeys: java.util.concurrent.ConcurrentHashMap<String, Boolean> =
            java.util.concurrent.ConcurrentHashMap()
        private const val MAX_PROCESSED_KEYS = 500

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
        val publicKey = CryptoManager.generateIdentityKeyPair()
        // Generate ML-KEM-1024 identity key pair for PQXDH (idempotent)
        CryptoManager.generateMLKEMIdentityKeyPair()
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
        mlkemPublicKey: String? = null
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
            if (updated !== existing) {
                contactDao.insertContact(updated)
                return updated
            }
            return existing
        }

        // If no signing key provided, try to fetch from Firebase
        val finalSigningKey = signingPublicKey ?: try {
            FirebaseRelay.fetchSigningPublicKeyByIdentity(publicKey)
        } catch (_: Exception) { null }

        // If no ML-KEM key provided, try to fetch from Firebase
        val finalMlkemKey = mlkemPublicKey ?: try {
            FirebaseRelay.fetchMLKEMPublicKeyByIdentity(publicKey)
        } catch (_: Exception) { null }

        val contact = Contact(
            contactId = UUID.randomUUID().toString(),
            displayName = displayName,
            publicKey = publicKey,
            signingPublicKey = finalSigningKey,
            mlkemPublicKey = finalMlkemKey
        )
        contactDao.insertContact(contact)
        return contact
    }

    suspend fun getContactByPublicKey(publicKey: String): Contact? =
        contactDao.getContactByPublicKey(publicKey)

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

        val conversation = Conversation(
            conversationId = finalConversationId,
            participantPublicKey = contactPublicKey,
            contactDisplayName = contactName,
            accepted = accepted,
            sharedFingerprint = sharedFingerprint
        )
        conversationDao.insertConversation(conversation)

        // Register as participant on Firebase (required for security rules)
        try {
            if (!FirebaseRelay.isAuthenticated()) {
                FirebaseRelay.signInAnonymously()
            }
            FirebaseRelay.registerParticipant(finalConversationId)
        } catch (_: Exception) { }

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
     * 3. Send to Firebase
     * 4. ONLY on success: persist new ratchet state + save message locally
     */
    suspend fun sendMessage(conversationId: String, plaintext: String): MessageLocal {
        val user = userDao.getUser()
            ?: throw IllegalStateException("User not initialized")
        val conversation = conversationDao.getConversationById(conversationId)
            ?: throw IllegalStateException("Conversation not found")

        // Ensure Firebase auth is active before sending
        if (!FirebaseRelay.isAuthenticated()) {
            FirebaseRelay.signInAnonymously()
        }

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

            // 4. Embed messageIndex in plaintext for metadata privacy, then encrypt
            val augmentedPlaintext = "$messageIndex|$plaintext"
            val encryptedData = CryptoManager.encrypt(augmentedPlaintext, messageKey)

            // 5. Sign ciphertext with Ed25519 (anti-forgery + anti-replay)
            val createdAt = System.currentTimeMillis()
            val signature = try {
                CryptoManager.signMessage(encryptedData.ciphertext, conversationId, createdAt)
            } catch (_: Exception) { "" }

            // 6. Send to Firebase — include ephemeral DH public key for Double Ratchet
            //    and KEM ciphertext on first message (PQXDH initiator)
            val firebaseMessage = FirebaseMessage(
                ciphertext = encryptedData.ciphertext,
                iv = encryptedData.iv,
                createdAt = createdAt,
                senderUid = CryptoManager.hashSenderUid(conversationId, FirebaseRelay.getCurrentUid() ?: ""),
                ephemeralKey = ratchetState.localDhPublic,
                signature = signature,
                kemCiphertext = ratchetState.pendingKemCiphertext
            )
            FirebaseRelay.sendMessage(conversationId, firebaseMessage)

            // 7. Firebase succeeded → persist ratchet state; clear pendingKemCiphertext
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
                        pendingClassicSecret = ""
                    )
                )
            } else {
                ratchetDao.insertOrUpdate(
                    ratchetState.copy(
                        sendChainKey = newSendChainKey,
                        sendIndex = messageIndex + 1,
                        pendingKemCiphertext = ""
                    )
                )
            }

            // 8. Save plaintext locally (with ephemeral timing if enabled)
            val ephDuration = conversation.ephemeralDuration
            val expiresAt = if (ephDuration > 0) System.currentTimeMillis() + ephDuration else 0L
            val localMessage = MessageLocal(
                localId = UUID.randomUUID().toString(),
                conversationId = conversationId,
                senderPublicKey = user.publicKey,
                plaintext = plaintext,
                isMine = true,
                ephemeralDuration = ephDuration,
                expiresAt = expiresAt,
                signatureValid = true  // Own messages are implicitly valid
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
     * Send a file with E2E encryption.
     * 1. Encrypt file locally with a random AES-256-GCM key
     * 2. Upload ciphertext to Firebase Storage
     * 3. Send metadata (URL + key + IV + filename + size) as a ratcheted message
     *
     * The receiver downloads, decrypts locally, and stores the plaintext file.
     */
    suspend fun sendFile(
        conversationId: String,
        fileBytes: ByteArray,
        fileName: String,
        isOneShot: Boolean = false
    ): MessageLocal {
        // 1. Encrypt file client-side
        val encResult = CryptoManager.encryptFile(fileBytes)

        // 2. Upload encrypted bytes to Firebase Storage
        val ext = fileName.substringAfterLast('.', "bin")
        val downloadUrl = FirebaseRelay.uploadEncryptedFile(conversationId, encResult.encryptedBytes, ext)

        // 3. Build metadata plaintext: FILE|url|key|iv|filename|size|oneshot
        val oneShotFlag = if (isOneShot) "1" else "0"
        val metadata = "${FILE_PREFIX}${downloadUrl}|${encResult.keyBase64}|${encResult.ivBase64}|${fileName}|${fileBytes.size}|${oneShotFlag}"

        // 4. Send via the normal ratchet pipeline (E2E encrypted metadata)
        val sentMessage = sendMessage(conversationId, metadata)

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
     * Path: /data/data/com.securechat/files/received_files/{conversationId}/{fileName}
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
     * Downloads encrypted file from Firebase Storage, decrypts locally, saves to disk.
     * Format: FILE|downloadUrl|keyBase64|ivBase64|fileName|fileSize
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

        val downloadUrl = parts[0]
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

        // Phase 2: Download, decrypt, save
        return try {
            val encryptedBytes = FirebaseRelay.downloadEncryptedFile(downloadUrl)
            val decryptedBytes = CryptoManager.decryptFile(encryptedBytes, keyBase64, ivBase64)
            val localFile = saveFileLocally(conversationId, fileName, decryptedBytes)
            FirebaseRelay.deleteEncryptedFile(downloadUrl)

            val displayPrefix = if (isOneShot) "\uD83D\uDD25" else "\uD83D\uDCCE"
            val finalMessage = placeholder.copy(
                plaintext = "$displayPrefix $fileName",
                localFilePath = localFile.absolutePath
            )
            messageDao.insertMessage(finalMessage)  // REPLACE by same localId
            updateConversationLastMessage(conversationId, finalMessage.plaintext)
            finalMessage
        } catch (e: Exception) {
            android.util.Log.w("ChatRepository", "File receive failed: ${e.message}")
            // Store retry metadata in plaintext: ⚠️|url|key|iv|fileName|fileSize
            val errorMessage = placeholder.copy(
                plaintext = "⚠️|$downloadUrl|$keyBase64|$ivBase64|$fileName|$fileSize"
            )
            messageDao.insertMessage(errorMessage)  // REPLACE by same localId
            updateConversationLastMessage(conversationId, "⚠️ Échec : $fileName")
            errorMessage
        }
    }

    /**
     * Retry a failed file download.
     * Reads the retry metadata from the message's plaintext, re-downloads and decrypts.
     */
    suspend fun retryFileDownload(messageId: String) {
        val message = messageDao.getMessageById(messageId) ?: return
        if (!message.plaintext.startsWith("⚠️|")) return

        val parts = message.plaintext.removePrefix("⚠️|").split("|", limit = 5)
        if (parts.size < 5) return

        val downloadUrl = parts[0]
        val keyBase64 = parts[1]
        val ivBase64 = parts[2]
        val fileName = parts[3]
        val fileSize = parts[4].toLongOrNull() ?: 0L

        // Update to downloading state
        val downloading = message.copy(plaintext = "⏳ $fileName", fileName = fileName, fileSize = fileSize)
        messageDao.insertMessage(downloading)

        try {
            val encryptedBytes = FirebaseRelay.downloadEncryptedFile(downloadUrl)
            val decryptedBytes = CryptoManager.decryptFile(encryptedBytes, keyBase64, ivBase64)
            val localFile = saveFileLocally(message.conversationId, fileName, decryptedBytes)
            FirebaseRelay.deleteEncryptedFile(downloadUrl)

            val finalMessage = message.copy(
                plaintext = "\uD83D\uDCCE $fileName",
                fileName = fileName,
                fileSize = fileSize,
                localFilePath = localFile.absolutePath
            )
            messageDao.insertMessage(finalMessage)
            updateConversationLastMessage(message.conversationId, finalMessage.plaintext)
        } catch (e: Exception) {
            android.util.Log.w("ChatRepository", "File retry failed: ${e.message}")
            val errorMessage = message.copy(
                plaintext = "⚠️|$downloadUrl|$keyBase64|$ivBase64|$fileName|$fileSize"
            )
            messageDao.insertMessage(errorMessage)
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
     * Decrypt a received message with Perfect Forward Secrecy.
     * Uses trial decryption since messageIndex is embedded in the ciphertext
     * (metadata hardening — Firebase cannot see ratchet position).
     *
     * Protected by a per-conversation Mutex to prevent ratchet desynchronization
     * when multiple Firebase messages arrive simultaneously.
     */
    suspend fun receiveMessage(
        conversationId: String,
        firebaseMessage: FirebaseMessage
    ): MessageLocal? {
        val myUid = FirebaseRelay.getCurrentUid() ?: ""

        // Skip own messages — compare hashed UID (matches what we send)
        val myHashedUid = CryptoManager.hashSenderUid(conversationId, myUid)
        if (myUid.isNotEmpty() && firebaseMessage.senderUid == myHashedUid) return null

        return getMutex(conversationId).withLock {
            // Guard against double-processing by parallel listeners (global + per-chat)
            // putIfAbsent is atomic on ConcurrentHashMap — no race window
            if (firebaseMessage.firebaseKey.isNotEmpty() &&
                processedFirebaseKeys.putIfAbsent(firebaseMessage.firebaseKey, true) != null
            ) {
                return@withLock null
            }
            // LRU-style eviction: keep only the most recent keys (never mid-processing)
            if (processedFirebaseKeys.size > MAX_PROCESSED_KEYS) {
                val keysToRemove = processedFirebaseKeys.keys().toList()
                    .take(processedFirebaseKeys.size - MAX_PROCESSED_KEYS / 2)
                keysToRemove.forEach { processedFirebaseKeys.remove(it) }
            }

            // Skip duplicates — check by conversationId + timestamp (received only)
            val exists = messageDao.receivedMessageExists(
                conversationId,
                firebaseMessage.createdAt
            )
            if (exists > 0) return@withLock null

            // Get conversation to find the contact's public key
            val conversation = conversationDao.getConversationById(conversationId)
                ?: return@withLock null

            // Get ratchet state
            var ratchetState = getOrCreateRatchetState(conversationId, conversation.participantPublicKey)

            // DH ratchet step: if remote sent a new ephemeral key, perform DH ratchet
            val remoteEphemeral = firebaseMessage.ephemeralKey

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
                            ciphertext = firebaseMessage.ciphertext,
                            iv = firebaseMessage.iv
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
                    && firebaseMessage.kemCiphertext.isNotEmpty()
                    && ratchetState.pendingClassicSecret.isNotEmpty()
                ) {
                    try {
                        val classicBytes = android.util.Base64.decode(
                            ratchetState.pendingClassicSecret, android.util.Base64.NO_WRAP
                        )
                        val ssPQ = CryptoManager.mlkemDecaps(firebaseMessage.kemCiphertext)
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
                        Log.w("SecureChat", "PQXDH deferred rootKey upgrade failed", e)
                    }
                }

                // Delete-after-delivery: remove ciphertext from Firebase
                FirebaseRelay.deleteMessage(conversationId, firebaseMessage.firebaseKey)
                // Track the latest successfully delivered timestamp so the listener
                // lower-bound stays fresh across app restarts (prevents re-processing
                // undecryptable stale messages that were never deleted from Firebase).
                conversationDao.updateLastDeliveredAt(conversationId, firebaseMessage.createdAt)
            } else if (firebaseMessage.firebaseKey.isNotEmpty()) {
                // Decryption failed — delete from Firebase anyway so the ciphertext
                // doesn't block the ratchet indefinitely on every restart.
                FirebaseRelay.deleteMessage(conversationId, firebaseMessage.firebaseKey)
            }

            // Verify Ed25519 signature (after ratchet advance — ratchet must ALWAYS progress)
            val contact = contactDao.getContactByPublicKey(conversation.participantPublicKey)

            // Lazy-fetch signing key if we don't have it yet
            var signingKey = contact?.signingPublicKey
            if (signingKey == null && firebaseMessage.signature.isNotEmpty() && contact != null) {
                signingKey = try {
                    FirebaseRelay.fetchSigningPublicKeyByIdentity(conversation.participantPublicKey)
                } catch (_: Exception) { null }
                if (signingKey != null) {
                    contactDao.insertContact(contact.copy(signingPublicKey = signingKey))
                }
            }
            val signatureValid: Boolean? = if (firebaseMessage.signature.isNotEmpty() && signingKey != null) {
                CryptoManager.verifySignature(
                    signingKey,
                    firebaseMessage.ciphertext,
                    conversationId,
                    firebaseMessage.createdAt,
                    firebaseMessage.signature
                )
            } else if (firebaseMessage.signature.isNotEmpty()) {
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
                    firebaseMessage.createdAt, signatureValid
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
                timestamp = firebaseMessage.createdAt,
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
    // FIREBASE LISTENERS
    // ========================================================================

    fun listenForMessages(conversationId: String, sinceTimestamp: Long = 0): Flow<FirebaseMessage> =
        FirebaseRelay.listenForMessages(conversationId, sinceTimestamp)

    /**
     * Mark a conversation as read (reset unread count to 0).
     * Called when the user opens a chat.
     */
    suspend fun markConversationRead(conversationId: String) {
        conversationDao.resetUnreadCount(conversationId)
    }

    /**
     * Start listening for new messages on ALL accepted conversations.
     * Used by the conversation list screen to update lastMessage in real-time
     * even when no individual chat is open.
     *
     * @param scope The CoroutineScope to launch listeners in (typically viewModelScope).
     * @param activeListeners A mutable set tracking which conversations already have listeners,
     *                        to avoid launching duplicate listeners.
     */
    suspend fun startListeningAllConversations(
        scope: kotlinx.coroutines.CoroutineScope,
        activeListeners: MutableSet<String>
    ) {
        val conversations = conversationDao.getAcceptedConversations()
        for (conv in conversations) {
            if (conv.conversationId in activeListeners) continue
            activeListeners.add(conv.conversationId)

            // Use lastDeliveredAt as lower-bound (same as ChatViewModel) so the
            // global listener doesn't re-fetch messages that were already decrypted.
            // Falls back to createdAt only for brand-new conversations with no deliveries yet.
            val since = if (conv.lastDeliveredAt > 0L) conv.lastDeliveredAt else conv.createdAt
            listenForMessages(conv.conversationId, since)
                .onEach { firebaseMessage ->
                    receiveMessage(conv.conversationId, firebaseMessage)
                }
                .catch { /* Silently handle Firebase errors */ }
                .launchIn(scope)
        }
    }

    // ========================================================================
    // CONTACT REQUESTS (INBOX)
    // ========================================================================

    /**
     * Store the user's display name on Firebase (used by Cloud Function for push).
     */
    suspend fun storeDisplayNameOnFirebase(displayName: String) {
        FirebaseRelay.storeDisplayName(displayName)
    }

    /**
     * Publish the ML-KEM-1024 public key on Firebase.
     * Should be called once after account creation or BIP-39 restore.
     */
    suspend fun publishMLKEMPublicKey() {
        val publicKey = CryptoManager.getPublicKey()
        if (publicKey == null) {
            Log.e("SecureChat", "publishMLKEMPublicKey: identity key is null!")
            return
        }
        val mlkemPubKey = CryptoManager.getMLKEMPublicKey()
        if (mlkemPubKey == null) {
            Log.e("SecureChat", "publishMLKEMPublicKey: ML-KEM key not yet generated")
            return
        }
        Log.d("SecureChat", "publishMLKEMPublicKey: publishing to Firebase")
        FirebaseRelay.registerMLKEMPublicKey(mlkemPubKey)
        FirebaseRelay.storeMLKEMPublicKeyByIdentity(publicKey, mlkemPubKey)
    }

    /**
     * Publish the Ed25519 signing public key on Firebase (by identity public key hash).
     * Should be called once after account creation or BIP-39 restore.
     */
    suspend fun publishSigningPublicKey() {
        val publicKey = CryptoManager.getPublicKey()
        if (publicKey == null) {
            Log.e("SecureChat", "publishSigningPublicKey: identity key is null!")
            return
        }
        val signingPubKey = try {
            CryptoManager.getSigningPublicKeyBase64()
        } catch (e: Exception) {
            Log.e("SecureChat", "publishSigningPublicKey: failed to get signing key", e)
            return
        }
        Log.d("SecureChat", "publishSigningPublicKey: publishing to Firebase")
        FirebaseRelay.storeSigningPublicKey(signingPubKey)
        FirebaseRelay.storeSigningPublicKeyByIdentity(publicKey, signingPubKey)
    }

    /**
     * Store FCM token on Firebase (opt-in push notifications).
     */
    suspend fun storeFcmToken(token: String) {
        FirebaseRelay.storeFcmToken(token)
    }

    /**
     * Delete FCM token from Firebase (opt-out push notifications).
     */
    suspend fun deleteFcmToken() {
        FirebaseRelay.deleteFcmToken()
    }

    /**
     * Send a contact request to the recipient's inbox on Firebase.
     * Called automatically when sending the first message to a new contact.
     */
    suspend fun sendContactRequest(contactPublicKey: String) {
        val user = userDao.getUser() ?: return
        // Use stored conversationId — never re-derive from public keys
        val conversationId = conversationDao.getConversationByParticipantPublicKey(contactPublicKey)?.conversationId
            ?: return  // No conversation found for this contact
        val signingPublicKey = try { CryptoManager.getSigningPublicKeyBase64() } catch (_: Exception) { null }

        try {
            FirebaseRelay.sendContactRequest(
                recipientPublicKey = contactPublicKey,
                senderPublicKey = user.publicKey,
                senderDisplayName = user.displayName,
                conversationId = conversationId,
                senderSigningPublicKey = signingPublicKey
            )
        } catch (_: Exception) {
            // Best effort — message will still be on Firebase
        }
    }

    /**
     * Listen for incoming contact requests in the user's inbox.
     */
    fun listenForContactRequests(): Flow<FirebaseRelay.ContactRequest> {
        val publicKey = CryptoManager.getPublicKey() ?: return kotlinx.coroutines.flow.emptyFlow()
        return FirebaseRelay.listenForContactRequests(publicKey)
    }

    /**
     * Accept an incoming contact request:
     * 1. Add the sender as a contact
     * 2. Create the conversation + initialize ratchet (accepted = true)
     * 3. Notify the sender via Firebase that the request was accepted
     * 4. Remove the request from Firebase inbox
     * Returns the created Conversation.
     */
    suspend fun acceptContactRequest(request: FirebaseRelay.ContactRequest): Conversation {
        // Ensure Firebase auth before any write operations
        if (!FirebaseRelay.isAuthenticated()) {
            FirebaseRelay.signInAnonymously()
        }

        // Add contact (with signing key from the request if available)
        addContact(request.senderDisplayName, request.senderPublicKey, request.senderSigningPublicKey)

        // Create conversation using the ID sent by the initiator
        val conversation = createConversation(
            request.senderPublicKey,
            request.senderDisplayName,
            accepted = true,
            conversationId = request.conversationId
        )

        // Sync any messages Bob sent before we accepted — critically, this picks up
        // his first message which carries the PQXDH kemCiphertext. Without this step,
        // the real-time listener (started later with sinceTimestamp = now) would miss
        // that message and Alice's PQXDH upgrade would never run, causing all of Bob's
        // messages to show [Échec du déchiffrement].
        syncExistingMessages(conversation.conversationId)

        // Notify sender that we accepted
        val myPublicKey = userDao.getUser()?.publicKey
        if (myPublicKey != null) {
            try {
                FirebaseRelay.notifyRequestAccepted(request.conversationId, myPublicKey)
            } catch (_: Exception) { }

            // Remove from inbox
            try {
                FirebaseRelay.removeContactRequest(myPublicKey, request.conversationId)
            } catch (_: Exception) { }
        }

        return conversation
    }

    /**
     * Fetch and decrypt all existing messages from Firebase for a conversation.
     * Used after accepting a contact request to retrieve messages sent before acceptance.
     */
    private suspend fun syncExistingMessages(conversationId: String) {
        try {
            val firebaseMessages = FirebaseRelay.fetchExistingMessages(conversationId)
            for (message in firebaseMessages) {
                receiveMessage(conversationId, message)
            }
        } catch (_: Exception) {
            // Best effort — messages will be picked up by the real-time listener later
        }
    }

    /**
     * Check if a contact request has already been accepted (conversation exists).
     */
    suspend fun isContactRequestAlreadyAccepted(conversationId: String): Boolean {
        return conversationDao.getConversationById(conversationId) != null
    }

    /**
     * Listen for an acceptance notification for ONE specific pending conversation.
     * This targets /accepted/{conversationId} directly so Firebase rules (per-participant
     * read) are satisfied — fixing the PERMISSION_DENIED the global listener had.
     */
    fun listenForAcceptance(conversationId: String): Flow<String> =
        FirebaseRelay.listenForAcceptance(conversationId)

    /**
     * Return all conversationIds where accepted = false (outgoing pending invites).
     */
    suspend fun getPendingConversationIds(): List<String> =
        conversationDao.getPendingConversationIds()

    /**
     * Listen for acceptance notifications from Firebase.
     * When a contact accepts our invitation, we update the local conversation.
     */
    fun listenForAcceptances(): Flow<String> = FirebaseRelay.listenForAcceptances()

    /**
     * Mark a conversation as accepted locally.
     * Called when we receive an acceptance notification from Firebase.
     */
    suspend fun markConversationAccepted(conversationId: String) {
        val conversation = conversationDao.getConversationById(conversationId) ?: return
        if (!conversation.accepted) {
            conversationDao.updateConversation(conversation.copy(accepted = true))
            // Clean up the acceptance notification from Firebase
            try {
                FirebaseRelay.removeAcceptanceNotification(conversationId)
            } catch (_: Exception) { }
        }
    }

    // ========================================================================
    // FINGERPRINT VERIFICATION
    // ========================================================================

    /**
     * Mark a conversation's fingerprint as verified/unverified.
     * Verification state is LOCAL-ONLY — each participant decides independently.
     * A notification event is pushed to Firebase so the other side sees a message.
     */
    suspend fun verifyFingerprint(conversationId: String, verified: Boolean) {
        conversationDao.updateFingerprintVerified(conversationId, verified)
        insertFingerprintInfoMessage(conversationId, verified, isLocal = true)
        val ts = System.currentTimeMillis()
        lastFingerprintPushTimestamp = ts
        val event = "${if (verified) "verified" else "unverified"}:$ts"
        try {
            FirebaseRelay.pushFingerprintEvent(conversationId, event)
        } catch (_: Exception) { }
    }

    /**
     * Listen for remote fingerprint verification events from Firebase.
     */
    fun listenForFingerprintEvent(conversationId: String): Flow<String> =
        FirebaseRelay.listenForFingerprintEvent(conversationId)

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

    /**
     * Check if a conversation still exists on Firebase (not deleted by other user).
     */
    suspend fun getConversationIdByContactPublicKey(publicKey: String): String? =
        conversationDao.getConversationByParticipantPublicKey(publicKey)?.conversationId

    suspend fun isConversationAliveOnFirebase(conversationId: String): Boolean {
        return try {
            FirebaseRelay.conversationExists(conversationId)
        } catch (_: Exception) {
            false // Any error (permission denied, network) = treat as dead
        }
    }

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
    // FIREBASE CLEANUP
    // ========================================================================

    /**
     * Delete messages older than 7 days from Firebase for a conversation.
     * Called periodically to prevent unlimited accumulation.
     */
    suspend fun cleanupOldFirebaseMessages(conversationId: String) {
        try {
            FirebaseRelay.deleteOldMessages(conversationId)
        } catch (_: Exception) {
            // Cleanup is best-effort, don't crash
        }
    }

    // ========================================================================
    // EPHEMERAL MESSAGES
    // ========================================================================

    /**
     * Set ephemeral duration for a conversation.
     * Writes to BOTH local DB and Firebase so the other participant sees it too.
     * Also inserts a local info message visible in the chat.
     */
    suspend fun setEphemeralDuration(conversationId: String, durationMs: Long) {
        conversationDao.updateEphemeralDuration(conversationId, durationMs)
        insertEphemeralInfoMessage(conversationId, durationMs)
        try {
            FirebaseRelay.setEphemeralDuration(conversationId, durationMs)
        } catch (_: Exception) { }
    }

    suspend fun setDummyTraffic(conversationId: String, enabled: Boolean) {
        conversationDao.updateDummyTraffic(conversationId, enabled)
    }

    suspend fun getConversationsWithDummyTraffic(): List<Conversation> =
        conversationDao.getConversationsWithDummyTraffic()

    /**
     * Listen for remote changes to ephemeral duration (set by the other participant).
     */
    fun listenForEphemeralDuration(conversationId: String): Flow<Long> =
        FirebaseRelay.listenForEphemeralDuration(conversationId)

    /**
     * Sync ephemeral duration to local DB only (no Firebase write).
     * Used when receiving a remote change to avoid infinite write loop.
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
     * 1. Delete user profile + conversations + inbox from Firebase
     * 2. Clear all Room tables (user, contacts, conversations, messages, ratchet state)
     * 3. Delete the identity key pair from EncryptedSharedPreferences
     * 4. Sign out from Firebase
     */
    suspend fun resetAccount() {
        // Gather data needed for Firebase cleanup BEFORE clearing local DB
        val publicKey = CryptoManager.getPublicKey()
        val conversations = conversationDao.getAllConversationsList()

        // Clean Firebase
        try {
            FirebaseRelay.deleteUserProfile()
            if (publicKey != null) {
                FirebaseRelay.deleteInbox(publicKey)
                FirebaseRelay.deleteSigningKey(publicKey)
                FirebaseRelay.deleteMLKEMKey(publicKey)       // was missing
            }
            for (convo in conversations) {
                FirebaseRelay.deleteConversation(convo.conversationId)
                // Remove any pending /accepted/{id} node we may have written
                if (!convo.accepted) {
                    try { FirebaseRelay.removeAcceptanceNotification(convo.conversationId) } catch (_: Exception) {}
                }
            }
        } catch (_: Exception) {
            // Best-effort cleanup — don\'t block account deletion
        }

        // Clear local data
        clearMutexes()
        db.clearAllTables()
        CryptoManager.deleteIdentityKey()
        FirebaseRelay.signOut()
    }
}
