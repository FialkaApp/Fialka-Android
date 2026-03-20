/*
 * SecureChat — Post-quantum encrypted messenger
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
package com.securechat.ui.onboarding

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.securechat.R
import com.securechat.crypto.CryptoManager
import com.securechat.crypto.MnemonicManager
import com.securechat.data.remote.FirebaseRelay
import com.securechat.data.repository.ChatRepository
import com.securechat.ui.conversations.ConversationsViewModel
import com.securechat.databinding.FragmentRestoreBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Restore screen — enter 24 BIP-39 words in a professional grid
 * with autocomplete suggestions from the BIP-39 wordlist.
 */
class RestoreFragment : Fragment() {

    private var _binding: FragmentRestoreBinding? = null
    private val binding get() = _binding!!

    private val wordInputs = mutableListOf<AutoCompleteTextView>()
    private val wordCells = mutableListOf<LinearLayout>()

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
                // Highlight invalid words
                highlightInvalidWords(words, wordList)
                return@setOnClickListener
            }

            restore(words, displayName)
        }
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
                    val privateKeyBytes = MnemonicManager.mnemonicToPrivateKey(words)
                    val publicKey = CryptoManager.restoreIdentityKey(privateKeyBytes)
                    privateKeyBytes.fill(0)

                    if (!FirebaseRelay.isAuthenticated()) {
                        FirebaseRelay.signInAnonymously()
                    }

                    // Remove old orphaned Firebase profile with same publicKey
                    FirebaseRelay.removeOldUserByPublicKey(publicKey)

                    val repository = ChatRepository(requireContext())
                    repository.createUserWithKey(displayName, publicKey)

                    FirebaseRelay.registerPublicKey(publicKey)
                    FirebaseRelay.storeDisplayName(displayName)

                    // Publish Ed25519 signing public key (derived from restored identity)
                    repository.publishSigningPublicKey()
                    ConversationsViewModel.markSigningKeyPublished()

                    findNavController().navigate(R.id.action_restore_to_conversations)
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

    override fun onDestroyView() {
        super.onDestroyView()
        wordInputs.clear()
        wordCells.clear()
        _binding = null
    }
}
