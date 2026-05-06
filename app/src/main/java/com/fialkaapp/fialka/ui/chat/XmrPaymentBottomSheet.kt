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
package com.fialkaapp.fialka.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.wallet.WalletRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bottom sheet for Monero XMR payment actions in chat.
 *
 * Three options:
 *  1. Share own receive address (sends XMR_ADDR message)
 *  2. Request payment (sends XMR_REQUEST message with optional amount + note)
 *  3. Send XMR (opens dialog → calls wallet send → sends XMR_SENT message)
 *
 * Callbacks are set by ChatFragment before show().
 * receiveAddress is passed via newInstance() factory method.
 */
class XmrPaymentBottomSheet : BottomSheetDialogFragment() {

    /** Called when user chooses "Partager mon adresse" — provides their own XMR address. */
    var onShareAddress: ((address: String) -> Unit)? = null

    /** Called when user creates a payment request — provides address, optional amount and note. */
    var onRequestPayment: ((address: String, amount: String, note: String) -> Unit)? = null

    /** Called when user wants to send XMR — provides destination address, amount, and fee priority. */
    var onSendPayment: ((toAddress: String, amountXmr: String, priority: Int) -> Unit)? = null

    private var receiveAddress: String = ""
    private var contactName: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_xmr_payment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        receiveAddress = arguments?.getString(ARG_ADDRESS) ?: ""
        contactName = arguments?.getString(ARG_CONTACT) ?: ""

        view.findViewById<View>(R.id.optionXmrShare).setOnClickListener {
            dismiss()
            handleShareAddress()
        }

        view.findViewById<View>(R.id.optionXmrRequest).setOnClickListener {
            showRequestDialog()
        }

        view.findViewById<View>(R.id.optionXmrSend).setOnClickListener {
            showSendDialog()
        }
    }

    // ── Option 1: Share address ──

    private fun handleShareAddress() {
        if (receiveAddress.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.xmr_wallet_not_ready), Toast.LENGTH_SHORT).show()
            return
        }
        onShareAddress?.invoke(receiveAddress)
    }

    // ── Option 2: Request payment ──

    private fun showRequestDialog() {
        if (receiveAddress.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.xmr_wallet_not_ready), Toast.LENGTH_SHORT).show()
            dismiss()
            return
        }

        // Capture context + strings NOW, before dismiss() — avoids IllegalStateException in callbacks
        val ctx = requireContext()
        val titleStr = getString(R.string.xmr_request_dialog_title)
        val sendStr = getString(R.string.wallet_send_btn)

        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_xmr_request, null)
        val etAmount = dialogView.findViewById<EditText>(R.id.etXmrAmount)
        val etNote = dialogView.findViewById<EditText>(R.id.etXmrNote)

        dismiss() // hide bottom sheet before showing dialog

        MaterialAlertDialogBuilder(ctx)
            .setTitle(titleStr)
            .setView(dialogView)
            .setPositiveButton(sendStr) { _, _ ->
                val amount = etAmount.text.toString().trim()
                val note = etNote.text.toString().trim()
                onRequestPayment?.invoke(receiveAddress, amount, note)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ── Option 3: Send XMR ──

    private fun showSendDialog() {
        // Capture context + strings NOW before dismiss() — avoids IllegalStateException in callbacks
        val ctx = requireContext()
        val titleStr = getString(R.string.xmr_send_dialog_title)
        val sendStr = getString(R.string.wallet_send_btn)
        val confirmTitle = getString(R.string.xmr_send_confirm_title)
        val confirmMsgTemplate = getString(R.string.xmr_send_confirm_msg)

        val dialogView = LayoutInflater.from(ctx).inflate(R.layout.dialog_xmr_send, null)
        val etAddress = dialogView.findViewById<EditText>(R.id.etXmrToAddress)
        val etAmount = dialogView.findViewById<EditText>(R.id.etXmrSendAmount)
        val tvBalance = dialogView.findViewById<TextView>(R.id.tvXmrSendBalance)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerXmrFee)

        // Fee priority options (matches MoneroWallet priority constants)
        val feeLabels = arrayOf(ctx.getString(com.fialkaapp.fialka.R.string.fee_automatic), ctx.getString(com.fialkaapp.fialka.R.string.fee_slow), ctx.getString(com.fialkaapp.fialka.R.string.fee_normal), ctx.getString(com.fialkaapp.fialka.R.string.fee_fast), ctx.getString(com.fialkaapp.fialka.R.string.fee_fastest))
        spinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, feeLabels)

        // Load balance using a scope independent of fragment lifecycle
        // (dismiss() destroys lifecycleScope BEFORE the coroutine can update the TextView)
        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            val balanceText = withContext(Dispatchers.IO) {
                try {
                    val snap = WalletRepository.getSnapshot(ctx)
                    val unlocked = snap.unlockedPiconero
                    val total = snap.balancePiconero
                    if (unlocked < 0) ctx.getString(com.fialkaapp.fialka.R.string.wallet_balance_unavailable)
                    else ctx.getString(com.fialkaapp.fialka.R.string.wallet_available, WalletRepository.formatXmr(unlocked)) +
                         if (total != unlocked) ctx.getString(com.fialkaapp.fialka.R.string.wallet_total, WalletRepository.formatXmr(total)) else ""
                } catch (_: Exception) { ctx.getString(com.fialkaapp.fialka.R.string.wallet_balance_unavailable_short) }
            }
            tvBalance?.text = balanceText
        }

        dismiss() // hide bottom sheet before showing dialog

        MaterialAlertDialogBuilder(ctx)
            .setTitle(titleStr)
            .setView(dialogView)
            .setPositiveButton(sendStr) { _, _ ->
                val toAddress = etAddress.text.toString().trim()
                val amount = etAmount.text.toString().trim()
                val priority = spinner.selectedItemPosition
                if (toAddress.isEmpty() || amount.isEmpty()) return@setPositiveButton
                // Confirmation dialog — use captured ctx (fragment already detached at this point)
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(confirmTitle)
                    .setMessage(confirmMsgTemplate.format(amount, toAddress) +
                        "\n\nFrais : ${feeLabels[priority]}")
                    .setCancelable(false)
                    .setPositiveButton(sendStr) { _, _ ->
                        onSendPayment?.invoke(toAddress, amount, priority)
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        const val TAG = "XmrPaymentBottomSheet"
        private const val ARG_ADDRESS = "receive_address"
        private const val ARG_CONTACT = "contact_name"

        fun newInstance(receiveAddress: String, contactName: String): XmrPaymentBottomSheet {
            return XmrPaymentBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_ADDRESS, receiveAddress)
                    putString(ARG_CONTACT, contactName)
                }
            }
        }
    }
}
