/*
 * SecureChat — Post-quantum encrypted messenger
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
package com.securechat.ui.profile

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
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

        var isKeyRevealed = false

        viewModel.user.observe(viewLifecycleOwner) { user ->
            if (user != null) {
                binding.etDisplayName.setText(user.displayName)
                binding.tvInitial.text = user.displayName.firstOrNull()?.uppercase() ?: "?"
                binding.tvDisplayNameHeader.text = user.displayName

                // Raw key — masked by default, tap to reveal
                binding.tvRawKey.text = maskKey(user.publicKey)
                binding.tvRawKey.tag = user.publicKey
                isKeyRevealed = false
                binding.tvKeyRevealHint.setText(R.string.key_reveal_hint)
                binding.tvRawKey.setOnClickListener {
                    val realKey = binding.tvRawKey.tag as? String ?: return@setOnClickListener
                    val displayKey = runCatching { QrCodeGenerator.stripX25519Header(realKey) }.getOrDefault(realKey)
                    isKeyRevealed = !isKeyRevealed
                    if (isKeyRevealed) {
                        binding.tvRawKey.text = displayKey
                        binding.tvKeyRevealHint.setText(R.string.key_hide_hint)
                    } else {
                        binding.tvRawKey.text = maskKey(realKey)
                        binding.tvKeyRevealHint.setText(R.string.key_reveal_hint)
                    }
                }

                // Link WITHOUT name — used by Copy / Share buttons (don't leak pseudo in shared URL)
                val inviteLink = QrCodeGenerator.buildDeepLink(user.publicKey, null, null)
                binding.tvPublicKey.text = inviteLink

                // Link WITH name — embedded only in the QR so the recipient's form auto-fills on scan
                val inviteLinkWithName = QrCodeGenerator.buildDeepLink(user.publicKey, null, user.displayName)

                try {
                    val qrBitmap = QrCodeGenerator.generate(inviteLinkWithName, 512)
                    if (qrBitmap != null) {
                        binding.ivQrCode.setImageBitmap(qrBitmap)
                        binding.ivQrCode.visibility = View.VISIBLE
                    } else {
                        binding.ivQrCode.visibility = View.GONE
                    }
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
            val link = binding.tvPublicKey.text.toString()
            if (link.isNotEmpty()) {
                val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Invite Link", link)
                clip.description.extras = PersistableBundle().apply {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
                clipboard.setPrimaryClip(clip)
                Toast.makeText(requireContext(), R.string.key_copied, Toast.LENGTH_SHORT).show()
                // Auto-clear clipboard after 30 seconds
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val current = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                        if (current == link) {
                            clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                        }
                    } catch (_: Exception) { }
                }, 30_000)
            }
        }

        binding.btnShareKey.setOnClickListener {
            val link = binding.tvPublicKey.text.toString()
            if (link.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, link)
                    putExtra(Intent.EXTRA_SUBJECT, "Mon lien d'invitation SecureChat")
                }
                startActivity(Intent.createChooser(intent, "Partager mon invitation"))
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

    private fun maskKey(key: String): String {
        // Strip the fixed X.509 header so the displayed key looks fully random
        val display = runCatching { QrCodeGenerator.stripX25519Header(key) }.getOrDefault(key)
        if (display.length <= 12) return "\u2022".repeat(display.length)
        return display.take(6) + "\u2022".repeat(display.length - 10) + display.takeLast(4)
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
