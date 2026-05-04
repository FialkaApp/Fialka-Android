/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.ui.conversations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fialkaapp.fialka.data.model.GroupLocal
import com.fialkaapp.fialka.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reuses [ItemConversationBinding] to show groups in the conversations list.
 * The accent strip uses the group initial instead of a contact initial.
 */
class GroupsAdapter(
    private val onClick: (GroupLocal) -> Unit
) : ListAdapter<GroupLocal, GroupsAdapter.VH>(DIFF) {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateFormat = SimpleDateFormat("dd/MM", Locale.getDefault())

    inner class VH(private val b: ItemConversationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(group: GroupLocal) {
            val initial = group.name.firstOrNull()?.uppercaseChar()?.toString() ?: "G"
            b.tvContactInitial.text = initial
            b.tvContactName.text = group.name

            val preview = if (group.lastMessage.isNotEmpty()) group.lastMessage
                          else "${group.memberCount} membres"
            b.tvLastMessage.text = preview

            val now = System.currentTimeMillis()
            val diff = now - group.lastMessageTimestamp
            b.tvTimestamp.text = when {
                diff < 24 * 3600_000L -> timeFormat.format(Date(group.lastMessageTimestamp))
                else -> dateFormat.format(Date(group.lastMessageTimestamp))
            }

            // Unread badge
            if (group.unreadCount > 0) {
                b.tvUnreadBadge.visibility = android.view.View.VISIBLE
                b.tvUnreadBadge.text = if (group.unreadCount > 99) "99+"
                                       else group.unreadCount.toString()
            } else {
                b.tvUnreadBadge.visibility = android.view.View.GONE
            }

            b.root.setOnClickListener { onClick(group) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemConversationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<GroupLocal>() {
            override fun areItemsTheSame(a: GroupLocal, b: GroupLocal) = a.groupId == b.groupId
            override fun areContentsTheSame(a: GroupLocal, b: GroupLocal) = a == b
        }
    }
}
