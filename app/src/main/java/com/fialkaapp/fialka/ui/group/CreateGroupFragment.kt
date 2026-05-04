/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.ui.group

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.data.model.Contact
import com.fialkaapp.fialka.databinding.FragmentCreateGroupBinding
import com.google.android.material.chip.Chip

class CreateGroupFragment : Fragment() {

    private var _binding: FragmentCreateGroupBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GroupViewModel by viewModels()
    private lateinit var adapter: ContactSelectableAdapter

    // Track selected contacts for chips
    private val selectedContacts = mutableListOf<Contact>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCreateGroupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        adapter = ContactSelectableAdapter { contact, isSelected ->
            if (isSelected) {
                selectedContacts.add(contact)
                addChip(contact)
            } else {
                selectedContacts.removeAll { it.publicKey == contact.publicKey }
                removeChip(contact.publicKey)
            }
            updateFabVisibility()
        }

        binding.rvContacts.layoutManager = LinearLayoutManager(requireContext())
        binding.rvContacts.adapter = adapter

        viewModel.contacts.observe(viewLifecycleOwner) { contacts ->
            adapter.submitList(contacts)
        }

        binding.fabCreate.setOnClickListener {
            val name = binding.etGroupName.text?.toString()?.trim() ?: ""
            if (name.isEmpty()) {
                binding.tilGroupName.error = "Donne un nom au groupe"
                return@setOnClickListener
            }
            if (selectedContacts.isEmpty()) {
                Toast.makeText(requireContext(), "Sélectionne au moins un contact", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            binding.tilGroupName.error = null
            viewModel.createGroup(name, selectedContacts.toList())
        }

        viewModel.isCreating.observe(viewLifecycleOwner) { creating ->
            binding.fabCreate.isEnabled = !creating
        }

        viewModel.createResult.observe(viewLifecycleOwner) { group ->
            if (group != null) {
                viewModel.onCreateHandled()
                // Navigate to group chat
                val bundle = Bundle().apply {
                    putString("groupId", group.groupId)
                    putString("groupName", group.name)
                }
                findNavController().navigate(R.id.action_createGroup_to_groupChat, bundle)
            }
        }

        updateFabVisibility()
    }

    private fun addChip(contact: Contact) {
        val chip = Chip(requireContext()).apply {
            text = contact.displayName
            isCloseIconVisible = true
            tag = contact.publicKey
            setOnCloseIconClickListener {
                selectedContacts.removeAll { it.publicKey == contact.publicKey }
                binding.chipGroupSelected.removeView(this)
                adapter.notifyDataSetChanged()
                updateFabVisibility()
            }
        }
        binding.chipGroupSelected.addView(chip)
    }

    private fun removeChip(publicKey: String) {
        for (i in 0 until binding.chipGroupSelected.childCount) {
            val chip = binding.chipGroupSelected.getChildAt(i)
            if (chip.tag == publicKey) {
                binding.chipGroupSelected.removeView(chip)
                break
            }
        }
    }

    private fun updateFabVisibility() {
        binding.fabCreate.visibility =
            if (selectedContacts.isNotEmpty()) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
