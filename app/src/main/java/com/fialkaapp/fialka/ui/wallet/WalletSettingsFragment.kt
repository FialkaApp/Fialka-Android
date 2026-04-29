/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667
 */
package com.fialkaapp.fialka.ui.wallet

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.FragmentWalletSettingsBinding
import com.fialkaapp.fialka.wallet.WalletPreferences
import com.fialkaapp.fialka.wallet.WalletRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WalletSettingsFragment : Fragment() {

    private var _binding: FragmentWalletSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalletSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.switchWalletEnabled.isChecked = WalletPreferences.isWalletEnabled(requireContext())
        binding.etNodeUrl.setText(WalletPreferences.getNodeUrl(requireContext()))

        binding.switchWalletEnabled.setOnCheckedChangeListener { _, checked ->
            binding.btnOpenWallet.visibility = if (checked) View.VISIBLE else View.GONE
        }
        binding.btnOpenWallet.visibility =
            if (binding.switchWalletEnabled.isChecked) View.VISIBLE else View.GONE

        binding.btnStartSync.setOnClickListener {
            WalletRepository.setSyncEnabled(requireContext(), true)
            refreshNodeStatus()
            Toast.makeText(requireContext(), R.string.wallet_sync_started, Toast.LENGTH_SHORT).show()
        }

        binding.btnStopSync.setOnClickListener {
            WalletRepository.setSyncEnabled(requireContext(), false)
            refreshNodeStatus()
            Toast.makeText(requireContext(), R.string.wallet_sync_stopped, Toast.LENGTH_SHORT).show()
        }

        binding.btnCheckNode.setOnClickListener {
            WalletRepository.invalidateNodeCache()
            refreshNodeStatus()
        }

        binding.btnSave.setOnClickListener {
            val enabled = binding.switchWalletEnabled.isChecked
            val nodeUrl = binding.etNodeUrl.text?.toString()?.trim().orEmpty()

            if (!isValidNodeUrl(nodeUrl)) {
                Toast.makeText(requireContext(), R.string.wallet_invalid_node_url, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            WalletPreferences.setWalletEnabled(requireContext(), enabled)
            WalletPreferences.setNodeUrl(requireContext(), nodeUrl)
            WalletRepository.invalidateNodeCache()

            Toast.makeText(requireContext(), R.string.wallet_saved, Toast.LENGTH_SHORT).show()
            refreshNodeStatus()
        }

        binding.btnOpenWallet.setOnClickListener {
            if (!WalletPreferences.isWalletEnabled(requireContext())) {
                Toast.makeText(requireContext(), R.string.wallet_enable_title, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            findNavController().navigate(R.id.action_walletSettings_to_walletHome)
        }

        binding.btnDeleteWallet.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.wallet_delete_confirm_title)
                .setMessage(R.string.wallet_delete_confirm_message)
                .setPositiveButton(R.string.wallet_delete_confirm_btn) { _, _ ->
                    val ctx = requireContext().applicationContext
                    lifecycleScope.launch(Dispatchers.IO) {
                        WalletRepository.deleteWallet(ctx)
                        withContext(Dispatchers.Main) {
                            if (_binding == null) return@withContext
                            Toast.makeText(ctx, R.string.wallet_delete_success, Toast.LENGTH_SHORT).show()
                            findNavController().navigateUp()
                        }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        refreshNodeStatus()
    }

    private fun refreshNodeStatus() {
        // checkNodeStatus() fait du réseau — doit tourner hors du main thread.
        val ctx = requireContext().applicationContext
        binding.tvNodeSyncStatus.text = getString(R.string.wallet_node_connecting)
        lifecycleScope.launch(Dispatchers.IO) {
            val snapshot = WalletRepository.getSnapshot(ctx)
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                val syncState = if (snapshot.syncEnabled) {
                    getString(R.string.wallet_sync_running)
                } else {
                    getString(R.string.wallet_sync_paused)
                }
                val h = snapshot.nodeStatus.daemonHeight?.toString() ?: "?"
                val t = snapshot.nodeStatus.targetHeight
                // targetHeight == 0 means daemon is fully synced (not a real target).
                // Only show "/t" when target is a meaningful positive value.
                val heightStr = if (t != null && t > 0L) "Hauteur : $h / $t" else "Hauteur : $h"
                binding.tvNodeSyncStatus.text = "${snapshot.nodeStatus.statusLabel}\n$syncState\n$heightStr\n${snapshot.walletEngineStatus}"
            }
        }
    }

    private fun isValidNodeUrl(url: String): Boolean {
        return try {
            val uri = Uri.parse(url)
            !uri.scheme.isNullOrBlank() && !uri.host.isNullOrBlank() && (uri.port > 0)
        } catch (_: Exception) {
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
