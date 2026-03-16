package com.securechat.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.securechat.R
import com.securechat.data.repository.ChatRepository
import com.securechat.databinding.FragmentConversationProfileBinding
import com.securechat.util.EphemeralManager
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

        // Fingerprint row → navigate to fingerprint sub-screen
        binding.rowFingerprint.setOnClickListener {
            findNavController().navigate(
                R.id.action_conversationProfile_to_fingerprint,
                bundleOf(
                    "conversationId" to conversationId,
                    "contactName" to contactName
                )
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
                EphemeralManager.getLabelForDuration(conversation.ephemeralDuration)

            // Fingerprint summary
            if (conversation.fingerprintVerified) {
                binding.tvFingerprintSummary.text = "✅ Vérifié"
            } else {
                binding.tvFingerprintSummary.text = "⚠\uFE0F Non vérifié"
            }
        }
    }

    private fun showEphemeralPicker() {
        val labels = EphemeralManager.DURATION_LABELS
        val durations = EphemeralManager.DURATION_OPTIONS

        lifecycleScope.launch {
            val conversation = repository.getConversation(conversationId) ?: return@launch
            val currentIndex = durations.toList().indexOf(conversation.ephemeralDuration).coerceAtLeast(0)

            AlertDialog.Builder(requireContext())
                .setTitle("Messages éphémères")
                .setSingleChoiceItems(labels, currentIndex) { dialog: android.content.DialogInterface, which: Int ->
                    val chosen = durations[which]
                    lifecycleScope.launch {
                        repository.setEphemeralDuration(conversationId, chosen)
                        binding.tvEphemeralSummary.text =
                            EphemeralManager.getLabelForDuration(chosen)
                    }
                    dialog.dismiss()
                }
                .setNegativeButton("Annuler", null)
                .show()
        }
    }

    private fun showDeleteConfirmation() {
        AlertDialog.Builder(requireContext())
            .setTitle("Supprimer la conversation")
            .setMessage("Tous les messages seront définitivement supprimés de cet appareil.")
            .setPositiveButton("Supprimer") { _, _ ->
                lifecycleScope.launch {
                    repository.deleteConversation(conversationId)
                    Toast.makeText(requireContext(), "Conversation supprimée", Toast.LENGTH_SHORT).show()
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
