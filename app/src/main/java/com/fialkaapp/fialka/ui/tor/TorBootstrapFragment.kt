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
package com.fialkaapp.fialka.ui.tor

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.databinding.FragmentTorBootstrapBinding
import com.fialkaapp.fialka.tor.TorCircuit
import com.fialkaapp.fialka.tor.TorManager
import com.fialkaapp.fialka.tor.TorState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Tor bootstrap screen — shown on EVERY app launch.
 * Displays real-time connection progress, circuit info (relays + countries),
 * and .onion address once published.
 */
class TorBootstrapFragment : Fragment() {

    private var _binding: FragmentTorBootstrapBinding? = null
    private val binding get() = _binding!!

    private var pulseAnimator: ObjectAnimator? = null
    private var hasNavigated = false

    // Smooth progress
    private var realPercent = 0
    private var displayedPercent = 0
    private var smoothJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTorBootstrapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // New user? Skip straight to onboarding — Tor connects in background
        if (!CryptoManager.hasIdentity()) {
            if (!hasNavigated) {
                hasNavigated = true
                findNavController().navigate(R.id.action_torBootstrap_to_onboarding)
            }
            return
        }

        // Returning user — show bootstrap progress
        binding.tvTitle.text = "Connexion au réseau Tor"
        binding.btnContinue.isEnabled = false
        binding.btnContinue.text = "Connexion…"
        binding.cardCircuitInfo.visibility = View.GONE
        startPulseAnimation()
        startSmoothProgress()

        // Tor is already started by FialkaApplication — just observe

        binding.btnContinue.setOnClickListener {
            val state = TorManager.state.value
            if (state is TorState.CONNECTED || state is TorState.ONION_PUBLISHED) {
                navigateToNext()
            }
        }

        binding.btnRetry.setOnClickListener {
            binding.btnRetry.visibility = View.GONE
            binding.btnContinue.isEnabled = false
            binding.btnContinue.text = "Connexion…"
            binding.tvTitle.text = "Connexion au réseau Tor"
            binding.cardCircuitInfo.visibility = View.GONE
            continueTimeoutJob?.cancel()
            realPercent = 0
            displayedPercent = 0
            TorManager.restart()
            startPulseAnimation()
            startSmoothProgress()
        }

