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
package com.securechat.ui.conversations

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.securechat.data.model.Conversation
import com.securechat.data.remote.FirebaseRelay
import com.securechat.data.repository.ChatRepository
import com.securechat.util.DummyTrafficManager
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

    // Track which conversations already have active Firebase message listeners
    private val activeMessageListeners = mutableSetOf<String>()
    // Track pending conversations that already have an acceptance listener running
    private val activeAcceptanceListeners = mutableSetOf<String>()

    // Observer to react when conversations change (new conversation added, etc.)
    private val conversationsObserver = Observer<List<Conversation>> { convos ->
        refreshMessageListeners()
        // Start acceptance listeners for any pending conversation not yet covered
        convos.filter { !it.accepted }.forEach { conv ->
            if (activeAcceptanceListeners.add(conv.conversationId)) {
                startAcceptanceListener(conv.conversationId)
            }
        }
    }

    init {
        ensureAuthenticated()
        listenForIncomingRequests()
        // Acceptance listeners are started dynamically via conversationsObserver
        conversations.observeForever(conversationsObserver)
        DummyTrafficManager.start(viewModelScope, repository)
        // Publish signing key once per process (skip on subsequent ViewModel recreations)
        if (!signingKeyPublished) {
            viewModelScope.launch {
                try {
                    if (!FirebaseRelay.isAuthenticated()) {
                        FirebaseRelay.signInAnonymously()
                    }
                    repository.publishSigningPublicKey()
                    signingKeyPublished = true
                } catch (_: Exception) { }
            }
        }
    }

    companion object {
        @Volatile
        private var signingKeyPublished = false

        fun markSigningKeyPublished() { signingKeyPublished = true }
    }

    override fun onCleared() {
        super.onCleared()
        conversations.removeObserver(conversationsObserver)
        DummyTrafficManager.stop()
    }

    /**
     * Start Firebase message listeners for all accepted conversations
     * that don't already have one. This ensures the conversation list
     * shows new messages in real-time even when no chat is open.
     */
    private fun refreshMessageListeners() {
        viewModelScope.launch {
            if (!FirebaseRelay.isAuthenticated()) {
                try { FirebaseRelay.signInAnonymously() } catch (_: Exception) { return@launch }
            }
            repository.startListeningAllConversations(viewModelScope, activeMessageListeners)
        }
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
                    // If conversation exists locally, check if it's still alive on Firebase
                    if (repository.isContactRequestAlreadyAccepted(request.conversationId)) {
                        val alive = repository.isConversationAliveOnFirebase(request.conversationId)
                        if (alive) return@onEach // Truly active — skip

                        // Stale conversation — clean up so user can re-accept
                        val contact = repository.getContactByPublicKey(request.senderPublicKey)
                        if (contact != null) {
                            repository.deleteStaleConversation(request.conversationId, contact)
                        }
                    }

                    // Avoid duplicates in pending list
                    if (pendingList.none { it.conversationId == request.conversationId }) {
                        pendingList.add(request)
                        _pendingRequests.postValue(pendingList.toList())
                    }
                }
                .catch { e ->
                    Log.e("SecureChat", "Inbox listener error", e)
                }
                .launchIn(viewModelScope)
        }
    }

    private fun startAcceptanceListener(conversationId: String) {
        viewModelScope.launch {
            if (!FirebaseRelay.isAuthenticated()) {
                try { FirebaseRelay.signInAnonymously() } catch (_: Exception) { return@launch }
            }
            repository.listenForAcceptance(conversationId)
                .onEach { repository.markConversationAccepted(it) }
                .catch { e -> Log.e("SecureChat", "Acceptance listener error for $conversationId", e) }
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

            // Listen per-conversationId on /accepted/{id} so Firebase per-participant
            // read rules are satisfied (global /accepted parent would get PERMISSION_DENIED)
            val pendingIds = repository.getPendingConversationIds()
            pendingIds.forEach { conversationId ->
                repository.listenForAcceptance(conversationId)
                    .onEach { acceptedId ->
                        repository.markConversationAccepted(acceptedId)
                    }
                    .catch { e ->
                        Log.e("SecureChat", "Acceptance listener error for $conversationId", e)
                    }
                    .launchIn(viewModelScope)
            }
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
