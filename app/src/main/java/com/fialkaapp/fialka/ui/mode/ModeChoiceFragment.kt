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
package com.fialkaapp.fialka.ui.mode

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.AppMode
import com.fialkaapp.fialka.AppModeType
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.FragmentModeChoiceBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * First-launch screen: choose NORMAL (chat) or MAILBOX (server) mode.
 * Choice is IRRÉVERSIBLE.
 */
class ModeChoiceFragment : Fragment() {

    private var _binding: FragmentModeChoiceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentModeChoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.cardNormal.setOnClickListener { confirmMode(AppModeType.NORMAL) }
        binding.cardMailbox.setOnClickListener { confirmMode(AppModeType.MAILBOX) }
    }

    private fun confirmMode(mode: AppModeType) {
        val title = if (mode == AppModeType.NORMAL) getString(R.string.mode_normal_title)
                    else getString(R.string.mode_mailbox_title)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(getString(R.string.mode_choice_warning))
            .setPositiveButton("Confirmer") { _, _ ->
                AppMode.setMode(requireContext(), mode)
                when (mode) {
                    AppModeType.NORMAL -> {
                        findNavController().navigate(R.id.action_modeChoice_to_onboarding)
                    }
                    AppModeType.MAILBOX -> {
                        findNavController().navigate(R.id.action_modeChoice_to_mailboxSetup)
                    }
                    else -> {}
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
