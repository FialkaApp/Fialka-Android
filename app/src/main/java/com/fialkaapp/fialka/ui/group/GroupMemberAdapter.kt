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
import com.fialkaapp.fialka.data.model.GroupLocal
import com.fialkaapp.fialka.data.model.GroupMember
import com.fialkaapp.fialka.databinding.ItemGroupMemberBinding
import com.fialkaapp.fialka.util.QrCodeGenerator
import com.google.android.material.button.MaterialButton

class GroupMemberAdapter(
    private val myPublicKey: String,
    private val myRole: String,
    private val onKick: (GroupMember) -> Unit,
    private val onPromote: (GroupMember) -> Unit,
    private val onDemote: (GroupMember) -> Unit = {}
) : ListAdapter<GroupMember, GroupMemberAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemGroupMemberBinding) : RecyclerView.ViewHolder(b.root) {

        fun bind(member: GroupMember) {
            val initial = member.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            b.tvMemberInitial.text = initial
            b.tvMemberName.text = if (member.publicKey == myPublicKey)
                "${member.displayName} (moi)" else member.displayName
            val displayKey = runCatching { QrCodeGenerator.stripX25519Header(member.publicKey) }.getOrDefault(member.publicKey)
            b.tvMemberKey.text = displayKey.take(20) + "…"

            // Role badge
            when (member.role) {
                GroupLocal.ROLE_OWNER -> {
                    b.tvRoleBadge.visibility = android.view.View.VISIBLE
                    b.tvRoleBadge.text = "CRÉATEUR"
                    b.btnKick.visibility = android.view.View.GONE
                }
                GroupLocal.ROLE_ADMIN -> {
                    b.tvRoleBadge.visibility = android.view.View.VISIBLE
                    b.tvRoleBadge.text = "ADMIN"
                    // Admins can be kicked only by owner
                    showKickIfAllowed(member)
                }
                else -> {
                    b.tvRoleBadge.visibility = android.view.View.GONE
                    showKickIfAllowed(member)
                }
            }

            // Long-press: owner can promote MEMBER or demote ADMIN
            b.root.setOnLongClickListener {
                if (myRole == GroupLocal.ROLE_OWNER && member.publicKey != myPublicKey) {
                    when (member.role) {
                        GroupLocal.ROLE_MEMBER -> { onPromote(member); true }
                        GroupLocal.ROLE_ADMIN  -> { onDemote(member); true }
                        else -> false
                    }
                } else false
            }
        }

        private fun showKickIfAllowed(member: GroupMember) {
            val canKick = (myRole == GroupLocal.ROLE_OWNER || myRole == GroupLocal.ROLE_ADMIN)
                && member.publicKey != myPublicKey
            if (canKick) {
                b.btnKick.visibility = android.view.View.VISIBLE
                // Keep role badge visible for admins; only hide for plain members
                if (member.role == GroupLocal.ROLE_MEMBER) {
                    b.tvRoleBadge.visibility = android.view.View.GONE
                }
                b.btnKick.setOnClickListener { onKick(member) }
            } else {
                b.btnKick.visibility = android.view.View.GONE
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemGroupMemberBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(getItem(position))

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<GroupMember>() {
            override fun areItemsTheSame(a: GroupMember, b: GroupMember) =
                a.groupId == b.groupId && a.publicKey == b.publicKey
            override fun areContentsTheSame(a: GroupMember, b: GroupMember) = a == b
        }
    }
}
