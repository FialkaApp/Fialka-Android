package com.securechat.ui.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.securechat.R
import com.securechat.data.model.MessageLocal
import com.securechat.databinding.ItemMessageReceivedBinding
import com.securechat.databinding.ItemMessageSentBinding
import com.securechat.util.EphemeralManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wrapper to represent either a chat message or a "new messages" divider.
 */
sealed class ChatItem {
    data class Message(val message: MessageLocal) : ChatItem()
    object UnreadDivider : ChatItem()
}

class MessagesAdapter : ListAdapter<ChatItem, RecyclerView.ViewHolder>(ChatItemDiffCallback) {

    companion object {
        private const val VIEW_TYPE_SENT = 0
        private const val VIEW_TYPE_RECEIVED = 1
        private const val VIEW_TYPE_UNREAD_DIVIDER = 2
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ChatItem.UnreadDivider -> VIEW_TYPE_UNREAD_DIVIDER
            is ChatItem.Message -> if (item.message.isMine) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val binding = ItemMessageSentBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                SentViewHolder(binding)
            }
            VIEW_TYPE_UNREAD_DIVIDER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_unread_divider, parent, false)
                UnreadDividerViewHolder(view)
            }
            else -> {
                val binding = ItemMessageReceivedBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                ReceivedViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SentViewHolder -> {
                val msg = (getItem(position) as ChatItem.Message).message
                holder.bind(msg)
            }
            is ReceivedViewHolder -> {
                val msg = (getItem(position) as ChatItem.Message).message
                holder.bind(msg)
            }
            is UnreadDividerViewHolder -> { /* static view, nothing to bind */ }
        }
    }

    class SentViewHolder(
        private val binding: ItemMessageSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageLocal) {
            binding.tvMessageSent.text = message.plaintext
            binding.tvTimeSent.text = timeFormat.format(Date(message.timestamp))

            // Ephemeral indicator
            if (message.ephemeralDuration > 0) {
                binding.tvEphemeralSent.visibility = View.VISIBLE
                binding.tvEphemeralSent.text = "⏱ ${EphemeralManager.getShortLabel(message.ephemeralDuration)}"
            } else {
                binding.tvEphemeralSent.visibility = View.GONE
            }
        }
    }

    class ReceivedViewHolder(
        private val binding: ItemMessageReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageLocal) {
            binding.tvMessageReceived.text = message.plaintext
            binding.tvTimeReceived.text = timeFormat.format(Date(message.timestamp))

            // Ephemeral indicator
            if (message.ephemeralDuration > 0) {
                binding.tvEphemeralReceived.visibility = View.VISIBLE
                binding.tvEphemeralReceived.text = "⏱ ${EphemeralManager.getShortLabel(message.ephemeralDuration)}"
            } else {
                binding.tvEphemeralReceived.visibility = View.GONE
            }
        }
    }

    class UnreadDividerViewHolder(view: View) : RecyclerView.ViewHolder(view)

    object ChatItemDiffCallback : DiffUtil.ItemCallback<ChatItem>() {
        override fun areItemsTheSame(a: ChatItem, b: ChatItem): Boolean {
            if (a is ChatItem.UnreadDivider && b is ChatItem.UnreadDivider) return true
            if (a is ChatItem.Message && b is ChatItem.Message) return a.message.localId == b.message.localId
            return false
        }

        override fun areContentsTheSame(a: ChatItem, b: ChatItem): Boolean = a == b
    }
}
