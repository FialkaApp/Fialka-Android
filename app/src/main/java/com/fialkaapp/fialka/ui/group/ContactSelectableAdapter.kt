/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.ui.group

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fialkaapp.fialka.data.model.Contact
import com.fialkaapp.fialka.databinding.ItemContactSelectableBinding
import com.fialkaapp.fialka.util.QrCodeGenerator

class ContactSelectableAdapter(
    private val onToggle: (Contact, Boolean) -> Unit
) : ListAdapter<Contact, ContactSelectableAdapter.VH>(DIFF) {

    private val selected = mutableSetOf<String>()

    fun getSelectedContacts(allContacts: List<Contact>): List<Contact> =
        allContacts.filter { it.publicKey in selected }

    inner class VH(private val b: ItemContactSelectableBinding) :
        RecyclerView.ViewHolder(b.root) {

        fun bind(contact: Contact) {
            val initial = contact.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            b.tvInitial.text = initial
            b.tvName.text = contact.displayName
            val displayKey = runCatching { QrCodeGenerator.stripX25519Header(contact.publicKey) }.getOrDefault(contact.publicKey)
            b.tvPublicKey.text = displayKey.take(24) + "…"
            b.checkbox.isChecked = contact.publicKey in selected
            b.root.setOnClickListener {
                val nowSelected = contact.publicKey !in selected
                if (nowSelected) selected.add(contact.publicKey)
                else selected.remove(contact.publicKey)
                b.checkbox.isChecked = nowSelected
                onToggle(contact, nowSelected)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemContactSelectableBinding.inflate(
            LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Contact>() {
            override fun areItemsTheSame(a: Contact, b: Contact) = a.publicKey == b.publicKey
            override fun areContentsTheSame(a: Contact, b: Contact) = a == b
        }
    }
}
