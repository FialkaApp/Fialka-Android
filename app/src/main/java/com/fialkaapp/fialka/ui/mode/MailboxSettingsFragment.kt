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
package com.fialkaapp.fialka.ui.mode

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.provider.Settings
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.FragmentMailboxSettingsBinding
import com.fialkaapp.fialka.tor.MailboxClientManager
import com.fialkaapp.fialka.ui.MainActivity
import com.fialkaapp.fialka.ui.addcontact.CustomScannerActivity
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Mailbox settings for NORMAL mode users.
 * Allows scanning/deep-linking to a mailbox, viewing connection info,
 * checking live status, and OWNER admin (members, kick, invite, share link).
 */
class MailboxSettingsFragment : Fragment() {

    private var _binding: FragmentMailboxSettingsBinding? = null
    private val binding get() = _binding!!

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchQrScanner()
        else Toast.makeText(requireContext(), R.string.camera_permission_denied, Toast.LENGTH_SHORT).show()
    }

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) handleScannedMailboxQr(result.contents)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMailboxSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        binding.btnScanMailboxQr.setOnClickListener {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        binding.btnPasteLink.setOnClickListener { showPasteLinkDialog() }

        binding.btnDisconnect.setOnClickListener { confirmDisconnect() }

        binding.btnRefresh.setOnClickListener { checkStatus() }

        binding.btnInviteRemote.setOnClickListener { showRemoteInviteQr() }

        binding.btnShareLink.setOnClickListener { shareMailboxLink() }

        // Handle pending deep link (with optional invite code)
        MainActivity.pendingMailboxJoin?.let { data ->
            MainActivity.pendingMailboxJoin = null
            handleJoinMailbox(data[0], data[1], data[2], data.getOrElse(3) { "" })
        }

        updateUi()

        // Auto-check status if connected
        MailboxClientManager.init(requireContext())
        if (MailboxClientManager.isJoined()) {
            checkStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        MailboxClientManager.init(requireContext())
        if (MailboxClientManager.isJoined()) {
            checkStatus()
        }
    }

    // ── UI Updates ──

    private fun updateUi() {
        MailboxClientManager.init(requireContext())
        val joined = MailboxClientManager.isJoined()
        binding.layoutNotConnected.visibility = if (joined) View.GONE else View.VISIBLE
        binding.layoutConnected.visibility = if (joined) View.VISIBLE else View.GONE

        if (joined) {
            binding.tvOnionAddress.text = MailboxClientManager.getMailboxOnion() ?: "—"
            binding.tvMailboxType.text = MailboxClientManager.getMailboxType() ?: "—"
            val role = MailboxClientManager.role.value ?: "MEMBER"
            binding.tvRole.text = role

            // Show OWNER section if role is OWNER
            val isOwner = role == "OWNER"
            binding.layoutOwnerSection.visibility = if (isOwner) View.VISIBLE else View.GONE
            if (isOwner) loadOwnerMembers()
        }
    }

    // ── Live Status Check ──

    private fun checkStatus() {
        binding.progressPing.visibility = View.VISIBLE
        binding.btnRefresh.visibility = View.GONE
        binding.tvConnectionStatus.text = getString(R.string.mailbox_status_checking)
        binding.tvConnectionStatus.setTextColor(requireContext().getColor(com.fialkaapp.fialka.R.color.grey_medium))

        viewLifecycleOwner.lifecycleScope.launch {
            val online = MailboxClientManager.pingMailbox()
            if (_binding == null) return@launch

            binding.progressPing.visibility = View.GONE
            binding.btnRefresh.visibility = View.VISIBLE

            if (online) {
                binding.tvConnectionStatus.text = getString(R.string.mailbox_status_online)
                binding.tvConnectionStatus.setTextColor(requireContext().getColor(com.fialkaapp.fialka.R.color.green_500))
                val dot = binding.statusDot.background
                if (dot is GradientDrawable) dot.setColor(requireContext().getColor(com.fialkaapp.fialka.R.color.green_500))
            } else {
                binding.tvConnectionStatus.text = getString(R.string.mailbox_status_offline)
                binding.tvConnectionStatus.setTextColor(requireContext().getColor(com.fialkaapp.fialka.R.color.red_500))
                val dot = binding.statusDot.background
                if (dot is GradientDrawable) dot.setColor(requireContext().getColor(com.fialkaapp.fialka.R.color.red_500))
            }
        }
    }

    // ── Owner: Load & Display Members ──

    private fun loadOwnerMembers() {
        binding.progressMembers.visibility = View.VISIBLE
        binding.tvNoMembers.visibility = View.GONE
        binding.memberListContainer.removeAllViews()

        viewLifecycleOwner.lifecycleScope.launch {
            val members = MailboxClientManager.listMembers()
            if (_binding == null) return@launch
            binding.progressMembers.visibility = View.GONE

            if (members == null || members.isEmpty()) {
                binding.tvNoMembers.visibility = View.VISIBLE
                binding.tvOwnerMembersTitle.text = getString(R.string.mailbox_owner_members_title, 0)
                return@launch
            }

            binding.tvOwnerMembersTitle.text = getString(R.string.mailbox_owner_members_title, members.size)
            val myPubKey = com.fialkaapp.fialka.crypto.CryptoManager.getPublicKey()

            for ((pubKeyB64, role, _) in members) {
                val row = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(16, 12, 16, 12)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                val roleEmoji = if (role == "OWNER") "👑 " else "👤 "
                val keyShort = pubKeyB64.take(12) + "…"
                val label = TextView(requireContext()).apply {
                    text = "$roleEmoji$keyShort"
                    textSize = 13f
                    setTextColor(requireContext().getColor(com.fialkaapp.fialka.R.color.grey_medium))
                    typeface = Typeface.MONOSPACE
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                row.addView(label)

                val roleTag = TextView(requireContext()).apply {
                    text = if (role == "OWNER") "Propriétaire" else "Membre"
                    textSize = 11f
                    setTextColor(if (role == "OWNER") 
                        requireContext().getColor(com.fialkaapp.fialka.R.color.orange_500)
                        else requireContext().getColor(com.fialkaapp.fialka.R.color.purple_500))
                    setPadding(12, 4, 12, 4)
                }
                row.addView(roleTag)

                // Kick button — not for self
                if (pubKeyB64 != myPubKey) {
                    val kickBtn = android.widget.ImageButton(requireContext()).apply {
                        setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                        setColorFilter(requireContext().getColor(com.fialkaapp.fialka.R.color.red_500))
                        setBackgroundResource(android.R.color.transparent)
                        setPadding(12, 8, 12, 8)
                        contentDescription = "Kick"
                        setOnClickListener { confirmKickMember(pubKeyB64, keyShort) }
                    }
                    row.addView(kickBtn)
                }

                binding.memberListContainer.addView(row)
            }

            // Show invite button only for PRIVATE mailbox
            val isPrivate = MailboxClientManager.getMailboxType()?.uppercase() == "PRIVATE"
            binding.btnInviteRemote.visibility = if (isPrivate) View.VISIBLE else View.GONE
        }
    }

    // ── Owner: Kick Member ──

    private fun confirmKickMember(pubKeyB64: String, displayKey: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mailbox_owner_kick_title)
            .setMessage(getString(R.string.mailbox_owner_kick_message, displayKey))
            .setPositiveButton(R.string.mailbox_owner_kick_confirm) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = MailboxClientManager.revokeMember(pubKeyB64)
                    if (_binding == null) return@launch
                    if (ok) {
                        Toast.makeText(requireContext(), R.string.mailbox_owner_kicked, Toast.LENGTH_SHORT).show()
                        loadOwnerMembers()
                    } else {
                        Toast.makeText(requireContext(), R.string.mailbox_owner_kick_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── Owner: Generate Invite ──

    private fun showRemoteInviteQr() {
        viewLifecycleOwner.lifecycleScope.launch {
            val code = MailboxClientManager.requestInvite()
            if (_binding == null) return@launch
            if (code != null) {
                val link = MailboxClientManager.buildMailboxLink(code)
                if (link == null) {
                    Toast.makeText(requireContext(), R.string.mailbox_owner_invite_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Compact JSON for QR (much smaller than URL)
                val qrData = JSONObject().apply {
                    put("onion", MailboxClientManager.getMailboxOnion())
                    put("pubkey", MailboxClientManager.getMailboxPubKey())
                    put("type", MailboxClientManager.getMailboxType() ?: "PERSONAL")
                    put("invite", code)
                }.toString()

                val bitmap = com.fialkaapp.fialka.util.QrCodeGenerator.generate(qrData, 512)
                if (bitmap == null) {
                    Toast.makeText(requireContext(), R.string.mailbox_owner_invite_failed, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val container = android.widget.LinearLayout(requireContext()).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = android.view.Gravity.CENTER
                    setPadding(32, 32, 32, 16)
                }
                val iv = android.widget.ImageView(requireContext()).apply {
                    setImageBitmap(bitmap)
                }
                container.addView(iv)
                val tvExpiry = android.widget.TextView(requireContext()).apply {
                    text = getString(R.string.mailbox_invite_qr_validity)
                    setTextColor(requireContext().getColor(com.fialkaapp.fialka.R.color.grey_medium))
                    textSize = 12f
                    setPadding(0, 16, 0, 0)
                }
                container.addView(tvExpiry)

                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.mailbox_invite_qr_title)
                    .setView(container)
                    .setPositiveButton(R.string.mailbox_invite_copy_link) { _, _ ->
                        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("invite_link", link))
                        Toast.makeText(requireContext(), R.string.mailbox_owner_invite_copied, Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("OK", null)
                    .show()
            } else {
                Toast.makeText(requireContext(), R.string.mailbox_owner_invite_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ── Share Mailbox Link ──

    private fun shareMailboxLink() {
        val link = MailboxClientManager.buildMailboxLink()
        if (link != null) {
            val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("mailbox_link", link))
            Toast.makeText(requireContext(), R.string.mailbox_link_copied, Toast.LENGTH_SHORT).show()
        }
    }

    // ── Join Handling (QR + Deep Link + Paste) ──

    private fun handleScannedMailboxQr(raw: String) {
        try {
            val json = JSONObject(raw)
            val onion = json.getString("onion")
            val pubkey = json.getString("pubkey")
            val type = json.optString("type", "PERSONAL")
            val invite = json.optString("invite", "")
            handleJoinMailbox(onion, pubkey, type, invite)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.mailbox_client_invalid_qr, Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleJoinMailbox(onion: String, pubkey: String, type: String, inviteCode: String = "") {
        if (!onion.endsWith(".onion")) {
            Toast.makeText(requireContext(), R.string.mailbox_client_invalid_qr, Toast.LENGTH_SHORT).show()
            return
        }
        MailboxClientManager.init(requireContext())
        MailboxClientManager.saveMailboxInfo(onion, pubkey, type)
        Toast.makeText(requireContext(), R.string.mailbox_client_configured, Toast.LENGTH_SHORT).show()

        // JOIN via Tor — only mark as joined on server confirmation
        viewLifecycleOwner.lifecycleScope.launch {
            val (success, message) = MailboxClientManager.joinMailbox(inviteCode)
            if (_binding == null) return@launch
            if (success) {
                Toast.makeText(requireContext(), getString(R.string.mailbox_client_joined, message), Toast.LENGTH_SHORT).show()
                updateUi()
                checkStatus()
            } else {
                // JOIN was rejected — clear saved config and show error
                MailboxClientManager.disconnect()
                updateUi()
                showJoinError(message)
            }
        }
    }

    private fun showJoinError(message: String) {
        val isClockError = message.contains("horloge", ignoreCase = true) ||
                message.contains("désynchronisée", ignoreCase = true)
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mailbox_client_join_failed_title)
            .setMessage(message)
            .setNegativeButton(R.string.close, null)
        if (isClockError) {
            builder.setPositiveButton(R.string.mailbox_fix_clock) { _, _ ->
                startActivity(Intent(Settings.ACTION_DATE_SETTINGS))
            }
        }
        builder.show()
    }

    private fun showPasteLinkDialog() {
        val editText = EditText(requireContext()).apply {
            hint = getString(R.string.mailbox_client_paste_prompt)
            setPadding(48, 32, 48, 16)
            setTextColor(requireContext().getColor(com.fialkaapp.fialka.R.color.white))
            setHintTextColor(requireContext().getColor(com.fialkaapp.fialka.R.color.grey_medium))
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mailbox_client_paste_link)
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val input = editText.text.toString().trim()
                parseLinkAndJoin(input)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun parseLinkAndJoin(input: String) {
        try {
            val uri = Uri.parse(input)
            if (uri.scheme?.lowercase() != "fialka" || uri.host != "mailbox") {
                Toast.makeText(requireContext(), R.string.mailbox_client_invalid_link, Toast.LENGTH_SHORT).show()
                return
            }
            val onion = uri.getQueryParameter("onion") ?: throw IllegalArgumentException()
            val pubkey = uri.getQueryParameter("pubkey") ?: throw IllegalArgumentException()
            val type = uri.getQueryParameter("type") ?: "PERSONAL"
            val invite = uri.getQueryParameter("invite") ?: ""
            handleJoinMailbox(onion, pubkey, type, invite)
        } catch (_: Exception) {
            Toast.makeText(requireContext(), R.string.mailbox_client_invalid_link, Toast.LENGTH_SHORT).show()
        }
    }

    // ── Disconnect ──

    private fun confirmDisconnect() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.mailbox_client_disconnect_title)
            .setMessage(R.string.mailbox_client_disconnect_message)
            .setPositiveButton(R.string.mailbox_client_disconnect_confirm) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    MailboxClientManager.leaveMailbox()
                    if (_binding != null) {
                        updateUi()
                        Toast.makeText(requireContext(), R.string.mailbox_client_disconnected, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── Scanner ──

    private fun launchQrScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt(getString(R.string.mailbox_client_scan_prompt))
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
