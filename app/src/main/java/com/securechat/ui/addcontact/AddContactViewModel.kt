package com.securechat.ui.addcontact

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.securechat.crypto.CryptoManager
import com.securechat.data.model.Conversation
import com.securechat.data.repository.ChatRepository
import kotlinx.coroutines.launch

class AddContactViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    private val _state = MutableLiveData<AddContactState>(AddContactState.Idle)
    val state: LiveData<AddContactState> = _state

    fun addContact(displayName: String, publicKey: String) {
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
                    _state.value = AddContactState.Error(
                        "Ce contact existe déjà sous le nom \"${existingContact.displayName}\"."
                    )
                    return@launch
                }

                // Add contact to local DB
                repository.addContact(displayName.trim(), trimmedKey)

                // Create conversation (also initializes ratchet)
                val conversation = repository.createConversation(trimmedKey, displayName.trim())

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
