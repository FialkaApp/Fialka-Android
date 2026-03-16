package com.securechat.data.repository

import android.content.Context
import androidx.lifecycle.LiveData
import com.securechat.crypto.CryptoManager
import com.securechat.crypto.SymmetricRatchet
import com.securechat.data.local.SecureChatDatabase
import com.securechat.data.model.*
import com.securechat.data.remote.FirebaseRelay
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
 *  - SymmetricRatchet (Perfect Forward Secrecy)
 *
 * Thread safety: ratchet operations are serialized per-conversation via Mutex
 * to prevent race conditions when multiple Firebase messages arrive simultaneously.
 */
class ChatRepository(context: Context) {

    private val db = SecureChatDatabase.getInstance(context)
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
    }

    // ========================================================================
    // USER
    // ========================================================================

    suspend fun getUser(): UserLocal? = userDao.getUser()

    fun getUserLive(): LiveData<UserLocal?> = userDao.getUserLive()

    suspend fun createUser(displayName: String): UserLocal {
        val publicKey = CryptoManager.generateIdentityKeyPair()
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

    suspend fun addContact(displayName: String, publicKey: String): Contact {
        // Check for duplicate contact by public key
        val existing = contactDao.getContactByPublicKey(publicKey)
        if (existing != null) return existing

        val contact = Contact(
            contactId = UUID.randomUUID().toString(),
            displayName = displayName,
            publicKey = publicKey
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

    suspend fun getConversation(conversationId: String): Conversation? =
        conversationDao.getConversationById(conversationId)

    suspend fun createConversation(contactPublicKey: String, contactName: String, accepted: Boolean = true): Conversation {
        val myPublicKey = userDao.getUser()?.publicKey
            ?: throw IllegalStateException("User not initialized")

        val conversationId = CryptoManager.deriveConversationId(myPublicKey, contactPublicKey)

        // Check if conversation already exists
        val existing = conversationDao.getConversationById(conversationId)
        if (existing != null) return existing

        // Compute shared emoji fingerprint (96-bit, same on both sides)
        val sharedFingerprint = CryptoManager.getSharedFingerprint(myPublicKey, contactPublicKey)

        val conversation = Conversation(
            conversationId = conversationId,
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
            FirebaseRelay.registerParticipant(conversationId)
        } catch (_: Exception) { }

        // Initialize ratchet state for this conversation
        initializeRatchet(conversationId, myPublicKey, contactPublicKey)

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
        val sharedSecret = CryptoManager.performKeyAgreement(contactPublicKey)
        val isInitiator = myPublicKey < contactPublicKey

        val (rootKey, sendChainKey, recvChainKey) = SymmetricRatchet.initializeChains(
            sharedSecret, isInitiator
        )

        val ratchetState = RatchetState(
            conversationId = conversationId,
            rootKey = rootKey,
            sendChainKey = sendChainKey,
            recvChainKey = recvChainKey,
            sendIndex = 0,
            recvIndex = 0
        )
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
            // 1. Get ratchet state and advance send chain
            val ratchetState = getOrCreateRatchetState(conversationId, conversation.participantPublicKey)
            val (newSendChainKey, messageKey) = SymmetricRatchet.advanceChain(ratchetState.sendChainKey)
            val messageIndex = ratchetState.sendIndex

            // 2. Embed messageIndex in plaintext for metadata privacy, then encrypt
            val augmentedPlaintext = "$messageIndex|$plaintext"
            val encryptedData = CryptoManager.encrypt(augmentedPlaintext, messageKey)

            // 3. Send to Firebase — metadata-hardened: no senderPublicKey, no messageIndex
            val firebaseMessage = FirebaseMessage(
                ciphertext = encryptedData.ciphertext,
                iv = encryptedData.iv,
                createdAt = System.currentTimeMillis(),
                senderUid = FirebaseRelay.getCurrentUid() ?: ""
            )
            FirebaseRelay.sendMessage(conversationId, firebaseMessage)

            // 4. Firebase succeeded → persist ratchet state
            ratchetDao.insertOrUpdate(
                ratchetState.copy(
                    sendChainKey = newSendChainKey,
                    sendIndex = messageIndex + 1
                )
            )

            // 5. Save plaintext locally (with ephemeral timing if enabled)
            val ephDuration = conversation.ephemeralDuration
            val expiresAt = if (ephDuration > 0) System.currentTimeMillis() + ephDuration else 0L
            val localMessage = MessageLocal(
                localId = UUID.randomUUID().toString(),
                conversationId = conversationId,
                senderPublicKey = user.publicKey,
                plaintext = plaintext,
                isMine = true,
                ephemeralDuration = ephDuration,
                expiresAt = expiresAt
            )
            messageDao.insertMessage(localMessage)
            updateConversationLastMessage(conversationId, plaintext)

            localMessage
        }
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

        // Skip own messages (fast check before acquiring mutex)
        if (myUid.isNotEmpty() && firebaseMessage.senderUid == myUid) return null

        return getMutex(conversationId).withLock {
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
            val ratchetState = getOrCreateRatchetState(conversationId, conversation.participantPublicKey)

            // Trial decryption: messageIndex is embedded in ciphertext as "index|plaintext"
            var tempChainKey = ratchetState.recvChainKey
            var decryptedPlaintext: String? = null
            var finalChainKey: String? = null
            var foundIndex = -1

            for (skip in 0..MAX_RATCHET_SKIP) {
                val (nextChainKey, messageKey) = SymmetricRatchet.advanceChain(tempChainKey)
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
                ratchetDao.insertOrUpdate(
                    ratchetState.copy(
                        recvChainKey = finalChainKey,
                        recvIndex = foundIndex + 1
                    )
                )
            }

            // Save locally (with ephemeral timing if conversation has it enabled)
            // For received messages, expiresAt starts at 0 (timer starts on READ).
            // If the chat is currently open, start the timer immediately.
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
                expiresAt = expiresAt
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

            listenForMessages(conv.conversationId, conv.createdAt)
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
        val conversationId = CryptoManager.deriveConversationId(user.publicKey, contactPublicKey)

        try {
            FirebaseRelay.sendContactRequest(
                recipientPublicKey = contactPublicKey,
                senderPublicKey = user.publicKey,
                senderDisplayName = user.displayName,
                conversationId = conversationId
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

        // Add contact
        addContact(request.senderDisplayName, request.senderPublicKey)

        // Create conversation (accepted = true, ratchet initialized, participant registered)
        val conversation = createConversation(request.senderPublicKey, request.senderDisplayName, accepted = true)

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
     * Mark a conversation's fingerprint as verified (user has manually compared emojis).
     */
    suspend fun verifyFingerprint(conversationId: String, verified: Boolean) {
        conversationDao.updateFingerprintVerified(conversationId, verified)
    }

    /**
     * Delete a conversation and all its messages from the local database.
     */
    suspend fun deleteConversation(conversationId: String) {
        messageDao.deleteMessagesForConversation(conversationId)
        val conversation = conversationDao.getConversationById(conversationId) ?: return
        conversationDao.deleteConversation(conversation)
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
     */
    suspend fun setEphemeralDuration(conversationId: String, durationMs: Long) {
        conversationDao.updateEphemeralDuration(conversationId, durationMs)
        try {
            FirebaseRelay.setEphemeralDuration(conversationId, durationMs)
        } catch (_: Exception) { }
    }

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
     * 2. Delete the identity key pair from Android Keystore
     * 3. Sign out from Firebase
     */
    suspend fun resetAccount() {
        // Clear ratchet mutexes
        clearMutexes()
        db.clearAllTables()
        CryptoManager.deleteIdentityKey()
        FirebaseRelay.signOut()
    }
}
