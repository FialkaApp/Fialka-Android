package com.securechat.ui.chat

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.securechat.R
import com.securechat.data.repository.ChatRepository
import com.securechat.databinding.FragmentChatBinding
import com.securechat.util.EphemeralManager
import kotlinx.coroutines.launch

/**
 * Chat screen — displays messages for a conversation.
 *
 * Flow:
 *  1. User types a message and taps send.
 *  2. ChatViewModel encrypts the message using CryptoManager (ECDH + AES-256-GCM).
 *  3. Encrypted message is sent to Firebase via FirebaseRelay.
 *  4. Plaintext is saved locally in Room.
 *  5. Incoming messages from Firebase are decrypted and displayed.
 */
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: MessagesAdapter

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
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar — custom title + fingerprint badge
        binding.tvToolbarTitle.text = contactName
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Navigate to contact profile on toolbar content click or menu
        val navigateToProfile = {
            findNavController().navigate(
                R.id.action_chat_to_conversationProfile,
                bundleOf(
                    "conversationId" to conversationId,
                    "contactName" to contactName
                )
            )
        }
        binding.toolbarContent.setOnClickListener { navigateToProfile() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_contact_profile -> {
                    navigateToProfile()
                    true
                }
                else -> false
            }
        }

        // Load fingerprint badge
        loadFingerprintBadge()

        adapter = MessagesAdapter()
        binding.rvMessages.adapter = adapter

        // Initialize ViewModel with conversation ID
        viewModel.init(conversationId)

        // Observe chat items (messages + optional unread divider)
        viewModel.chatItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items) {
                // Scroll to bottom when new messages arrive
                if (items.isNotEmpty()) {
                    binding.rvMessages.scrollToPosition(items.size - 1)
                }
            }
        }

        // Observe acceptance status
        viewModel.isAccepted.observe(viewLifecycleOwner) { accepted ->
            if (accepted) {
                binding.inputBar.visibility = View.VISIBLE
                binding.tvPendingBanner.visibility = View.GONE
            } else {
                binding.inputBar.visibility = View.GONE
                binding.tvPendingBanner.visibility = View.VISIBLE
            }
        }

        // Send button
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString()
            if (text.isNotBlank()) {
                viewModel.sendMessage(text)
                binding.etMessage.text?.clear()
            }
        }

        // Error handling
        viewModel.sendError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        }

        // Dead conversation detection
        viewModel.conversationDead.observe(viewLifecycleOwner) { dead ->
            if (dead) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Conversation supprimée")
                    .setMessage("Ce contact a supprimé son compte. Cette conversation n'existe plus sur le serveur.")
                    .setCancelable(false)
                    .setPositiveButton("Supprimer") { _, _ ->
                        viewModel.deleteDeadConversation()
                        findNavController().navigateUp()
                    }
                    .setNegativeButton("Retour") { _, _ ->
                        findNavController().navigateUp()
                    }
                    .show()
            }
        }

        // Observe ephemeral duration changes (real-time sync from other user)
        viewModel.ephemeralDuration.observe(viewLifecycleOwner) { duration ->
            if (_binding != null) {
                if (duration > 0) {
                    binding.tvEphemeralBadge.visibility = View.VISIBLE
                    binding.tvEphemeralBadge.text = "⏱ ${EphemeralManager.getShortLabel(duration)}"
                } else {
                    binding.tvEphemeralBadge.visibility = View.GONE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh badge when returning from profile (may have been verified)
        if (_binding != null) loadFingerprintBadge()
    }

    private fun loadFingerprintBadge() {
        lifecycleScope.launch {
            val repo = ChatRepository(requireContext())
            val conversation = repo.getConversation(conversationId)
            if (conversation != null && _binding != null) {
                val preview = conversation.sharedFingerprint.take(8) // First 4 emojis
                if (conversation.fingerprintVerified) {
                    binding.tvFingerprintBadge.text = "$preview ✓"
                    binding.tvFingerprintBadge.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.green_verified)
                    )
                } else {
                    binding.tvFingerprintBadge.text = "$preview ➤"
                    binding.tvFingerprintBadge.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.orange_warning)
                    )
                }

                // Ephemeral badge
                if (conversation.ephemeralDuration > 0) {
                    binding.tvEphemeralBadge.visibility = View.VISIBLE
                    binding.tvEphemeralBadge.text = "⏱ ${EphemeralManager.getShortLabel(conversation.ephemeralDuration)}"
                } else {
                    binding.tvEphemeralBadge.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
