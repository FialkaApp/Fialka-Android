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
package com.securechat.ui.chat

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.securechat.R
import com.securechat.crypto.CryptoManager
import com.securechat.data.repository.ChatRepository
import com.securechat.databinding.FragmentFingerprintBinding
import com.securechat.ui.addcontact.CustomScannerActivity
import kotlinx.coroutines.launch

/**
 * Fingerprint verification screen.
 * Toggle between emoji grid and QR code display.
 * QR scanner for automatic verification.
 */
class FingerprintFragment : Fragment() {

    private var _binding: FragmentFingerprintBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: ChatRepository
    private var conversationId: String = ""
    private var contactName: String = ""
    private var isVerified: Boolean = false
    private var showingQr: Boolean = false
    private var fingerprintHex: String = ""

    /** QR scanner result — uses same CustomScannerActivity as contact invite */
    private val scanLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        val scannedData = result.contents
        if (scannedData == null) {
            Toast.makeText(requireContext(), "Scan annulé", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        handleScanResult(scannedData)
    }

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

            // Display emoji fingerprint
            binding.tvFingerprint.text = conversation.sharedFingerprint
            isVerified = conversation.fingerprintVerified
            updateVerificationUI(isVerified)

            // Compute hex fingerprint for QR (deterministic, no emoji encoding issues)
            val myPubKey = CryptoManager.getPublicKey() ?: return@launch
            fingerprintHex = CryptoManager.getSharedFingerprintHex(myPubKey, conversation.participantPublicKey)

            // Generate QR from hex hash
            generateQrCode(fingerprintHex)

            // Toggle emoji ↔ QR on icon tap
            binding.tvToggleIcon.setOnClickListener { toggleView() }
            binding.tvToggleHint.setOnClickListener { toggleView() }

            // Scan QR code (same custom scanner as invite)
            binding.btnScanQr.setOnClickListener { launchScanner() }

            // Manual verify toggle
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

    // ── Toggle emoji ↔ QR ──────────────────────────────

    private fun toggleView() {
        showingQr = !showingQr

        binding.tvToggleIcon.animate()
            .rotationBy(180f)
            .setDuration(300)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        if (showingQr) {
            binding.tvFingerprint.animate().alpha(0f).setDuration(150).withEndAction {
                binding.tvFingerprint.visibility = View.GONE
                binding.ivQrCode.alpha = 0f
                binding.ivQrCode.visibility = View.VISIBLE
                binding.ivQrCode.animate().alpha(1f).setDuration(150).start()
            }.start()
            binding.tvToggleIcon.text = "📱"
            binding.tvToggleHint.text = "Appuyez pour voir les emojis"
            binding.tvExplanation.text = "Montrez ce QR code à votre contact pour qu'il le scanne, ou scannez le sien."
        } else {
            binding.ivQrCode.animate().alpha(0f).setDuration(150).withEndAction {
                binding.ivQrCode.visibility = View.GONE
                binding.tvFingerprint.alpha = 0f
                binding.tvFingerprint.visibility = View.VISIBLE
                binding.tvFingerprint.animate().alpha(1f).setDuration(150).start()
            }.start()
            binding.tvToggleIcon.text = "🔐"
            binding.tvToggleHint.text = "Appuyez pour voir le QR code"
            binding.tvExplanation.text = "Vérifiez que les emojis ci-dessous sont identiques sur les deux téléphones pour confirmer que la conversation est sécurisée."
        }
    }

    // ── QR Code Generation ─────────────────────────────

    private fun generateQrCode(hexData: String) {
        try {
            val writer = QRCodeWriter()
            val size = 512
            val bitMatrix = writer.encode(hexData, BarcodeFormat.QR_CODE, size, size)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            binding.ivQrCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Erreur génération QR", Toast.LENGTH_SHORT).show()
        }
    }

    // ── QR Scanner (same CustomScannerActivity as invite) ──

    private fun launchScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scannez le QR code de votre contact")
            setBeepEnabled(false)
            setOrientationLocked(false)
            setCaptureActivity(CustomScannerActivity::class.java)
            setCameraId(0)
        }
        scanLauncher.launch(options)
    }

    private fun handleScanResult(scannedData: String) {
        // Compare hex fingerprints (deterministic, no encoding issues)
        val match = scannedData.trim().equals(fingerprintHex.trim(), ignoreCase = true)

        if (match) {
            lifecycleScope.launch {
                repository.verifyFingerprint(conversationId, true)
                isVerified = true
                updateVerificationUI(true)
            }
            showResultDialog(
                title = "✅ Empreinte vérifiée",
                message = "Les empreintes correspondent ! La conversation est authentique et sécurisée."
            )
        } else {
            showResultDialog(
                title = "❌ Empreintes différentes",
                message = "ATTENTION : les empreintes ne correspondent pas !\n\nCela peut indiquer une attaque de type homme du milieu (MITM). Ne partagez pas d'informations sensibles dans cette conversation."
            )
        }
    }

    private fun showResultDialog(title: String, message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .setCancelable(true)
            .show()
    }

    // ── Verification UI ────────────────────────────────

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
