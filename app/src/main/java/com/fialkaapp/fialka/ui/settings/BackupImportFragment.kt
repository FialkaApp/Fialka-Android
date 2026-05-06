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

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.backup.FialkaBackupManager
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.crypto.WalletSeedManager
import com.fialkaapp.fialka.data.local.FialkaDatabase
import com.fialkaapp.fialka.data.repository.ChatRepository
import com.fialkaapp.fialka.databinding.FragmentBackupImportBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Import screen — lets the user pick an encrypted .fialka backup file,
 * enter the passphrase, preview the contents, and restore everything.
 */
class BackupImportFragment : Fragment() {

    private var _binding: FragmentBackupImportBinding? = null
    private val binding get() = _binding!!

    /** Raw bytes of the chosen file (held only until restore completes). */
    private var loadedFileBytes: ByteArray? = null
    /** Decrypted content ready for restore. */
    private var pendingContent: FialkaBackupManager.BackupContent? = null

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        loadFile(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackupImportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnChooseFile.setOnClickListener {
            openFileLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        }

        binding.btnDecrypt.setOnClickListener { onDecryptClicked() }
        binding.btnRestore.setOnClickListener { onRestoreClicked() }
    }

    // ─── Step 1: load file bytes ─────────────────────────────────────────────────

    private fun loadFile(uri: Uri) {
        hideError()
        pendingContent = null
        binding.cardPreview.visibility = View.GONE
        binding.btnRestore.visibility  = View.GONE

        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Impossible de lire le fichier.")
                }
                loadedFileBytes = bytes

                // Display file name
                val name = resolveFileName(uri)
                binding.tvFileName.text = name
                binding.tvFileName.visibility = View.VISIBLE

                // Show passphrase card
                binding.cardPassphrase.visibility = View.VISIBLE
                binding.etPassphrase.requestFocus()

            } catch (e: Exception) {
                showError("Impossible de lire le fichier : ${e.message}")
            }
        }
    }

    private fun resolveFileName(uri: Uri): String {
        var name = uri.lastPathSegment ?: "fichier inconnu"
        try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && idx >= 0) name = cursor.getString(idx)
            }
        } catch (_: Exception) {}
        return name
    }

    // ─── Step 2: decrypt ─────────────────────────────────────────────────────────

    private fun onDecryptClicked() {
        val passphrase = binding.etPassphrase.text.toString()
        if (passphrase.isBlank()) {
            showError("Veuillez saisir la passphrase.")
            return
        }

        val data = loadedFileBytes
        if (data == null) {
            showError(getString(R.string.backup_import_no_file))
            return
        }

        hideError()
        showLoading(true)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                FialkaBackupManager.openBackup(data, passphrase.toCharArray(), requireContext())
            }

            showLoading(false)

            when (result) {
                is FialkaBackupManager.OpenResult.Success -> {
                    pendingContent = result.content
                    showPreview(result.content)
                }
                is FialkaBackupManager.OpenResult.WrongPassphrase -> {
                    showError(getString(R.string.backup_import_wrong_passphrase))
                }
                is FialkaBackupManager.OpenResult.Invalid -> {
                    showError(result.reason)
                }
            }
        }
    }

    private fun showPreview(content: FialkaBackupManager.BackupContent) {
        val primaryColor = requireContext().getColor(R.color.primary)

        fun showRow(tv: android.widget.TextView, text: String) {
            tv.text = text
            tv.setTextColor(primaryColor)
            tv.visibility = View.VISIBLE
        }

        if (content.options.includeIdentity && content.identitySeedB64 != null) {
            showRow(binding.tvPreviewIdentity, "✓  ${getString(R.string.backup_preview_identity)}${
                content.displayName?.let { " — $it" } ?: ""
            }")
        }
        if (content.options.includeWallet && content.walletMnemonic != null) {
            showRow(binding.tvPreviewWallet, "✓  Wallet Monero")
        }
        if (content.options.includeContacts && content.contacts.isNotEmpty()) {
            showRow(binding.tvPreviewContacts, "✓  ${content.contacts.size} contact(s)")
        }
        // Conversations and messages are not backed up
        binding.tvPreviewConversations.visibility = android.view.View.GONE
        binding.tvPreviewMessages.visibility = android.view.View.GONE

        binding.cardPreview.visibility = View.VISIBLE
        binding.btnRestore.visibility  = View.VISIBLE
    }

    // ─── Step 3: restore ─────────────────────────────────────────────────────────

    private fun onRestoreClicked() {
        val content = pendingContent ?: return
        hideError()
        showLoading(true)
        binding.btnRestore.isEnabled = false

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    applyRestore(content)
                }
                // Navigate to Tor bootstrap (re-derives .onion from restored identity)
                findNavController().navigate(R.id.action_backupImport_to_torBootstrap)
            } catch (e: Exception) {
                showError("Erreur lors de la restauration : ${e.message}")
                binding.btnRestore.isEnabled = true
            } finally {
                showLoading(false)
            }
        }
    }

    private suspend fun applyRestore(content: FialkaBackupManager.BackupContent) {
        val ctx = requireContext().applicationContext
        val db  = FialkaDatabase.getInstance(ctx)

        // 1. Identity — this is the core: restore seed → derive all keys
        if (content.options.includeIdentity && content.identitySeedB64 != null) {
            val seedBytes = Base64.decode(content.identitySeedB64, Base64.NO_WRAP)
            try {
                val publicKey = CryptoManager.restoreFromSeed(seedBytes)
                val repo      = ChatRepository(ctx)
                repo.createUserWithKey(
                    displayName = content.displayName ?: "Moi",
                    publicKey   = publicKey
                )
            } finally {
                seedBytes.fill(0)
            }
        }

        // 2. Wallet
        if (content.options.includeWallet && content.walletMnemonic != null) {
            WalletSeedManager.importFromMnemonic(ctx, content.walletMnemonic)
        }

        // 3. Contacts (insert; REPLACE on conflict so we don't create duplicates)
        if (content.options.includeContacts) {
            content.contacts.forEach { db.contactDao().insertContact(it) }
        }

        // Conversations and messages are not restored (ratchet desync prevention).
        // Wipe all ratchet states so contacts re-initialize fresh PQXDH sessions.
        db.ratchetStateDao().deleteAllStates()
        ctx.getSharedPreferences("fialka_flags", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("needs_session_reset", true).apply()
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvError.visibility = View.GONE
    }

    private fun showLoading(visible: Boolean) {
        binding.progressBar.visibility = if (visible) View.VISIBLE else View.GONE
        binding.btnDecrypt.isEnabled   = !visible
    }

    override fun onDestroyView() {
        super.onDestroyView()
        loadedFileBytes?.fill(0)
        loadedFileBytes = null
        _binding = null
    }
}
