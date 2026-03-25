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
package com.fialkaapp.fialka.ui.addcontact

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.data.model.Conversation
import com.fialkaapp.fialka.data.repository.ChatRepository
import kotlinx.coroutines.launch

class AddContactViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    private val _state = MutableLiveData<AddContactState>(AddContactState.Idle)
    val state: LiveData<AddContactState> = _state

    fun addContact(displayName: String, publicKey: String, mlkemPublicKey: String? = null, ed25519PublicKey: String? = null) {
        // Validate input
        if (displayName.isBlank() || publicKey.isBlank()) {
            _state.value = AddContactState.Error("Veuillez remplir tous les champs.")
            return
        }

        val trimmedKey = publicKey.trim()

        if (!CryptoManager.isValidPublicKey(trimmedKey)) {
            _state.value = AddContactState.Error("Clé publique invalide.")
            return
        }

        // Validate ML-KEM key if present
        if (mlkemPublicKey != null && mlkemPublicKey.isNotEmpty()) {
            if (mlkemPublicKey.length > 2500) {
                _state.value = AddContactState.Error("Clé ML-KEM invalide (trop grande).")
                return
            }
            try {
                val decoded = android.util.Base64.decode(mlkemPublicKey, android.util.Base64.NO_WRAP)
                if (decoded.size !in 1500..1650) {
                    _state.value = AddContactState.Error("Clé ML-KEM invalide.")
                    return
                }
            } catch (_: Exception) {
                _state.value = AddContactState.Error("Clé ML-KEM invalide.")
                return
            }
        }

        // Check it's not our own key
        val myKey = CryptoManager.getPublicKey()
        if (myKey == trimmedKey) {
            _state.value = AddContactState.Error("Vous ne pouvez pas ajouter votre propre clé.")
            return
        }

        _state.value = AddContactState.Loading

        viewModelScope.launch {
            try {
                // Check for duplicate contact
                val existingContact = repository.getContactByPublicKey(trimmedKey)
                if (existingContact != null) {
                    val convoId = repository.getConversationIdByContactPublicKey(trimmedKey)
                    if (convoId != null) {
                        _state.value = AddContactState.Error(
                            "Ce contact existe déjà sous le nom \"${existingContact.displayName}\"."
                        )
                        return@launch
                    }
                }

                // Compute .onion address from Ed25519 key if available
                val signingKey = ed25519PublicKey  // raw Ed25519 base64
                val onionAddress = if (ed25519PublicKey != null) {
                    try {
                        val ed25519Bytes = android.util.Base64.decode(ed25519PublicKey, android.util.Base64.NO_WRAP)
                        CryptoManager.computeOnionFromEd25519(ed25519Bytes)
                    } catch (_: Exception) { null }
                } else null

                // Add contact to local DB (with Ed25519 signing key + .onion from QR scan)
                val contact = repository.addContact(
                    displayName.trim(),
                    trimmedKey,
                    signingPublicKey = signingKey,
                    mlkemPublicKey = mlkemPublicKey
                )

                // Update onion address on the contact if we computed it
                if (onionAddress != null && contact.onionAddress == null) {
                    repository.updateContact(contact.copy(onionAddress = onionAddress))
                }

                // Create conversation as pending (not yet accepted by the other user)
                val conversation = repository.createConversation(trimmedKey, displayName.trim(), accepted = false)

                // Notify the contact via Tor P2P
                repository.sendContactRequest(trimmedKey)

                _state.value = AddContactState.Success(conversation)
            } catch (e: Exception) {
                _state.value = AddContactState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    sealed class AddContactState {
        object Idle : AddContactState()
        object Loading : AddContactState()
        data class Success(val conversation: Conversation) : AddContactState()
        data class Error(val message: String) : AddContactState()
    }
}
