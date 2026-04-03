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
package com.fialkaapp.fialka.ui.onboarding

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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.crypto.MnemonicManager
import com.fialkaapp.fialka.databinding.FragmentBackupPhraseBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Displays the 24-word BIP-39 mnemonic phrase for the user to back up.
 * Provides three export options: clipboard (auto-cleared), share sheet, .fialka file in Downloads.
 */
class BackupPhraseFragment : Fragment() {

    private var _binding: FragmentBackupPhraseBinding? = null
    private val binding get() = _binding!!

    private var words: List<String> = emptyList()
    private val clipboardClearHandler = Handler(Looper.getMainLooper())

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackupPhraseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val seed = CryptoManager.getIdentitySeed()
        words = MnemonicManager.seedToMnemonic(seed)
        seed.fill(0)

        binding.rvWords.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvWords.adapter = WordAdapter(words)

        binding.cbConfirm.setOnCheckedChangeListener { _, isChecked ->
            binding.btnContinue.isEnabled = isChecked
        }

        binding.btnContinue.setOnClickListener {
            findNavController().navigate(R.id.action_backup_to_seedVerification)
        }

        binding.btnCopy.setOnClickListener { showCopyWarning() }
        binding.btnShare.setOnClickListener { shareWords() }
        binding.btnExport.setOnClickListener { exportToDownloads() }
    }

    // ── Export helpers ────────────────────────────────────────────────────────

    private fun showCopyWarning() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.backup_copy_warning_title))
            .setMessage(getString(R.string.backup_copy_warning_message))
            .setNegativeButton(getString(R.string.backup_copy_cancel), null)
            .setPositiveButton(getString(R.string.backup_copy_confirm)) { _, _ -> copyToClipboard() }
            .show()
    }

    private fun copyToClipboard() {
        val text = buildSeedText()
        val cm = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Fialka seed phrase", text))

        // Auto-clear after 90 seconds
        clipboardClearHandler.removeCallbacksAndMessages(null)
        clipboardClearHandler.postDelayed({
            if (!isAdded) return@postDelayed
            val empty = ClipData.newPlainText("", "")
            cm.setPrimaryClip(empty)
        }, 90_000L)

        Toast.makeText(
            requireContext(),
            getString(R.string.backup_copy_done),
            Toast.LENGTH_LONG
        ).show()
    }

    private fun shareWords() {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Fialka — phrase de récupération")
            putExtra(Intent.EXTRA_TEXT, buildSeedText())
        }
        startActivity(Intent.createChooser(intent, getString(R.string.backup_share_chooser)))
    }

    private fun exportToDownloads() {
        val fileName = "fialka_seed_backup.fialka"
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
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Toast.makeText(requireContext(), getString(R.string.backup_export_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun buildSeedText(): String = buildString {
        appendLine("Fialka — phrase de récupération (24 mots BIP-39)")
        appendLine("Gardez ces mots en lieu sûr. Ne les partagez jamais.")
        appendLine()
        words.forEachIndexed { i, w -> appendLine("${i + 1}. $w") }
    }

    // ── Word grid adapter ─────────────────────────────────────────────────────

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
            holder.tvWord.text = "${position + 1}. ${words[position]}"
            holder.tvWord.textSize = 15f
        }

        override fun getItemCount() = words.size
    }
}
