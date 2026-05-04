/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.ui.conversations

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView

/**
 * Simple view-based adapter for ViewPager2.
 * Each position holds one pre-inflated page View.
 * Returns a unique viewType per position so RecyclerView never recycles
 * a page into a different slot.
 */
class ConversationsPagerAdapter(
    private val pages: List<View>
) : RecyclerView.Adapter<ConversationsPagerAdapter.VH>() {

    inner class VH(val container: FrameLayout) : RecyclerView.ViewHolder(container)

    override fun getItemCount() = pages.size
    override fun getItemViewType(position: Int) = position   // Prevent cross-page recycling

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val frame = FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return VH(frame)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val view = pages[position]
        (view.parent as? ViewGroup)?.removeView(view)
        holder.container.removeAllViews()
        holder.container.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }
}
