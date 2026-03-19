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
import com.securechat.databinding.FragmentFingerprintBinding
import kotlinx.coroutines.launch

/**
 * Fingerprint verification sub-screen.
 * Displays the shared emoji fingerprint and allows the user to mark it as verified.
 */
class FingerprintFragment : Fragment() {

    private var _binding: FragmentFingerprintBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: ChatRepository
    private var conversationId: String = ""
    private var contactName: String = ""
    private var isVerified: Boolean = false

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
        _binding = FragmentFingerprintBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        repository = ChatRepository(requireContext())

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.tvContactLabel.text = "Conversation avec $contactName"

        lifecycleScope.launch {
            val conversation = repository.getConversation(conversationId) ?: return@launch

            binding.tvFingerprint.text = conversation.sharedFingerprint
            isVerified = conversation.fingerprintVerified
            updateVerificationUI(isVerified)

            binding.btnVerify.setOnClickListener {
                lifecycleScope.launch {
                    val newState = !isVerified
                    repository.verifyFingerprint(conversationId, newState)
                    isVerified = newState
                    updateVerificationUI(isVerified)
                    val msg = if (newState) "Empreinte vérifiée ✓" else "Vérification retirée"
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
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
            binding.btnVerify.text = "Retirer la vérification"
            binding.btnVerify.visibility = View.VISIBLE
        } else {
            binding.tvVerificationStatus.text = "⚠️ Non vérifié"
            binding.tvVerificationStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.orange_warning)
            )
            binding.btnVerify.text = "Marquer comme vérifié"
            binding.btnVerify.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
