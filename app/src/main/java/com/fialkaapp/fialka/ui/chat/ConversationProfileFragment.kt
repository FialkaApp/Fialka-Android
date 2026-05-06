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
package com.fialkaapp.fialka.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.data.repository.ChatRepository
import com.fialkaapp.fialka.databinding.FragmentConversationProfileBinding
import com.fialkaapp.fialka.util.EphemeralManager
import kotlinx.coroutines.launch

/**
 * Conversation profile hub — shows contact info, ephemeral settings,
 * fingerprint verification link, crypto info, and delete option.
 */
class ConversationProfileFragment : Fragment() {

    private var _binding: FragmentConversationProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: ChatRepository
    private var conversationId: String = ""
    private var contactName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        conversationId = arguments?.getString("conversationId") ?: ""
        contactName = arguments?.getString("contactName") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = ChatRepository(requireContext())

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Contact info
        binding.tvContactName.text = contactName
        binding.tvContactInitial.text = contactName.firstOrNull()?.uppercase() ?: "?"

        // Ephemeral row → picker dialog
        binding.rowEphemeral.setOnClickListener { showEphemeralPicker() }

        // Dummy traffic toggle
        binding.switchDummyTraffic.setOnCheckedChangeListener(null)  // prevent trigger during load
        binding.switchDummyTraffic.setOnCheckedChangeListener { _, isChecked ->
            lifecycleScope.launch {
                repository.setDummyTraffic(conversationId, isChecked)
                binding.tvDummySummary.text = if (isChecked) getString(R.string.conv_dummy_active) else getString(R.string.conv_dummy_inactive)
            }
        }

        // Fingerprint row → navigate to fingerprint sub-screen
        binding.rowFingerprint.setOnClickListener {
            findNavController().navigate(
                R.id.action_conversationProfile_to_fingerprint,
                Bundle().apply {
                    putString("conversationId", conversationId)
                    putString("contactName", contactName)
                }
            )
        }

        // Delete row → confirmation dialog
        binding.rowDelete.setOnClickListener { showDeleteConfirmation() }

        // Load conversation data
        loadConversationData()
    }

    override fun onResume() {
        super.onResume()
        if (_binding != null) loadConversationData()
    }

    private fun loadConversationData() {
        lifecycleScope.launch {
            val conversation = repository.getConversation(conversationId) ?: return@launch

            // Ephemeral summary
            binding.tvEphemeralSummary.text =
                EphemeralManager.getLabelForDuration(requireContext(), conversation.ephemeralDuration)

            // Dummy traffic state
            binding.switchDummyTraffic.setOnCheckedChangeListener(null)
            binding.switchDummyTraffic.isChecked = conversation.dummyTrafficEnabled
            binding.tvDummySummary.text = if (conversation.dummyTrafficEnabled) getString(R.string.conv_dummy_active) else getString(R.string.conv_dummy_inactive)
            binding.switchDummyTraffic.setOnCheckedChangeListener { _, isChecked ->
                lifecycleScope.launch {
                    repository.setDummyTraffic(conversationId, isChecked)
                    binding.tvDummySummary.text = if (isChecked) getString(R.string.conv_dummy_active) else getString(R.string.conv_dummy_inactive)
                }
            }

            // Fingerprint summary
            if (conversation.fingerprintVerified) {
                binding.tvFingerprintSummary.text = getString(R.string.conv_fingerprint_verified)
            } else {
                binding.tvFingerprintSummary.text = getString(R.string.conv_fingerprint_unverified)
            }
        }
    }

    private fun showEphemeralPicker() {
        val labels = EphemeralManager.getLabels(requireContext())
        val durations = EphemeralManager.DURATION_OPTIONS

        lifecycleScope.launch {
            val conversation = repository.getConversation(conversationId) ?: return@launch
            val currentIndex = durations.toList().indexOf(conversation.ephemeralDuration).coerceAtLeast(0)

            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.conv_profile_ephemeral_title))
                .setSingleChoiceItems(labels, currentIndex) { dialog: android.content.DialogInterface, which: Int ->
                    val chosen = durations[which]
                    lifecycleScope.launch {
                        repository.setEphemeralDuration(conversationId, chosen)
                        binding.tvEphemeralSummary.text =
                            EphemeralManager.getLabelForDuration(requireContext(), chosen)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton(getString(R.string.action_cancel), null)
                .show()
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Supprimer la conversation")
            .setMessage(getString(R.string.conv_profile_delete_confirm_message))
            .setPositiveButton("Supprimer") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteConversation(conversationId)
                    Toast.makeText(requireContext(), getString(R.string.conv_profile_deleted_toast), Toast.LENGTH_SHORT).show()
                    // Pop back to conversations list
                    findNavController().popBackStack(R.id.conversationsFragment, false)
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
