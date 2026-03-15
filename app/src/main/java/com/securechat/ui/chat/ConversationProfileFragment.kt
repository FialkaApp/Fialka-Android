package com.securechat.ui.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.securechat.R
import com.securechat.data.repository.ChatRepository
import com.securechat.databinding.FragmentConversationProfileBinding
import kotlinx.coroutines.launch

/**
 * Conversation profile screen — shows contact info and emoji fingerprint verification.
 *
 * The shared fingerprint (96-bit, 16 emojis from a 64-emoji palette) is computed
 * from both public keys sorted lexicographically, so both sides see the same emojis.
 * Users compare visually (in person or via video call) to detect MITM attacks.
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

        // Load conversation and display fingerprint
        lifecycleScope.launch {
            val conversation = repository.getConversation(conversationId) ?: return@launch

            binding.tvFingerprint.text = conversation.sharedFingerprint

            updateVerificationUI(conversation.fingerprintVerified)

            binding.btnVerifyFingerprint.setOnClickListener {
                lifecycleScope.launch {
                    repository.verifyFingerprint(conversationId, true)
                    updateVerificationUI(true)
                    Toast.makeText(
                        requireContext(),
                        "Empreinte vérifiée ✓",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun updateVerificationUI(verified: Boolean) {
        if (verified) {
            binding.tvVerificationStatus.text = "✅ Vérifié"
            binding.tvVerificationStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.green_verified)
            )
            binding.btnVerifyFingerprint.visibility = View.GONE
        } else {
            binding.tvVerificationStatus.text = "⚠️ Non vérifié"
            binding.tvVerificationStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.orange_warning)
            )
            binding.btnVerifyFingerprint.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
