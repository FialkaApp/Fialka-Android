package com.securechat.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.securechat.R
import com.securechat.crypto.CryptoManager
import com.securechat.crypto.MnemonicManager
import com.securechat.data.remote.FirebaseRelay
import com.securechat.data.repository.ChatRepository
import com.securechat.databinding.FragmentRestoreBinding
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Restore screen — enter 24 BIP-39 words + display name to restore identity.
 */
class RestoreFragment : Fragment() {

    private var _binding: FragmentRestoreBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRestoreBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRestore.setOnClickListener {
            val mnemonicText = binding.etMnemonic.text.toString().trim()
            val displayName = binding.etDisplayName.text.toString().trim()

            if (displayName.isBlank()) {
                showError("Veuillez entrer un pseudo.")
                return@setOnClickListener
            }

            val words = mnemonicText.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }
            if (words.size != 24) {
                showError("Entrez exactement 24 mots séparés par des espaces.")
                return@setOnClickListener
            }

            if (!MnemonicManager.validateMnemonic(words)) {
                showError("Phrase invalide. Vérifiez les mots et réessayez.")
                return@setOnClickListener
            }

            restore(words, displayName)
        }
    }

    private fun restore(words: List<String>, displayName: String) {
        binding.progressBar.visibility = View.VISIBLE
        binding.btnRestore.isEnabled = false
        binding.tvError.visibility = View.GONE

        lifecycleScope.launch {
            try {
                withTimeout(15_000L) {
                    val privateKeyBytes = MnemonicManager.mnemonicToPrivateKey(words)
                    val publicKey = CryptoManager.restoreIdentityKey(privateKeyBytes)
                    privateKeyBytes.fill(0)

                    if (!FirebaseRelay.isAuthenticated()) {
                        FirebaseRelay.signInAnonymously()
                    }

                    // Remove old orphaned Firebase profile with same publicKey
                    FirebaseRelay.removeOldUserByPublicKey(publicKey)

                    val repository = ChatRepository(requireContext())
                    repository.createUserWithKey(displayName, publicKey)

                    FirebaseRelay.registerPublicKey(publicKey)
                    FirebaseRelay.storeDisplayName(displayName)

                    findNavController().navigate(R.id.action_restore_to_conversations)
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                binding.btnRestore.isEnabled = true
                showError(e.message ?: "Erreur lors de la restauration.")
            }
        }
    }

    private fun showError(message: String) {
        binding.tvError.text = message
        binding.tvError.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
