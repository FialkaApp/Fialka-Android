/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.FragmentWalletBackupPhraseBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Displays the 25 Monero mnemonic words for the user to back up.
 *
 * The seed is already stored by the ViewModel before this screen is shown.
 * The user must:
 *  1. View the 25 words
 *  2. Optionally copy/share them
 *  3. Check the confirmation checkbox
 *  4. Tap "Continuer" → wallet becomes active, navigate to wallet home
 *
 * Security: clipboard is auto-cleared after 60 seconds.
 */
class WalletBackupPhraseFragment : Fragment() {

    private var _binding: FragmentWalletBackupPhraseBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WalletOnboardingViewModel by activityViewModels()
    private val clipClearHandler = Handler(Looper.getMainLooper())
    private var words: List<String> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalletBackupPhraseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeViewModel()

        // Trigger seed generation if not already done
        if (viewModel.state.value is WalletOnboardingViewModel.WalletOnboardingState.Idle ||
            viewModel.state.value is WalletOnboardingViewModel.WalletOnboardingState.Error
        ) {
            viewModel.generateNewWallet()
        }

        binding.cbConfirm.setOnCheckedChangeListener { _, checked ->
            binding.btnContinue.isEnabled = checked
        }

        binding.btnContinue.setOnClickListener {
            showConfirmDialog()
        }

        binding.btnCopy.setOnClickListener { copyToClipboard() }
        binding.btnShare.setOnClickListener { shareWords() }
    }

    private fun observeViewModel() {
        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is WalletOnboardingViewModel.WalletOnboardingState.Loading -> {
                    binding.progressBar.isVisible = true
                    binding.scrollContent.isVisible = false
                }
                is WalletOnboardingViewModel.WalletOnboardingState.MnemonicReady -> {
                    binding.progressBar.isVisible = false
                    binding.scrollContent.isVisible = true
                    words = state.words
                    binding.rvWords.layoutManager = GridLayoutManager(requireContext(), 3)
                    binding.rvWords.adapter = WordAdapter(words)
                }
                is WalletOnboardingViewModel.WalletOnboardingState.WalletActivated -> {
                    findNavController().navigate(R.id.action_walletBackupPhrase_to_walletHome)
                }
                is WalletOnboardingViewModel.WalletOnboardingState.Error -> {
                    binding.progressBar.isVisible = false
                    binding.scrollContent.isVisible = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
                else -> Unit
            }
        }
    }

    private fun showConfirmDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.wallet_backup_dialog_title))
            .setMessage(getString(R.string.wallet_backup_dialog_message))
            .setPositiveButton(getString(R.string.yes_ive_saved_it)) { _, _ ->
                viewModel.confirmBackupAndActivate()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun copyToClipboard() {
        if (words.isEmpty()) return
        val text = words.joinToString(" ")
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("XMR mnemonic", text))
        Toast.makeText(requireContext(), R.string.wallet_backup_copied, Toast.LENGTH_SHORT).show()
        // Auto-clear clipboard after 60 s
        clipClearHandler.removeCallbacksAndMessages(null)
        clipClearHandler.postDelayed({
            cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }, 60_000L)
    }

    private fun shareWords() {
        if (words.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, words.joinToString(" "))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.backup_btn_share)))
    }

    override fun onDestroyView() {
        clipClearHandler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _binding = null
    }

    // ── Word display adapter ──────────────────────────────────────────────────

    private class WordAdapter(private val words: List<String>) :
        RecyclerView.Adapter<WordAdapter.VH>() {

        inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvIndex: TextView = itemView.findViewById(R.id.tvWordIndex)
            val tvWord: TextView = itemView.findViewById(R.id.tvWord)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_mnemonic_word, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.tvIndex.text = "${position + 1}."
            holder.tvWord.text = words[position]
        }

        override fun getItemCount() = words.size
    }
}
