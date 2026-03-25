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
package com.fialkaapp.fialka.ui.addcontact

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
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.FragmentAddContactBinding
import com.fialkaapp.fialka.util.QrCodeGenerator

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
            val scanned = result.contents
            when {
                scanned.startsWith("fialka://invite?") -> {
                    val invite = QrCodeGenerator.parseInvite(scanned)
                    if (invite != null) {
                        // Store the FULL deeplink so "Créer" re-parses it with all keys intact
                        binding.etPublicKey.setText(scanned)
                        binding.etPublicKey.tag = invite.mlkemPublicKey
                        if (!invite.displayName.isNullOrBlank()) {
                            binding.etContactName.setText(invite.displayName)
                            binding.tilContactName.helperText = "✅ Pré-rempli depuis le QR"
                        }
                    } else {
                        binding.etPublicKey.setText(scanned)
                    }
                }
                scanned.startsWith("FIALKA:") -> {
                    // Legacy V1 format: FIALKA:pubkey:displayname
                    val parts = scanned.removePrefix("FIALKA:").split(":", limit = 2)
                    if (parts.size == 2) {
                        binding.etPublicKey.setText(parts[0])
                        binding.etContactName.setText(parts[1])
                    } else {
                        binding.etPublicKey.setText(scanned)
                    }
                }
                else -> {
                    binding.etPublicKey.setText(scanned)
                }
            }
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

        // Pre-fill from deep link if app was opened via fialka://invite
        val intentData = requireActivity().intent?.data
        if (intentData != null && intentData.scheme == "Fialka" && intentData.host == "invite") {
            val invite = QrCodeGenerator.parseInvite(intentData.toString())
            if (invite != null) {
                binding.etPublicKey.setText(invite.x25519PublicKey)
                binding.etPublicKey.tag = invite.mlkemPublicKey
                if (!invite.displayName.isNullOrBlank()) {
                    binding.etContactName.setText(invite.displayName)
                    binding.tilContactName.helperText = "✅ Pré-rempli depuis le lien"
                }
            }
            requireActivity().intent.data = null // consume so it doesn't re-fill on back+return
        }

        // Clear helperText if user manually edits the name field
        binding.etContactName.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (binding.tilContactName.helperText != null) {
                    binding.tilContactName.helperText = null
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        binding.btnScanQr.setOnClickListener {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        binding.btnCreateConversation.setOnClickListener {
            val name = binding.etContactName.text.toString()
            val rawInput = binding.etPublicKey.text.toString().trim()
            // Support v3 (Ed25519), v2 (X25519+mlkem), and raw X25519 keys
            val invite = QrCodeGenerator.parseInvite(rawInput)
            val key = invite?.x25519PublicKey ?: rawInput
            val mlkemKey = (binding.etPublicKey.tag as? String) ?: invite?.mlkemPublicKey
            val ed25519Key = invite?.ed25519PublicKey
            viewModel.addContact(name, key, mlkemKey, ed25519Key)
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
            setOrientationLocked(false)
            setCaptureActivity(CustomScannerActivity::class.java)
        }
        qrScannerLauncher.launch(options)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
