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

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.backup.FialkaBackupManager
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.crypto.MnemonicManager
import com.fialkaapp.fialka.crypto.WalletSeedManager
import com.fialkaapp.fialka.data.local.FialkaDatabase
import com.fialkaapp.fialka.data.repository.ChatRepository
import com.fialkaapp.fialka.databinding.FragmentRestoreBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Restore screen — two modes:
 *  1) Seed 24 mots : saisir la phrase BIP-39 → restaure uniquement l'identité
 *  2) Fichier .fialka : choisir un fichier + passphrase → restaure tout (identité, wallet, contacts…)
 */
class RestoreFragment : Fragment() {

    private var _binding: FragmentRestoreBinding? = null
    private val binding get() = _binding!!

    // ── Seed mode ────────────────────────────────────────────────────────────────
    private val wordInputs = mutableListOf<AutoCompleteTextView>()
    private val wordCells  = mutableListOf<LinearLayout>()

    // ── File mode ────────────────────────────────────────────────────────────────
    private var loadedFileBytes: ByteArray? = null
    private var pendingContent: FialkaBackupManager.BackupContent? = null

    private val openFileLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri ?: return@registerForActivityResult
        loadBackupFile(uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRestoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val wordList = MnemonicManager.getWordList()
        buildWordGrid(wordList)

        // ── Mode toggle ──────────────────────────────────────────────────────────
        binding.toggleMode.check(R.id.btnModeSeed)
        binding.toggleMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            hideError()
            when (checkedId) {
                R.id.btnModeSeed -> {
                    binding.sectionSeed.visibility = View.VISIBLE
                    binding.sectionFile.visibility = View.GONE
                }
                R.id.btnModeFile -> {
                    binding.sectionSeed.visibility = View.GONE
                    binding.sectionFile.visibility = View.VISIBLE
                }
            }
        }

        // ── Seed mode ────────────────────────────────────────────────────────────
        binding.btnRestore.setOnClickListener {
            val displayName = binding.etDisplayName.text.toString().trim()

            if (displayName.isBlank()) {
                showError("Veuillez entrer un pseudo.")
                return@setOnClickListener
            }

            val words = wordInputs.map { it.text.toString().trim().lowercase() }
            val emptyIndices = words.mapIndexedNotNull { i, w -> if (w.isBlank()) i + 1 else null }
            if (emptyIndices.isNotEmpty()) {
                showError("Mots manquants : ${emptyIndices.joinToString(", ")}")
                return@setOnClickListener
            }

            if (!MnemonicManager.validateMnemonic(words)) {
                showError("Phrase invalide. Vérifiez les mots et réessayez.")
                highlightInvalidWords(words, wordList)
                return@setOnClickListener
            }

            restore(words, displayName)
        }

        // ── File mode ────────────────────────────────────────────────────────────
        binding.btnChooseFile.setOnClickListener {
            openFileLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        }

