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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.FragmentSettingsSecurityBinding
import com.fialkaapp.fialka.ui.settings.DurationSelectorBottomSheet
import com.fialkaapp.fialka.util.AppLockManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SecurityFragment : Fragment() {

    private var _binding: FragmentSettingsSecurityBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsSecurityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        setupPin()
        setupBiometric()
        setupAutoLock()
    }

    private fun setupAutoLock() {
        binding.layoutAutoLock.setOnClickListener {
            val currentDelay = AppLockManager.getAutoLockDelay(requireContext())
            DurationSelectorBottomSheet.show(
                childFragmentManager,
                DurationSelectorBottomSheet.Mode.AUTO_LOCK,
                currentDelay
            ) { selectedDelay ->
                AppLockManager.setAutoLockDelay(requireContext(), selectedDelay)
                updateAutoLockLabel()
            }
        }
    }

    private fun updateAutoLockLabel() {
        val label = AppLockManager.getAutoLockLabel(requireContext())
        binding.tvAutoLockSummary.text = "Après ${label.lowercase()}"
    }

    private fun setupPin() {
        val pinSet = AppLockManager.isPinSet(requireContext())
        binding.switchPin.isChecked = pinSet
        updatePinUI(pinSet)

        binding.switchPin.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                showPinSetup(changing = false)
            } else {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.setting_security_disable_pin_title)
                    .setMessage(R.string.setting_security_disable_pin_message)
                    .setPositiveButton(R.string.action_remove) { _, _ ->
                        AppLockManager.removePin(requireContext())
                        updatePinUI(false)
                        Toast.makeText(requireContext(), "Code supprimé", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(R.string.action_cancel, null)
                    .setCancelable(false)
                    .show()
            }
        }

        binding.layoutChangePin.setOnClickListener {
            showPinSetup(changing = true)
        }
    }

    private fun setupBiometric() {
        binding.switchBiometric.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                val bm = BiometricManager.from(requireContext())
                val canAuth = bm.canAuthenticate(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                    AppLockManager.setBiometricEnabled(requireContext(), true)
                    updateBiometricStatus(true)
                } else {
                    binding.switchBiometric.isChecked = false
                    val msg = when (canAuth) {
                        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "Cet appareil n'a pas de capteur biométrique"
                        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Le capteur biométrique n'est pas disponible"
                        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Aucune empreinte/visage enregistré dans les paramètres du téléphone"
                        else -> "Biométrie non disponible"
                    }
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
                }
            } else {
                AppLockManager.setBiometricEnabled(requireContext(), false)
                updateBiometricStatus(false)
            }
        }
    }

    private fun showPinSetup(changing: Boolean) {
        val dialog = PinSetupDialogFragment.newInstance(changing)
        dialog.onPinSet = { updatePinUI(true) }
        dialog.show(childFragmentManager, "pin_setup")
    }

    private fun updatePinUI(pinSet: Boolean) {
        binding.switchPin.isChecked = pinSet
        binding.layoutPinOptions.visibility = if (pinSet) View.VISIBLE else View.GONE
        binding.tvPinStatus.text = if (pinSet) {
            "✅ Code actif — demandé à chaque ouverture"
        } else {
            "Protégez l'accès à l'application"
        }

        if (pinSet) {
            updateAutoLockLabel()
            val bm = BiometricManager.from(requireContext())
            val canAuth = bm.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS || canAuth == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
                binding.cardBiometric.visibility = View.VISIBLE
                val bioEnabled = AppLockManager.isBiometricEnabled(requireContext())
                binding.switchBiometric.isChecked = bioEnabled
                updateBiometricStatus(bioEnabled)
            } else {
                binding.cardBiometric.visibility = View.GONE
            }
        } else {
            binding.cardBiometric.visibility = View.GONE
        }
    }

    private fun updateBiometricStatus(enabled: Boolean) {
        binding.tvBiometricStatus.text = if (enabled) {
            "✅ Activé — utilisez votre empreinte ou visage"
        } else {
            "Empreinte digitale, reconnaissance faciale…"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
