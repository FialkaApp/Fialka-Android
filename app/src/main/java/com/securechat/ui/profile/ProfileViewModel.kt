package com.securechat.ui.profile

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.securechat.data.model.UserLocal
import com.securechat.data.repository.ChatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    val user: LiveData<UserLocal?> = repository.getUserLive()

    private val _saveResult = MutableLiveData<Boolean>()
    val saveResult: LiveData<Boolean> = _saveResult

    private val _accountReset = MutableLiveData<Boolean?>()
    val accountReset: LiveData<Boolean?> = _accountReset

    fun updateDisplayName(newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            try {
                repository.updateDisplayName(newName.trim())
                repository.storeDisplayNameOnFirebase(newName.trim())
                _saveResult.value = true
            } catch (e: Exception) {
                _saveResult.value = false
            }
        }
    }

    fun resetAccount() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.resetAccount()
                }
                Log.d("SecureChat", "Account reset successful from profile")
                _accountReset.value = true
            } catch (e: Exception) {
                Log.e("SecureChat", "Account reset failed from profile", e)
                _accountReset.value = false
            }
        }
    }

    fun onAccountResetHandled() {
        _accountReset.value = null
    }
}
