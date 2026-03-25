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
package com.fialkaapp.fialka.ui.conversations

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fialkaapp.fialka.data.model.ContactRequest
import com.fialkaapp.fialka.databinding.ItemContactRequestBinding

class ContactRequestsAdapter(
    private val onAccept: (ContactRequest) -> Unit,
    private val onDecline: (ContactRequest) -> Unit
) : ListAdapter<ContactRequest, ContactRequestsAdapter.ViewHolder>(DiffCallback) {

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

        fun bind(request: ContactRequest) {
            binding.tvRequestName.text = request.senderDisplayName
            binding.tvRequestInitial.text =
                request.senderDisplayName.firstOrNull()?.uppercase() ?: "?"

            binding.btnAccept.setOnClickListener { onAccept(request) }
            binding.btnDecline.setOnClickListener { onDecline(request) }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<ContactRequest>() {
        override fun areItemsTheSame(
            a: ContactRequest,
            b: ContactRequest
        ) = a.conversationId == b.conversationId

        override fun areContentsTheSame(
            a: ContactRequest,
            b: ContactRequest
        ) = a == b
    }
}
