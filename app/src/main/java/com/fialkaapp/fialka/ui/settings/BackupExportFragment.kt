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
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.backup.FialkaBackupManager
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.crypto.WalletSeedManager
import com.fialkaapp.fialka.data.local.FialkaDatabase
import com.fialkaapp.fialka.databinding.FragmentBackupExportBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Export screen — builds an encrypted .fialka backup file and saves it
 * to a user-chosen location via the Storage Access Framework.
 */
class BackupExportFragment : Fragment() {

    private var _binding: FragmentBackupExportBinding? = null
    private val binding get() = _binding!!

    /** Pending encrypted bytes waiting for a URI from the file-save picker. */
    private var pendingBytes: ByteArray? = null

    private val saveFileLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        val data = pendingBytes ?: return@registerForActivityResult
        pendingBytes = null
        writeBackupToUri(uri, data)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBackupExportBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        // Show wallet checkbox only if a wallet seed is stored
        val ctx = requireContext()
        if (WalletSeedManager.hasWalletSeed(ctx)) {
            binding.cbWallet.visibility = View.VISIBLE
        }

        binding.btnExport.setOnClickListener { onExportClicked() }
    }

    private fun onExportClicked() {
        val passphrase    = binding.etPassphrase.text.toString()
        val passphraseConf = binding.etPassphraseConfirm.text.toString()

        if (passphrase.isBlank()) {
            showError("Veuillez saisir une passphrase.")
            return
        }
        if (passphrase.length < 8) {
            showError("La passphrase doit contenir au moins 8 caractères.")
            return
        }
        if (passphrase != passphraseConf) {
            showError("Les deux passphrases ne correspondent pas.")
            return
        }

        showLoading(true)
        hideError()

        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.Default) {
                    buildBackupBytes(passphrase.toCharArray())
                }
                pendingBytes = bytes

                val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                saveFileLauncher.launch("fialka_backup_$dateStr.fialka")
            } catch (e: Exception) {
                showError("Erreur lors de la création : ${e.message}")
            } finally {
                showLoading(false)
            }
        }
    }

    private suspend fun buildBackupBytes(passphrase: CharArray): ByteArray {
        val ctx = requireContext().applicationContext
        val db  = FialkaDatabase.getInstance(ctx)

        // Gather identity seed
        val seedBytes = CryptoManager.getIdentitySeed()
        val seedB64   = Base64.encodeToString(seedBytes, Base64.NO_WRAP)
        seedBytes.fill(0)

        val displayName = CryptoManager.getAccountId() // used as display name fallback; real name is in UserLocal
        val userLocal   = db.userLocalDao().getUser()

        // Gather wallet mnemonic (optional)
        val walletMnemonic = if (binding.cbWallet.isChecked && binding.cbWallet.visibility == View.VISIBLE) {
            WalletSeedManager.getMnemonic(ctx)
        } else null

        // Gather contacts
        val contacts = if (binding.cbContacts.isChecked) {
            db.contactDao().getAllContactsList()
        } else emptyList<com.fialkaapp.fialka.data.model.Contact>()

        val options = FialkaBackupManager.BackupOptions(
            includeIdentity = true,
            includeWallet   = walletMnemonic != null,
            includeContacts = binding.cbContacts.isChecked
        )

        val content = FialkaBackupManager.BackupContent(
            options         = options,
            identitySeedB64 = seedB64,
            displayName     = userLocal?.displayName ?: displayName,
            walletMnemonic  = walletMnemonic,
            contacts        = contacts
        )

        return FialkaBackupManager.createBackup(passphrase, content)
    }

    private fun writeBackupToUri(uri: Uri, data: ByteArray) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(data)
                    }
                }
                showSuccess()
            } catch (e: Exception) {
                showError("Impossible d'écrire le fichier : ${e.message}")
            }
        }
    }

    private fun showSuccess() {
        hideError()
        binding.tvError.apply {
            text = "Sauvegarde créée avec succès."
            setTextColor(requireContext().getColor(com.fialkaapp.fialka.R.color.primary))
            visibility = View.VISIBLE
        }
        binding.btnExport.isEnabled = false
    }

    private fun showError(msg: String) {
        binding.tvError.text = msg
        binding.tvError.setTextColor(requireContext().getColor(com.fialkaapp.fialka.R.color.error))
        binding.tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvError.visibility = View.GONE
    }

    private fun showLoading(visible: Boolean) {
        binding.progressBar.visibility = if (visible) View.VISIBLE else View.GONE
        binding.btnExport.isEnabled = !visible
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pendingBytes?.fill(0)
        pendingBytes = null
        _binding = null
    }
}
