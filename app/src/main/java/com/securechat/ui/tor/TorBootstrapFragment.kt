package com.securechat.ui.tor

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.securechat.R
import com.securechat.databinding.FragmentTorBootstrapBinding
import com.securechat.tor.TorManager
import com.securechat.tor.TorState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * First-launch screen: user picks Tor or Normal, clicks Continue.
 * If Tor: shows progress, user clicks Continue again when connected.
 * Skipped on subsequent launches (choice already made).
 */
class TorBootstrapFragment : Fragment() {

    private var _binding: FragmentTorBootstrapBinding? = null
    private val binding get() = _binding!!

    private var pulseAnimator: ObjectAnimator? = null
    private var hasNavigated = false
    private var torSelected = true

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

        // Skip on subsequent launches
        if (TorManager.isTorChoiceMade()) {
            navigateToNext()
            return
        }

        setupChoiceCards()

        binding.btnContinue.setOnClickListener {
            if (binding.progressContainer.visibility == View.VISIBLE) {
                // Phase 2: user clicks after loading
                val state = TorManager.state.value
                if (state is TorState.CONNECTED) {
                    TorManager.setTorEnabled(true)
                    navigateToNext()
                } else if (state is TorState.ERROR || state is TorState.DISCONNECTED) {
                    TorManager.setTorEnabled(false)
                    TorManager.stop()
                    navigateToNext()
                }
            } else {
                // Phase 1: user made a choice
                if (torSelected) {
                    showLoadingPhase()
                    TorManager.start()
                } else {
                    TorManager.setTorEnabled(false)
                    navigateToNext()
                }
            }
        }

        binding.btnRetry.setOnClickListener {
            binding.btnRetry.visibility = View.GONE
            binding.btnContinue.isEnabled = false
            binding.btnContinue.text = "Connexion…"
            binding.tvTitle.text = "Connexion en cours…"
            realPercent = 0
            displayedPercent = 0
            TorManager.restart()
            startSmoothProgress()
        }

        // Observe Tor state (only affects UI in loading phase)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TorManager.state.collect { state ->
                    if (_binding == null) return@collect
                    if (binding.progressContainer.visibility == View.VISIBLE) {
                        handleLoadingState(state)
                    }
                }
            }
        }
    }

    // ── Phase 1: Choice ──

    private fun setupChoiceCards() {
        updateCardSelection()

        binding.cardTor.setOnClickListener {
            torSelected = true
            updateCardSelection()
        }
        binding.cardNormal.setOnClickListener {
            torSelected = false
            updateCardSelection()
        }
    }

    private fun updateCardSelection() {
        val primary = ContextCompat.getColor(requireContext(), R.color.primary)
        val grey = ContextCompat.getColor(requireContext(), R.color.grey_medium)
        val dp2 = (2 * resources.displayMetrics.density).toInt()
        val dp1 = (1 * resources.displayMetrics.density).toInt()

        binding.cardTor.strokeColor = if (torSelected) primary else grey
        binding.cardTor.strokeWidth = if (torSelected) dp2 else dp1
        binding.tvTorRadio.text = if (torSelected) "●" else "○"
        binding.tvTorRadio.setTextColor(if (torSelected) primary else grey)

        binding.cardNormal.strokeColor = if (!torSelected) primary else grey
        binding.cardNormal.strokeWidth = if (!torSelected) dp2 else dp1
        binding.tvNormalRadio.text = if (!torSelected) "●" else "○"
        binding.tvNormalRadio.setTextColor(if (!torSelected) primary else grey)
    }

    // ── Phase 2: Loading ──

    private fun showLoadingPhase() {
        binding.choiceContainer.visibility = View.GONE
        binding.progressContainer.visibility = View.VISIBLE
        binding.tvTitle.text = "Connexion en cours…"
        binding.btnContinue.isEnabled = false
        binding.btnContinue.text = "Connexion…"
        startPulseAnimation()
        startSmoothProgress()
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
                binding.tvStatus.text = "Connecté au réseau Tor"
                binding.btnContinue.isEnabled = true
                binding.btnContinue.text = "Continuer"
                binding.btnRetry.visibility = View.GONE
                stopPulseAnimation()
            }
            is TorState.ERROR -> {
                smoothJob?.cancel()
                binding.progressIndicator.isIndeterminate = false
                binding.tvTitle.text = "Échec de connexion"
                binding.tvStatus.text = state.message
                binding.btnRetry.visibility = View.VISIBLE
                binding.btnContinue.isEnabled = true
                binding.btnContinue.text = "Continuer sans Tor"
                stopPulseAnimation()
            }
            is TorState.DISCONNECTED -> {
                smoothJob?.cancel()
                binding.progressIndicator.isIndeterminate = false
                binding.tvTitle.text = "Déconnecté"
                binding.tvStatus.text = "La connexion a été interrompue"
                binding.btnRetry.visibility = View.VISIBLE
                binding.btnContinue.isEnabled = true
                binding.btnContinue.text = "Continuer sans Tor"
                stopPulseAnimation()
            }
        }
    }

    /**
     * Smooth progress: slowly increments the displayed percent to give visual
     * feedback even when Tor's output is buffered and jumps from 0% to 100%.
     * When real progress comes in, we catch up to it; otherwise we fake-crawl.
     */
    private fun startSmoothProgress() {
        smoothJob?.cancel()
        displayedPercent = 0
        realPercent = 0
        smoothJob = viewLifecycleOwner.lifecycleScope.launch {
            binding.progressIndicator.isIndeterminate = false
            binding.progressIndicator.max = 100
            binding.progressIndicator.setProgressCompat(0, false)

            while (displayedPercent < 100) {
                delay(400)
                if (_binding == null) return@launch

                val target = realPercent
                if (target >= 100) {
                    // Real progress hit 100, handled by CONNECTED state
                    break
                } else if (displayedPercent < target) {
                    // Catch up to real progress (5% steps)
                    displayedPercent = minOf(displayedPercent + 5, target)
                } else if (displayedPercent < 90) {
                    // Slow fake progress (~1% per 400ms)
                    displayedPercent++
                }
                // else: stay at 90+ until real progress catches up

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
        if (isAdded) {
            findNavController().navigate(R.id.action_torBootstrap_to_onboarding)
        }
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
        pulseAnimator?.cancel()
        pulseAnimator = null
        _binding = null
        super.onDestroyView()
    }
}
