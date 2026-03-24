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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.crypto.MnemonicManager
import com.fialkaapp.fialka.databinding.FragmentBackupPhraseBinding

/**
 * Displays the 24-word BIP-39 mnemonic phrase for the user to write down.
 * Mandatory step after identity creation before accessing conversations.
 */
class BackupPhraseFragment : Fragment() {

    private var _binding: FragmentBackupPhraseBinding? = null
    private val binding get() = _binding!!

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
        val words = MnemonicManager.seedToMnemonic(seed)
        seed.fill(0)

        binding.rvWords.layoutManager = GridLayoutManager(requireContext(), 3)
        binding.rvWords.adapter = WordAdapter(words)

        binding.cbConfirm.setOnCheckedChangeListener { _, isChecked ->
            binding.btnContinue.isEnabled = isChecked
        }

        binding.btnContinue.setOnClickListener {
            findNavController().navigate(R.id.action_backup_to_torBootstrap)
        }
    }

    override fun onDestroyView() {
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
