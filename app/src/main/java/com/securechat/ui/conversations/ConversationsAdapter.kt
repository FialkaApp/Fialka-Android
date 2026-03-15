package com.securechat.ui.conversations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.securechat.data.model.Conversation
import com.securechat.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationsAdapter(
    private val onClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationsAdapter.ViewHolder>(DiffCallback) {

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

            binding.tvLastMessage.text = conversation.lastMessage.ifEmpty { "Nouvelle conversation" }

            val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            binding.tvTimestamp.text = dateFormat.format(Date(conversation.lastMessageTimestamp))

            binding.root.setOnClickListener { onClick(conversation) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(a: Conversation, b: Conversation) =
            a.conversationId == b.conversationId

        override fun areContentsTheSame(a: Conversation, b: Conversation) = a == b
    }
}
