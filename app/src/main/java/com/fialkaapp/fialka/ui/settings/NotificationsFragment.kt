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
package com.fialkaapp.fialka.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.databinding.FragmentSettingsNotificationsBinding

/**
 * Notification settings screen.
 *
 * Shows the REAL state of:
 *  - POST_NOTIFICATIONS permission (Android 13+)
 *  - Battery optimizations (needed to keep Tor alive in background)
 *
 * The user can tap each row to grant the permission / open the relevant system screen.
 * The old "push enabled" toggle is removed — the Android permission IS the toggle.
 */
class NotificationsFragment : Fragment() {

    private var _binding: FragmentSettingsNotificationsBinding? = null
    private val binding get() = _binding!!

    private val notifPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            refreshStatus()
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

        refreshStatus()

        // Tap notification row → request permission or open system settings
        binding.switchPush.setOnClickListener {
            handleNotifTap()
        }
        binding.switchPush.setOnCheckedChangeListener { _, _ ->
            handleNotifTap()
        }

        // Tap battery row → open system settings
        binding.btnBatteryOptimization.setOnClickListener {
            openBatterySettings()
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh when user returns from system settings
        refreshStatus()
    }

    private fun handleNotifTap() {
        val granted = hasNotifPermission()
        if (granted) {
            // Already granted — open app notification settings so user can disable if they want
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
            })
        } else {
            // Request permission (Android 13+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // Below API 33 — permission automatic, open system settings
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().packageName)
                })
            }
        }
    }

    private fun openBatterySettings() {
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${requireContext().packageName}")
            })
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun refreshStatus() {
        if (_binding == null) return
        val notifGranted = hasNotifPermission()
        val batteryIgnored = isBatteryIgnored()

        // Notification row
        binding.switchPush.isChecked = notifGranted
        binding.tvPushStatus.text = if (notifGranted) {
            "✅ Activées — vous recevrez des notifications de nouveaux messages"
        } else {
            "🔕 Désactivées — appuyez pour autoriser"
        }

        // Battery row
        binding.tvBatteryStatus.text = if (batteryIgnored) {
            "✅ Optimisations batterie désactivées — Tor reste actif en arrière-plan"
        } else {
            "⚠️ Les optimisations batterie peuvent tuer Tor en arrière-plan — appuyez pour corriger"
        }
    }

    private fun hasNotifPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Automatic below API 33
        }
    }

    private fun isBatteryIgnored(): Boolean {
        val pm = requireContext().getSystemService(PowerManager::class.java)
        return pm.isIgnoringBatteryOptimizations(requireContext().packageName)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val PREFS_NAME = "fialka_settings"
        const val KEY_PUSH_ENABLED = "push_notifications_enabled"
    }
}
