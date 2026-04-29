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
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.databinding.FragmentTorSettingsBinding
import com.fialkaapp.fialka.tor.TorCircuit
import com.fialkaapp.fialka.tor.TorManager
import com.fialkaapp.fialka.tor.TorState
import kotlinx.coroutines.launch

class TorSettingsFragment : Fragment() {

    private var _binding: FragmentTorSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTorSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        setupTor()
    }

    private fun setupTor() {
        binding.btnReconnect.setOnClickListener {
            binding.torProgressIndicator.visibility = View.VISIBLE
            binding.torProgressIndicator.isIndeterminate = true
            TorManager.restart()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TorManager.state.collect { state ->
                    if (_binding == null) return@collect
                    updateTorStatus(state)
                }
            }
        }

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

    private fun buildCircuitViews(circuits: List<TorCircuit>) {
        val container = binding.circuitsContainer
        container.removeAllViews()
        val dp = resources.displayMetrics.density

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
        if (countryCode.length != 2) return "\uD83C\uDF10"
        val code = countryCode.uppercase()
        if (!code[0].isLetter() || !code[1].isLetter()) return "\uD83C\uDF10"
        val first = 0x1F1E6 - 'A'.code + code[0].code
        val second = 0x1F1E6 - 'A'.code + code[1].code
        return String(intArrayOf(first, second), 0, 2)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
