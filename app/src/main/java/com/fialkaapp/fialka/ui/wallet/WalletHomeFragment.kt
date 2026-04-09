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
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.crypto.WalletSeedManager
import com.fialkaapp.fialka.data.model.WalletTransaction
import com.fialkaapp.fialka.databinding.FragmentWalletHomeBinding
import com.fialkaapp.fialka.util.QrCodeGenerator

class WalletHomeFragment : Fragment() {

    private var _binding: FragmentWalletHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: WalletHomeViewModel by viewModels()

    private lateinit var recentAdapter: WalletTransactionAdapter
    private lateinit var allAdapter: WalletTransactionAdapter

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWalletHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbar()
        setupRecyclerViews()
        setupBottomNav()
        observeViewModel()
        setupReceiveSection()
        setupSettingsSection()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupToolbar() {
        binding.walletToolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupRecyclerViews() {
        recentAdapter = WalletTransactionAdapter(maxItems = 5)
        binding.rvRecentTxs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = recentAdapter
            isNestedScrollingEnabled = false
        }

        allAdapter = WalletTransactionAdapter()
        binding.rvAllTxs.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = allAdapter
        }
    }

    private fun setupBottomNav() {
        binding.walletBottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_wallet_home    -> showSection(Section.HOME)
                R.id.nav_wallet_receive -> showSection(Section.RECEIVE)
                R.id.nav_wallet_history -> showSection(Section.HISTORY)
                R.id.nav_wallet_settings -> showSection(Section.SETTINGS)
            }
            true
        }
        // default section
        showSection(Section.HOME)
    }

    private fun setupReceiveSection() {
        binding.btnCopyPrimary.setOnClickListener {
            val addr = viewModel.primaryAddress.value ?: return@setOnClickListener
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE)
                    as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Monero address", addr))
            Toast.makeText(requireContext(), R.string.wallet_address_copied, Toast.LENGTH_SHORT).show()
        }

        binding.btnShareAddress.setOnClickListener {
            val addr = viewModel.primaryAddress.value ?: return@setOnClickListener
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, addr)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.wallet_share_address)))
        }
    }

    private fun setupSettingsSection() {
        // Populate current server URL
        binding.etServerUrl.setText(viewModel.getServerUrl())

        binding.btnSaveServer.setOnClickListener {
            val url = binding.etServerUrl.text?.toString()?.trim()
            viewModel.setServerUrl(url)
            Toast.makeText(requireContext(), R.string.wallet_settings_server_saved, Toast.LENGTH_SHORT).show()
            binding.tilServerUrl.clearFocus()
        }

        binding.btnSyncNow.setOnClickListener {
            viewModel.triggerSync()
        }

        binding.btnWipeWallet.setOnClickListener {
            confirmWipe()
        }
    }

    // ── ViewModel observers ───────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.balance.observe(viewLifecycleOwner) { piconero ->
            val xmrStr = WalletTransaction.piconeroToXmr(piconero)
                .let { raw ->
                    // Trim trailing zeros up to 5 decimal places minimum
                    val parts = raw.split(".")
                    val frac = parts.getOrElse(1) { "0" }.trimEnd('0').padEnd(5, '0')
                    "${parts[0]}.$frac XMR"
                }
            binding.tvBalance.text = xmrStr
        }

        viewModel.syncState.observe(viewLifecycleOwner) { state ->
            if (state == null) {
                binding.tvWalletSyncStatus.text = getString(R.string.wallet_home_never_synced)
                binding.tvBlockHeight.text = "—"
                binding.tvScanProgress.text = "—"
                binding.tvLastSync.text = getString(R.string.wallet_home_never_synced)
                return@observe
            }

            // Toolbar subtitle
            val lastSyncMs = state.lastSyncedAt
            binding.tvWalletSyncStatus.text = if (lastSyncMs > 0L) {
                DateUtils.getRelativeTimeSpanString(
                    lastSyncMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                ).toString()
            } else {
                getString(R.string.wallet_home_never_synced)
            }

            // Block height
            binding.tvBlockHeight.text = "%,d".format(state.lastScannedHeight)

            // Scan progress
            val progress = if (state.networkHeight > 0L) {
                val scanned = state.lastScannedHeight - state.restoreHeight
                val total   = state.networkHeight - state.restoreHeight
                if (total > 0L) (scanned * 100 / total).coerceIn(0, 100).toInt() else 100
            } else 0

            if (progress < 100) {
                binding.progressSync.visibility = View.VISIBLE
                binding.progressSync.progress = progress
                binding.tvScanProgress.text = "$progress%"
            } else {
                binding.progressSync.visibility = View.GONE
                binding.tvScanProgress.text = "100%"
            }

            // Settings last sync label
            binding.tvLastSync.text = if (lastSyncMs > 0L) {
                getString(R.string.wallet_settings_last_sync,
                    DateUtils.getRelativeTimeSpanString(
                        lastSyncMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
                    ))
            } else {
                getString(R.string.wallet_home_never_synced)
            }
        }

        viewModel.transactions.observe(viewLifecycleOwner) { txs ->
            recentAdapter.submitList(txs.take(5))
            allAdapter.submitList(txs)

            binding.tvNoTxHome.visibility = if (txs.isEmpty()) View.VISIBLE else View.GONE
            binding.rvRecentTxs.visibility = if (txs.isEmpty()) View.GONE else View.VISIBLE

            binding.tvNoTxHistory.visibility = if (txs.isEmpty()) View.VISIBLE else View.GONE
            binding.rvAllTxs.visibility = if (txs.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.primaryAddress.observe(viewLifecycleOwner) { addr ->
            if (addr == null) {
                binding.tvAddressLoading.visibility = View.VISIBLE
                binding.tvPrimaryAddress.visibility = View.GONE
                binding.addressActions.visibility = View.GONE
                binding.ivQrCode.setImageBitmap(null)
            } else {
                binding.tvAddressLoading.visibility = View.GONE
                binding.tvPrimaryAddress.visibility = View.VISIBLE
                binding.tvPrimaryAddress.text = addr
                binding.addressActions.visibility = View.VISIBLE
                generateQr(addr)
            }
        }

        viewModel.syncRunning.observe(viewLifecycleOwner) { running ->
            binding.btnSyncNow.isEnabled = !running
            binding.btnSyncNow.text = if (running)
                getString(R.string.wallet_settings_syncing)
            else
                getString(R.string.wallet_settings_sync_now)
        }

        viewModel.syncResult.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show()
            }
        }
    }

    // ── Section switching ─────────────────────────────────────────────────────

    private enum class Section { HOME, RECEIVE, HISTORY, SETTINGS }

    private fun showSection(section: Section) {
        binding.homeSection.visibility     = if (section == Section.HOME)     View.VISIBLE else View.GONE
        binding.receiveSection.visibility  = if (section == Section.RECEIVE)  View.VISIBLE else View.GONE
        binding.historySection.visibility  = if (section == Section.HISTORY)  View.VISIBLE else View.GONE
        binding.settingsSection.visibility = if (section == Section.SETTINGS) View.VISIBLE else View.GONE

        // Lazy-load primary address the first time Receive is shown
        if (section == Section.RECEIVE) {
            viewModel.loadPrimaryAddress()
        }
    }

    // ── QR code ───────────────────────────────────────────────────────────────

    private fun generateQr(address: String) {
        val bitmap = QrCodeGenerator.generate(address, 512)
        binding.ivQrCode.setImageBitmap(bitmap)
    }

    // ── Wipe confirmation ─────────────────────────────────────────────────────

    private fun confirmWipe() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.wallet_settings_wipe_btn)
            .setMessage(R.string.wallet_settings_wipe_confirm)
            .setPositiveButton(R.string.wallet_settings_wipe_btn) { _, _ ->
                viewModel.wipeWallet()
                Toast.makeText(requireContext(), R.string.wallet_wiped_toast, Toast.LENGTH_LONG).show()
                findNavController().navigate(R.id.action_walletHome_to_conversations)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
