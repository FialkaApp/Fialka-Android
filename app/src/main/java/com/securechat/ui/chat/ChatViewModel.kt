package com.securechat.ui.chat

import android.app.Application
import androidx.lifecycle.*
import com.securechat.data.model.MessageLocal
import com.securechat.data.remote.FirebaseRelay
import com.securechat.data.repository.ChatRepository
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    private var conversationId: String = ""

    private val _conversationIdLive = MutableLiveData<String>()

    val messages: LiveData<List<MessageLocal>> = _conversationIdLive.switchMap { id ->
        repository.getMessages(id)
    }

    private val _sendError = MutableLiveData<String?>()
    val sendError: LiveData<String?> = _sendError

    private val _isAccepted = MutableLiveData<Boolean>(true)
    val isAccepted: LiveData<Boolean> = _isAccepted

    /**
     * Initialize the ViewModel with a conversation ID.
     * Ensures Firebase auth is active, then starts listening for messages.
     * Only listens for messages created after the conversation was established.
     */
    fun init(conversationId: String) {
        if (this.conversationId == conversationId) return
        this.conversationId = conversationId

        _conversationIdLive.value = conversationId

        viewModelScope.launch {
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
            val conversation = repository.getConversation(conversationId)
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
                _sendError.value = e.message ?: "Échec de l'envoi"
            }
        }
    }
}
