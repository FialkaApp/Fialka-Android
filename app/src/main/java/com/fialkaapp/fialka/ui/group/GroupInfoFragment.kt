/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.ui.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.data.model.GroupLocal
import com.fialkaapp.fialka.data.model.GroupMember
import com.fialkaapp.fialka.databinding.FragmentGroupInfoBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class GroupInfoFragment : Fragment() {

    private var _binding: FragmentGroupInfoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GroupViewModel by viewModels()

    // Adapter is created once; we only call submitList on subsequent updates.
    private var memberAdapter: GroupMemberAdapter? = null

    private var groupId: String = ""
    private var groupName: String = ""
    private var myPubKey: String = ""

    // Cache so the members observer can use the latest role without a race.
    private var myRole: String = GroupLocal.ROLE_MEMBER
    private var invitePolicy: String = GroupLocal.INVITE_ADMIN_ONLY

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGroupInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        groupId   = arguments?.getString("groupId")   ?: ""
        groupName = arguments?.getString("groupName") ?: "Groupe"

        myPubKey = try {
            CryptoManager.getPublicKey() ?: ""
        } catch (_: Exception) { "" }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.tvGroupNameInfo.text = groupName
        binding.tvGroupInitial.text = groupName.firstOrNull()?.uppercaseChar()?.toString() ?: "G"

        viewModel.loadGroup(groupId)

        // ── Group info observer ──────────────────────────────────────────────
        viewModel.currentGroup.observe(viewLifecycleOwner) { group ->
            if (group == null) return@observe

            val roleChanged = myRole != group.myRole
            myRole       = group.myRole
            invitePolicy = group.invitePolicy

            binding.tvGroupNameInfo.text = group.name
            binding.tvGroupInitial.text = group.name.firstOrNull()?.uppercaseChar()?.toString() ?: "G"
            binding.tvGroupMeta.text = getString(com.fialkaapp.fialka.R.string.group_created_on,
                java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date(group.createdAt)))

            val isAdmin = group.myRole != GroupLocal.ROLE_MEMBER
            binding.btnRenameGroup.visibility = if (isAdmin) View.VISIBLE else View.GONE

            // Settings section: only admin/owner
            binding.tvSettingsHeader.visibility = if (isAdmin) View.VISIBLE else View.GONE
            binding.layoutInvitePolicy.visibility = if (isAdmin) View.VISIBLE else View.GONE

            // Invite button: admin always; member only if INVITE_ALL policy
            val canInvite = isAdmin || group.invitePolicy == GroupLocal.INVITE_ALL
            binding.btnInviteMember.visibility = if (canInvite) View.VISIBLE else View.GONE

            // Sync switch state without triggering the listener
            binding.switchInvitePolicy.setOnCheckedChangeListener(null)
            binding.switchInvitePolicy.isChecked = group.invitePolicy == GroupLocal.INVITE_ALL
            binding.switchInvitePolicy.setOnCheckedChangeListener { _, isChecked ->
                val newPolicy = if (isChecked) GroupLocal.INVITE_ALL else GroupLocal.INVITE_ADMIN_ONLY
                viewModel.updateInvitePolicy(groupId, newPolicy)
            }

            // If role changed, recreate adapter with the new role
            if (roleChanged) memberAdapter = null
        }

        // ── Members observer ─────────────────────────────────────────────────
        viewModel.getMembersLive(groupId).observe(viewLifecycleOwner) { members ->
            val memberCount = members.size
            binding.tvGroupMeta.text = "$memberCount membre${if (memberCount > 1) "s" else ""}"

            val adapter = memberAdapter ?: createAdapter().also {
                memberAdapter = it
                binding.rvMembers.layoutManager = LinearLayoutManager(requireContext())
                binding.rvMembers.adapter = it
            }
            adapter.submitList(members)
        }

        // ── Buttons ──────────────────────────────────────────────────────────
        binding.btnRenameGroup.setOnClickListener { showRenameDialog() }

        binding.btnInviteMember.setOnClickListener {
            // Navigate to contact selection; pass groupId so CreateGroupFragment (or a
            // dedicated InviteMemberFragment) knows which group to invite into.
            val args = Bundle().apply {
                putString("groupId", groupId)
                putString("groupName", groupName)
            }
            findNavController().navigate(R.id.action_groupInfoFragment_to_inviteMemberFragment, args)
        }

        binding.btnLeaveGroup.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Quitter le groupe")
                .setMessage(getString(R.string.group_leave_confirm_message, groupName))
                .setPositiveButton("Quitter") { _, _ -> viewModel.leaveGroup(groupId) }
                .setNegativeButton("Annuler", null)
                .show()
        }

        viewModel.adminResult.observe(viewLifecycleOwner) { result ->
            if (result == "left") {
                viewModel.onAdminResultHandled()
                findNavController().popBackStack(R.id.conversationsFragment, false)
            }
        }
    }

    // ── Adapter factory ──────────────────────────────────────────────────────

    private fun createAdapter(): GroupMemberAdapter = GroupMemberAdapter(
        myPublicKey = myPubKey,
        myRole      = myRole,
        onKick = { member ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Retirer ${member.displayName}")
                .setMessage("Retirer ${member.displayName} du groupe ?")
                .setPositiveButton("Retirer") { _, _ ->
                    viewModel.kickMember(groupId, member.publicKey)
                }
                .setNegativeButton("Annuler", null)
                .show()
        },
        onPromote = { member ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Promouvoir ${member.displayName}")
                .setMessage("Nommer ${member.displayName} administrateur ?")
                .setPositiveButton("Promouvoir") { _, _ ->
                    viewModel.promoteMember(groupId, member.publicKey)
                }
                .setNegativeButton("Annuler", null)
                .show()
        },
        onDemote = { member ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.group_demote_title, member.displayName))
                .setMessage("Retirer les droits admin de ${member.displayName} ?")
                .setPositiveButton(getString(R.string.group_demote_btn)) { _, _ ->
                    viewModel.demoteMember(groupId, member.publicKey)
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    )

    // ── Dialogs ──────────────────────────────────────────────────────────────

    private fun showRenameDialog() {
        val et = EditText(requireContext()).apply {
            hint = "Nouveau nom"
            setText(groupName)
            selectAll()
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Renommer le groupe")
            .setView(et)
            .setPositiveButton("Renommer") { _, _ ->
                val newName = et.text.toString().trim()
                if (newName.isNotEmpty()) {
                    groupName = newName
                    viewModel.renameGroup(groupId, newName)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
