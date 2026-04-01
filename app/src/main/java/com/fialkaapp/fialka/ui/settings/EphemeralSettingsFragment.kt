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
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.FragmentSettingsEphemeralBinding
import com.fialkaapp.fialka.ui.settings.DurationSelectorBottomSheet
import com.fialkaapp.fialka.util.EphemeralManager

/**
 * Ephemeral messages settings - uses Material 3 bottom sheet for duration selection.
 */
class EphemeralSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsEphemeralBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsEphemeralBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Show current duration
        updateCurrentDuration()

        // Open duration selector bottom sheet
        binding.cardDurationSelector.setOnClickListener {
            val currentDuration = EphemeralManager.getDefaultDuration(requireContext())
            DurationSelectorBottomSheet.show(
                childFragmentManager,
                DurationSelectorBottomSheet.Mode.EPHEMERAL_MESSAGES,
                currentDuration
            ) { selectedDuration ->
                EphemeralManager.setDefaultDuration(requireContext(), selectedDuration)
                updateCurrentDuration()
            }
        }
    }

    private fun updateCurrentDuration() {
        val duration = EphemeralManager.getDefaultDuration(requireContext())
        val label = if (duration > 0) {
            EphemeralManager.getLabelForDuration(duration)
        } else {
            getString(R.string.setting_dummy_traffic_summary) // "Messages éphémères, trafic factice"
        }
        binding.tvCurrentDuration.text = label
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
