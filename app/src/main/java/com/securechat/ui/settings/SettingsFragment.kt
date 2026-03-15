package com.securechat.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.messaging.FirebaseMessaging
import com.securechat.data.remote.FirebaseRelay
import com.securechat.data.repository.ChatRepository
import com.securechat.databinding.FragmentSettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val repository by lazy { ChatRepository(requireContext()) }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                enablePush()
            } else {
                binding.switchPush.isChecked = false
                updateStatusText(false)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Load current setting
        val prefs = requireContext().getSharedPreferences(PREFS_NAME, 0)
        val pushEnabled = prefs.getBoolean(KEY_PUSH_ENABLED, false)
        binding.switchPush.isChecked = pushEnabled
        updateStatusText(pushEnabled)

        binding.switchPush.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // Request notification permission on Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    enablePush()
                }
            } else {
                disablePush()
            }
        }
    }

    private fun enablePush() {
        savePref(true)
        updateStatusText(true)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                if (!FirebaseRelay.isAuthenticated()) {
                    FirebaseRelay.signInAnonymously()
                }
                repository.storeFcmToken(token)
            } catch (_: Exception) { }
        }
    }

    private fun disablePush() {
        savePref(false)
        updateStatusText(false)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!FirebaseRelay.isAuthenticated()) {
                    FirebaseRelay.signInAnonymously()
                }
                repository.deleteFcmToken()
            } catch (_: Exception) { }
        }
    }

    private fun savePref(enabled: Boolean) {
        requireContext().getSharedPreferences(PREFS_NAME, 0)
            .edit().putBoolean(KEY_PUSH_ENABLED, enabled).apply()
    }

    private fun updateStatusText(enabled: Boolean) {
        binding.tvPushStatus.text = if (enabled) {
            "✅ Activé — vous recevrez des notifications de nouveaux messages"
        } else {
            "\uD83D\uDD12 Désactivé par défaut pour protéger votre vie privée"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val PREFS_NAME = "securechat_settings"
        const val KEY_PUSH_ENABLED = "push_notifications_enabled"
    }
}
