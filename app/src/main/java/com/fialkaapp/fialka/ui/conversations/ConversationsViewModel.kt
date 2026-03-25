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
package com.fialkaapp.fialka.ui.conversations

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.fialkaapp.fialka.data.model.ContactRequest
import com.fialkaapp.fialka.data.model.Conversation
import com.fialkaapp.fialka.data.repository.ChatRepository
import com.fialkaapp.fialka.tor.P2PServer
import com.fialkaapp.fialka.util.DummyTrafficManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    val conversations: LiveData<List<Conversation>> = repository.getAllConversations()

    private val _accountReset = MutableLiveData<Boolean?>()
    val accountReset: LiveData<Boolean?> = _accountReset

    private val _pendingRequests = MutableLiveData<List<ContactRequest>>(emptyList())
    val pendingRequests: LiveData<List<ContactRequest>> = _pendingRequests

    private val pendingList = mutableListOf<ContactRequest>()

    // Track pending conversations that already have an acceptance listener running
    private val activeAcceptanceListeners = mutableSetOf<String>()

    // Observer to react when conversations change (new conversation added, etc.)
    private val conversationsObserver = Observer<List<Conversation>> { convos ->
        // Start acceptance listeners for any pending conversation not yet covered
        convos.filter { !it.accepted }.forEach { conv ->
            if (activeAcceptanceListeners.add(conv.conversationId)) {
                startAcceptanceListener(conv.conversationId)
            }
        }
    }

    init {
        listenForIncomingRequests()
        // Acceptance listeners are started dynamically via conversationsObserver
        conversations.observeForever(conversationsObserver)
        DummyTrafficManager.start(viewModelScope, repository)
    }

    override fun onCleared() {
        super.onCleared()
        conversations.removeObserver(conversationsObserver)
        DummyTrafficManager.stop()
    }

    /**
     * Collect incoming contact requests from P2PServer SharedFlow.
     * Requests arrive via Tor P2P — no Firebase polling needed.
     */
    private fun listenForIncomingRequests() {
        viewModelScope.launch {
            P2PServer.incomingRequests.collect { request ->
                // If conversation exists locally, treat as stale — clean up so user can re-accept
                if (repository.isContactRequestAlreadyAccepted(request.conversationId)) {
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
        }
    }

    /** Listen for acceptance of a pending conversation via P2PServer SharedFlow. */
    private fun startAcceptanceListener(conversationId: String) {
        viewModelScope.launch {
            P2PServer.incomingAcceptances.collect { acceptedId ->
                if (acceptedId == conversationId) {
                    repository.markConversationAccepted(acceptedId)
                }
            }
        }
    }

    fun acceptRequest(request: ContactRequest) {
        viewModelScope.launch {
            try {
                repository.acceptContactRequest(request)

                // Remove from pending list
                pendingList.removeAll { it.conversationId == request.conversationId }
                _pendingRequests.value = pendingList.toList()
            } catch (e: Exception) {
            }
        }
    }

    fun declineRequest(request: ContactRequest) {
        // Just remove from pending list — no Firebase to clean up
        pendingList.removeAll { it.conversationId == request.conversationId }
        _pendingRequests.value = pendingList.toList()
    }

    fun resetAccount() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.resetAccount()
                }
                _accountReset.value = true
            } catch (e: Exception) {
                _accountReset.value = false
            }
        }
    }

    fun onAccountResetHandled() {
        _accountReset.value = null
    }
}
