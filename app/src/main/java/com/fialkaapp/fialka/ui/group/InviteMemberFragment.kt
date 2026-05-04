/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.ui.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fialkaapp.fialka.databinding.FragmentInviteMemberBinding

/**
 * Lets the user pick contacts to invite to an existing group.
 * Contacts already in the group are filtered out (best-effort: by publicKey).
 */
class InviteMemberFragment : Fragment() {

    private var _binding: FragmentInviteMemberBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GroupViewModel by viewModels()

    private var groupId: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentInviteMemberBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        groupId = arguments?.getString("groupId") ?: ""
        val groupName = arguments?.getString("groupName") ?: "Groupe"

        binding.toolbar.title = "Inviter dans « $groupName »"
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val adapter = ContactSelectableAdapter { _, _ -> }
        binding.rvContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContacts.adapter = adapter

        // Load contacts, filter out members already in the group
        viewModel.contacts.observe(viewLifecycleOwner) { allContacts ->
            viewModel.getMembersLive(groupId).observe(viewLifecycleOwner) { currentMembers ->
                val memberKeys = currentMembers.map { it.publicKey }.toSet()
                val available = allContacts.filter { it.publicKey !in memberKeys }
                adapter.submitList(available)
            }
        }

        binding.fabSendInvites.setOnClickListener {
            val allContacts = viewModel.contacts.value ?: emptyList()
            val selected = adapter.getSelectedContacts(allContacts)
            if (selected.isEmpty()) return@setOnClickListener

            selected.forEach { contact ->
                viewModel.inviteMember(groupId, contact)
            }
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
