package com.securechat.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.securechat.R
import com.securechat.databinding.FragmentSettingsPrivacyBinding
import com.securechat.util.EphemeralManager

class PrivacyFragment : Fragment() {

    private var _binding: FragmentSettingsPrivacyBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsPrivacyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        setupEphemeral()
    }

    override fun onResume() {
        super.onResume()
        updateEphemeralSummary()
    }

    private fun setupEphemeral() {
        binding.rowEphemeral.setOnClickListener {
            findNavController().navigate(R.id.action_privacy_to_ephemeral)
        }
        updateEphemeralSummary()
    }

    private fun updateEphemeralSummary() {
        val duration = EphemeralManager.getDefaultDuration(requireContext())
        binding.tvEphemeralSummary.text = if (duration > 0)
            EphemeralManager.getLabelForDuration(duration) else "Désactivé"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