        binding.btnRestoreFile.setOnClickListener {
            val passphrase = binding.etFilePassphrase.text.toString()
            if (passphrase.isBlank()) {
                showError("Veuillez saisir la passphrase.")
                return@setOnClickListener
            }
            val data = loadedFileBytes
            if (data == null) {
                showError("Aucun fichier chargé.")
                return@setOnClickListener
            }
            // If preview already shown, go straight to restore
            if (pendingContent != null) {
                applyFileRestore(pendingContent!!)
            } else {
                decryptAndPreview(data, passphrase.toCharArray())
            }
        }
    }

    // ── File mode helpers ─────────────────────────────────────────────────────────

    private fun loadBackupFile(uri: Uri) {
        hideError()
        pendingContent = null
        binding.cardFilePreview.visibility   = View.GONE
        binding.btnRestoreFile.text          = "Restaurer la sauvegarde"

        lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    requireContext().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IllegalStateException("Impossible de lire le fichier.")
                }
                loadedFileBytes = bytes

                // Afficher le nom du fichier
                val name = resolveFileName(uri)
                binding.tvFileName.text       = name
                binding.tvFileName.visibility = View.VISIBLE

                // Afficher le champ passphrase
                binding.cardFilePassphrase.visibility = View.VISIBLE
                binding.etFilePassphrase.requestFocus()

                // Afficher le bouton (le déclic déclenche déchiffrement puis restauration)
                binding.btnRestoreFile.visibility = View.VISIBLE

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

    private fun decryptAndPreview(data: ByteArray, passphrase: CharArray) {
        showLoading(true)
        hideError()

        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                FialkaBackupManager.openBackup(data, passphrase)
            }
            showLoading(false)

            when (result) {
                is FialkaBackupManager.OpenResult.Success -> {
                    pendingContent = result.content
                    showFilePreview(result.content)
                    // Bouton passe en "Confirmer la restauration"
                    binding.btnRestoreFile.text = "Confirmer la restauration"
                }
                is FialkaBackupManager.OpenResult.WrongPassphrase ->
                    showError("Passphrase incorrecte. Vérifiez et réessayez.")
                is FialkaBackupManager.OpenResult.Invalid ->
                    showError(result.reason)
            }
        }
    }

    private fun showFilePreview(content: FialkaBackupManager.BackupContent) {
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        fun showRow(tv: TextView, text: String) {
            tv.text = text; tv.setTextColor(primaryColor); tv.visibility = View.VISIBLE
        }
        if (content.options.includeIdentity && content.identitySeedB64 != null)
            showRow(binding.tvPreviewIdentity, "✓  Identité Fialka${content.displayName?.let { " — $it" } ?: ""}")
        if (content.options.includeWallet && content.walletMnemonic != null)
            showRow(binding.tvPreviewWallet, "✓  Wallet Monero")
        if (content.options.includeContacts && content.contacts.isNotEmpty())
            showRow(binding.tvPreviewContacts, "✓  ${content.contacts.size} contact(s)")
        // Conversations and messages are not backed up
        binding.tvPreviewConversations.visibility = View.GONE
        binding.tvPreviewMessages.visibility = View.GONE
        binding.cardFilePreview.visibility = View.VISIBLE
    }

    private fun applyFileRestore(content: FialkaBackupManager.BackupContent) {
        showLoading(true)
        binding.btnRestoreFile.isEnabled = false

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val ctx = requireContext().applicationContext
                    val db  = FialkaDatabase.getInstance(ctx)

                    if (content.options.includeIdentity && content.identitySeedB64 != null) {
                        val seedBytes = Base64.decode(content.identitySeedB64, Base64.NO_WRAP)
                        try {
                            val publicKey = CryptoManager.restoreFromSeed(seedBytes)
                            ChatRepository(ctx).createUserWithKey(
                                displayName = content.displayName ?: "Moi",
                                publicKey   = publicKey
                            )
                        } finally { seedBytes.fill(0) }
                    }
                    if (content.options.includeWallet && content.walletMnemonic != null)
                        WalletSeedManager.importFromMnemonic(ctx, content.walletMnemonic)
                    if (content.options.includeContacts)
                        content.contacts.forEach { db.contactDao().insertContact(it) }
                    // Conversations and messages are not restored (ratchet desync prevention)

                    // Clear all ratchet states — they are time-bound ephemeral state that
                    // diverges from contacts' current state after a restore.
                    // broadcastPresence() will send TYPE_SESSION_RESET to all contacts once
                    // Tor reconnects, triggering a mutual fresh PQXDH re-initialization.
                    db.ratchetStateDao().deleteAllStates()
                    ctx.getSharedPreferences("fialka_flags", android.content.Context.MODE_PRIVATE)
                        .edit().putBoolean("needs_session_reset", true).apply()
                }
                findNavController().navigate(R.id.action_restore_to_torBootstrap)
            } catch (e: Exception) {
                showLoading(false)
                binding.btnRestoreFile.isEnabled = true
                showError("Erreur lors de la restauration : ${e.message}")
            }
        }
    }

    private fun showLoading(visible: Boolean) {
        binding.progressBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun buildWordGrid(wordList: List<String>) {
        val grid = binding.gridWords
        val ctx = requireContext()

        val primaryColor = ContextCompat.getColor(ctx, R.color.primary)
        val surfaceColor = ContextCompat.getColor(ctx, R.color.surface)
        val textPrimary = ContextCompat.getColor(ctx, R.color.text_primary)
        val textSecondary = ContextCompat.getColor(ctx, R.color.text_secondary)

        val adapter = ArrayAdapter(ctx, android.R.layout.simple_dropdown_item_1line, wordList)

        for (i in 0 until 24) {
            // Cell container
            val cell = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val bg = GradientDrawable().apply {
                    setColor(surfaceColor)
                    cornerRadius = 12f * resources.displayMetrics.density
                    setStroke(
                        (1f * resources.displayMetrics.density).toInt(),
                        Color.parseColor("#33FFFFFF")
                    )
                }
                background = bg
                val dp4 = (4 * resources.displayMetrics.density).toInt()
                val dp8 = (8 * resources.displayMetrics.density).toInt()
                setPadding(dp8, dp4, dp4, dp4)
            }

            // Number label
            val numLabel = TextView(ctx).apply {
                text = "${i + 1}."
                textSize = 11f
                setTextColor(textSecondary)
                typeface = Typeface.DEFAULT_BOLD
                val dp2 = (2 * resources.displayMetrics.density).toInt()
                setPadding(0, 0, dp2, 0)
            }

            // AutoComplete input
            val input = AutoCompleteTextView(ctx).apply {
                setAdapter(adapter)
                threshold = 1
                textSize = 13f
                setTextColor(textPrimary)
                background = null
                setSingleLine(true)
                imeOptions = EditorInfo.IME_ACTION_NEXT
                inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                setPadding(0, 0, 0, 0)
                dropDownWidth = (140 * resources.displayMetrics.density).toInt()
            }

            // On autocomplete item selected → validate + advance
            input.setOnItemClickListener { _, _, _, _ ->
                val word = input.text.toString().trim().lowercase()
                input.setText(word)
                input.setSelection(word.length)
                validateCell(i, word, wordList)
                advanceToNext(i)
            }

            // On Enter/Next → validate + advance
            input.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_NEXT || actionId == EditorInfo.IME_ACTION_DONE) {
                    val word = input.text.toString().trim().lowercase()
                    input.setText(word)
                    if (word.isNotBlank()) input.setSelection(word.length)
                    validateCell(i, word, wordList)
                    advanceToNext(i)
                    true
                } else false
            }

            // Watch text changes for counter + border color
            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    updateWordCount()
                    val word = s.toString().trim().lowercase()
                    if (word.isBlank()) {
                        setCellBorder(i, Color.parseColor("#33FFFFFF"))
                    }
                }
            })

            // Focus: highlight active cell
            input.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    setCellBorder(i, primaryColor)
                } else {
                    val word = input.text.toString().trim().lowercase()
                    if (word.isNotBlank()) {
                        validateCell(i, word, wordList)
                    } else {
                        setCellBorder(i, Color.parseColor("#33FFFFFF"))
                    }
                }
            }

            cell.addView(numLabel)
            cell.addView(input, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))

            val dp3 = (3 * resources.displayMetrics.density).toInt()
            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(i % 3, 1f)
                rowSpec = GridLayout.spec(i / 3)
                setMargins(dp3, dp3, dp3, dp3)
            }
            grid.addView(cell, lp)

            wordInputs.add(input)
            wordCells.add(cell)
        }

        // Focus first cell
        wordInputs[0].requestFocus()
    }

    private fun validateCell(index: Int, word: String, wordList: List<String>) {
        val valid = wordList.contains(word)
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val errorColor = ContextCompat.getColor(requireContext(), R.color.error)
        setCellBorder(index, if (valid) primaryColor else errorColor)
    }

    private fun setCellBorder(index: Int, color: Int) {
        val bg = wordCells[index].background as? GradientDrawable ?: return
        bg.setStroke((1f * resources.displayMetrics.density).toInt(), color)
    }

    private fun advanceToNext(currentIndex: Int) {
        if (currentIndex < 23) {
            wordInputs[currentIndex + 1].requestFocus()
        } else {
            // All 24 entered, move to display name
            binding.etDisplayName.requestFocus()
        }
    }

    private fun updateWordCount() {
        val filled = wordInputs.count { it.text.toString().trim().isNotBlank() }
        binding.tvWordCount.text = "$filled / 24 mots"
        val primaryColor = ContextCompat.getColor(requireContext(), R.color.primary)
        val secondaryColor = ContextCompat.getColor(requireContext(), R.color.text_secondary)
        binding.tvWordCount.setTextColor(if (filled == 24) primaryColor else secondaryColor)
    }

    private fun highlightInvalidWords(words: List<String>, wordList: List<String>) {
        val errorColor = ContextCompat.getColor(requireContext(), R.color.error)
        for (i in words.indices) {
            if (!wordList.contains(words[i])) {
                setCellBorder(i, errorColor)
            }
        }
    }

    private fun restore(words: List<String>, displayName: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnRestore.isEnabled = false
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                withTimeout(15_000L) {
                    val seed = MnemonicManager.mnemonicToSeed(words)
                    val publicKey = CryptoManager.restoreFromSeed(seed)
                    seed.fill(0)

                    val repository = ChatRepository(requireContext())
                    repository.createUserWithKey(displayName, publicKey)

                    // Identity restored — publish .onion (Tor is already connected)
                    findNavController().navigate(R.id.action_restore_to_torBootstrap)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnRestore.isEnabled = true
                showError(e.message ?: "Erreur lors de la restauration.")
            }
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        binding.tvError.visibility = View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        wordInputs.clear()
        wordCells.clear()
        loadedFileBytes?.fill(0)
        loadedFileBytes = null
        _binding = null
    }
}
