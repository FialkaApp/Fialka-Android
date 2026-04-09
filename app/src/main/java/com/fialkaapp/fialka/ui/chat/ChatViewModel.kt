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
package com.fialkaapp.fialka.ui.chat

import android.app.Application
import androidx.lifecycle.*
import com.fialkaapp.fialka.data.model.MessageLocal
import com.fialkaapp.fialka.data.repository.ChatRepository
import com.fialkaapp.fialka.tor.P2PServer
import com.fialkaapp.fialka.util.DummyTrafficManager
import com.fialkaapp.fialka.util.NotificationHelper
import com.fialkaapp.fialka.wallet.PaymentMessageData
import com.fialkaapp.fialka.wallet.WalletRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
                val isFingerprint = msg.plaintext.startsWith("🔐") || msg.plaintext.startsWith("🔓")
                items.add(ChatItem.InfoMessage(msg.plaintext, msg.timestamp, clickable = isFingerprint))
            } else {
                items.add(ChatItem.Message(msg))
            }
        }
        return items
    }

    /**
     * Initialize the ViewModel with a conversation ID.
     * Messages arrive via P2PServer (Tor P2P).
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
            NotificationHelper.cancelConversation(getApplication(), conversationId)
            ChatRepository.currentlyViewedConversation = conversationId

            _conversationIdLive.value = conversationId

            // Activate ephemeral timers on received messages now that user is reading them
            repository.activateEphemeralTimers(conversationId)

            // Start periodic ephemeral message cleanup
            startEphemeralCleanup()

            // Listen for remote ephemeral setting changes (other user changed it)
            listenForEphemeralChanges(conversationId)

            // Listen for remote fingerprint verification changes
            listenForFingerprintChanges(conversationId)

            // Check if conversation is accepted
            _isAccepted.value = conversation?.accepted ?: true

            if (conversation?.accepted != true) {
                // Listen for acceptance via P2PServer SharedFlow
                viewModelScope.launch {
                    P2PServer.incomingAcceptances.collect { acceptedId ->
                        if (acceptedId == conversationId) {
                            repository.markConversationAccepted(conversationId)
                            _isAccepted.postValue(true)
                        }
                    }
                }
            }
        }
    }

    /**
     * Send a message: encrypt → Tor P2P → save locally.
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
                DummyTrafficManager.onRealMessageSent()
                _sendError.value = null
            } catch (e: Exception) {
                _sendError.value = e.message ?: "Échec de l'envoi"
            }
        }
    }

    /**
     * Generate a fresh XMR subaddress for this conversation and send it as a
     * payment request message over the existing ratchet channel.
     *
     * @param amountPiconero  Requested amount (0 = open / "any amount")
     * @param label           Optional memo shown on the payment card
     */
    fun sendPaymentRequest(amountPiconero: Long = 0L, label: String = "") {
        if (conversationId.isEmpty()) return
        if (_isAccepted.value != true) {
            _sendError.value = "En attente d'acceptation par le contact"
            return
        }
        viewModelScope.launch {
            try {
                val walletRepo = WalletRepository(getApplication())
                val address = walletRepo.getOrGenerateFreshAddress(conversationId)
                if (address == null) {
                    _sendError.value = "Impossible de générer une adresse XMR"
                    return@launch
                }
                val plaintext = PaymentMessageData(address, amountPiconero, label).toPlaintext()
                repository.sendMessage(conversationId, plaintext)
                DummyTrafficManager.onRealMessageSent()
                _sendError.value = null
            } catch (e: Exception) {
                _sendError.value = e.message ?: "Échec envoi demande de paiement"
            }
        }
    }

    /**
     * Send a file with E2E encryption.
     * The file is encrypted locally, sent via Tor P2P as ciphertext,
     * and the decryption key is sent via the Double Ratchet.
     */
    fun sendFile(fileBytes: ByteArray, fileName: String, isOneShot: Boolean = false) {
        if (conversationId.isEmpty()) return

        if (_isAccepted.value != true) {
            _sendError.value = "En attente d'acceptation par le contact"
            return
        }

        viewModelScope.launch {
            try {
                repository.sendFile(conversationId, fileBytes, fileName, isOneShot)
                DummyTrafficManager.onRealMessageSent()
                _sendError.value = null
            } catch (e: Exception) {
                _sendError.value = "Échec de l'envoi du fichier: ${e.message}"
            }
        }
    }

    /**
     * Retry a failed file download.
     */
    fun retryFileDownload(messageId: String) {
        viewModelScope.launch {
            try {
                repository.retryFileDownload(messageId)
            } catch (_: Exception) { }
        }
    }

    /**
     * Mark a one-shot image as opened — deletes file, prevents re-viewing.
     */
    fun markOneShotOpened(messageId: String) {
        viewModelScope.launch {
            try {
                repository.markOneShotOpened(messageId)
            } catch (_: Exception) { }
        }
    }

    /**
     * Resend a failed message — resets the outbox retry so it gets picked up again.
     */
    fun resendMessage(messageId: String) {
        viewModelScope.launch {
            try {
                repository.resendMessage(messageId)
            } catch (_: Exception) { }
        }
    }

    /** Ephemeral duration LiveData — updated when either user changes the setting. */
    private val _ephemeralDuration = MutableLiveData<Long>(0L)
    val ephemeralDuration: LiveData<Long> = _ephemeralDuration

    /**
     * Listen for remote ephemeral duration changes via P2PServer SharedFlow.
     * When the OTHER user changes ephemeral setting, we update our local DB
     * so both sides stay in sync, and insert an info message in the chat.
     */
    private fun listenForEphemeralChanges(conversationId: String) {
        viewModelScope.launch {
            P2PServer.ephemeralEvents.collect { (convId, duration) ->
                if (convId != conversationId) return@collect
                _ephemeralDuration.postValue(duration)
                // Sync remote → local DB + insert info message if changed
                val currentConv = repository.getConversation(conversationId)
                if (currentConv != null && currentConv.ephemeralDuration != duration) {
                    repository.syncEphemeralDurationLocally(conversationId, duration)
                    repository.insertEphemeralInfoMessage(conversationId, duration)
                }
            }
        }
    }

    /**
     * Listen for remote fingerprint verification events via P2PServer SharedFlow.
     * Each user's verification state is independent — this only shows
     * an info message when the OTHER user verifies/unverifies.
     * Self-echo is filtered using the timestamp embedded in the event.
     */
    private fun listenForFingerprintChanges(conversationId: String) {
        viewModelScope.launch {
            P2PServer.fingerprintEvents.collect { (convId, event) ->
                if (convId != conversationId) return@collect
                // Parse event: "verified:1679000000000" or "unverified:1679000000000"
                val parts = event.split(":")
                if (parts.size != 2) return@collect
                val verified = parts[0] == "verified"
                val timestamp = parts[1].toLongOrNull() ?: return@collect
                // Skip self-echo (our own push)
                if (timestamp == ChatRepository.lastFingerprintPushTimestamp) return@collect
                // This is from the other participant — show info message
                repository.insertFingerprintInfoMessage(conversationId, verified, isLocal = false)
            }
        }
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
