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
package com.fialkaapp.fialka.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.webkit.MimeTypeMap
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fialkaapp.fialka.audio.VoicePlayer
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.data.model.MessageLocal
import com.fialkaapp.fialka.databinding.ItemMessageReceivedBinding
import com.fialkaapp.fialka.databinding.ItemMessageSentBinding
import com.fialkaapp.fialka.databinding.ItemMessageXmrReceivedBinding
import com.fialkaapp.fialka.databinding.ItemMessageXmrSentBinding
import com.fialkaapp.fialka.util.EphemeralManager
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
    private val onOneShotOpen: ((String) -> Unit)? = null,
    private val onResend: ((String) -> Unit)? = null,
    private val onMessageLongPress: ((anchorView: View, message: MessageLocal) -> Unit)? = null,
    /** Called when user taps the action button on a received XMR bubble.
     *  Passes the raw plaintext (XMR_REQUEST|... or XMR_SENT|...). */
    private val onXmrAction: ((plaintext: String) -> Unit)? = null,
    /** Called when user taps a quote bar — passes the replyToId to scroll to. */
    private val onQuoteClick: ((replyToId: String) -> Unit)? = null
) : ListAdapter<ChatItem, RecyclerView.ViewHolder>(ChatItemDiffCallback) {

    private var lastAnimatedPosition = -1

    companion object {
        private const val VIEW_TYPE_SENT = 0
        private const val VIEW_TYPE_RECEIVED = 1
        private const val VIEW_TYPE_UNREAD_DIVIDER = 2
        private const val VIEW_TYPE_INFO = 3
        private const val VIEW_TYPE_XMR_SENT = 4
        private const val VIEW_TYPE_XMR_RECEIVED = 5
        private const val VIEW_TYPE_VOICE_SENT = 6
        private const val VIEW_TYPE_VOICE_RECEIVED = 7

        // XMR message prefixes (E2E encrypted, same channel as text)
        const val PREFIX_XMR_ADDR = "XMR_ADDR|"
        const val PREFIX_XMR_REQUEST = "XMR_REQUEST|"
        const val PREFIX_XMR_SENT = "XMR_SENT|"

        private fun isXmrMessage(text: String): Boolean =
            text.startsWith(PREFIX_XMR_ADDR) ||
            text.startsWith(PREFIX_XMR_REQUEST) ||
            text.startsWith(PREFIX_XMR_SENT)

        private fun isVoiceMessage(msg: com.fialkaapp.fialka.data.model.MessageLocal): Boolean =
            msg.voiceDurationMs > 0

        /**
         * Parse XMR message and bind to the shared bubble views.
         *
         * Formats:
         *  XMR_ADDR|<address>
         *  XMR_REQUEST|<address>|<amount_or_empty>|<note_or_empty>
         *  XMR_SENT|<txHash>|<amountFormatted>|<address>
         */
        private fun bindXmrContent(
            plaintext: String,
            tvTitle: TextView,
            tvAmount: TextView,
            tvNote: TextView,
            tvDetail: TextView,
            btnCopy: ImageView
        ) {
            val ctx = tvTitle.context
            when {
                plaintext.startsWith(PREFIX_XMR_ADDR) -> {
                    val address = plaintext.removePrefix(PREFIX_XMR_ADDR)
                    tvTitle.text = ctx.getString(R.string.xmr_bubble_addr)
                    tvAmount.visibility = View.GONE
                    tvNote.visibility = View.GONE
                    tvDetail.text = address
                    setupCopyButton(btnCopy, address, ctx.getString(R.string.xmr_address_copied))
                }
                plaintext.startsWith(PREFIX_XMR_REQUEST) -> {
                    val parts = plaintext.removePrefix(PREFIX_XMR_REQUEST).split("|", limit = 3)
                    val address = parts.getOrElse(0) { "" }
                    val amount = parts.getOrElse(1) { "" }
                    val note = parts.getOrElse(2) { "" }
                    tvTitle.text = ctx.getString(R.string.xmr_bubble_request)
                    if (amount.isNotEmpty()) {
                        tvAmount.visibility = View.VISIBLE
                        tvAmount.text = "$amount XMR"
                    } else {
                        tvAmount.visibility = View.GONE
                    }
                    if (note.isNotEmpty()) {
                        tvNote.visibility = View.VISIBLE
                        tvNote.text = note
                    } else {
                        tvNote.visibility = View.GONE
                    }
                    tvDetail.text = address
                    setupCopyButton(btnCopy, address, ctx.getString(R.string.xmr_address_copied))
                }
                plaintext.startsWith(PREFIX_XMR_SENT) -> {
                    val parts = plaintext.removePrefix(PREFIX_XMR_SENT).split("|", limit = 3)
                    val txHash = parts.getOrElse(0) { "" }
                    val amount = parts.getOrElse(1) { "" }
                    tvTitle.text = ctx.getString(R.string.xmr_bubble_sent)
                    if (amount.isNotEmpty()) {
                        tvAmount.visibility = View.VISIBLE
                        tvAmount.text = amount
                    } else {
                        tvAmount.visibility = View.GONE
                    }
                    tvNote.visibility = View.GONE
                    tvDetail.text = txHash
                    setupCopyButton(btnCopy, txHash, ctx.getString(R.string.xmr_txhash_copied))
                }
                else -> {
                    tvTitle.text = "XMR"
                    tvAmount.visibility = View.GONE
                    tvNote.visibility = View.GONE
                    tvDetail.text = plaintext
                    btnCopy.visibility = View.GONE
                }
            }
        }

        private fun setupCopyButton(btnCopy: ImageView, content: String, toastMsg: String) {
            if (content.isEmpty()) {
                btnCopy.visibility = View.GONE
                return
            }
            btnCopy.visibility = View.VISIBLE
            btnCopy.setOnClickListener { v ->
                val clipboard = v.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("xmr", content))
                Toast.makeText(v.context, toastMsg, Toast.LENGTH_SHORT).show()
            }
        }
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
            is ChatItem.Message -> when {
                isVoiceMessage(item.message) && item.message.isMine -> VIEW_TYPE_VOICE_SENT
                isVoiceMessage(item.message) -> VIEW_TYPE_VOICE_RECEIVED
                isXmrMessage(item.message.plaintext) && item.message.isMine -> VIEW_TYPE_XMR_SENT
                isXmrMessage(item.message.plaintext) -> VIEW_TYPE_XMR_RECEIVED
                item.message.isMine -> VIEW_TYPE_SENT
                else -> VIEW_TYPE_RECEIVED
            }
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
            VIEW_TYPE_VOICE_SENT -> {
                val binding = com.fialkaapp.fialka.databinding.ItemMessageVoiceSentBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                VoiceSentViewHolder(binding)
            }
            VIEW_TYPE_VOICE_RECEIVED -> {
                val binding = com.fialkaapp.fialka.databinding.ItemMessageVoiceReceivedBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                VoiceReceivedViewHolder(binding)
            }
            VIEW_TYPE_XMR_SENT -> {
                val binding = ItemMessageXmrSentBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                XmrSentViewHolder(binding)
            }
            VIEW_TYPE_XMR_RECEIVED -> {
                val binding = ItemMessageXmrReceivedBinding.inflate(
                    LayoutInflater.from(parent.context), parent, false
                )
                XmrReceivedViewHolder(binding)
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
                holder.bind(msg, onOneShotOpen, onResend, onMessageLongPress, onQuoteClick)
            }
            is ReceivedViewHolder -> {
                val msg = (getItem(position) as ChatItem.Message).message
                holder.bind(msg, onRetryDownload, onOneShotOpen, onMessageLongPress, onQuoteClick)
            }
            is XmrSentViewHolder -> {
                val msg = (getItem(position) as ChatItem.Message).message
                holder.bind(msg)
            }
            is XmrReceivedViewHolder -> {
                val msg = (getItem(position) as ChatItem.Message).message
                holder.bind(msg, onXmrAction)
            }
            is VoiceSentViewHolder -> {
                val msg = (getItem(position) as ChatItem.Message).message
                holder.bind(msg)
            }
            is VoiceReceivedViewHolder -> {
                val msg = (getItem(position) as ChatItem.Message).message
                holder.bind(msg)
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
        fun bind(message: MessageLocal, onOneShotOpen: ((String) -> Unit)? = null, onResend: ((String) -> Unit)? = null, onMessageLongPress: ((anchorView: View, message: MessageLocal) -> Unit)? = null, onQuoteClick: ((replyToId: String) -> Unit)? = null) {
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
                        openFile(v, message.localFilePath)
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
                    binding.btnOpenFileSent.setOnClickListener { openFile(it, message.localFilePath) }
                    binding.bubbleSent.setOnClickListener { openFile(it, message.localFilePath) }
                }
                else -> {
                    // Text message
                    binding.tvMessageSent.visibility = View.VISIBLE
                    binding.tvMessageSent.text = message.plaintext
                    binding.fileRowSent.visibility = View.GONE
                    binding.bubbleSent.setOnClickListener(null)
                    binding.bubbleSent.setOnLongClickListener { v ->
                        onMessageLongPress?.invoke(v, message)
                        true
                    }
                }
            }

            // Quote bar
            if (!message.replyToPreview.isNullOrEmpty()) {
                binding.quoteBarSent.visibility = View.VISIBLE
                binding.tvQuotePreviewSent.text = message.replyToPreview
                if (!message.replyToId.isNullOrEmpty()) {
                    binding.quoteBarSent.setOnClickListener { onQuoteClick?.invoke(message.replyToId) }
                } else {
                    binding.quoteBarSent.setOnClickListener(null)
                }
            } else {
                binding.quoteBarSent.visibility = View.GONE
                binding.quoteBarSent.setOnClickListener(null)
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

            // Delivery status badge
            when (message.deliveryStatus) {
                MessageLocal.DELIVERY_MAILBOX -> {
                    binding.ivSentCheck.visibility = View.VISIBLE
                    binding.tvDeliveryBadge.visibility = View.VISIBLE
                    binding.tvDeliveryBadge.text = "📬"
                    binding.failedRowSent.visibility = View.GONE
                    binding.progressSending.visibility = View.GONE
                }
                MessageLocal.DELIVERY_FAILED -> {
                    binding.ivSentCheck.visibility = View.GONE
                    binding.tvDeliveryBadge.visibility = View.VISIBLE
                    binding.tvDeliveryBadge.text = "❌"
                    binding.failedRowSent.visibility = View.VISIBLE
                    binding.progressSending.visibility = View.GONE
                    binding.btnResend.setOnClickListener { onResend?.invoke(message.localId) }
                }
                MessageLocal.DELIVERY_PENDING -> {
                    binding.ivSentCheck.visibility = View.VISIBLE
                    binding.ivSentCheck.alpha = 0.4f
                    binding.tvDeliveryBadge.visibility = View.VISIBLE
                    binding.tvDeliveryBadge.text = "⏳"
                    binding.failedRowSent.visibility = View.GONE
                    binding.progressSending.visibility = View.GONE
                }
                MessageLocal.DELIVERY_SENDING -> {
                    binding.ivSentCheck.visibility = View.GONE
                    binding.tvDeliveryBadge.visibility = View.GONE
                    binding.failedRowSent.visibility = View.GONE
                    binding.progressSending.visibility = View.VISIBLE
                }
                else -> {
                    // DELIVERY_SENT (direct)
                    binding.ivSentCheck.visibility = View.VISIBLE
                    binding.ivSentCheck.alpha = 1.0f
                    binding.tvDeliveryBadge.visibility = View.GONE
                    binding.failedRowSent.visibility = View.GONE
                    binding.progressSending.visibility = View.GONE
                }
            }
        }
    }

    class ReceivedViewHolder(
        private val binding: ItemMessageReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageLocal, onRetryDownload: ((String) -> Unit)?, onOneShotOpen: ((String) -> Unit)?, onMessageLongPress: ((anchorView: View, message: MessageLocal) -> Unit)? = null, onQuoteClick: ((replyToId: String) -> Unit)? = null) {
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
                    val fileName = message.fileName
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
                        openFile(v, message.localFilePath)
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
                    binding.btnOpenFileReceived.setOnClickListener { openFile(it, message.localFilePath) }
                    binding.bubbleReceived.setOnClickListener { openFile(it, message.localFilePath) }
                }
                else -> {
                    // Text message
                    binding.tvMessageReceived.visibility = View.VISIBLE
                    binding.tvMessageReceived.text = message.plaintext
                    binding.fileRowReceived.visibility = View.GONE
                    binding.statusRowReceived.visibility = View.GONE
                    binding.bubbleReceived.setOnClickListener(null)
                    binding.bubbleReceived.setOnLongClickListener { v ->
                        onMessageLongPress?.invoke(v, message)
                        true
                    }
                }
            }

            // Quote bar
            if (!message.replyToPreview.isNullOrEmpty()) {
                binding.quoteBarReceived.visibility = View.VISIBLE
                binding.tvQuotePreviewReceived.text = message.replyToPreview
                if (!message.replyToId.isNullOrEmpty()) {
                    binding.quoteBarReceived.setOnClickListener { onQuoteClick?.invoke(message.replyToId) }
                } else {
                    binding.quoteBarReceived.setOnClickListener(null)
                }
            } else {
                binding.quoteBarReceived.visibility = View.GONE
                binding.quoteBarReceived.setOnClickListener(null)
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

    /**
     * ViewHolder for XMR sent messages.
     * Parses XMR_ADDR|, XMR_REQUEST|, XMR_SENT| prefixes and renders a Monero bubble.
     */
    /**
     * ViewHolder for sent voice messages.
     * Plays in-memory via VoicePlayer (AudioTrack, no disk write).
     */
    class VoiceSentViewHolder(
        private val binding: com.fialkaapp.fialka.databinding.ItemMessageVoiceSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var player: com.fialkaapp.fialka.audio.VoicePlayer? = null

        fun bind(message: MessageLocal) {
            val durationMs = message.voiceDurationMs
            binding.tvTimestampVoiceSent.text = timeFormat.format(Date(message.timestamp))
            binding.tvVoiceDurationSent.text = VoicePlayer().formatDuration(durationMs)
            applyWaveform(message.voiceWaveform)
            updateDeliveryStatus(message.deliveryStatus)

            binding.btnVoicePlaySent.setOnClickListener {
                togglePlay(message)
            }
        }

        private fun applyWaveform(waveformCsv: String?) {
            if (waveformCsv.isNullOrEmpty()) return
            val bars = listOf(
                binding.waveBar1Sent, binding.waveBar2Sent, binding.waveBar3Sent,
                binding.waveBar4Sent, binding.waveBar5Sent, binding.waveBar6Sent,
                binding.waveBar7Sent, binding.waveBar8Sent
            )
            val samples = waveformCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
            val step = if (samples.size > 1) samples.size.toFloat() / bars.size else 1f
            bars.forEachIndexed { i, bar ->
                val sampleIdx = (i * step).toInt().coerceIn(0, samples.lastIndex.coerceAtLeast(0))
                val amp = samples.getOrElse(sampleIdx) { 50 }.coerceIn(4, 100)
                val params = bar.layoutParams
                params.height = (amp * 24 / 100).coerceAtLeast(3)
                    .let { android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, it.toFloat(),
                        bar.resources.displayMetrics).toInt() }
                bar.layoutParams = params
            }
        }

        private fun togglePlay(message: MessageLocal) {
            val currentPlayer = player
            if (currentPlayer != null && currentPlayer.isPlaying) {
                currentPlayer.stop()
                player = null
                binding.btnVoicePlaySent.setImageResource(R.drawable.ic_play)
                return
            }

            val filePath = message.localFilePath ?: return
            val file = java.io.File(filePath)
            if (!file.exists()) return

            val audioBytes = file.readBytes()
            val vPlayer = com.fialkaapp.fialka.audio.VoicePlayer()
            player = vPlayer
            binding.btnVoicePlaySent.setImageResource(R.drawable.ic_pause)

            vPlayer.play(
                audioBytes = audioBytes,
                onProgress = { currentMs, totalMs ->
                    (binding.root.context as? android.app.Activity)?.runOnUiThread {
                        binding.tvVoiceDurationSent.text = vPlayer.formatDuration(currentMs.toInt())
                    }
                    // Zero the bytes array is handled inside VoicePlayer after decode
                },
                onFinish = {
                    (binding.root.context as? android.app.Activity)?.runOnUiThread {
                        binding.btnVoicePlaySent.setImageResource(R.drawable.ic_play)
                        binding.tvVoiceDurationSent.text = vPlayer.formatDuration(message.voiceDurationMs)
                        player = null
                    }
                    java.util.Arrays.fill(audioBytes, 0) // Zero decrypted bytes after playback
                },
                onError = {
                    (binding.root.context as? android.app.Activity)?.runOnUiThread {
                        binding.btnVoicePlaySent.setImageResource(R.drawable.ic_play)
                        player = null
                    }
                    java.util.Arrays.fill(audioBytes, 0)
                }
            )
        }

        private fun updateDeliveryStatus(status: Int) {
            val iconRes = when (status) {
                MessageLocal.DELIVERY_SENT -> R.drawable.ic_sent_check
                MessageLocal.DELIVERY_MAILBOX -> R.drawable.ic_sent_check
                else -> R.drawable.ic_send
            }
            binding.ivDeliveryStatusVoiceSent.setImageResource(iconRes)
        }
    }

    /**
     * ViewHolder for received voice messages.
     */
    class VoiceReceivedViewHolder(
        private val binding: com.fialkaapp.fialka.databinding.ItemMessageVoiceReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var player: com.fialkaapp.fialka.audio.VoicePlayer? = null

        fun bind(message: MessageLocal) {
            val durationMs = message.voiceDurationMs
            binding.tvTimestampVoiceReceived.text = timeFormat.format(Date(message.timestamp))
            binding.tvVoiceDurationReceived.text = VoicePlayer().formatDuration(durationMs)
            applyWaveform(message.voiceWaveform)

            binding.btnVoicePlayReceived.setOnClickListener {
                togglePlay(message)
            }
        }

        private fun applyWaveform(waveformCsv: String?) {
            if (waveformCsv.isNullOrEmpty()) return
            val bars = listOf(
                binding.waveBar1Received, binding.waveBar2Received, binding.waveBar3Received,
                binding.waveBar4Received, binding.waveBar5Received, binding.waveBar6Received,
                binding.waveBar7Received, binding.waveBar8Received
            )
            val samples = waveformCsv.split(",").mapNotNull { it.trim().toIntOrNull() }
            val step = if (samples.size > 1) samples.size.toFloat() / bars.size else 1f
            bars.forEachIndexed { i, bar ->
                val sampleIdx = (i * step).toInt().coerceIn(0, samples.lastIndex.coerceAtLeast(0))
                val amp = samples.getOrElse(sampleIdx) { 50 }.coerceIn(4, 100)
                val params = bar.layoutParams
                params.height = (amp * 24 / 100).coerceAtLeast(3)
                    .let { android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_DIP, it.toFloat(),
                        bar.resources.displayMetrics).toInt() }
                bar.layoutParams = params
            }
        }

        private fun togglePlay(message: MessageLocal) {
            val currentPlayer = player
            if (currentPlayer != null && currentPlayer.isPlaying) {
                currentPlayer.stop()
                player = null
                binding.btnVoicePlayReceived.setImageResource(R.drawable.ic_play)
                return
            }

            val filePath = message.localFilePath ?: return
            val file = java.io.File(filePath)
            if (!file.exists()) return

            val audioBytes = file.readBytes()
            val vPlayer = com.fialkaapp.fialka.audio.VoicePlayer()
            player = vPlayer
            binding.btnVoicePlayReceived.setImageResource(R.drawable.ic_pause)

            vPlayer.play(
                audioBytes = audioBytes,
                onProgress = { currentMs, _ ->
                    (binding.root.context as? android.app.Activity)?.runOnUiThread {
                        binding.tvVoiceDurationReceived.text = vPlayer.formatDuration(currentMs.toInt())
                    }
                },
                onFinish = {
                    (binding.root.context as? android.app.Activity)?.runOnUiThread {
                        binding.btnVoicePlayReceived.setImageResource(R.drawable.ic_play)
                        binding.tvVoiceDurationReceived.text = vPlayer.formatDuration(message.voiceDurationMs)
                        player = null
                    }
                    java.util.Arrays.fill(audioBytes, 0)
                },
                onError = {
                    (binding.root.context as? android.app.Activity)?.runOnUiThread {
                        binding.btnVoicePlayReceived.setImageResource(R.drawable.ic_play)
                        player = null
                    }
                    java.util.Arrays.fill(audioBytes, 0)
                }
            )
        }
    }

    class XmrSentViewHolder(
        private val binding: ItemMessageXmrSentBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageLocal) {
            binding.tvTimeXmrSent.text = timeFormat.format(Date(message.timestamp))
            bindXmrContent(
                message.plaintext,
                binding.tvXmrTitleSent,
                binding.tvXmrAmountSent,
                binding.tvXmrNoteSent,
                binding.tvXmrDetailSent,
                binding.btnXmrCopySent
            )
        }
    }

    /**
     * ViewHolder for XMR received messages.
     */
    class XmrReceivedViewHolder(
        private val binding: ItemMessageXmrReceivedBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: MessageLocal, onXmrAction: ((String) -> Unit)? = null) {
            binding.tvTimeXmrReceived.text = timeFormat.format(Date(message.timestamp))
            bindXmrContent(
                message.plaintext,
                binding.tvXmrTitleReceived,
                binding.tvXmrAmountReceived,
                binding.tvXmrNoteReceived,
                binding.tvXmrDetailReceived,
                binding.btnXmrCopyReceived
            )
            // Action button: "Payer" on REQUEST, "Voir la TX" on SENT, hidden on ADDR
            val actionBtn = binding.btnXmrActionReceived
            when {
                message.plaintext.startsWith(PREFIX_XMR_REQUEST) -> {
                    actionBtn.visibility = View.VISIBLE
                    actionBtn.text = "Payer"
                    actionBtn.setOnClickListener { onXmrAction?.invoke(message.plaintext) }
                }
                message.plaintext.startsWith(PREFIX_XMR_SENT) -> {
                    actionBtn.visibility = View.VISIBLE
                    actionBtn.text = "Voir la transaction"
                    actionBtn.setOnClickListener { onXmrAction?.invoke(message.plaintext) }
                }
                else -> {
                    actionBtn.visibility = View.GONE
                    actionBtn.setOnClickListener(null)
                }
            }
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
