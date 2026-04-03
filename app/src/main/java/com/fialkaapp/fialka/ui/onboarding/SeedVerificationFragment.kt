/*
 * Fialka â€” Post-quantum encrypted messenger
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
package com.fialkaapp.fialka.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.crypto.MnemonicManager
import com.fialkaapp.fialka.databinding.FragmentSeedVerificationBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Verifies that the user actually wrote down the 24-word recovery phrase.
 */
class SeedVerificationFragment : Fragment() {

    private var _binding: FragmentSeedVerificationBinding? = null
    private val binding get() = _binding!!

    private lateinit var words: List<String>
    private lateinit var promptIndexes: List<Int>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val seed = CryptoManager.getIdentitySeed()
        words = MnemonicManager.seedToMnemonic(seed)
        seed.fill(0)
        // Always use the fixed indexes stored at identity creation — never reshuffled
        promptIndexes = CryptoManager.getSeedPromptIndexes()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSeedVerificationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindPrompts()
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnReviewSeed.setOnClickListener {
            findNavController().navigateUp()
        }
        binding.btnContinue.setOnClickListener {
            validateAndContinue()
        }
        binding.btnSkip.setOnClickListener {
            showSkipWarning()
        }

        binding.etWordOne.doAfterTextChanged { clearError() }
        binding.etWordTwo.doAfterTextChanged { clearError() }
        binding.etWordThree.doAfterTextChanged { clearError() }
    }

    private fun bindPrompts() {
        binding.tvPromptOne.text = getString(R.string.seed_verify_word_label, promptIndexes[0] + 1)
        binding.tvPromptTwo.text = getString(R.string.seed_verify_word_label, promptIndexes[1] + 1)
        binding.tvPromptThree.text = getString(R.string.seed_verify_word_label, promptIndexes[2] + 1)
    }

    private fun validateAndContinue() {
        val answers = listOf(
            binding.etWordOne.text?.toString()?.trim()?.lowercase().orEmpty(),
            binding.etWordTwo.text?.toString()?.trim()?.lowercase().orEmpty(),
            binding.etWordThree.text?.toString()?.trim()?.lowercase().orEmpty()
        )
        val expected = promptIndexes.map { words[it].lowercase() }

        if (answers.any { it.isBlank() }) {
            showError(getString(R.string.seed_verify_error_empty))
            return
        }

        if (answers != expected) {
            showError(getString(R.string.seed_verify_error_invalid))
            return
        }

        CryptoManager.markSeedVerified()
        findNavController().navigate(R.id.action_seedVerification_to_torBootstrap)
    }

    private fun showSkipWarning() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.seed_skip_warning_title))
            .setMessage(getString(R.string.seed_skip_warning_message))
            .setNegativeButton(getString(R.string.seed_skip_cancel), null)
            .setPositiveButton(getString(R.string.seed_skip_confirm)) { _, _ ->
                CryptoManager.markSeedVerified()
                findNavController().navigate(R.id.action_seedVerification_to_torBootstrap)
            }
            .show()
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun clearError() {
        binding.tvError.visibility = View.GONE
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Prompt indexes are persisted in CryptoManager — no need to save here
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        // KEY_PROMPTS removed — indexes are now stored in CryptoManager permanently
    }
}
