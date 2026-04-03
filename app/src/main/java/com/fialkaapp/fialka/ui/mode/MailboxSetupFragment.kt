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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.AppMode
import com.fialkaapp.fialka.MailboxType
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.databinding.FragmentMailboxSetupBinding
import com.fialkaapp.fialka.tor.MailboxServer
import com.fialkaapp.fialka.tor.OutboxManager
import com.fialkaapp.fialka.tor.P2PServer
import com.fialkaapp.fialka.tor.TorManager
import com.fialkaapp.fialka.util.QrCodeGenerator
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Mailbox setup — 3-step flow:
 *   Step 1: Choose Personal or Private → press Continuer
 *   Step 2: Loading (Tor bootstrap + MailboxServer start)
 *   Step 3: Owner QR displayed → press Valider → dashboard (QR never shown again)
 */
class MailboxSetupFragment : Fragment() {

    private var _binding: FragmentMailboxSetupBinding? = null
    private val binding get() = _binding!!

    private var selectedType: MailboxType? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMailboxSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── Step 1: Choose type ──
        binding.cardPersonal.setOnClickListener { selectType(MailboxType.PERSONAL) }
        binding.cardPrivate.setOnClickListener { selectType(MailboxType.PRIVATE) }

        binding.btnStartSetup.setOnClickListener {
            val type = selectedType ?: return@setOnClickListener
            startMailboxSetup(type)
        }

        // ── Step 3: Validate owner QR ──
        binding.btnValidateOwner.setOnClickListener {
            AppMode.setOwnerQrValidated(requireContext())
            findNavController().navigate(R.id.action_mailboxSetup_to_dashboard)
        }
    }

    private fun selectType(type: MailboxType) {
        selectedType = type
        binding.cardPersonal.strokeWidth = if (type == MailboxType.PERSONAL) 3 else 1
        binding.cardPrivate.strokeWidth = if (type == MailboxType.PRIVATE) 3 else 1
        binding.btnStartSetup.isEnabled = true
    }

    private fun startMailboxSetup(type: MailboxType) {
        // Save type + generate identity
        AppMode.setMailboxType(requireContext(), type)
        if (!CryptoManager.hasIdentity()) {
            CryptoManager.generateIdentity()
            // MAILBOX mode: auto-mark seed as verified — server devices follow
            // a different onboarding path; they can access backup from settings.
            CryptoManager.markSeedVerified()
        }

        // ── Step 2: Show loading ──
        binding.stepChoose.visibility = View.GONE
        binding.stepLoading.visibility = View.VISIBLE

        // Stop P2PServer + OutboxManager that were started for NOT_SET mode
        P2PServer.stop()
        OutboxManager.stop()

        // Init + start mailbox server
        MailboxServer.init(requireContext())
        MailboxServer.start()

        // Identity now exists — tell Tor to publish the Hidden Service
        TorManager.publishOnionIfReady()

        // Wait for .onion to become available
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                TorManager.onionAddress.collect { address ->
                    if (_binding == null || address == null) return@collect
                    showOwnerQr(address, type)
                }
            }
        }
    }

    private fun showOwnerQr(onionAddress: String, type: MailboxType) {
        // ── Step 3: Show Owner QR ──
        binding.stepLoading.visibility = View.GONE
        binding.stepOwnerQr.visibility = View.VISIBLE

        val pubKeyB64 = CryptoManager.getPublicKey() ?: ""
        val qrData = JSONObject().apply {
            put("onion", onionAddress)
            put("pubkey", pubKeyB64)
            put("type", type.name)
        }.toString()

        val bitmap = QrCodeGenerator.generate(qrData, 512)
        binding.ivQrCode.setImageBitmap(bitmap)
        binding.tvOnionAddress.text = onionAddress
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
