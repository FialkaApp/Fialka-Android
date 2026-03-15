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

            // Cleanup old Firebase messages (best-effort, >7 days)
            repository.cleanupOldFirebaseMessages(conversationId)

            // Get conversation creation time to ignore old Firebase messages
            val conversation = repository.getConversation(conversationId)
            val sinceTimestamp = conversation?.createdAt ?: 0L

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
