/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.ui.wallet

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.data.model.WalletTransaction
import com.fialkaapp.fialka.databinding.ItemWalletTransactionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class WalletTransactionAdapter(
    private val maxItems: Int = Int.MAX_VALUE
) : ListAdapter<WalletTransaction, WalletTransactionAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<WalletTransaction>() {
            override fun areItemsTheSame(a: WalletTransaction, b: WalletTransaction) =
                a.txId == b.txId
            override fun areContentsTheSame(a: WalletTransaction, b: WalletTransaction) =
                a == b
        }

        private val DATE_FMT = SimpleDateFormat("d MMM", Locale.FRENCH)
        private val DATETIME_FMT = SimpleDateFormat("d MMM HH:mm", Locale.FRENCH)
    }

    override fun getItemCount(): Int = minOf(super.getItemCount(), maxItems)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemWalletTransactionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class VH(private val b: ItemWalletTransactionBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(tx: WalletTransaction) {
            val ctx = b.root.context

            // ── Amount ────────────────────────────────────────────────────────
            val xmr = WalletTransaction.piconeroToXmr(tx.amountPiconero)
                .trimEnd('0').trimEnd('.')
                .ifEmpty { "0" }

            if (tx.direction == WalletTransaction.DIRECTION_INCOMING) {
                b.tvTxAmount.text = "+$xmr XMR"
                b.tvTxAmount.setTextColor(ContextCompat.getColor(ctx, R.color.green_verified))
                b.ivDirection.setImageResource(R.drawable.ic_receive)
                b.ivDirection.setColorFilter(ContextCompat.getColor(ctx, R.color.green_verified))
            } else {
                b.tvTxAmount.text = "−$xmr XMR"
                b.tvTxAmount.setTextColor(ContextCompat.getColor(ctx, R.color.orange_warning))
                b.ivDirection.setImageResource(R.drawable.ic_wallet)
                b.ivDirection.setColorFilter(ContextCompat.getColor(ctx, R.color.orange_warning))
            }

            // ── Short txId ────────────────────────────────────────────────────
            b.tvTxId.text = if (tx.txId.length > 16) tx.txId.take(8) + "…" + tx.txId.takeLast(8)
                            else tx.txId

            // ── Status badge ──────────────────────────────────────────────────
            val (statusText, statusBg) = when (tx.status) {
                WalletTransaction.STATUS_PENDING    ->
                    ctx.getString(R.string.wallet_tx_pending) to R.drawable.bg_circle_option_orange

                WalletTransaction.STATUS_CONFIRMING ->
                    ctx.getString(R.string.wallet_tx_confirming) to R.drawable.bg_circle_option_blue

                WalletTransaction.STATUS_CONFIRMED  ->
                    ctx.getString(R.string.wallet_tx_confirmed) to R.drawable.bg_circle_option_green

                else -> // FAILED
                    ctx.getString(R.string.wallet_tx_failed) to R.drawable.bg_circle_option_red
            }
            b.tvTxStatus.text = statusText
            b.tvTxStatus.setBackgroundResource(statusBg)

            // ── Date ──────────────────────────────────────────────────────────
            val date = Date(tx.timestamp)
            b.tvTxDate.text = DATETIME_FMT.format(date)
        }
    }
}