        // Observe Tor state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TorManager.state.collect { state ->
                    if (_binding == null) return@collect
                    handleLoadingState(state)
                }
            }
        }

        // Observe all circuits
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TorManager.circuits.collect { circuits ->
                    if (_binding == null || circuits.isEmpty()) return@collect
                    binding.cardCircuitInfo.visibility = View.VISIBLE
                    binding.tvCircuitCount.text = "${circuits.size} circuits Tor actifs"
                    buildCircuitViews(circuits)
                    binding.btnContinue.isEnabled = true
                    binding.btnContinue.text = "Continuer"
                }
            }
        }

        // Observe .onion address (published before circuit info is ready)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TorManager.onionAddress.collect { address ->
                    if (_binding == null || address == null) return@collect
                    binding.cardCircuitInfo.visibility = View.VISIBLE
                    binding.tvOnionAddress.text = address
                    binding.tvStatus.text = "Service .onion publié"
                    binding.btnContinue.isEnabled = true
                    binding.btnContinue.text = "Continuer"
                }
            }
        }
    }

    private fun handleLoadingState(state: TorState) {
        when (state) {
            is TorState.IDLE, is TorState.STARTING -> {
                binding.progressIndicator.isIndeterminate = false
                binding.btnRetry.visibility = View.GONE
            }
            is TorState.BOOTSTRAPPING -> {
                realPercent = state.percent
                binding.progressIndicator.isIndeterminate = false
                binding.btnRetry.visibility = View.GONE
            }
            is TorState.CONNECTED -> {
                realPercent = 100
                smoothJob?.cancel()
                displayedPercent = 100
                binding.progressIndicator.isIndeterminate = false
                binding.progressIndicator.setProgressCompat(100, true)
                binding.tvTitle.text = "Connecté ✓"
                binding.btnRetry.visibility = View.GONE
                stopPulseAnimation()
                if (!CryptoManager.hasIdentity()) {
                    // New user: no seed yet, can't publish .onion — let them proceed to onboarding
                    binding.tvStatus.text = "Tor connecté — créez votre identité"
                    binding.btnContinue.isEnabled = true
                    binding.btnContinue.text = "Continuer"
                } else {
                    binding.tvStatus.text = "Récupération du circuit…"
                    // Wait for circuit info or timeout
                    startContinueTimeout()
                }
            }
            is TorState.PUBLISHING_ONION -> {
                realPercent = 100
                binding.progressIndicator.isIndeterminate = true
                binding.tvTitle.text = "Publication .onion…"
                binding.tvStatus.text = "Service caché en cours de publication"
                binding.btnRetry.visibility = View.GONE
            }
            is TorState.ONION_PUBLISHED -> {
                realPercent = 100
                smoothJob?.cancel()
                displayedPercent = 100
                binding.progressIndicator.isIndeterminate = false
                binding.progressIndicator.setProgressCompat(100, true)
                binding.tvTitle.text = "Connecté ✓"
                binding.tvStatus.text = "Service .onion publié"
                binding.btnContinue.isEnabled = true
                binding.btnContinue.text = "Continuer"
                binding.btnRetry.visibility = View.GONE
                stopPulseAnimation()
                startContinueTimeout()
            }
            is TorState.ERROR -> {
                smoothJob?.cancel()
                binding.progressIndicator.isIndeterminate = false
                binding.tvTitle.text = "Échec de connexion"
                binding.tvStatus.text = state.message
                binding.btnRetry.visibility = View.VISIBLE
                binding.btnContinue.isEnabled = false
                binding.btnContinue.text = "Connexion…"
                stopPulseAnimation()
            }
            is TorState.DISCONNECTED -> {
                smoothJob?.cancel()
                binding.progressIndicator.isIndeterminate = false
                binding.tvTitle.text = "Déconnecté"
                binding.tvStatus.text = "La connexion a été interrompue"
                binding.btnRetry.visibility = View.VISIBLE
                binding.btnContinue.isEnabled = false
                binding.btnContinue.text = "Connexion…"
                stopPulseAnimation()
            }
        }
    }

    private var continueTimeoutJob: Job? = null

    private fun countryToFlag(countryCode: String): String {
        if (countryCode.length != 2) return "\uD83C\uDF10" // 🌐
        val code = countryCode.uppercase()
        if (!code[0].isLetter() || !code[1].isLetter()) return "\uD83C\uDF10" // 🌐
        val first = 0x1F1E6 - 'A'.code + code[0].code
        val second = 0x1F1E6 - 'A'.code + code[1].code
        return String(intArrayOf(first, second), 0, 2)
    }

    private fun buildCircuitViews(circuits: List<TorCircuit>) {
        val container = binding.circuitsContainer
        container.removeAllViews()
        val dp = resources.displayMetrics.density

        circuits.forEach { circuit ->
            val chain = circuit.relays.joinToString(" → ") { relay ->
                "${countryToFlag(relay.country)} ${relay.name}"
            }
            val line = TextView(requireContext()).apply {
                text = "${purposeIcon(circuit.purpose)} $chain"
                setTextColor(resources.getColor(R.color.white, null))
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

    /**
     * Fallback: if circuit info hasn't arrived after 12s, enable continue anyway.
     */
    private fun startContinueTimeout() {
        if (continueTimeoutJob?.isActive == true) return
        continueTimeoutJob = viewLifecycleOwner.lifecycleScope.launch {
            delay(12_000)
            if (_binding != null && !binding.btnContinue.isEnabled) {
                binding.btnContinue.isEnabled = true
                binding.btnContinue.text = "Continuer"
                binding.tvStatus.text = "Connecté au réseau Tor"
            }
        }
    }

    private fun startSmoothProgress() {
        smoothJob?.cancel()
        displayedPercent = 0
        realPercent = 0
        smoothJob = viewLifecycleOwner.lifecycleScope.launch {
            binding.progressIndicator.isIndeterminate = false
            binding.progressIndicator.max = 100
            binding.progressIndicator.setProgressCompat(0, false)

            while (displayedPercent < 100) {
                delay(if (displayedPercent < 80) 350L else 600L)
                if (_binding == null) return@launch

                val target = realPercent
                if (target >= 100) break
                else if (displayedPercent < target) {
                    displayedPercent = minOf(displayedPercent + 5, target)
                } else if (displayedPercent < 98) {
                    displayedPercent++
                }

                binding.progressIndicator.setProgressCompat(displayedPercent, true)
                binding.tvStatus.text = "$displayedPercent% — ${getStatusText(displayedPercent)}"
            }
        }
    }

    private fun getStatusText(percent: Int): String = when {
        percent < 25 -> "Connexion aux relais…"
        percent < 50 -> "Chargement des descripteurs…"
        percent < 80 -> "Établissement des circuits…"
        else -> "Finalisation…"
    }

    private fun navigateToNext() {
        if (hasNavigated) return
        hasNavigated = true
        if (!isAdded) return
        val action = if (CryptoManager.hasIdentity()) {
            R.id.action_torBootstrap_to_conversations
        } else {
            R.id.action_torBootstrap_to_onboarding
        }
        findNavController().navigate(action)
    }

    private fun startPulseAnimation() {
        val scaleX = PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.1f, 1f)
        val scaleY = PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.1f, 1f)
        val alpha = PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.7f, 1f)

        pulseAnimator = ObjectAnimator.ofPropertyValuesHolder(
            binding.tvOnionIcon, scaleX, scaleY, alpha
        ).apply {
            duration = 1500
            repeatCount = ObjectAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        binding.tvOnionIcon.scaleX = 1f
        binding.tvOnionIcon.scaleY = 1f
        binding.tvOnionIcon.alpha = 1f
    }

    override fun onDestroyView() {
        smoothJob?.cancel()
        continueTimeoutJob?.cancel()
        pulseAnimator?.cancel()
        pulseAnimator = null
        _binding = null
        super.onDestroyView()
    }
}
