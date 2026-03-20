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

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.securechat.R
import com.securechat.data.repository.ChatRepository
import com.securechat.databinding.FragmentChatBinding
import com.securechat.util.EphemeralManager
import kotlinx.coroutines.launch
import java.io.File

/**
 * Chat screen — displays messages for a conversation.
 *
 * Flow:
 *  1. User types a message and taps send.
 *  2. ChatViewModel encrypts the message using CryptoManager (ECDH + AES-256-GCM).
 *  3. Encrypted message is sent to Firebase via FirebaseRelay.
 *  4. Plaintext is saved locally in Room.
 *  5. Incoming messages from Firebase are decrypted and displayed.
 */
class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ChatViewModel by viewModels()
    private lateinit var adapter: MessagesAdapter

    private var conversationId: String = ""
    private var contactName: String = ""

    /** Max file size for E2E file sharing (25 MB). */
    private val MAX_FILE_SIZE = 25L * 1024 * 1024

    /** Whether the attachment icons are expanded. */
    private var attachmentExpanded = false

    /** Temp file URI for camera capture. */
    private var cameraPhotoUri: Uri? = null

    // ── Image picker (Android Photo Picker — sandboxed, no broad access) ──
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri ?: return@registerForActivityResult
        processSelectedUri(uri, isImage = true)
    }

    // ── File picker (any document) ──
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        processSelectedUri(uri, isImage = false)
    }

    // ── Camera capture ──
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraPhotoUri?.let { uri -> processSelectedUri(uri, isImage = true) }
        }
    }

    // ── Camera permission ──
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            Toast.makeText(requireContext(), "Permission caméra refusée", Toast.LENGTH_SHORT).show()
        }
    }

    // ── Photo permission (Android 13+: READ_MEDIA_IMAGES) ──
    private val photoPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchPhotoPicker()
        } else {
            showPermissionDeniedDialog("photos et vidéos")
        }
    }

    // ── File permission (Android 13+: READ_MEDIA_AUDIO) ──
    private val filePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchFilePicker()
        } else {
            showPermissionDeniedDialog("fichiers, musique et audio")
        }
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
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Toolbar — custom title + fingerprint badge
        binding.tvToolbarTitle.text = contactName
        binding.toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }

        // Navigate to contact profile on toolbar content click or menu
        val navigateToProfile = {
            findNavController().navigate(
                R.id.action_chat_to_conversationProfile,
                bundleOf(
                    "conversationId" to conversationId,
                    "contactName" to contactName
                )
            )
        }
        binding.toolbarContent.setOnClickListener { navigateToProfile() }
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_contact_profile -> {
                    navigateToProfile()
                    true
                }
                else -> false
            }
        }

        // Load fingerprint badge
        loadFingerprintBadge()

        adapter = MessagesAdapter {
            findNavController().navigate(
                R.id.action_chat_to_fingerprint,
                bundleOf(
                    "conversationId" to conversationId,
                    "contactName" to contactName
                )
            )
        }
        binding.rvMessages.adapter = adapter

        // Initialize ViewModel with conversation ID
        viewModel.init(conversationId)

        // Observe chat items (messages + optional unread divider)
        viewModel.chatItems.observe(viewLifecycleOwner) { items ->
            adapter.submitList(items) {
                // Scroll to bottom when new messages arrive
                if (items.isNotEmpty()) {
                    binding.rvMessages.scrollToPosition(items.size - 1)
                }
            }
        }

        // Observe acceptance status
        viewModel.isAccepted.observe(viewLifecycleOwner) { accepted ->
            if (accepted) {
                binding.inputBar.visibility = View.VISIBLE
                binding.tvPendingBanner.visibility = View.GONE
            } else {
                binding.inputBar.visibility = View.GONE
                binding.tvPendingBanner.visibility = View.VISIBLE
            }
        }

        // Send button
        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString()
            if (text.isNotBlank()) {
                viewModel.sendMessage(text)
                binding.etMessage.text?.clear()
            }
        }

        // Attach button — toggle inline icons (Session-style)
        binding.btnAttach.setOnClickListener {
            toggleAttachmentIcons()
        }

        // Inline attachment icon actions
        binding.btnPickPhoto.setOnClickListener {
            collapseAttachmentIcons()
            requestPhotoPermissionAndPick()
        }
        binding.btnPickCamera.setOnClickListener {
            collapseAttachmentIcons()
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                launchCamera()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
        binding.btnPickFile.setOnClickListener {
            collapseAttachmentIcons()
            requestFilePermissionAndPick()
        }

        // Tap anywhere outside icons to dismiss
        binding.attachmentOverlay.setOnClickListener {
            collapseAttachmentIcons()
        }

        // Error handling
        viewModel.sendError.observe(viewLifecycleOwner) { error ->
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
        }

        // Dead conversation detection
        viewModel.conversationDead.observe(viewLifecycleOwner) { dead ->
            if (dead) {
                AlertDialog.Builder(requireContext())
                    .setTitle("Conversation supprimée")
                    .setMessage("Ce contact a supprimé son compte. Cette conversation n'existe plus sur le serveur.")
                    .setCancelable(false)
                    .setPositiveButton("Supprimer") { _, _ ->
                        viewModel.deleteDeadConversation()
                        findNavController().navigateUp()
                    }
                    .setNegativeButton("Retour") { _, _ ->
                        findNavController().navigateUp()
                    }
                    .show()
            }
        }

        // Observe ephemeral duration changes (real-time sync from other user)
        viewModel.ephemeralDuration.observe(viewLifecycleOwner) { duration ->
            if (_binding != null) {
                if (duration > 0) {
                    binding.tvEphemeralBadge.visibility = View.VISIBLE
                    binding.tvEphemeralBadge.text = "⏱ ${EphemeralManager.getShortLabel(duration)}"
                } else {
                    binding.tvEphemeralBadge.visibility = View.GONE
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh badge when returning from profile (may have been verified)
        if (_binding != null) loadFingerprintBadge()
    }

    private fun loadFingerprintBadge() {
        lifecycleScope.launch {
            val repo = ChatRepository(requireContext())
            val conversation = repo.getConversation(conversationId)
            if (conversation != null && _binding != null) {
                val preview = conversation.sharedFingerprint.take(8) // First 4 emojis
                if (conversation.fingerprintVerified) {
                    binding.tvFingerprintBadge.text = "$preview  ✅ Vérifié"
                    binding.tvFingerprintBadge.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.green_verified)
                    )
                } else {
                    binding.tvFingerprintBadge.text = "$preview  ⚠️ Non vérifié"
                    binding.tvFingerprintBadge.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.orange_warning)
                    )
                }

                // Ephemeral badge
                if (conversation.ephemeralDuration > 0) {
                    binding.tvEphemeralBadge.visibility = View.VISIBLE
                    binding.tvEphemeralBadge.text = "⏱ ${EphemeralManager.getShortLabel(conversation.ephemeralDuration)}"
                } else {
                    binding.tvEphemeralBadge.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Attachment flow ──

    private fun toggleAttachmentIcons() {
        if (attachmentExpanded) {
            collapseAttachmentIcons()
        } else {
            expandAttachmentIcons()
        }
    }

    private fun expandAttachmentIcons() {
        attachmentExpanded = true
        binding.attachmentOverlay.visibility = View.VISIBLE
        val container = binding.attachmentIcons
        container.visibility = View.VISIBLE
        container.alpha = 0f
        container.translationY = 80f
        container.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        // Rotate + to ×
        binding.btnAttach.animate().rotation(45f).setDuration(200).start()
    }

    private fun collapseAttachmentIcons() {
        if (!attachmentExpanded) return
        attachmentExpanded = false
        binding.attachmentOverlay.visibility = View.GONE
        val container = binding.attachmentIcons
        container.animate()
            .alpha(0f)
            .translationY(80f)
            .setDuration(150)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { container.visibility = View.GONE }
            .start()
        binding.btnAttach.animate().rotation(0f).setDuration(200).start()
    }

    private fun requestPhotoPermissionAndPick() {
        // PickVisualMedia is sandboxed and doesn't need READ_MEDIA_IMAGES,
        // but we check for consistency with Session UX on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED -> {
                    imagePickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
                shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_IMAGES) -> {
                    showPermissionDeniedDialog("photos and images")
                }
                else -> {
                    photoPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
        } else {
            // Pre-13: PickVisualMedia works without permission
            imagePickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    private fun requestFilePermissionAndPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_AUDIO)
                    == PackageManager.PERMISSION_GRANTED -> {
                    filePickerLauncher.launch("*/*")
                }
                shouldShowRequestPermissionRationale(Manifest.permission.READ_MEDIA_AUDIO) -> {
                    showPermissionDeniedDialog("files and audio")
                }
                else -> {
                    filePermissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
                }
            }
        } else {
            filePickerLauncher.launch("*/*")
        }
    }

    private fun showPermissionDeniedDialog(type: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Permission Required")
            .setMessage("SecureChat needs access to $type to send attachments. Please enable it in Settings.")
            .setPositiveButton("Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", requireContext().packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun launchPhotoPicker() {
        imagePickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun launchFilePicker() {
        filePickerLauncher.launch("*/*")
    }

    private fun launchCamera() {
        val photoFile = File(requireContext().cacheDir, "camera_${System.currentTimeMillis()}.jpg")
        cameraPhotoUri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.fileprovider", photoFile
        )
        cameraLauncher.launch(cameraPhotoUri)
    }

    /**
     * Read bytes + filename from a URI, then show the confirmation dialog.
     */
    private fun processSelectedUri(uri: Uri, isImage: Boolean) {
        try {
            val contentResolver = requireContext().contentResolver
            var fileBytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return

            if (fileBytes.size > MAX_FILE_SIZE) {
                Toast.makeText(requireContext(), "Fichier trop volumineux (max 25 Mo)", Toast.LENGTH_SHORT).show()
                return
            }

            val cursor = contentResolver.query(uri, null, null, null, null)
            val originalName = cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) it.getString(nameIndex) else null
                } else null
            } ?: "file"

            val mimeType = contentResolver.getType(uri)
            val isImageType = isImage || mimeType?.startsWith("image/") == true

            // Strip EXIF/metadata from images by re-encoding through Bitmap
            if (isImageType) {
                fileBytes = stripImageMetadata(fileBytes, mimeType)
            }

            // Anonymize filename: IMG_<hex> for images, DOC_<hex> for others
            val ext = originalName.substringAfterLast('.', if (isImageType) "jpg" else "bin")
            val randomHex = java.security.SecureRandom().nextLong().let {
                String.format("%016x", it)
            }
            val fileName = if (isImageType) "IMG_$randomHex.$ext" else "DOC_$randomHex.$ext"

            showConfirmSendDialog(fileBytes, fileName, uri, isImageType)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Erreur: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Strip all metadata (EXIF, GPS, device info) from image bytes
     * by decoding to Bitmap and re-encoding.
     */
    private fun stripImageMetadata(fileBytes: ByteArray, mimeType: String?): ByteArray {
        return try {
            val bitmap = BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.size)
                ?: return fileBytes  // Not a decodable image — return as-is
            val format = when {
                mimeType == "image/png" -> android.graphics.Bitmap.CompressFormat.PNG
                mimeType == "image/webp" -> android.graphics.Bitmap.CompressFormat.WEBP
                else -> android.graphics.Bitmap.CompressFormat.JPEG
            }
            val quality = if (format == android.graphics.Bitmap.CompressFormat.PNG) 100 else 90
            val out = java.io.ByteArrayOutputStream()
            bitmap.compress(format, quality, out)
            bitmap.recycle()
            out.toByteArray()
        } catch (_: Exception) {
            fileBytes  // On error, send original bytes rather than failing
        }
    }

    /**
     * Show a confirmation dialog with preview before sending.
     */
    private fun showConfirmSendDialog(
        fileBytes: ByteArray,
        fileName: String,
        uri: Uri,
        isImage: Boolean
    ) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_confirm_send, null)

        val ivPreview = dialogView.findViewById<ImageView>(R.id.ivPreview)
        val layoutFileInfo = dialogView.findViewById<LinearLayout>(R.id.layoutFileInfo)
        val tvFileName = dialogView.findViewById<TextView>(R.id.tvFileName)
        val tvFileSize = dialogView.findViewById<TextView>(R.id.tvFileSize)

        if (isImage) {
            ivPreview.visibility = View.VISIBLE
            layoutFileInfo.visibility = View.GONE
            try {
                val bitmap = BitmapFactory.decodeByteArray(fileBytes, 0, fileBytes.size)
                if (bitmap != null) {
                    ivPreview.setImageBitmap(bitmap)
                } else {
                    ivPreview.setImageURI(uri)
                }
            } catch (_: Exception) {
                ivPreview.setImageURI(uri)
            }
        } else {
            ivPreview.visibility = View.GONE
            layoutFileInfo.visibility = View.VISIBLE
            tvFileName.text = fileName
            tvFileSize.text = formatFileSize(fileBytes.size.toLong())
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Envoyer à $contactName ?")
            .setView(dialogView)
            .setPositiveButton("Envoyer") { _, _ ->
                viewModel.sendFile(fileBytes, fileName)
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes o"
            bytes < 1024 * 1024 -> "${bytes / 1024} Ko"
            else -> String.format("%.1f Mo", bytes / (1024.0 * 1024.0))
        }
    }
}
