package com.securechat.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.securechat.R
import com.securechat.databinding.FragmentSettingsBinding
import com.securechat.util.AppLockManager
import com.securechat.util.EphemeralManager
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

        binding.rowEphemeral.setOnClickListener {
            findNavController().navigate(R.id.action_settings_to_ephemeral)
        }

        // Version
        try {
            val info = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.tvVersion.text = info.versionName ?: "1.0"
        } catch (_: Exception) { }
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

        // Security summary
        val pinSet = AppLockManager.isPinSet(requireContext())
        val bioEnabled = AppLockManager.isBiometricEnabled(requireContext())
        val lockLabel = AppLockManager.getAutoLockLabel(requireContext()).lowercase()
        binding.tvSecuritySummary.text = when {
            pinSet && bioEnabled -> "PIN + Biométrie · $lockLabel"
            pinSet -> "PIN activé · $lockLabel"
            else -> "Désactivé"
        }

        // Ephemeral summary
        val ephDuration = EphemeralManager.getDefaultDuration(requireContext())
        binding.tvEphemeralSummary.text = if (ephDuration > 0)
            EphemeralManager.getLabelForDuration(ephDuration) else "Désactivé"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
