package com.securechat.ui.profile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.securechat.R
import com.securechat.databinding.FragmentProfileBinding
import com.securechat.util.QrCodeGenerator

/**
 * Profile screen — displays the user's pseudo, public key, and QR code.
 * Allows renaming, copying/sharing the public key, and deleting the account.
 */
class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.etDisplayName.setText(user.displayName)
                binding.tvPublicKey.text = user.publicKey
                binding.tvInitial.text = user.displayName.firstOrNull()?.uppercase() ?: "?"

                // Generate QR code from public key
                try {
                    val qrBitmap = QrCodeGenerator.generate(user.publicKey, 512)
                    binding.ivQrCode.setImageBitmap(qrBitmap)
                } catch (e: Exception) {
                    Log.e("SecureChat", "QR generation failed", e)
                }
            }
        }

        binding.btnSaveName.setOnClickListener {
            val newName = binding.etDisplayName.text.toString()
            viewModel.updateDisplayName(newName)
        }

        viewModel.saveResult.observe(viewLifecycleOwner) { success ->
            if (success) {
                Toast.makeText(requireContext(), "Pseudo mis à jour", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnCopyKey.setOnClickListener {
            val key = binding.tvPublicKey.text.toString()
            if (key.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Public Key", key))
                Toast.makeText(requireContext(), R.string.key_copied, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnShareKey.setOnClickListener {
            val key = binding.tvPublicKey.text.toString()
            if (key.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, key)
                    putExtra(Intent.EXTRA_SUBJECT, "Ma clé publique SecureChat")
                }
                startActivity(Intent.createChooser(intent, "Partager ma clé publique"))
            }
        }

        binding.btnDeleteAccount.setOnClickListener {
            showDeleteAccountDialog()
        }

        viewModel.accountReset.observe(viewLifecycleOwner) { success ->
            if (success == true) {
                viewModel.onAccountResetHandled()
                findNavController().navigate(R.id.action_profile_to_onboarding)
            }
        }
    }

    private fun showDeleteAccountDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_account_title)
            .setMessage(R.string.delete_account_message)
            .setPositiveButton(R.string.delete_account_confirm) { _, _ ->
                viewModel.resetAccount()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
