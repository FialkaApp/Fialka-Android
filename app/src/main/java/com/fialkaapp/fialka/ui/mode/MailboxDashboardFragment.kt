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
package com.fialkaapp.fialka.ui.mode

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fialkaapp.fialka.AppMode
import com.fialkaapp.fialka.MailboxType
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.data.local.MailboxDatabase
import com.fialkaapp.fialka.data.model.MailboxMember
import com.fialkaapp.fialka.databinding.FragmentMailboxDashboardBinding
import com.fialkaapp.fialka.tor.MailboxServer
import com.fialkaapp.fialka.tor.TorManager
import com.fialkaapp.fialka.util.QrCodeGenerator
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.security.SecureRandom

/**
 * Mailbox dashboard: stats, member list, invite generation, purge.
 * Only shown in MAILBOX mode.
 */
class MailboxDashboardFragment : Fragment() {

    private var _binding: FragmentMailboxDashboardBinding? = null
    private val binding get() = _binding!!

    private val memberAdapter = MemberAdapter { member -> showMemberOptions(member) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMailboxDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val isPrivate = AppMode.getMailboxType(requireContext()) == MailboxType.PRIVATE

        // Show members section only for PRIVATE mode
        if (isPrivate) {
            binding.tvMembersLabel.visibility = View.VISIBLE
            binding.rvMembers.visibility = View.VISIBLE
            binding.rvMembers.layoutManager = LinearLayoutManager(requireContext())
            binding.rvMembers.adapter = memberAdapter
            binding.btnInvite.visibility = View.VISIBLE
        } else {
            binding.btnInvite.visibility = View.GONE
        }

        // Observe stats — live state
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MailboxServer.blobCount.collect { count ->
                    if (_binding != null) binding.tvBlobCount.text = count.toString()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MailboxServer.memberCount.collect { count ->
                    if (_binding != null) binding.tvMemberCount.text = count.toString()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MailboxServer.totalSize.collect { size ->
                    if (_binding != null) binding.tvStorageSize.text = formatSize(size)
                }
            }
        }

        // Observe stats — cumulative totals
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MailboxServer.totalDeposited.collect { count ->
                    if (_binding != null) binding.tvTotalDeposited.text = count.toString()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MailboxServer.totalFetched.collect { count ->
                    if (_binding != null) binding.tvTotalFetched.text = count.toString()
                }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                MailboxServer.totalDataProcessed.collect { size ->
                    if (_binding != null) binding.tvTotalData.text = formatSize(size)
                }
            }
        }

