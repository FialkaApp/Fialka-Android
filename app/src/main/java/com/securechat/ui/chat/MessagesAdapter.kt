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

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.securechat.R
import com.securechat.data.model.MessageLocal
import com.securechat.databinding.ItemMessageReceivedBinding
import com.securechat.databinding.ItemMessageSentBinding
import com.securechat.util.EphemeralManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wrapper to represent either a chat message or a "new messages" divider.
 */
sealed class ChatItem {
    data class Message(val message: MessageLocal) : ChatItem()
    object UnreadDivider : ChatItem()
    data class InfoMessage(val text: String, val timestamp: Long, val clickable: Boolean = false) : ChatItem()
}

class MessagesAdapter(
    private val onFingerprintInfoClick: (() -> Unit)? = null,
    private val onRetryDownload: ((String) -> Unit)? = null,
    private val onOneShotOpen: ((String) -> Unit)? = null
) : ListAdapter<ChatItem, RecyclerView.ViewHolder>(ChatItemDiffCallback) {

    private var lastAnimatedPosition = -1

    companion object {
        private const val VIEW_TYPE_SENT = 0
        private const val VIEW_TYPE_RECEIVED = 1
        private const val VIEW_TYPE_UNREAD_DIVIDER = 2
        private const val VIEW_TYPE_INFO = 3
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        /** Format file size to human-readable string. */
        private fun formatFileSize(bytes: Long): String = when {
            bytes < 1024 -> "$bytes o"
            bytes < 1024 * 1024 -> "${bytes / 1024} Ko"
            else -> String.format(Locale.getDefault(), "%.1f Mo", bytes / (1024.0 * 1024.0))
        }

        /** Safe image extensions (only raster images decoded by BitmapFactory). */
        private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "bmp", "gif")

        /** Check if a filename is a supported image type. */
        private fun isImageFile(fileName: String): Boolean {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return ext in IMAGE_EXTENSIONS
        }

        /**
         * Safely decode a thumbnail from a local file.
         * Security: BitmapFactory is a pure pixel decoder — no code execution.
         * - First pass: inJustDecodeBounds to validate dimensions without allocating memory.
         * - Rejects images > 16384px on either axis (memory bomb protection).
         * - Uses inSampleSize to downsample to a max of 512px.
         * - Catches both Exception and OutOfMemoryError.
         */
        private fun loadSecureThumbnail(filePath: String, maxDim: Int = 512): Bitmap? {
            return try {
                val file = File(filePath)
                if (!file.exists() || file.length() <= 0) return null

                val opts = BitmapFactory.Options()
                opts.inJustDecodeBounds = true
                BitmapFactory.decodeFile(filePath, opts)

                val w = opts.outWidth
                val h = opts.outHeight
                if (w <= 0 || h <= 0 || w > 16384 || h > 16384) return null

                var sampleSize = 1
                while (w / sampleSize > maxDim || h / sampleSize > maxDim) {
                    sampleSize *= 2
                }

                opts.inJustDecodeBounds = false
                opts.inSampleSize = sampleSize
                BitmapFactory.decodeFile(filePath, opts)
            } catch (_: Exception) {
                null
            } catch (_: OutOfMemoryError) {
                null
            }
        }

        /** Bind a secure image preview into the given ImageView. Returns true if an image was loaded. */
        private fun bindImagePreview(imageView: ImageView, filePath: String?, fileName: String?): Boolean {
            if (filePath == null || fileName == null || !isImageFile(fileName)) {
                imageView.visibility = View.GONE
                imageView.setImageDrawable(null)
                return false
            }
            val bitmap = loadSecureThumbnail(filePath)
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap)
                imageView.visibility = View.VISIBLE
                return true
            }
            imageView.visibility = View.GONE
            imageView.setImageDrawable(null)
            return false
        }

        /** Open a decrypted file using the system file viewer. */
        private fun openFile(view: View, filePath: String) {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    Toast.makeText(view.context, "Fichier introuvable", Toast.LENGTH_SHORT).show()
                    return
                }
                val context = view.context
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                // Resolve MIME type from file extension (contentResolver.getType is unreliable for FileProvider)
                val ext = file.extension.lowercase()
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                    ?: "application/octet-stream"

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, mimeType)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    // Fallback: let user pick an app
                    val chooser = Intent.createChooser(intent, "Ouvrir avec…")
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                Toast.makeText(view.context, "Impossible d'ouvrir le fichier", Toast.LENGTH_SHORT).show()
            }
        }

        /** Bind the signature badge: ✅ valid, ⚠️ invalid, hidden if null (no signature). */
        private fun bindSignatureBadge(badge: android.widget.TextView, signatureValid: Boolean?) {
            when (signatureValid) {
                true -> {
                    badge.visibility = View.VISIBLE
                    badge.text = "✅"
                }
                false -> {
                    badge.visibility = View.VISIBLE
                    badge.text = "⚠️"
                }
                null -> {
                    badge.visibility = View.GONE
                }
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = getItem(position)) {
            is ChatItem.UnreadDivider -> VIEW_TYPE_UNREAD_DIVIDER
            is ChatItem.InfoMessage -> VIEW_TYPE_INFO
            is ChatItem.Message -> if (item.message.isMine) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_SENT -> {
                val binding = ItemMessageSentBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                SentViewHolder(binding)
            }
            VIEW_TYPE_UNREAD_DIVIDER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_unread_divider, parent, false)
                UnreadDividerViewHolder(view)
            }
            VIEW_TYPE_INFO -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_info_message, parent, false)
                InfoViewHolder(view)
            }
            else -> {
                val binding = ItemMessageReceivedBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                ReceivedViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is SentViewHolder -> {
                val msg = (getItem(position) as ChatItem.Message).message
                holder.bind(msg, onOneShotOpen)
            }
            is ReceivedViewHolder -> {
                val msg = (getItem(position) as ChatItem.Message).message
                holder.bind(msg, onRetryDownload, onOneShotOpen)
            }
            is InfoViewHolder -> {
                val info = getItem(position) as ChatItem.InfoMessage
                holder.bind(info.text, if (info.clickable) onFingerprintInfoClick else null)
            }
            is UnreadDividerViewHolder -> { /* static view, nothing to bind */ }
        }

        // Animate new items only
        if (position > lastAnimatedPosition) {
            val animRes = when (holder) {
                is SentViewHolder -> R.anim.bubble_in_sent
                is ReceivedViewHolder -> R.anim.bubble_in_received
                else -> R.anim.fade_in
            }
            val anim = AnimationUtils.loadAnimation(holder.itemView.context, animRes)
            holder.itemView.startAnimation(anim)
            lastAnimatedPosition = position
        }
    }

    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        holder.itemView.clearAnimation()
        super.onViewDetachedFromWindow(holder)
    }

    class SentViewHolder(
        private val binding: ItemMessageSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageLocal, onOneShotOpen: ((String) -> Unit)? = null) {
            binding.tvTimeSent.text = timeFormat.format(Date(message.timestamp))
            binding.ivImagePreviewSent.visibility = View.GONE

            val isOneShotExpired = message.isOneShot && message.oneShotOpened
            val isFileReady = message.localFilePath != null && message.fileName != null

            when {
                isOneShotExpired -> {
                    // One-shot already viewed by sender: locked
                    binding.tvMessageSent.visibility = View.GONE
                    binding.fileRowSent.visibility = View.VISIBLE
                    binding.ivFileIconSent.setImageResource(R.drawable.ic_oneshot)
                    binding.tvFileNameSent.text = "Photo éphémère"
                    binding.tvFileSizeSent.text = "Déjà vue"
                    binding.btnOpenFileSent.text = "Verrouillée"
                    binding.btnOpenFileSent.alpha = 0.5f
                    binding.btnOpenFileSent.setOnClickListener(null)
                    binding.bubbleSent.setOnClickListener(null)
                }
                isFileReady && message.isOneShot -> {
                    // One-shot ready: sender can view once
                    binding.tvMessageSent.visibility = View.GONE
                    binding.fileRowSent.visibility = View.VISIBLE
                    binding.ivFileIconSent.setImageResource(R.drawable.ic_oneshot)
                    binding.tvFileNameSent.text = "Photo éphémère"
                    binding.tvFileSizeSent.text = "Vérifier / Ouvrir (1 fois)"
                    binding.btnOpenFileSent.text = "Ouvrir"
                    binding.btnOpenFileSent.alpha = 1.0f
                    val clickListener = View.OnClickListener { v ->
                        // Disable immediately to prevent multiple opens
                        binding.btnOpenFileSent.setOnClickListener(null)
                        binding.bubbleSent.setOnClickListener(null)
                        binding.btnOpenFileSent.text = "Verrouillée"
                        binding.btnOpenFileSent.alpha = 0.5f
                        binding.tvFileSizeSent.text = "Déjà vue"
                        openFile(v, message.localFilePath!!)
                        // Flag as opened immediately in DB (file deletion is delayed in repository)
                        onOneShotOpen?.invoke(message.localId)
                    }
                    binding.btnOpenFileSent.setOnClickListener(clickListener)
                    binding.bubbleSent.setOnClickListener(clickListener)
                }
                isFileReady -> {
                    // Normal file
                    binding.tvMessageSent.visibility = View.GONE
                    binding.fileRowSent.visibility = View.VISIBLE
                    binding.ivFileIconSent.setImageResource(R.drawable.ic_file)
                    binding.tvFileNameSent.text = message.fileName
                    binding.tvFileSizeSent.text = formatFileSize(message.fileSize)
                    binding.btnOpenFileSent.text = "Ouvrir"
                    binding.btnOpenFileSent.alpha = 1.0f
                    binding.btnOpenFileSent.setOnClickListener { openFile(it, message.localFilePath!!) }
                    binding.bubbleSent.setOnClickListener { openFile(it, message.localFilePath!!) }
                }
                else -> {
                    // Text message
                    binding.tvMessageSent.visibility = View.VISIBLE
                    binding.tvMessageSent.text = message.plaintext
                    binding.fileRowSent.visibility = View.GONE
                    binding.bubbleSent.setOnClickListener(null)
                }
            }

            // Ephemeral indicator
            if (message.ephemeralDuration > 0) {
                binding.tvEphemeralSent.visibility = View.VISIBLE
                binding.tvEphemeralSent.text = "⏱ ${EphemeralManager.getShortLabel(message.ephemeralDuration)}"
            } else {
                binding.tvEphemeralSent.visibility = View.GONE
            }

            // Signature badge
            bindSignatureBadge(binding.tvSignatureBadge, message.signatureValid)
        }
    }

    class ReceivedViewHolder(
        private val binding: ItemMessageReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageLocal, onRetryDownload: ((String) -> Unit)?, onOneShotOpen: ((String) -> Unit)?) {
            binding.tvTimeReceived.text = timeFormat.format(Date(message.timestamp))
            binding.ivImagePreviewReceived.visibility = View.GONE

            // Determine state: downloading, error+retry, file ready, or text
            val isDownloading = message.fileName != null && message.localFilePath == null
                    && !message.plaintext.startsWith("⚠️")
            val isError = message.fileName != null && message.localFilePath == null
                    && message.plaintext.startsWith("⚠️")
            val isFileReady = message.localFilePath != null && message.fileName != null
            val isOneShotExpired = message.isOneShot && message.oneShotOpened

            when {
                isOneShotExpired -> {
                    // One-shot already viewed: show expired message
                    binding.tvMessageReceived.visibility = View.GONE
                    binding.fileRowReceived.visibility = View.VISIBLE
                    binding.statusRowReceived.visibility = View.GONE
                    binding.ivFileIconReceived.setImageResource(R.drawable.ic_oneshot)
                    binding.tvFileNameReceived.text = "Photo éphémère"
                    binding.tvFileSizeReceived.text = "Déjà vue"
                    binding.btnOpenFileReceived.text = "Expirée"
                    binding.btnOpenFileReceived.alpha = 0.5f
                    binding.btnOpenFileReceived.setOnClickListener(null)
                    binding.bubbleReceived.setOnClickListener(null)
                }
                isDownloading -> {
                    binding.tvMessageReceived.visibility = View.GONE
                    binding.fileRowReceived.visibility = View.GONE
                    binding.statusRowReceived.visibility = View.VISIBLE
                    binding.progressReceived.visibility = View.VISIBLE
                    binding.tvStatusReceived.text = "Téléchargement…"
                    binding.btnRetryReceived.visibility = View.GONE
                    binding.bubbleReceived.setOnClickListener(null)
                }
                isError -> {
                    val fileName = message.fileName ?: "fichier"
                    binding.tvMessageReceived.visibility = View.GONE
                    binding.fileRowReceived.visibility = View.GONE
                    binding.statusRowReceived.visibility = View.VISIBLE
                    binding.progressReceived.visibility = View.GONE
                    binding.tvStatusReceived.text = "⚠️ Échec : $fileName"
                    binding.btnRetryReceived.visibility = View.VISIBLE
                    binding.btnRetryReceived.setOnClickListener {
                        onRetryDownload?.invoke(message.localId)
                    }
                    binding.bubbleReceived.setOnClickListener(null)
                }
                isFileReady && message.isOneShot -> {
                    // One-shot ready: show fire icon + "Voir" button
                    binding.tvMessageReceived.visibility = View.GONE
                    binding.fileRowReceived.visibility = View.VISIBLE
                    binding.statusRowReceived.visibility = View.GONE
                    binding.ivFileIconReceived.setImageResource(R.drawable.ic_oneshot)
                    binding.tvFileNameReceived.text = "Photo éphémère"
                    binding.tvFileSizeReceived.text = "Visible une seule fois"
                    binding.btnOpenFileReceived.text = "Voir"
                    binding.btnOpenFileReceived.alpha = 1.0f
                    val clickListener = View.OnClickListener { v ->
                        // Disable immediately to prevent multiple opens
                        binding.btnOpenFileReceived.setOnClickListener(null)
                        binding.bubbleReceived.setOnClickListener(null)
                        binding.btnOpenFileReceived.text = "Expirée"
                        binding.btnOpenFileReceived.alpha = 0.5f
                        binding.tvFileSizeReceived.text = "Déjà vue"
                        openFile(v, message.localFilePath!!)
                        // Flag as opened immediately in DB (file deletion is delayed in repository)
                        onOneShotOpen?.invoke(message.localId)
                    }
                    binding.btnOpenFileReceived.setOnClickListener(clickListener)
                    binding.bubbleReceived.setOnClickListener(clickListener)
                }
                isFileReady -> {
                    // Normal file ready
                    binding.tvMessageReceived.visibility = View.GONE
                    binding.fileRowReceived.visibility = View.VISIBLE
                    binding.statusRowReceived.visibility = View.GONE
                    binding.ivFileIconReceived.setImageResource(R.drawable.ic_file)
                    binding.tvFileNameReceived.text = message.fileName
                    binding.tvFileSizeReceived.text = formatFileSize(message.fileSize)
                    binding.btnOpenFileReceived.text = "Ouvrir"
                    binding.btnOpenFileReceived.alpha = 1.0f
                    binding.btnOpenFileReceived.setOnClickListener { openFile(it, message.localFilePath!!) }
                    binding.bubbleReceived.setOnClickListener { openFile(it, message.localFilePath!!) }
                }
                else -> {
                    // Text message
                    binding.tvMessageReceived.visibility = View.VISIBLE
                    binding.tvMessageReceived.text = message.plaintext
                    binding.fileRowReceived.visibility = View.GONE
                    binding.statusRowReceived.visibility = View.GONE
                    binding.bubbleReceived.setOnClickListener(null)
                }
            }

            // Ephemeral indicator
            if (message.ephemeralDuration > 0) {
                binding.tvEphemeralReceived.visibility = View.VISIBLE
                binding.tvEphemeralReceived.text = "⏱ ${EphemeralManager.getShortLabel(message.ephemeralDuration)}"
            } else {
                binding.tvEphemeralReceived.visibility = View.GONE
            }

            // Signature badge
            bindSignatureBadge(binding.tvSignatureBadge, message.signatureValid)
        }
    }

    class UnreadDividerViewHolder(view: View) : RecyclerView.ViewHolder(view)

    class InfoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvInfoMessage: android.widget.TextView = view.findViewById(R.id.tvInfoMessage)
        fun bind(text: String, onClick: (() -> Unit)?) {
            if (onClick != null) {
                val link = "Voir l'empreinte"
                val full = "$text\n$link"
                val spannable = android.text.SpannableString(full)
                val start = full.indexOf(link)
                spannable.setSpan(
                    object : android.text.style.ClickableSpan() {
                        override fun onClick(widget: View) { onClick() }
                        override fun updateDrawState(ds: android.text.TextPaint) {
                            ds.isUnderlineText = true
                            ds.color = ds.linkColor
                        }
                    },
                    start, start + link.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                tvInfoMessage.text = spannable
                tvInfoMessage.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            } else {
                tvInfoMessage.text = text
                tvInfoMessage.movementMethod = null
            }
        }
    }

    object ChatItemDiffCallback : DiffUtil.ItemCallback<ChatItem>() {
        override fun areItemsTheSame(a: ChatItem, b: ChatItem): Boolean {
            if (a is ChatItem.UnreadDivider && b is ChatItem.UnreadDivider) return true
            if (a is ChatItem.InfoMessage && b is ChatItem.InfoMessage) return a.timestamp == b.timestamp
            if (a is ChatItem.Message && b is ChatItem.Message) return a.message.localId == b.message.localId
            return false
        }

        override fun areContentsTheSame(a: ChatItem, b: ChatItem): Boolean = a == b
    }
}
