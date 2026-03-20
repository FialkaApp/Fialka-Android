/*
 * SecureChat — Post-quantum encrypted messenger
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
package com.securechat.ui.conversations

import android.util.TypedValue
import android.view.View
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.securechat.R
import com.securechat.data.model.Conversation
import com.securechat.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ConversationsAdapter(
    private val onClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationsAdapter.ViewHolder>(DiffCallback) {

    companion object {
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        private val dayOfWeekFormat = SimpleDateFormat("EEE", Locale.getDefault())
        private val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.getDefault())

        /** Format timestamp as relative: "HH:mm" today, "Hier", day name this week, or "dd/MM/yy" */
        fun formatRelativeTimestamp(timestamp: Long): String {
            val now = Calendar.getInstance()
            val msg = Calendar.getInstance().apply { timeInMillis = timestamp }

            // Same day → "14:32"
            if (now.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR)) {
                return timeFormat.format(Date(timestamp))
            }

            // Yesterday → "Hier"
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            if (yesterday.get(Calendar.YEAR) == msg.get(Calendar.YEAR) &&
                yesterday.get(Calendar.DAY_OF_YEAR) == msg.get(Calendar.DAY_OF_YEAR)) {
                return "Hier"
            }

            // Within last 7 days → "Lun", "Mar", etc.
            val weekAgo = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -6) }
            if (msg.after(weekAgo)) {
                return dayOfWeekFormat.format(Date(timestamp)).replaceFirstChar { it.uppercase() }
            }

            // Older → "15/03/26"
            return dateFormat.format(Date(timestamp))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemConversationBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(conversation: Conversation) {
            binding.tvContactName.text = conversation.contactDisplayName
            binding.tvContactInitial.text =
                conversation.contactDisplayName.firstOrNull()?.uppercase() ?: "?"

            if (!conversation.accepted) {
                binding.tvLastMessage.text = "⏳ En attente d'acceptation…"
                binding.tvLastMessage.setTextColor(0xFFFF9800.toInt())
            } else {
                binding.tvLastMessage.text = conversation.lastMessage.ifEmpty { "Nouvelle conversation" }
                binding.tvLastMessage.setTextColor(
                    binding.root.context.getColor(R.color.text_secondary)
                )
            }

            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.tvTimestamp.text = formatRelativeTimestamp(conversation.lastMessageTimestamp)

            // Unread badge
            if (conversation.unreadCount > 0) {
                binding.tvUnreadBadge.visibility = View.VISIBLE
                binding.tvUnreadBadge.text = if (conversation.unreadCount > 99) "99+" else conversation.unreadCount.toString()
                val tv = TypedValue()
                binding.root.context.theme.resolveAttribute(R.attr.colorBadge, tv, true)
                binding.tvTimestamp.setTextColor(tv.data)
            } else {
                binding.tvUnreadBadge.visibility = View.GONE
                binding.tvTimestamp.setTextColor(binding.root.context.getColor(R.color.text_secondary))
            }

            binding.root.setOnClickListener { onClick(conversation) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(a: Conversation, b: Conversation) =
            a.conversationId == b.conversationId

        override fun areContentsTheSame(a: Conversation, b: Conversation) = a == b
    }
}
