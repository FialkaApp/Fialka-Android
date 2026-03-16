package com.securechat.ui.chat

import android.app.Application
import androidx.lifecycle.*
import com.securechat.data.model.MessageLocal
import com.securechat.data.remote.FirebaseRelay
import com.securechat.data.repository.ChatRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    private var conversationId: String = ""

    private val _conversationIdLive = MutableLiveData<String>()

    /** Number of unread messages at the time the chat was opened (consumed on first build). */
    private var initialUnreadCount = 0

    /** localId of the first unread message — anchors the divider position even as new messages arrive. */
    private var dividerAnchorId: String? = null

    /** Periodic job that cleans up expired ephemeral messages. */
    private var ephemeralCleanupJob: Job? = null

    override fun onCleared() {
        super.onCleared()
        ChatRepository.currentlyViewedConversation = null
        ephemeralCleanupJob?.cancel()
    }

    /**
     * Chat items = messages + optional "new messages" divider.
     * The divider is inserted once at the position where unread messages start.
     * After the user reads them and re-opens the conversation, it won't appear.
     */
    val chatItems: LiveData<List<ChatItem>> = _conversationIdLive.switchMap { id ->
        repository.getMessages(id).map { messages ->
            buildChatItems(messages)
        }
    }

    private val _sendError = MutableLiveData<String?>()
    val sendError: LiveData<String?> = _sendError

    private val _isAccepted = MutableLiveData<Boolean>(true)
    val isAccepted: LiveData<Boolean> = _isAccepted

    private val _conversationDead = MutableLiveData<Boolean>(false)
    val conversationDead: LiveData<Boolean> = _conversationDead

    private fun buildChatItems(messages: List<MessageLocal>): List<ChatItem> {
        val realMessages = messages.filter { !it.isInfoMessage }

        // On the first emission, anchor the divider to the first unread message's localId
        if (dividerAnchorId == null && initialUnreadCount > 0 && realMessages.isNotEmpty()) {
            val idx = realMessages.size - initialUnreadCount
            if (idx > 0 && idx < realMessages.size) {
                dividerAnchorId = realMessages[idx].localId
            }
            initialUnreadCount = 0 // consumed
        }

        val items = mutableListOf<ChatItem>()
        for (msg in messages) {
            if (!msg.isInfoMessage && msg.localId == dividerAnchorId) {
                items.add(ChatItem.UnreadDivider)
            }
            if (msg.isInfoMessage) {
                items.add(ChatItem.InfoMessage(msg.plaintext, msg.timestamp))
            } else {
                items.add(ChatItem.Message(msg))
            }
        }
        return items
    }

    /**
     * Initialize the ViewModel with a conversation ID.
     * Ensures Firebase auth is active, then starts listening for messages.
     * Only listens for messages created after the conversation was established.
     */
    fun init(conversationId: String) {
        if (this.conversationId == conversationId) return
        this.conversationId = conversationId

        viewModelScope.launch {
            // Capture unread count before resetting
            val conversation = repository.getConversation(conversationId)
            initialUnreadCount = conversation?.unreadCount ?: 0

            // Mark as read + flag as currently viewed
            repository.markConversationRead(conversationId)
            ChatRepository.currentlyViewedConversation = conversationId

            _conversationIdLive.value = conversationId

            // Activate ephemeral timers on received messages now that user is reading them
            repository.activateEphemeralTimers(conversationId)

            // Start periodic ephemeral message cleanup
            startEphemeralCleanup()

            // Listen for remote ephemeral setting changes (other user changed it)
            listenForEphemeralChanges(conversationId)

            // Ensure Firebase auth is still active (can expire after app kill)
            if (!FirebaseRelay.isAuthenticated()) {
                try {
                    FirebaseRelay.signInAnonymously()
                } catch (e: Exception) {
                    _sendError.value = "Erreur d'authentification Firebase"
                    return@launch
                }
            }

            // Check if conversation is accepted
            _isAccepted.value = conversation?.accepted ?: true

            if (conversation?.accepted == true) {
                startListening(conversationId, conversation.createdAt)
            } else {
                // Listen for acceptance
                repository.listenForAcceptances()
                    .onEach { acceptedId ->
                        if (acceptedId == conversationId) {
                            repository.markConversationAccepted(conversationId)
                            _isAccepted.postValue(true)
                            val conv = repository.getConversation(conversationId)
                            startListening(conversationId, conv?.createdAt ?: 0L)
                        }
                    }
                    .catch { }
                    .launchIn(viewModelScope)
            }
        }
    }

    private fun startListening(conversationId: String, sinceTimestamp: Long) {
        viewModelScope.launch {
            // Cleanup old Firebase messages (best-effort, >7 days)
            repository.cleanupOldFirebaseMessages(conversationId)

            repository.listenForMessages(conversationId, sinceTimestamp)
                .onEach { firebaseMessage ->
                    repository.receiveMessage(conversationId, firebaseMessage)
                }
                .catch { /* Silently handle Firebase errors */ }
                .launchIn(viewModelScope)
        }
    }

    /**
     * Send a message: encrypt → Firebase → save locally.
     */
    fun sendMessage(plaintext: String) {
        if (plaintext.isBlank() || conversationId.isEmpty()) return

        if (_isAccepted.value != true) {
            _sendError.value = "En attente d'acceptation par le contact"
            return
        }

        viewModelScope.launch {
            try {
                repository.sendMessage(conversationId, plaintext.trim())
                _sendError.value = null
            } catch (e: Exception) {
                // Check if conversation was deleted on Firebase
                val alive = repository.isConversationAliveOnFirebase(conversationId)
                if (!alive) {
                    _conversationDead.postValue(true)
                } else {
                    _sendError.value = e.message ?: "Échec de l'envoi"
                }
            }
        }
    }

    /** Ephemeral duration LiveData — updated when either user changes the setting. */
    private val _ephemeralDuration = MutableLiveData<Long>(0L)
    val ephemeralDuration: LiveData<Long> = _ephemeralDuration

    /**
     * Listen for remote ephemeral duration changes from Firebase.
     * When the OTHER user changes ephemeral setting, we update our local DB
     * so both sides stay in sync, and insert an info message in the chat.
     */
    private fun listenForEphemeralChanges(conversationId: String) {
        repository.listenForEphemeralDuration(conversationId)
            .onEach { duration ->
                _ephemeralDuration.postValue(duration)
                // Sync remote → local DB + insert info message if changed
                val currentConv = repository.getConversation(conversationId)
                if (currentConv != null && currentConv.ephemeralDuration != duration) {
                    repository.syncEphemeralDurationLocally(conversationId, duration)
                    repository.insertEphemeralInfoMessage(conversationId, duration)
                }
            }
            .catch { /* Silently handle */ }
            .launchIn(viewModelScope)
    }

    /**
     * Delete the dead conversation locally so the user can re-add the contact later.
     * Cleans up: messages, conversation, ratchet state, and contact.
     */
    fun deleteDeadConversation() {
        viewModelScope.launch {
            val conversation = repository.getConversation(conversationId)
            if (conversation != null) {
                val contact = repository.getContactByPublicKey(conversation.participantPublicKey)
                if (contact != null) {
                    repository.deleteStaleConversation(conversationId, contact)
                    return@launch
                }
            }
            repository.deleteConversation(conversationId)
        }
    }

    /** Periodically delete expired ephemeral messages (every 5s). */
    private fun startEphemeralCleanup() {
        ephemeralCleanupJob?.cancel()
        ephemeralCleanupJob = viewModelScope.launch {
            while (true) {
                delay(5_000)
                repository.deleteExpiredMessages()
            }
        }
    }
}
