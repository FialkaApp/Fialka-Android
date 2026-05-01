/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.fialkaapp.fialka.ui.donation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.crypto.FialkaNative
import com.fialkaapp.fialka.databinding.FragmentDonationBinding
import com.fialkaapp.fialka.donation.DonationKeys
import com.fialkaapp.fialka.donation.DonationTxStore
import com.fialkaapp.fialka.wallet.MoneroWallet
import com.fialkaapp.fialka.wallet.WalletPreferences
import com.fialkaapp.fialka.wallet.WalletRepository
import com.fialkaapp.fialka.wallet.WalletSnapshot
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

class DonationFragment : Fragment() {

    private var _binding: FragmentDonationBinding? = null
    private val binding get() = _binding!!

    private var donationAddress: String? = null
    private var lastSnapshot: WalletSnapshot? = null

    private val syncHandler = Handler(Looper.getMainLooper())
    private var syncRunnable: Runnable? = null

    // Fee labels matching WalletHomeFragment priorities
    private val feeLabels = arrayOf(
        "Automatique", "Lent (Ã—0.2)", "Normal (Ã—1)", "Rapide (Ã—5)", "Le plus rapide (Ã—200)"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDonationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.donSwipeRefresh.setOnRefreshListener {
            WalletRepository.invalidateAllCaches()
            renderState()
        }

        // Fee spinner (exposed dropdown)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, feeLabels)
        binding.spinnerDonFee.setAdapter(adapter)
        binding.spinnerDonFee.setText(feeLabels[0], false)

        binding.btnCopyAddress.setOnClickListener { copyAddress() }
        binding.btnShareAddress.setOnClickListener { shareAddress() }
        binding.btnDonMax.setOnClickListener { setSendMax() }
        binding.btnDonConfirm.setOnClickListener { onConfirmClicked() }
        binding.btnSetupWallet.setOnClickListener {
            findNavController().navigate(R.id.action_donation_to_walletHome)
        }

        deriveAddressAndQr()
        renderState()
    }

    override fun onResume() {
        super.onResume()
        startSyncPolling()
    }

    override fun onPause() {
        super.onPause()
        stopSyncPolling()
    }

