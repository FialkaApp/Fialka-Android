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

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.FragmentSettingsSecurityBinding
import com.fialkaapp.fialka.tor.TorCircuit
import com.fialkaapp.fialka.tor.TorManager
import com.fialkaapp.fialka.tor.TorState
import com.fialkaapp.fialka.ui.settings.DurationSelectorBottomSheet
import com.fialkaapp.fialka.util.AppLockManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

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
        setupTor()
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

    private fun setupTor() {
        // Tor is mandatory — no toggle, just show status + reconnect
        binding.btnReconnect.setOnClickListener {
            binding.torProgressIndicator.visibility = View.VISIBLE
            binding.torProgressIndicator.isIndeterminate = true
            TorManager.restart()
        }

        // Observe Tor state in real-time
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TorManager.state.collect { state ->
                    if (_binding == null) return@collect
                    updateTorStatus(state)
                }
            }
        }

        // Observe all circuits
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TorManager.circuits.collect { circuits ->
                    if (_binding == null) return@collect
                    if (circuits.isNotEmpty()) {
                        binding.cardCircuitInfo.visibility = View.VISIBLE
                        buildCircuitViews(circuits)
                    } else {
                        binding.cardCircuitInfo.visibility = View.GONE
                    }
                }
            }
        }

        // Observe .onion address (may arrive before circuit info)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TorManager.onionAddress.collect { address ->
                    if (_binding == null) return@collect
                    if (address != null) {
                        binding.tvOnionAddress.visibility = View.VISIBLE
                        binding.tvOnionAddress.text = address
                    }
                }
            }
        }
    }

    private fun buildCircuitViews(circuits: List<TorCircuit>) {
        val container = binding.circuitsContainer
        container.removeAllViews()
        val dp = resources.displayMetrics.density

        // Title
        val title = TextView(requireContext()).apply {
            text = "${circuits.size} circuits actifs"
            setTextColor(resources.getColor(com.fialkaapp.fialka.R.color.white, null))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, (6 * dp).toInt())
        }
        container.addView(title)

        circuits.forEach { circuit ->
            val chain = circuit.relays.joinToString(" → ") { relay ->
                "${countryToFlag(relay.country)} ${relay.name}"
            }
            val line = TextView(requireContext()).apply {
                text = "${purposeIcon(circuit.purpose)} $chain"
                setTextColor(resources.getColor(com.fialkaapp.fialka.R.color.white, null))
                textSize = 11f
                setPadding(0, (3 * dp).toInt(), 0, (3 * dp).toInt())
            }
            container.addView(line)
        }
    }

    private fun purposeIcon(purpose: String): String = when (purpose) {
        "GENERAL" -> "🌐"
        "CONFLUX_LINKED" -> "⚡"
        "HS_SERVICE_INTRO" -> "🧅"
        "HS_VANGUARDS" -> "🛡️"
        "HS_SERVICE_HSDIR" -> "📒"
        else -> "🔗"
    }

    private fun countryToFlag(countryCode: String): String {
        if (countryCode.length != 2) return "\uD83C\uDF10" // 🌐
        val code = countryCode.uppercase()
        if (!code[0].isLetter() || !code[1].isLetter()) return "\uD83C\uDF10" // 🌐
        val first = 0x1F1E6 - 'A'.code + code[0].code
        val second = 0x1F1E6 - 'A'.code + code[1].code
        return String(intArrayOf(first, second), 0, 2)
    }

    private fun updateTorStatus(state: TorState) {
        when (state) {
            is TorState.IDLE -> {
                binding.tvTorStatus.text = "En attente…"
                binding.torProgressIndicator.visibility = View.GONE
            }
            is TorState.STARTING -> {
                binding.tvTorStatus.text = "Démarrage…"
                binding.torProgressIndicator.visibility = View.VISIBLE
                binding.torProgressIndicator.isIndeterminate = true
            }
            is TorState.BOOTSTRAPPING -> {
                binding.tvTorStatus.text = "Connexion… ${state.percent}%"
                binding.torProgressIndicator.visibility = View.VISIBLE
                binding.torProgressIndicator.isIndeterminate = false
                binding.torProgressIndicator.max = 100
                binding.torProgressIndicator.setProgressCompat(state.percent, true)
            }
            is TorState.CONNECTED -> {
                binding.tvTorStatus.text = "✅ Connecté au réseau Tor"
                binding.torProgressIndicator.visibility = View.GONE
            }
            is TorState.PUBLISHING_ONION -> {
                binding.tvTorStatus.text = "Publication .onion…"
                binding.torProgressIndicator.visibility = View.VISIBLE
                binding.torProgressIndicator.isIndeterminate = true
            }
            is TorState.ONION_PUBLISHED -> {
                binding.tvTorStatus.text = "✅ ${state.address}"
                binding.torProgressIndicator.visibility = View.GONE
            }
            is TorState.ERROR -> {
                binding.tvTorStatus.text = "❌ ${state.message}"
                binding.torProgressIndicator.visibility = View.GONE
            }
            is TorState.DISCONNECTED -> {
                binding.tvTorStatus.text = "Déconnecté"
                binding.torProgressIndicator.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
