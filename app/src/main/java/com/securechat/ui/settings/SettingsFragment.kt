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

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.securechat.R
import com.securechat.databinding.FragmentSettingsBinding
import com.securechat.util.AppLockManager
import com.securechat.util.DeviceSecurityManager
import com.securechat.util.EphemeralManager
import com.securechat.util.SecurityLevel
import com.securechat.util.StrongBoxStatus
import com.securechat.util.ThemeManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Navigation to sub-screens
        binding.rowAppearance.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_appearance)
        }

        binding.rowNotifications.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_notifications)
        }

        binding.rowSecurity.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_security)
        }

        binding.rowPrivacy.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_privacy)
        }

        // Version
        try {
            val info = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.tvVersion.text = info.versionName ?: "1.0"
        } catch (_: Exception) { }

        // Device security card
        setupDeviceSecurity()
    }

    override fun onResume() {
        super.onResume()
        updateSummaries()
    }

    private fun updateSummaries() {
        // Theme summary
        binding.tvThemeSummary.text = getString(ThemeManager.getThemeInfo(ThemeManager.getTheme(requireContext())).nameRes)

        // Notifications summary
        val prefs = requireContext().getSharedPreferences("securechat_settings", 0)
        val pushEnabled = prefs.getBoolean("push_notifications_enabled", false)
        binding.tvNotifSummary.text = if (pushEnabled) "Activées" else "Désactivées"

        // Privacy summary
        val ephDuration = EphemeralManager.getDefaultDuration(requireContext())
        binding.tvPrivacySummary.text = if (ephDuration > 0) {
            "Éphémère: ${EphemeralManager.getLabelForDuration(ephDuration)}"
        } else {
            "Messages éphémères, trafic factice"
        }

        // Security summary
        val pinSet = AppLockManager.isPinSet(requireContext())
        val bioEnabled = AppLockManager.isBiometricEnabled(requireContext())
        val lockLabel = AppLockManager.getAutoLockLabel(requireContext()).lowercase()
        binding.tvSecuritySummary.text = when {
            pinSet && bioEnabled -> "PIN + Biométrie · $lockLabel"
            pinSet -> "PIN activé · $lockLabel"
            else -> "Désactivé"
        }
    }

    private fun setupDeviceSecurity() {
        val profile = DeviceSecurityManager.getSecurityProfile(requireContext())

        binding.tvAboutOsName.text = "Android"
        binding.tvAboutDeviceName.text = profile.deviceName
        binding.tvAboutSecurityLevel.text = profile.securityLevelLabel

        val badgeColor = when (profile.securityLevel) {
            SecurityLevel.MAXIMUM  -> Color.parseColor("#1DB954")
            SecurityLevel.STANDARD -> Color.parseColor("#888780")
        }
        binding.tvAboutBadge.backgroundTintList = ColorStateList.valueOf(badgeColor)
        binding.tvAboutBadge.text = profile.securityLevelLabel

        binding.tvAboutKeyStorage.text = when {
            profile.isStrongBoxAvailable                                       -> "Secure Element actif"
            profile.strongBoxStatus == StrongBoxStatus.NOT_AVAILABLE            -> "TEE standard (KeyStore)"
            profile.strongBoxStatus == StrongBoxStatus.DECLARED_BUT_UNAVAILABLE -> "Déclaré mais non fonctionnel"
            else                                                                -> "TEE standard (KeyStore)"
        }

        binding.bannerStrongboxNonGos.visibility =
            if (profile.isStrongBoxAvailable) View.VISIBLE else View.GONE
        binding.bannerSecondaryProfile.visibility =
            if (profile.isSecondaryProfile) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
