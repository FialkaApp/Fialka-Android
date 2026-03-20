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
import com.securechat.databinding.FragmentSettingsNotificationsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class NotificationsFragment : Fragment() {

    private var _binding: FragmentSettingsNotificationsBinding? = null
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
        _binding = FragmentSettingsNotificationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val prefs = requireContext().getSharedPreferences(PREFS_NAME, 0)
        val pushEnabled = prefs.getBoolean(KEY_PUSH_ENABLED, false)
        binding.switchPush.isChecked = pushEnabled
        updateStatusText(pushEnabled)

        binding.switchPush.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
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
                if (!FirebaseRelay.isAuthenticated()) FirebaseRelay.signInAnonymously()
                repository.storeFcmToken(token)
            } catch (_: Exception) { }
        }
    }

    private fun disablePush() {
        savePref(false)
        updateStatusText(false)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!FirebaseRelay.isAuthenticated()) FirebaseRelay.signInAnonymously()
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
            "✅ Activées — vous recevrez des notifications de nouveaux messages"
        } else {
            "\uD83D\uDD12 Désactivées par défaut pour protéger votre vie privée"
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