    // â”€â”€ Sync polling (while wallet not synced) â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun startSyncPolling() {
        stopSyncPolling()
        val r = object : Runnable {
            override fun run() {
                if (_binding == null) return
                val synced = MoneroWallet.isOpen && MoneroWallet.isSynchronized()
                if (!synced) {
                    WalletRepository.invalidateWalletStateCache()
                    renderState()
                    syncHandler.postDelayed(this, 3_000L)
                }
            }
        }
        syncRunnable = r
        syncHandler.postDelayed(r, 3_000L)
    }

    private fun stopSyncPolling() {
        syncRunnable?.let { syncHandler.removeCallbacks(it) }
        syncRunnable = null
    }

    // â”€â”€ State rendering â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun renderState() {
        val ctx = requireContext().applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            val snapshot = WalletRepository.getSnapshot(ctx)
            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                lastSnapshot = snapshot
                binding.donSwipeRefresh.isRefreshing = false
                bindBalanceCard(snapshot)
                bindSendCard(snapshot)
                bindTxHistory(snapshot)
                // Sync bar
                val daemonH = snapshot.nodeStatus.daemonHeight ?: 0L
                binding.tvDonNodeStatus.text = snapshot.nodeStatus.statusLabel
                binding.tvDonSyncStatus.text = when {
                    snapshot.walletSyncHeight <= 0L -> ""
                    daemonH > 0L && snapshot.walletSyncHeight < daemonH ->
                        "sync ${snapshot.walletSyncHeight} / $daemonH..."
                    else -> "h.${snapshot.walletSyncHeight}"
                }
                binding.layoutSyncBar.isVisible = snapshot.enabled && snapshot.hasSeed
            }
        }
    }

    private fun bindBalanceCard(snapshot: WalletSnapshot) {
        if (!snapshot.enabled || !snapshot.hasSeed) {
            binding.tvDonBalance.text = "-- XMR"
            binding.tvDonUnlocked.text = "Wallet non configuré"
        } else {
            binding.tvDonBalance.text = WalletRepository.formatXmr(snapshot.balancePiconero)
            binding.tvDonUnlocked.text = if (
                snapshot.unlockedPiconero >= 0L &&
                snapshot.unlockedPiconero != snapshot.balancePiconero
            ) {
                "disponible : ${WalletRepository.formatXmr(snapshot.unlockedPiconero)}"
            } else ""
        }
    }

    private fun bindSendCard(snapshot: WalletSnapshot) {
        val hasWallet = snapshot.enabled && snapshot.hasSeed
        binding.layoutSendLoading.isVisible = false
        binding.layoutNoWallet.isVisible = !hasWallet
        binding.layoutSendForm.isVisible = hasWallet

        if (hasWallet) {
            val unlocked = snapshot.unlockedPiconero
            binding.tvSendAvailable.text = "Disponible : ${WalletRepository.formatXmr(unlocked)}"
        }
    }

    private fun bindTxHistory(snapshot: WalletSnapshot) {
        val accountId = CryptoManager.getAccountId() ?: ""
        val donationTxIds = if (accountId.isNotEmpty()) {
            DonationTxStore.getTxIds(requireContext(), accountId)
        } else emptySet()

        // Show only outgoing txs that were sent as donations from this device.
        // Txs from external wallets are not visible here (Monero privacy: no server scan).
        val donationTxs = snapshot.transactions
            .filter { it.direction == 1 && it.txId in donationTxIds }
            .sortedByDescending { it.timestamp }

        binding.containerTxHistory.removeAllViews()
        if (donationTxs.isEmpty()) {
            binding.tvNoTx.isVisible = true
        } else {
            binding.tvNoTx.isVisible = false
            for (tx in donationTxs) {
                binding.containerTxHistory.addView(buildTxRow(tx))
            }
        }
    }

    // â”€â”€ Address derivation â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun deriveAddressAndQr() {
        binding.layoutAddressLoading.isVisible = true
        binding.layoutAddressContent.isVisible = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val accountId = CryptoManager.getAccountId() ?: return@runCatching null
                    FialkaNative.xmrDeriveDonationSubaddress(
                        DonationKeys.spendPubBytes,
                        DonationKeys.viewPrivBytes,
                        accountId.toByteArray(Charsets.UTF_8),
                        DonationKeys.NETWORK_TYPE
                    ).toString(Charsets.UTF_8)
                }.getOrNull()
            }

            if (_binding == null) return@launch

            if (result.isNullOrEmpty()) {
                binding.layoutAddressLoading.isVisible = false
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Adresse indisponible")
                    .setMessage("Impossible de générer l'adresse de don. Vérifiez que votre identité Fialka est configurée.")
                    .setPositiveButton("OK", null)
                    .show()
                return@launch
            }

            donationAddress = result

            val qrBitmap = withContext(Dispatchers.Default) {
                runCatching { generateQrBitmap(result) }.getOrNull()
            }

            if (_binding == null) return@launch

            binding.tvDonationAddress.text = result
            qrBitmap?.let { binding.ivQrCode.setImageBitmap(it) }
            binding.layoutAddressLoading.isVisible = false
            binding.layoutAddressContent.isVisible = true
        }
    }

    private fun generateQrBitmap(content: String): Bitmap {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 512, 512)
        val bmp = BarcodeEncoder().createBitmap(bitMatrix)
        val result = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bmp, 0f, 0f, null)
        return result
    }

    // â”€â”€ Send actions â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun setSendMax() {
        val unlocked = lastSnapshot?.unlockedPiconero ?: return
        if (unlocked > 0L) {
            binding.etDonAmount.setText("%.6f".format(unlocked / 1_000_000_000_000.0))
        }
    }

    private fun onConfirmClicked() {
        val addr = donationAddress
        if (addr.isNullOrEmpty()) {
            Toast.makeText(requireContext(), "L'adresse de don n'est pas encore disponible.", Toast.LENGTH_SHORT).show()
            return
        }

        val amountStr = binding.etDonAmount.text?.toString()?.trim().orEmpty()
        val amountXmr = amountStr.toDoubleOrNull()
        if (amountXmr == null || amountXmr <= 0.0) {
            binding.etDonAmount.error = "Montant invalide"
            return
        }
        val amountPico = (amountXmr * 1_000_000_000_000.0).toLong()
        val feeLabel = binding.spinnerDonFee.text?.toString() ?: feeLabels[0]
        val priority = feeLabels.indexOf(feeLabel).coerceAtLeast(0)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Confirmer le don")
            .setMessage(
                "Vous allez envoyer\n\n" +
                WalletRepository.formatXmr(amountPico) +
                "\n\nPriorité des frais : $feeLabel\n\n" +
                "Adresse :\n${addr.take(32)}…"
            )
            .setPositiveButton("Envoyer") { _, _ ->
                doSend(addr, amountPico, priority)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun doSend(address: String, amountPico: Long, priority: Int) {
        binding.btnDonConfirm.isEnabled = false
        binding.btnDonMax.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val result = MoneroWallet.sendTransaction(address, amountPico, priority, 0)

            // Resolve the txId on IO — getFirstTxIdJ() sometimes returns null right after
            // commit(), so we fallback to the most recent untracked outgoing tx in history.
            // Both MoneroWallet.getHistory() (JNI) and DonationTxStore (SharedPrefs) are
            // safe to call on IO.
            var resolvedTxId = result.txId
            if (result.success && resolvedTxId.isEmpty()) {
                val accountId = CryptoManager.getAccountId()
                if (!accountId.isNullOrEmpty()) {
                    val known = DonationTxStore.getTxIds(requireContext(), accountId)
                    resolvedTxId = MoneroWallet.getHistory()
                        .filter { it.direction == 1 && it.txId !in known }
                        .maxByOrNull { it.timestamp }
                        ?.txId ?: ""
                }
            }

            withContext(Dispatchers.Main) {
                if (_binding == null) return@withContext
                binding.btnDonConfirm.isEnabled = true
                binding.btnDonMax.isEnabled = true
                if (result.success) {
                    // Persist txId so history shows this donation specifically.
                    val accountId = CryptoManager.getAccountId()
                    if (!accountId.isNullOrEmpty() && resolvedTxId.isNotEmpty()) {
                        DonationTxStore.saveTxId(requireContext(), accountId, resolvedTxId)
                    }
                    WalletRepository.invalidateWalletStateCache()
                    binding.etDonAmount.text?.clear()
                    Toast.makeText(
                        requireContext(),
                        "Don envoy\u00e9 ! Merci pour votre soutien \u2764\ufe0f",
                        Toast.LENGTH_LONG
                    ).show()
                    renderState()
                } else {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Envoi échoué")
                        .setMessage(result.error ?: "Erreur inconnue")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }

    // â”€â”€ Copy / Share â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun copyAddress() {
        val addr = donationAddress ?: return
        val clipboard = requireContext().getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText("XMR Donation Address", addr))
        Toast.makeText(requireContext(), getString(R.string.donation_copied), Toast.LENGTH_SHORT).show()
    }

    private fun shareAddress() {
        val addr = donationAddress ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, addr)
            putExtra(Intent.EXTRA_SUBJECT, "Mon adresse XMR Fialka")
        }
        startActivity(Intent.createChooser(intent, getString(R.string.donation_share)))
    }

    // â”€â”€ TX row builder â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€

    private fun buildTxRow(tx: MoneroWallet.TxInfo): View {
        val ctx = requireContext()

        val wrapper = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true
            isFocusable = true
            with(android.util.TypedValue()) {
                ctx.theme.resolveAttribute(android.R.attr.selectableItemBackground, this, true)
                setBackgroundResource(resourceId)
            }
        }

        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 12, 0, 12)
        }

        val leftCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val dateStr = if (tx.timestamp > 0L) {
            DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(tx.timestamp * 1000L))
        } else "En attente"

        val status = when {
            tx.isPending -> "En attente"
            tx.confirmations < 10 -> "${tx.confirmations} confirmation(s)"
            else -> "Confirmé"
        }

        leftCol.addView(TextView(ctx).apply {
            text = dateStr
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, android.R.color.tab_indicator_text))
        })
        leftCol.addView(TextView(ctx).apply {
            text = status
            textSize = 11f
            setTextColor(ContextCompat.getColor(ctx, android.R.color.darker_gray))
        })

        val amountLabel = TextView(ctx).apply {
            text = "- ${WalletRepository.formatXmr(tx.amount)}"
            textSize = 14f
            setTextColor(Color.parseColor("#1565C0"))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }

        row.addView(leftCol)
        row.addView(amountLabel)

        val divider = View(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
            setBackgroundColor(Color.parseColor("#22000000"))
        }

        wrapper.addView(row)
        wrapper.addView(divider)

        // Tap â†’ show TX detail dialog
        wrapper.setOnClickListener { showTxDetail(tx) }

        return wrapper
    }

    private fun showTxDetail(tx: MoneroWallet.TxInfo) {
        val ctx = requireContext()
        val dateStr = if (tx.timestamp > 0L) {
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(tx.timestamp * 1000L))
        } else "—"

        val text = buildString {
            appendLine("Montant   : - ${WalletRepository.formatXmr(tx.amount)}")
            if (tx.fee > 0L) appendLine("Frais     : ${WalletRepository.formatXmr(tx.fee)}")
            appendLine("Statut    : ${when {
                tx.isPending -> "En attente"
                tx.confirmations < 10 -> "${tx.confirmations} confirmation(s)"
                else -> "Confirmé (${tx.confirmations})"
            }}")
            if (tx.height > 0L) appendLine("Hauteur   : ${tx.height}")
            appendLine("Date      : $dateStr")
            appendLine()
            append("TX ID :\n${tx.txId}")
        }

        val tv = TextView(ctx).apply {
            this.text = text
            textSize = 13f
            setPadding(64, 32, 64, 16)
            setTextIsSelectable(true)
        }
        val sv = android.widget.ScrollView(ctx).also { it.addView(tv) }

        MaterialAlertDialogBuilder(ctx)
            .setTitle("Détails de la transaction")
            .setView(sv)
            .setPositiveButton("Copier TX ID") { _, _ ->
                val clipboard = ctx.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("TX ID", tx.txId))
                Toast.makeText(ctx, "TX ID copié", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Fermer", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

