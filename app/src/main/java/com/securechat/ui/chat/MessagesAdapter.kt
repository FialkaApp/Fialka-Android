package com.securechat.ui.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.securechat.data.model.MessageLocal
import com.securechat.databinding.ItemMessageReceivedBinding
import com.securechat.databinding.ItemMessageSentBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessagesAdapter : ListAdapter<MessageLocal, RecyclerView.ViewHolder>(DiffCallback) {

    companion object {
        private const val VIEW_TYPE_SENT = 0
        private const val VIEW_TYPE_RECEIVED = 1
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isMine) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_SENT) {
            val binding = ItemMessageSentBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            SentViewHolder(binding)
        } else {
            val binding = ItemMessageReceivedBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            ReceivedViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = getItem(position)
        when (holder) {
            is SentViewHolder -> holder.bind(message)
            is ReceivedViewHolder -> holder.bind(message)
        }
    }

    class SentViewHolder(
        private val binding: ItemMessageSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageLocal) {
            binding.tvMessageSent.text = message.plaintext
            binding.tvTimeSent.text = timeFormat.format(Date(message.timestamp))
        }
    }

    class ReceivedViewHolder(
        private val binding: ItemMessageReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageLocal) {
            binding.tvMessageReceived.text = message.plaintext
            binding.tvTimeReceived.text = timeFormat.format(Date(message.timestamp))
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<MessageLocal>() {
        override fun areItemsTheSame(a: MessageLocal, b: MessageLocal) =
            a.localId == b.localId

        override fun areContentsTheSame(a: MessageLocal, b: MessageLocal) = a == b
    }
}