        // Observe .onion address
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TorManager.onionAddress.collect { address ->
                    if (_binding != null) {
                        binding.tvOnion.text = address ?: getString(R.string.mailbox_no_onion)
                    }
                }
            }
        }

        // Auto-refresh members when member count changes (PRIVATE mode)
        if (isPrivate) {
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    MailboxServer.memberCount.collect {
                        loadMembers()
                    }
                }
            }
        }

        // Refresh stats on resume
        viewLifecycleOwner.lifecycleScope.launch {
            MailboxServer.refreshStats()
        }

        // Periodic auto-refresh every 30s while dashboard is visible
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(30_000)
                    MailboxServer.refreshStats()
                }
            }
        }

        // Buttons
        binding.btnPurge.setOnClickListener { confirmPurge() }
        binding.btnInvite.setOnClickListener { showInviteQr() }
        binding.btnDeleteMailbox.setOnClickListener { confirmDeleteMailbox() }
        binding.btnShareLink.setOnClickListener { shareMailboxLink() }
        binding.btnRefreshDashboard.setOnClickListener { refreshDashboard() }
    }

    private fun refreshDashboard() {
        viewLifecycleOwner.lifecycleScope.launch {
            MailboxServer.refreshStats()
            if (AppMode.getMailboxType(requireContext()) == MailboxType.PRIVATE) {
                loadMembers()
            }
            Toast.makeText(requireContext(), R.string.mailbox_dashboard_refreshed, Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun loadMembers() {
        val db = MailboxDatabase.getInstance(requireContext())
        val members = db.memberDao().getAll()
        memberAdapter.submitList(members)
    }

    private fun showInviteQr() {
        viewLifecycleOwner.lifecycleScope.launch {
            val onion = TorManager.onionAddress.value
            if (onion == null) {
                Toast.makeText(requireContext(), R.string.mailbox_no_onion, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val db = MailboxDatabase.getInstance(requireContext())
            val code = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val codeB64 = Base64.encodeToString(code, Base64.NO_WRAP or Base64.URL_SAFE)
            val invite = com.fialkaapp.fialka.data.model.MailboxInvite(
                code = codeB64,
                createdAt = System.currentTimeMillis(),
                expiresAt = System.currentTimeMillis() + 24L * 60 * 60 * 1000
            )
            db.inviteDao().insert(invite)

            val pubKeyB64 = CryptoManager.getPublicKey() ?: ""
            val mailboxType = AppMode.getMailboxType(requireContext())

            // Compact JSON for QR (much smaller than URL)
            val qrData = JSONObject().apply {
                put("onion", onion)
                put("pubkey", pubKeyB64)
                put("type", mailboxType.name)
                put("invite", codeB64)
            }.toString()

            // Full deep link for clipboard share
            val link = "fialka://mailbox?onion=${Uri.encode(onion)}&pubkey=${Uri.encode(pubKeyB64)}&type=${mailboxType.name}&invite=${Uri.encode(codeB64)}"

            if (_binding == null) return@launch

            val bitmap = QrCodeGenerator.generate(qrData, 512) ?: return@launch
            val container = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                setPadding(32, 32, 32, 16)
            }
            val iv = android.widget.ImageView(requireContext()).apply {
                setImageBitmap(bitmap)
            }
            container.addView(iv)
            val tvExpiry = TextView(requireContext()).apply {
                text = getString(R.string.mailbox_invite_qr_validity)
                setTextColor(android.graphics.Color.parseColor("#9E9BB0"))
                textSize = 12f
                setPadding(0, 16, 0, 0)
            }
            container.addView(tvExpiry)

            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.mailbox_invite_qr_title)
                .setView(container)
                .setPositiveButton(R.string.mailbox_invite_copy_link) { _, _ ->
                    val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("invite_link", link))
                    Toast.makeText(requireContext(), R.string.mailbox_invite_copied, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("OK", null)
                .show()
        }
    }

    private fun confirmPurge() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.mailbox_purge_confirm)
            .setPositiveButton(R.string.mailbox_purge) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    MailboxServer.purgeExpired()
                    loadMembers()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDeleteMailbox() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mailbox_delete)
            .setMessage(R.string.mailbox_delete_confirm)
            .setPositiveButton(R.string.mailbox_delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    MailboxServer.resetMailbox()
                    findNavController().navigate(R.id.action_mailboxDashboard_to_mailboxSetup)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // generateInvite removed — replaced by showInviteQr above

    private fun shareMailboxLink() {
        val onion = TorManager.onionAddress.value ?: return
        val pubKeyB64 = CryptoManager.getPublicKey() ?: ""
        val mailboxType = AppMode.getMailboxType(requireContext())
        val link = "fialka://mailbox?onion=${Uri.encode(onion)}&pubkey=${Uri.encode(pubKeyB64)}&type=${mailboxType.name}"
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("mailbox_link", link))
        Toast.makeText(requireContext(), R.string.mailbox_link_copied, Toast.LENGTH_SHORT).show()
    }

    private fun showMemberOptions(member: MailboxMember) {
        val options = mutableListOf<String>()
        val actions = mutableListOf<() -> Unit>()

        if (member.role == MailboxMember.ROLE_OWNER) {
            options.add(getString(R.string.mailbox_dashboard_demote))
            actions.add { demoteMember(member) }
        }
        options.add(getString(R.string.mailbox_dashboard_kick))
        actions.add { kickMemberLocal(member) }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mailbox_dashboard_member_options)
            .setItems(options.toTypedArray()) { _, which -> actions[which]() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun kickMemberLocal(member: MailboxMember) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mailbox_dashboard_kick)
            .setMessage(member.pubKey.take(16) + "…")
            .setPositiveButton(R.string.mailbox_dashboard_kick) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val db = MailboxDatabase.getInstance(requireContext())
                    db.memberDao().deleteByPubKey(member.pubKey)
                    for (blob in db.blobDao().getBlobsForRecipient(member.pubKey)) {
                        db.blobDao().deleteById(blob.blobId)
                    }
                    MailboxServer.refreshStats()
                    loadMembers()
                    Toast.makeText(requireContext(), R.string.mailbox_dashboard_kicked, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun demoteMember(member: MailboxMember) {
        viewLifecycleOwner.lifecycleScope.launch {
            val db = MailboxDatabase.getInstance(requireContext())
            db.memberDao().insert(member.copy(role = MailboxMember.ROLE_MEMBER))
            loadMembers()
            Toast.makeText(requireContext(), R.string.mailbox_dashboard_demoted, Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Modern member list adapter with MaterialCardView ──

    private class MemberAdapter(
        private val onMemberClick: (MailboxMember) -> Unit
    ) : RecyclerView.Adapter<MemberAdapter.VH>() {
        private var members: List<MailboxMember> = emptyList()

        fun submitList(list: List<MailboxMember>) {
            members = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = com.fialkaapp.fialka.databinding.ItemMailboxMemberBinding.inflate(
                android.view.LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = members[position]
            val keyShort = m.pubKey.take(16) + "…"
            holder.binding.tvMemberKey.text = keyShort
            holder.binding.tvMemberRole.text = if (m.role == "OWNER") "Propriétaire 👑" else "Membre"

            // Color based on role
            val roleColor = if (m.role == "OWNER") {
                holder.itemView.context.getColor(com.fialkaapp.fialka.R.color.orange_500)
            } else {
                holder.itemView.context.getColor(com.fialkaapp.fialka.R.color.purple_500)
            }
            holder.binding.tvMemberRole.setTextColor(roleColor)

            holder.itemView.setOnClickListener { onMemberClick(m) }
        }

        override fun getItemCount() = members.size

        class VH(val binding: com.fialkaapp.fialka.databinding.ItemMailboxMemberBinding) :
            RecyclerView.ViewHolder(binding.root)
    }
}
