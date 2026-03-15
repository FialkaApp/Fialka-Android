package com.securechat.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.securechat.data.model.UserLocal
import com.securechat.data.remote.FirebaseRelay
import com.securechat.data.repository.ChatRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChatRepository(application)

    private val _state = MutableLiveData<OnboardingState>(OnboardingState.Idle)
    val state: LiveData<OnboardingState> = _state

    private val _existingUser = MutableLiveData<UserLocal?>()
    val existingUser: LiveData<UserLocal?> = _existingUser

    init {
        viewModelScope.launch {
            _existingUser.value = repository.getUser()
        }
    }

    fun createIdentity(displayName: String) {
        if (displayName.isBlank()) {
            _state.value = OnboardingState.Error("Veuillez entrer un pseudo.")
            return
        }

        _state.value = OnboardingState.Loading

        viewModelScope.launch {
            try {
                withTimeout(15_000L) {
                    // Sign in anonymously to Firebase
                    if (!FirebaseRelay.isAuthenticated()) {
                        FirebaseRelay.signInAnonymously()
                    }

                    // Create local user (generates keys)
                    val user = repository.createUser(displayName.trim())

                    // Register public key on Firebase
                    FirebaseRelay.registerPublicKey(user.publicKey)

                    _state.value = OnboardingState.Success(user)
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _state.value = OnboardingState.Error(
                    "Délai d'attente dépassé. Vérifiez votre connexion Internet " +
                    "et que la Realtime Database est bien créée dans Firebase Console."
                )
            } catch (e: Exception) {
                val message = when {
                    e.message?.contains("CONFIGURATION_NOT_FOUND") == true ->
                        "L'authentification anonyme n'est pas activée dans Firebase Console. " +
                        "Allez dans Authentication > Méthode de connexion > Anonyme > Activer."
                    e.message?.contains("NETWORK") == true ||
                    e.message?.contains("network") == true ->
                        "Erreur réseau. Vérifiez votre connexion Internet."
                    e.message?.contains("Failed to get FirebaseDatabase") == true ->
                        "URL de la base de données Firebase introuvable. " +
                        "Vérifiez l'URL dans FirebaseRelay.kt."
                    else -> e.message ?: "Erreur inconnue"
                }
                _state.value = OnboardingState.Error(message)
            }
        }
    }

    sealed class OnboardingState {
        object Idle : OnboardingState()
        object Loading : OnboardingState()
        data class Success(val user: UserLocal) : OnboardingState()
        data class Error(val message: String) : OnboardingState()
    }
}
