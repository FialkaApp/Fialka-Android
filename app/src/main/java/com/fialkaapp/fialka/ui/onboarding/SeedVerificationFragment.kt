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
import kotlin.random.Random

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

        val restored = savedInstanceState?.getIntArray(KEY_PROMPTS)?.toList()
        promptIndexes = restored ?: generatePromptIndexes()
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

        findNavController().navigate(R.id.action_seedVerification_to_torBootstrap)
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun clearError() {
        binding.tvError.visibility = View.GONE
    }

    private fun generatePromptIndexes(): List<Int> {
        return (0 until 24).shuffled(Random(System.currentTimeMillis())).take(3).sorted()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(KEY_PROMPTS, promptIndexes.toIntArray())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val KEY_PROMPTS = "seed_prompt_indexes"
    }
}
