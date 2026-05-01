/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667
 */
package com.fialkaapp.fialka.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.crypto.WalletSeedManager
import com.fialkaapp.fialka.databinding.FragmentWalletSeedBackupBinding
import com.fialkaapp.fialka.wallet.WalletRepository

/**
 * Displays the 25-word Monero mnemonic for the user to back up — same UX as BackupPhraseFragment.
 * The mnemonic is passed as a nav argument (generated just before navigation).
 */
class WalletSeedBackupFragment : Fragment() {

    private var _binding: FragmentWalletSeedBackupBinding? = null
    private val binding get() = _binding!!

    private var words: List<String> = emptyList()
    private val clipboardClearHandler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalletSeedBackupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Read the mnemonic that was stored right before navigation.
        val mnemonic = WalletSeedManager.getMnemonic(requireContext())
        if (mnemonic.isNullOrBlank()) {
            Toast.makeText(requireContext(), R.string.wallet_seed_missing, Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
            return
        }

        words = mnemonic.split(Regex("\\s+")).filter { it.isNotBlank() }

        // Network badge
        val isStagenet = com.fialkaapp.fialka.wallet.WalletPreferences.isStagenet(requireContext())
        binding.tvSeedNetworkBadge.visibility = View.VISIBLE
        if (isStagenet) {
            binding.tvSeedNetworkBadge.text = getString(R.string.wallet_network_stagenet_badge)
            binding.tvSeedNetworkBadge.setBackgroundColor(android.graphics.Color.parseColor("#CC0000"))
        } else {
            binding.tvSeedNetworkBadge.text = getString(R.string.wallet_network_mainnet_label)
            binding.tvSeedNetworkBadge.setBackgroundColor(android.graphics.Color.parseColor("#1B5E20"))
        }

        binding.rvWords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvWords.adapter = WordAdapter(words)

        binding.cbConfirm.setOnCheckedChangeListener { _, isChecked ->
            binding.btnContinue.isEnabled = isChecked
        }

        binding.btnContinue.setOnClickListener {
            // User confirmed they saved the seed — activate the wallet and go back to home.
            com.fialkaapp.fialka.wallet.WalletPreferences.setWalletEnabled(requireContext(), true)
            com.fialkaapp.fialka.wallet.WalletPreferences.setWalletCreated(requireContext(), true)
            WalletRepository.invalidateSeedCache()
            findNavController().navigateUp()
        }

        binding.btnCopy.setOnClickListener { copyToClipboard() }
        binding.btnShare.setOnClickListener { shareWords() }
        binding.btnExport.setOnClickListener { exportToDownloads() }
    }

    private fun copyToClipboard() {
        val text = buildSeedText()
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Fialka wallet seed", text))
        clipboardClearHandler.removeCallbacksAndMessages(null)
        clipboardClearHandler.postDelayed({
            if (!isAdded) return@postDelayed
            cm.setPrimaryClip(ClipData.newPlainText("", ""))
        }, 90_000L)
        Toast.makeText(requireContext(), getString(R.string.backup_copy_done), Toast.LENGTH_LONG).show()
    }

    private fun shareWords() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Fialka — seed wallet Monero")
            putExtra(Intent.EXTRA_TEXT, buildSeedText())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.backup_share_chooser)))
    }

    private fun exportToDownloads() {
        val fileName = "fialka_wallet_seed.fialka"
        val content = buildSeedText().toByteArray(Charsets.UTF_8)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/x-fialka-backup")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = requireContext().contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Toast.makeText(requireContext(), getString(R.string.backup_export_failed), Toast.LENGTH_SHORT).show()
            return
        }
        try {
            resolver.openOutputStream(uri)?.use { it.write(content) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            Toast.makeText(requireContext(), getString(R.string.backup_export_done), Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            resolver.delete(uri, null, null)
            Toast.makeText(requireContext(), getString(R.string.backup_export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildSeedText(): String = buildString {
        appendLine("Fialka — seed wallet Monero (25 mots)")
        appendLine("Gardez ces mots en lieu sûr. Ne les partagez jamais.")
        appendLine()
        words.forEachIndexed { i, w -> appendLine("${i + 1}. $w") }
    }

    override fun onDestroyView() {
        clipboardClearHandler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _binding = null
    }

    private class WordAdapter(private val words: List<String>) :
        RecyclerView.Adapter<WordAdapter.WordViewHolder>() {

        class WordViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvWord: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WordViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return WordViewHolder(view)
        }

        override fun onBindViewHolder(holder: WordViewHolder, position: Int) {
            holder.tvWord.text = "${position + 1}.  ${words[position]}"
            holder.tvWord.textSize = 17f
            holder.tvWord.setPadding(16, 8, 16, 8)
        }

        override fun getItemCount() = words.size
    }
}
