/*
 * Fialka - Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667
 */
package com.fialkaapp.fialka.ui.wallet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.crypto.WalletSeedManager
import com.fialkaapp.fialka.databinding.FragmentWalletHomeBinding
import com.fialkaapp.fialka.wallet.MoneroWallet
import com.fialkaapp.fialka.wallet.WalletRepository
import com.fialkaapp.fialka.wallet.WalletPreferences
import com.fialkaapp.fialka.wallet.WalletSnapshot
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WalletHomeFragment : Fragment() {

    private enum class Tab { HOME, SEND, RECEIVE, TRANSACTIONS }

    private var _binding: FragmentWalletHomeBinding? = null
    private val binding get() = _binding!!
    private var currentTab = Tab.HOME
    private val clipboardClearHandler = Handler(Looper.getMainLooper())
    private val syncPollingHandler = Handler(Looper.getMainLooper())
    private var syncPollingRunnable: Runnable? = null

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

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.toolbar.inflateMenu(R.menu.menu_wallet_home)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_wallet_settings -> {
                    findNavController().navigate(R.id.action_walletHome_to_walletSettings)
                    true
                }
                R.id.action_wallet_reveal_seed -> {
                    revealStoredSeed()
                    true
                }
                R.id.action_wallet_delete -> {
                    showDeleteWalletConfirmation()
                    true
                }
                else -> false
            }
        }

        binding.btnCreateWallet.setOnClickListener { createWallet() }
        binding.btnRestoreWallet.setOnClickListener { promptRestoreSeed() }

        binding.tabButtonHome.setOnClickListener { showTab(Tab.HOME) }
        binding.tabButtonSend.setOnClickListener { showTab(Tab.SEND) }
        binding.tabButtonReceive.setOnClickListener { showTab(Tab.RECEIVE) }
        binding.tabButtonTransactions.setOnClickListener { showTab(Tab.TRANSACTIONS) }

        binding.btnQuickSend.setOnClickListener { showTab(Tab.SEND) }
        binding.btnQuickReceive.setOnClickListener { showTab(Tab.RECEIVE) }

        binding.btnSendMax.setOnClickListener { setSendMax() }
        binding.btnSendConfirm.setOnClickListener { confirmAndSend() }

        binding.btnReceiveCopy.setOnClickListener { copyReceiveAddress() }

        binding.walletSwipeRefresh.setOnRefreshListener {
            WalletRepository.invalidateAllCaches()
            renderState()
        }

        renderState()
        startSyncPolling()
    }

    private var lastKnownWalletHeight = -1L
    private var stuckPollCount = 0
    private var hasStoredAfterSync = false

    /** Poll every 3s while wallet is not fully synced (including during init). */
    private fun startSyncPolling() {
        stopSyncPolling()
        val runnable = object : Runnable {
            override fun run() {
                if (_binding == null) return
                val isOpen = MoneroWallet.isOpen
                val synced = isOpen && MoneroWallet.isSynchronized()
                if (!synced) {
                    // Keep polling while initializing OR while open-but-not-synced
                    WalletRepository.invalidateWalletStateCache()
                    renderState()

                    // Stuck detection: if wallet is open but height hasn't moved for 3 polls
                    // (9s), the auto-refresh likely hit status=1 (stagenet reorg). Restart it.
                    if (isOpen) {
                        val currentH = MoneroWallet.getBlockchainHeight()
                        if (currentH > 0L && currentH == lastKnownWalletHeight) {
                            stuckPollCount++
                            if (stuckPollCount >= 3) {
                                stuckPollCount = 0
                                val ctx = requireContext().applicationContext
                                Thread({ WalletRepository.restartRefresh() }, "refresh-restart").start()
                            }
                        } else {
                            stuckPollCount = 0
                            lastKnownWalletHeight = currentH
                        }
                    } else {
                        stuckPollCount = 0
                        lastKnownWalletHeight = -1L
                    }

                    syncPollingHandler.postDelayed(this, 3_000L)
                } else {
                    // Wallet is synced — save state to disk so next launch doesn't rescan from 0
                    if (!hasStoredAfterSync) {
                        hasStoredAfterSync = true
                        val ctx = requireContext().applicationContext
                        Thread({ WalletRepository.storeWallet() }, "wallet-store").start()
                    }
                    // Stop polling — swipe-to-refresh still works for manual updates
                }
            }
        }
        syncPollingRunnable = runnable
        syncPollingHandler.postDelayed(runnable, 3_000L)
    }

    private fun stopSyncPolling() {
        syncPollingRunnable?.let { syncPollingHandler.removeCallbacks(it) }
        syncPollingRunnable = null
    }

    private fun renderState() {
        val ctx = requireContext().applicationContext
        binding.tvSyncStatus.text = getString(R.string.wallet_node_connecting)

        // Network badge — shown immediately without waiting for snapshot
        val isStagenet = WalletPreferences.isStagenet(requireContext())
        binding.tvNetworkBadge.visibility = View.VISIBLE
        if (isStagenet) {
            binding.tvNetworkBadge.text = getString(R.string.wallet_network_stagenet_badge)
            binding.tvNetworkBadge.setBackgroundColor(android.graphics.Color.parseColor("#CC0000"))
        } else {
            binding.tvNetworkBadge.text = getString(R.string.wallet_network_mainnet_label)
            binding.tvNetworkBadge.setBackgroundColor(android.graphics.Color.parseColor("#1B5E20"))
        }

        lifecycleScope.launch(Dispatchers.IO) {
            val snapshot = WalletRepository.getSnapshot(ctx)
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.walletSwipeRefresh.isRefreshing = false
                val showSetup = !snapshot.enabled || !snapshot.hasSeed
                binding.setupContainer.visibility = if (showSetup) View.VISIBLE else View.GONE
                binding.walletContentContainer.visibility = if (showSetup) View.GONE else View.VISIBLE
                binding.tvNodeValue.text = snapshot.nodeStatus.statusLabel
                val daemonH0 = snapshot.nodeStatus.daemonHeight ?: 0L
                binding.tvSyncStatus.text = when {
                    snapshot.walletSyncHeight <= 0L -> ""
                    daemonH0 > 0L && snapshot.walletSyncHeight < daemonH0 ->
                        "sync ${snapshot.walletSyncHeight} / $daemonH0…"
                    else -> "h.${snapshot.walletSyncHeight}"
                }
                if (!showSetup) {
                    bindWalletContent(snapshot)
                    showTab(currentTab)
                }
            }
        }
    }

    private fun bindWalletContent(snapshot: WalletSnapshot) {
        // Home tab
        binding.tvHomeBalance.text = formatXmr(snapshot.balancePiconero)
        binding.tvHomeUnlocked.text = if (snapshot.unlockedPiconero >= 0L &&
            snapshot.unlockedPiconero != snapshot.balancePiconero
        ) {
            "disponible : ${formatXmr(snapshot.unlockedPiconero)}"
        } else ""

        addTxRows(binding.containerRecentTx, snapshot.transactions, maxItems = 5)

        // Send tab
        binding.tvSendAvailable.text = "Disponible : ${formatXmr(snapshot.unlockedPiconero)}"

        // Receive tab
        binding.tvReceiveAddress.text = snapshot.receiveAddress
            ?: getString(R.string.wallet_receive_address_unavailable)

        // Transactions tab
        val daemonH = snapshot.nodeStatus.daemonHeight
        binding.tvTxSyncInfo.text = if (daemonH != null) {
            "Hauteur ${snapshot.walletSyncHeight} / $daemonH · ${snapshot.transactions.size} tx"
        } else {
            "Hauteur ${snapshot.walletSyncHeight} · ${snapshot.transactions.size} tx"
        }
        addTxRows(binding.containerTxList, snapshot.transactions, maxItems = Int.MAX_VALUE)
    }

    private fun addTxRows(container: LinearLayout, txs: List<MoneroWallet.TxInfo>, maxItems: Int) {
        container.removeAllViews()
        if (txs.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = getString(R.string.wallet_tx_empty)
                textSize = 13f
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                setPadding(0, 16, 0, 16)
            }
            container.addView(empty)
            return
        }
        val sorted = txs.sortedByDescending { it.timestamp }
        sorted.take(maxItems).forEach { tx ->
            container.addView(buildTxRow(tx))
        }
    }

    private fun buildTxRow(tx: MoneroWallet.TxInfo): View {
        val ctx = requireContext()
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
        }

        val leftCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val dirText = if (tx.isIncoming) "Recu" else "Envoye"
        val statusText = when {
            tx.isPending -> "En attente"
            tx.confirmations < 10 -> "${tx.confirmations} confirmation(s)"
            else -> "Confirme"
        }

        val dirLabel = TextView(ctx).apply {
            text = dirText
            textSize = 14f
            setTextColor(if (tx.isIncoming) Color.parseColor("#388E3C") else Color.parseColor("#1565C0"))
        }
        val statusLabel = TextView(ctx).apply {
            text = statusText
            textSize = 11f
            setTextColor(ContextCompat.getColor(ctx, android.R.color.darker_gray))
        }
        leftCol.addView(dirLabel)
        leftCol.addView(statusLabel)

        val amountLabel = TextView(ctx).apply {
            text = (if (tx.isIncoming) "+" else "-") + formatXmr(tx.amount)
            textSize = 14f
            setTextColor(if (tx.isIncoming) Color.parseColor("#388E3C") else Color.parseColor("#1565C0"))
        }

        row.addView(leftCol)
        row.addView(amountLabel)

        // Divider
        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            with(android.util.TypedValue()) {
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)
                setBackgroundResource(resourceId)
            }
        }
        wrapper.setOnClickListener { showTxDetail(tx) }
        wrapper.addView(row)
        val divider = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1
            ).also { it.setMargins(0, 0, 0, 0) }
            setBackgroundColor(Color.parseColor("#22000000"))
        }
        wrapper.addView(divider)
        return wrapper
    }

    private fun showTxDetail(tx: MoneroWallet.TxInfo) {
        val ctx = requireContext()
        val dateStr = if (tx.timestamp > 0L) {
            java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(tx.timestamp * 1000L))
        } else "—"

        val lines = buildString {
            appendLine("Direction : ${if (tx.isIncoming) "Reçu ▲" else "Envoyé ▼"}")
            appendLine("Montant   : ${(if (tx.isIncoming) "+" else "-") + formatXmr(tx.amount)}")
            if (tx.fee > 0L) appendLine("Frais     : ${formatXmr(tx.fee)}")
            appendLine("Statut    : ${when {
                tx.isPending -> "En attente (non confirmé)"
                tx.confirmations < 10 -> "${tx.confirmations} confirmation(s)"
                else -> "Confirmé (${tx.confirmations})"
            }}")
            if (tx.height > 0L) appendLine("Hauteur   : ${tx.height}")
            appendLine("Date      : $dateStr")
            if (tx.label.isNotBlank()) appendLine("Libellé   : ${tx.label}")
            if (tx.paymentId.isNotBlank() && tx.paymentId != "0000000000000000")
                appendLine("Payment ID: ${tx.paymentId}")
            appendLine()
            append("TX ID :\n${tx.txId}")
        }

        val scrollView = android.widget.ScrollView(ctx)
        val tv = TextView(ctx).apply {
            text = lines
            textSize = 13f
            setPadding(64, 32, 64, 16)
            setTextIsSelectable(true)
        }
        scrollView.addView(tv)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Détails de la transaction")
            .setView(scrollView)
            .setPositiveButton("Copier TX ID") { _, _ ->
                val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("TX ID", tx.txId))
                Toast.makeText(ctx, "TX ID copié", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    private fun formatXmr(piconero: Long): String = WalletRepository.formatXmr(piconero)

    private fun setSendMax() {
        val ctx = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            val snapshot = WalletRepository.getSnapshot(ctx)
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                val unlocked = snapshot.unlockedPiconero
                if (unlocked > 0L) {
                    val xmr = "%.6f".format(unlocked / 1_000_000_000_000.0)
                    binding.etSendAmount.setText(xmr)
                }
            }
        }
    }

    private fun confirmAndSend() {
        val address = binding.etSendAddress.text?.toString()?.trim().orEmpty()
        val amountStr = binding.etSendAmount.text?.toString()?.trim().orEmpty()

        if (address.isEmpty()) {
            Toast.makeText(requireContext(), "Adresse requise", Toast.LENGTH_SHORT).show()
            return
        }
        val amountXmr = amountStr.toDoubleOrNull()
        if (amountXmr == null || amountXmr <= 0.0) {
            Toast.makeText(requireContext(), "Montant invalide", Toast.LENGTH_SHORT).show()
            return
        }
        val amountPico = (amountXmr * 1_000_000_000_000.0).toLong()

        // Fee priority picker
        val feeLabels = arrayOf(
            "Automatique", "Lent (×0.2)", "Normal (×1)", "Rapide (×5)", "Le plus rapide (×200)"
        )
        val spinner = android.widget.Spinner(requireContext())
        spinner.adapter = android.widget.ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, feeLabels
        )
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(64, 16, 64, 8)
        }
        val label = TextView(requireContext()).apply {
            text = "Priorité des frais"
            textSize = 13f
            alpha = 0.7f
        }
        container.addView(label)
        container.addView(spinner)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.wallet_send_confirm_title))
            .setMessage(getString(R.string.wallet_send_confirm_msg, formatXmr(amountPico), address))
            .setView(container)
            .setPositiveButton(getString(R.string.wallet_send_btn)) { _, _ ->
                doSend(address, amountPico, spinner.selectedItemPosition)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun doSend(address: String, amountPico: Long, priority: Int = 0) {
        val ctx = requireContext().applicationContext
        binding.btnSendConfirm.isEnabled = false
        lifecycleScope.launch(Dispatchers.IO) {
            val result = MoneroWallet.sendTransaction(address, amountPico, priority, 0)
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.btnSendConfirm.isEnabled = true
                if (result.success) {
                    WalletRepository.invalidateWalletStateCache()
                    Toast.makeText(ctx, getString(R.string.wallet_send_success), Toast.LENGTH_LONG).show()
                    binding.etSendAddress.text?.clear()
                    binding.etSendAmount.text?.clear()
                    binding.etSendNote.text?.clear()
                    showTab(Tab.HOME)
                    renderState()
                } else {
                    Toast.makeText(ctx, getString(R.string.wallet_send_error, result.error), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun copyReceiveAddress() {
        val address = binding.tvReceiveAddress.text?.toString().orEmpty()
        if (address.isBlank()) return
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("XMR Address", address))
        Toast.makeText(requireContext(), "Adresse copiee !", Toast.LENGTH_SHORT).show()
        clipboardClearHandler.postDelayed({
            try { clipboard.clearPrimaryClip() } catch (_: Throwable) {}
        }, 60_000L)
    }

    private fun showTab(tab: Tab) {
        currentTab = tab
        binding.tabHome.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        binding.tabSend.visibility = if (tab == Tab.SEND) View.VISIBLE else View.GONE
        binding.tabReceive.visibility = if (tab == Tab.RECEIVE) View.VISIBLE else View.GONE
        binding.tabTransactions.visibility = if (tab == Tab.TRANSACTIONS) View.VISIBLE else View.GONE

        val primary = ContextCompat.getColor(requireContext(), com.google.android.material.R.color.m3_sys_color_dynamic_light_primary)
        val muted = ContextCompat.getColor(requireContext(), android.R.color.darker_gray)

        updateTabButton(binding.iconTabHome, binding.labelTabHome, tab == Tab.HOME, primary, muted)
        updateTabButton(binding.iconTabSend, binding.labelTabSend, tab == Tab.SEND, primary, muted)
        updateTabButton(binding.iconTabReceive, binding.labelTabReceive, tab == Tab.RECEIVE, primary, muted)
        updateTabButton(binding.iconTabTransactions, binding.labelTabTransactions, tab == Tab.TRANSACTIONS, primary, muted)
    }

    private fun updateTabButton(
        icon: android.widget.ImageView,
        label: TextView,
        selected: Boolean,
        primaryColor: Int,
        mutedColor: Int
    ) {
        val color = if (selected) primaryColor else mutedColor
        icon.imageTintList = android.content.res.ColorStateList.valueOf(color)
        label.setTextColor(color)
    }

    private fun createWallet() {
        val ctx = requireContext().applicationContext

        // Show a loading indicator while we prepare the seed
        binding.btnCreateWallet.isEnabled = false
        binding.tvSyncStatus.text = "Génération de la seed…"

        lifecycleScope.launch(Dispatchers.IO) {
            // Close any open wallet before creating a new one
            if (MoneroWallet.isOpen) MoneroWallet.closeWallet()
            // Delete any existing wallet2 files so createWallet starts fully fresh
            java.io.File(ctx.filesDir, "xmr_wallet").listFiles()?.forEach { it.delete() }
            WalletRepository.invalidateAllCaches()
            // Reset sync tracking for fresh wallet
            lastKnownWalletHeight = -1L; stuckPollCount = 0; hasStoredAfterSync = false
            // Use current daemon tip as restore height so the new wallet syncs instantly.
            // A new wallet has no history, so restoreHeight = current tip is always correct.
            val cachedDaemonH = WalletRepository.getLastKnownDaemonHeight()
            val restoreH = if (cachedDaemonH > 2_100_000L) cachedDaemonH else 2_100_000L
            WalletPreferences.setRestoreHeight(ctx, restoreH)
            // Generate and store the mnemonic — but do NOT enable the wallet yet.
            // The wallet is only activated after the user confirms they saved the seed.
            WalletSeedManager.generateNewMnemonic(ctx)

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.btnCreateWallet.isEnabled = true
                binding.tvSyncStatus.text = ""
                // Navigate to seed backup/confirmation screen.
                // WalletSeedBackupFragment will call WalletPreferences.setWalletEnabled() on confirm,
                // then navigateUp() back here, where renderState() + startSyncPolling() will
                // trigger wallet init automatically.
                findNavController().navigate(R.id.action_walletHome_to_seedBackup)
            }
        }
    }

    private fun promptRestoreSeed() {
        val ctx = requireContext()
        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 16, 48, 8)
        }
        val seedInput = android.widget.EditText(ctx).apply {
            hint = "25 mots Monero (séparés par espaces)"
            minLines = 3
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val heightLabel = android.widget.TextView(ctx).apply {
            text = "Hauteur de restauration (laisser 2100000 si incertain)"
            textSize = 12f
            setPadding(0, 16, 0, 4)
        }
        val heightInput = android.widget.EditText(ctx).apply {
            hint = "2100000"
            setText("2100000")
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
        }
        container.addView(seedInput)
        container.addView(heightLabel)
        container.addView(heightInput)

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Restaurer le wallet")
            .setView(container)
            .setPositiveButton("Restaurer") { _, _ ->
                val mnemonic = seedInput.text?.toString()?.trim().orEmpty()
                val words = mnemonic.split(Regex("\\s+")).filter { it.isNotBlank() }
                if (words.size != 25) {
                    Toast.makeText(ctx, "La seed doit contenir exactement 25 mots (${words.size} detectes)", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }
                val heightStr = heightInput.text?.toString()?.trim().orEmpty()
                val restoreHeight = heightStr.toLongOrNull()?.coerceAtLeast(0L) ?: 2_100_000L

                // Immediately show wallet UI (not setup screen) while sync loads
                binding.setupContainer.visibility = View.GONE
                binding.walletContentContainer.visibility = View.VISIBLE
                binding.tvHomeBalance.text = getString(R.string.wallet_balance_loading)
                binding.tvSyncStatus.text = "Restauration en cours..."
                showTab(Tab.HOME)

                val appCtx = ctx.applicationContext
                lifecycleScope.launch(Dispatchers.IO) {
                    // Close current wallet before restoring new seed
                    if (MoneroWallet.isOpen) MoneroWallet.closeWallet()
                    // Delete old wallet2 files — forces fresh createWallet with correct keys.
                    // Without this, openWallet() reopens the old cached file whose block hash chain
                    // diverges from the daemon at restoreHeight, causing refresh() status=1.
                    java.io.File(appCtx.filesDir, "xmr_wallet").listFiles()?.forEach { it.delete() }
                    WalletRepository.invalidateAllCaches()
                    // Reset sync tracking for fresh restore
                    lastKnownWalletHeight = -1L; stuckPollCount = 0; hasStoredAfterSync = false
                    WalletPreferences.setRestoreHeight(appCtx, restoreHeight)

                    val importOk = WalletSeedManager.importFromMnemonic(appCtx, words.joinToString(" "))

                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext
                        if (!importOk) {
                            // Restore failed — go back to setup
                            binding.setupContainer.visibility = View.VISIBLE
                            binding.walletContentContainer.visibility = View.GONE
                            Toast.makeText(appCtx, "Seed invalide — verifie les 25 mots et le checksum", Toast.LENGTH_LONG).show()
                            return@withContext
                        }
                    }

                    if (!importOk) return@launch

                    WalletPreferences.setWalletEnabled(appCtx, true)
                    WalletPreferences.setWalletCreated(appCtx, true)
                    WalletRepository.invalidateAllCaches()

                    // Full sync — returns when done (may take 5-30s depending on restoreHeight)
                    val snapshot = WalletRepository.getSnapshot(appCtx)

                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext
                        binding.walletSwipeRefresh.isRefreshing = false
                        val showSetup = !snapshot.enabled || !snapshot.hasSeed
                        binding.setupContainer.visibility = if (showSetup) View.VISIBLE else View.GONE
                        binding.walletContentContainer.visibility = if (showSetup) View.GONE else View.VISIBLE
                        binding.tvNodeValue.text = snapshot.nodeStatus.statusLabel
                        val daemonH2 = snapshot.nodeStatus.daemonHeight ?: 0L
                        binding.tvSyncStatus.text = when {
                            snapshot.walletSyncHeight <= 0L -> ""
                            daemonH2 > 0L && snapshot.walletSyncHeight < daemonH2 ->
                                "sync ${snapshot.walletSyncHeight} / $daemonH2…"
                            else -> "h.${snapshot.walletSyncHeight}"
                        }
                        if (!showSetup) {
                            bindWalletContent(snapshot)
                            showTab(Tab.HOME)
                        }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun revealStoredSeed() {
        findNavController().navigate(R.id.action_walletHome_to_seedBackup)
    }

    private fun showDeleteWalletConfirmation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.wallet_delete_confirm_title)
            .setMessage(R.string.wallet_delete_confirm_message)
            .setPositiveButton(R.string.wallet_delete_confirm_btn) { _, _ ->
                val ctx = requireContext().applicationContext
                lifecycleScope.launch(Dispatchers.IO) {
                    WalletRepository.deleteWallet(ctx)
                    withContext(Dispatchers.Main) {
                        if (_binding == null) return@withContext
                        stopSyncPolling()
                        lastKnownWalletHeight = -1L
                        stuckPollCount = 0
                        hasStoredAfterSync = false
                        Toast.makeText(ctx, R.string.wallet_delete_success, Toast.LENGTH_SHORT).show()
                        renderState()
                        startSyncPolling()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        stopSyncPolling()
        clipboardClearHandler.removeCallbacksAndMessages(null)
        super.onDestroyView()
        _binding = null
    }
}
