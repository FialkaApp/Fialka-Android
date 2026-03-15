package com.securechat.ui.conversations

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.securechat.data.model.Conversation
import com.securechat.data.remote.FirebaseRelay
import com.securechat.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    val conversations: LiveData<List<Conversation>> = repository.getAllConversations()

    private val _accountReset = MutableLiveData<Boolean?>()
    val accountReset: LiveData<Boolean?> = _accountReset

    private val _pendingRequests = MutableLiveData<List<FirebaseRelay.ContactRequest>>(emptyList())
    val pendingRequests: LiveData<List<FirebaseRelay.ContactRequest>> = _pendingRequests

    private val pendingList = mutableListOf<FirebaseRelay.ContactRequest>()

    init {
        ensureAuthenticated()
        listenForIncomingRequests()
        listenForAcceptances()
    }

    private fun ensureAuthenticated() {
        if (!FirebaseRelay.isAuthenticated()) {
            viewModelScope.launch {
                try {
                    FirebaseRelay.signInAnonymously()
                } catch (e: Exception) {
                    Log.e("SecureChat", "Firebase re-auth failed", e)
                }
            }
        }
    }

    private fun listenForIncomingRequests() {
        viewModelScope.launch {
            // Wait for auth to be ready
            if (!FirebaseRelay.isAuthenticated()) {
                try {
                    FirebaseRelay.signInAnonymously()
                } catch (_: Exception) { return@launch }
            }

            repository.listenForContactRequests()
                .onEach { request ->
                    // Skip if already accepted
                    if (!repository.isContactRequestAlreadyAccepted(request.conversationId)) {
                        // Avoid duplicates in pending list
                        if (pendingList.none { it.conversationId == request.conversationId }) {
                            pendingList.add(request)
                            _pendingRequests.postValue(pendingList.toList())
                        }
                    }
                }
                .catch { e ->
                    Log.e("SecureChat", "Inbox listener error", e)
                }
                .launchIn(viewModelScope)
        }
    }

    private fun listenForAcceptances() {
        viewModelScope.launch {
            if (!FirebaseRelay.isAuthenticated()) {
                try {
                    FirebaseRelay.signInAnonymously()
                } catch (_: Exception) { return@launch }
            }

            repository.listenForAcceptances()
                .onEach { conversationId ->
                    repository.markConversationAccepted(conversationId)
                }
                .catch { e ->
                    Log.e("SecureChat", "Acceptance listener error", e)
                }
                .launchIn(viewModelScope)
        }
    }

    fun acceptRequest(request: FirebaseRelay.ContactRequest) {
        viewModelScope.launch {
            try {
                val conversation = repository.acceptContactRequest(request)

                // Remove from pending list
                pendingList.removeAll { it.conversationId == request.conversationId }
                _pendingRequests.value = pendingList.toList()
            } catch (e: Exception) {
                Log.e("SecureChat", "Accept request failed", e)
            }
        }
    }

    fun declineRequest(request: FirebaseRelay.ContactRequest) {
        viewModelScope.launch {
            // Just remove from pending list and Firebase inbox
            pendingList.removeAll { it.conversationId == request.conversationId }
            _pendingRequests.value = pendingList.toList()

            val myPublicKey = repository.getUser()?.publicKey ?: return@launch
            try {
                FirebaseRelay.removeContactRequest(myPublicKey, request.conversationId)
            } catch (_: Exception) { }
        }
    }

    fun resetAccount() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.resetAccount()
                }
                _accountReset.value = true
            } catch (e: Exception) {
                Log.e("SecureChat", "Account reset failed", e)
                _accountReset.value = false
            }
        }
    }

    fun onAccountResetHandled() {
        _accountReset.value = null
    }
}
