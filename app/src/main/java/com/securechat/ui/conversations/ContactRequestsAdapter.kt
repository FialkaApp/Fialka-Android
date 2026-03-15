package com.securechat.ui.conversations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.securechat.data.remote.FirebaseRelay
import com.securechat.databinding.ItemContactRequestBinding

class ContactRequestsAdapter(
    private val onAccept: (FirebaseRelay.ContactRequest) -> Unit,
    private val onDecline: (FirebaseRelay.ContactRequest) -> Unit
) : ListAdapter<FirebaseRelay.ContactRequest, ContactRequestsAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemContactRequestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemContactRequestBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(request: FirebaseRelay.ContactRequest) {
            binding.tvRequestName.text = request.senderDisplayName
            binding.tvRequestInitial.text =
                request.senderDisplayName.firstOrNull()?.uppercase() ?: "?"

            binding.btnAccept.setOnClickListener { onAccept(request) }
            binding.btnDecline.setOnClickListener { onDecline(request) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<FirebaseRelay.ContactRequest>() {
        override fun areItemsTheSame(
            a: FirebaseRelay.ContactRequest,
            b: FirebaseRelay.ContactRequest
        ) = a.conversationId == b.conversationId

        override fun areContentsTheSame(
            a: FirebaseRelay.ContactRequest,
            b: FirebaseRelay.ContactRequest
        ) = a == b
    }
}
