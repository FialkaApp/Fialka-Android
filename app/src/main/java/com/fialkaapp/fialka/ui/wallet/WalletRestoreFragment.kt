/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.ui.wallet

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.crypto.MoneroMnemonic
import com.fialkaapp.fialka.databinding.FragmentWalletRestoreBinding

/**
 * Wallet restore screen: 25-word Monero mnemonic entry.
 *
 * Layout: 2-column grid of 25 AutoCompleteTextViews with live prefix-based
 * autocomplete from the Monero English wordlist.
 *
 * Also requests an optional restore height (block number) to avoid scanning
 * the entire Monero blockchain from genesis — critical for UX on mobile.
 */
class WalletRestoreFragment : Fragment() {

    private var _binding: FragmentWalletRestoreBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WalletOnboardingViewModel by activityViewModels()
    private val wordInputs = mutableListOf<AutoCompleteTextView>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalletRestoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        MoneroMnemonic.init(requireContext())
        buildWordGrid()
        observeViewModel()

        binding.btnRestore.setOnClickListener { attemptRestore() }
    }

    // ── Word grid ─────────────────────────────────────────────────────────────

    private fun buildWordGrid() {
        val ctx = requireContext()
        val grid = binding.gridWords
        grid.removeAllViews()
        wordInputs.clear()

        // Build autocomplete wordlist from MoneroMnemonic internal list by loading it
        val allWords: List<String> = try {
            ctx.resources.openRawResource(
                ctx.resources.getIdentifier("monero_wordlist", "raw", ctx.packageName)
            ).bufferedReader().readLines().filter { it.isNotBlank() }
        } catch (_: Exception) { emptyList() }

        for (i in 1..25) {
            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = android.widget.GridLayout.LayoutParams().apply {
                    width = 0
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    columnSpec = android.widget.GridLayout.spec(
                        android.widget.GridLayout.UNDEFINED, android.widget.GridLayout.FILL, 1f
                    )
                    setMargins(4, 4, 4, 4)
                }
            }

            val label = TextView(ctx).apply {
                text = "$i."
                textSize = 10f
                setTextColor(ContextCompat.getColor(ctx, R.color.text_secondary))
                gravity = Gravity.CENTER
            }

            val input = AutoCompleteTextView(ctx).apply {
                hint = "mot $i"
                textSize = 13f
                setSingleLine()
                imeOptions = if (i == 25) EditorInfo.IME_ACTION_DONE else EditorInfo.IME_ACTION_NEXT
                threshold = 1
                setAdapter(
                    ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, allWords)
                )
                setPadding(8, 8, 8, 8)
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(ctx, R.color.text_secondary)
                )
            }

            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    validateWord(input, s?.toString() ?: "")
                    updateRestoreButton()
                }
            })

            cell.addView(label)
            cell.addView(input)
            grid.addView(cell)
            wordInputs.add(input)
        }
    }

    private fun validateWord(input: AutoCompleteTextView, word: String) {
        val ctx = requireContext()
        val trimmed = word.trim().lowercase()
        if (trimmed.isEmpty()) {
            input.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(ctx, R.color.text_secondary)
            )
            return
        }
        val valid = MoneroMnemonic.isValidMnemonic(
            // Just test the prefix
            listOf(trimmed) + List(24) { "abbey" }  // dummy check — real check on submit
        ).let { false } .let {
            // Lightweight prefix check: accept if any word starts with the 3-char prefix
            trimmed.length < 3 || try {
                val raw = ctx.resources.openRawResource(
                    ctx.resources.getIdentifier("monero_wordlist", "raw", ctx.packageName)
                ).bufferedReader().readLines().any { it.startsWith(trimmed.take(3)) }
                raw
            } catch (_: Exception) { true }
        }
        input.backgroundTintList = ColorStateList.valueOf(
            ContextCompat.getColor(ctx, if (valid) R.color.primary else R.color.error)
        )
    }

    private fun updateRestoreButton() {
        val allFilled = wordInputs.all { it.text.toString().trim().isNotEmpty() }
        binding.btnRestore.isEnabled = allFilled
    }

    // ── Restore logic ─────────────────────────────────────────────────────────

    private fun attemptRestore() {
        val words = wordInputs.map { it.text.toString().trim().lowercase() }
        if (words.size != 25 || words.any { it.isEmpty() }) {
            showError(getString(R.string.wallet_restore_error_count))
            return
        }

        val heightText = binding.etRestoreHeight.text.toString().trim()
        val restoreHeight = if (heightText.isNotEmpty()) heightText.toLongOrNull() ?: 0L else 0L

        binding.tvError.isVisible = false
        viewModel.importWalletFromMnemonic(words.joinToString(" "), restoreHeight)
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.isVisible = true
    }

    // ── ViewModel observer ────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is WalletOnboardingViewModel.WalletOnboardingState.Loading -> {
                    binding.progressBar.isVisible = true
                    binding.scrollContent.isVisible = false
                }
                is WalletOnboardingViewModel.WalletOnboardingState.WalletActivated -> {
                    findNavController().navigate(R.id.action_walletRestore_to_walletHome)
                }
                is WalletOnboardingViewModel.WalletOnboardingState.Error -> {
                    binding.progressBar.isVisible = false
                    binding.scrollContent.isVisible = true
                    showError(state.message)
                }
                else -> {
                    binding.progressBar.isVisible = false
                    binding.scrollContent.isVisible = true
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
