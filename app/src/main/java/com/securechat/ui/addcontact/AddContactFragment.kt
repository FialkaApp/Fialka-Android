package com.securechat.ui.addcontact

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.securechat.R
import com.securechat.databinding.FragmentAddContactBinding

/**
 * Add Contact screen — scan a QR code or paste a contact's public key.
 * Creates both a Contact and a Conversation.
 */
class AddContactFragment : Fragment() {

    private var _binding: FragmentAddContactBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AddContactViewModel by viewModels()

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchQrScanner()
        } else {
            Toast.makeText(requireContext(), R.string.camera_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            binding.etPublicKey.setText(result.contents)
            Toast.makeText(requireContext(), R.string.qr_scanned_success, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddContactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnScanQr.setOnClickListener {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        binding.btnCreateConversation.setOnClickListener {
            val name = binding.etContactName.text.toString()
            val key = binding.etPublicKey.text.toString()
            viewModel.addContact(name, key)
        }

        viewModel.state.observe(viewLifecycleOwner) { state ->
            when (state) {
                is AddContactViewModel.AddContactState.Idle -> {
                    binding.btnCreateConversation.isEnabled = true
                }
                is AddContactViewModel.AddContactState.Loading -> {
                    binding.btnCreateConversation.isEnabled = false
                }
                is AddContactViewModel.AddContactState.Success -> {
                    Toast.makeText(requireContext(), R.string.contact_added, Toast.LENGTH_SHORT).show()
                    val bundle = Bundle().apply {
                        putString("conversationId", state.conversation.conversationId)
                        putString("contactName", state.conversation.contactDisplayName)
                    }
                    findNavController().navigate(R.id.action_addContact_to_chat, bundle)
                }
                is AddContactViewModel.AddContactState.Error -> {
                    binding.btnCreateConversation.isEnabled = true
                    Toast.makeText(requireContext(), state.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun launchQrScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scannez le QR code du contact")
            setCameraId(0)
            setBeepEnabled(false)
            setOrientationLocked(true)
        }
        qrScannerLauncher.launch(options)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
