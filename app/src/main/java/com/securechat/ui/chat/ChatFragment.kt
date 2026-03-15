package com.securechat.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.securechat.R
import com.securechat.databinding.FragmentChatBinding

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

        binding.toolbar.title = contactName
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        adapter = MessagesAdapter()
        binding.rvMessages.adapter = adapter

        // Initialize ViewModel with conversation ID
        viewModel.init(conversationId)

        // Observe messages
        viewModel.messages.observe(viewLifecycleOwner) { messages ->
            adapter.submitList(messages) {
                // Scroll to bottom when new messages arrive
                if (messages.isNotEmpty()) {
                    binding.rvMessages.scrollToPosition(messages.size - 1)
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
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
