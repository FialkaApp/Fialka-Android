/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.ui.wallet

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.crypto.WalletSeedManager
import com.fialkaapp.fialka.databinding.FragmentWalletChoiceBinding

/**
 * Entry point for wallet onboarding.
 *
 * Offers two paths:
 * - "Créer" → [WalletBackupPhraseFragment] — generates a new seed
 * - "Restaurer" → [WalletRestoreFragment] — imports from 25-word mnemonic
 *
 * If a wallet already exists, skips directly to the wallet home screen
 * (future Phase 5: WalletHomeFragment).
 */
class WalletChoiceFragment : Fragment() {

    private var _binding: FragmentWalletChoiceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalletChoiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // If wallet is already active, skip onboarding
        if (WalletSeedManager.hasWalletSeed(requireContext())) {
            findNavController().navigate(R.id.action_walletChoice_to_walletHome)
            return
        }

        binding.btnCreate.setOnClickListener {
            findNavController().navigate(R.id.action_walletChoice_to_walletBackupPhrase)
        }

        binding.btnRestore.setOnClickListener {
            findNavController().navigate(R.id.action_walletChoice_to_walletRestore)
        }

        binding.btnCancel.setOnClickListener {
            findNavController().popBackStack()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
