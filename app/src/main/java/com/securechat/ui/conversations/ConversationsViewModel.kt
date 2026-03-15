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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConversationsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    val conversations: LiveData<List<Conversation>> = repository.getAllConversations()

    private val _accountReset = MutableLiveData<Boolean?>()
    val accountReset: LiveData<Boolean?> = _accountReset

    init {
        // Ensure Firebase auth is active on screen load
        ensureAuthenticated()
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
