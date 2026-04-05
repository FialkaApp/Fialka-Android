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
package com.fialkaapp.fialka.util

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fialkaapp.fialka.FialkaApplication
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.data.repository.ChatRepository
import com.fialkaapp.fialka.ui.MainActivity

/**
 * Central helper for posting in-app and system notifications.
 *
 * - New message: fires only when push is enabled AND user isn't viewing that conversation.
 * - Contact request: fires always (no pref gate — requests are rare and important).
 * - Privacy-first: notification body never contains message plaintext.
 */
object NotificationHelper {

    private const val PREFS_NAME = "fialka_settings"
    private const val KEY_PUSH_ENABLED = "push_notifications_enabled"

    /** Stable ID used for all contact-request notifications (grouped under one). */
    private const val NOTIF_ID_CONTACT_REQUEST = 9_001

    /** Extra key carrying the conversation ID in the tap-to-open PendingIntent. */
    const val EXTRA_CONVERSATION_ID = "extra_conversation_id"

    /** Extra key carrying the contact display name in the tap-to-open PendingIntent. */
    const val EXTRA_CONTACT_NAME = "extra_contact_name"

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /**
     * Post a new-message notification for [conversationId] / [contactName].
     * Silently dropped if:
     *  - The user didn't enable push notifications in Settings
     *  - The user is currently viewing [conversationId]
     *  - POST_NOTIFICATIONS permission is missing (Android 13+)
     */
    fun notifyNewMessage(context: Context, conversationId: String, contactName: String) {
        if (!isPushEnabled(context)) return
        if (ChatRepository.currentlyViewedConversation == conversationId) return
        if (!hasPermission(context)) return

        val pendingIntent = buildOpenChatIntent(context, conversationId, contactName)

        val notification = NotificationCompat.Builder(context, FialkaApplication.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(contactName)
            .setContentText(context.getString(R.string.notif_new_message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setGroup("fialka_messages_$conversationId")
            .build()

        // Use abs() to avoid the rare Int.MIN_VALUE edge case from hashCode()
        val notifId = conversationId.hashCode().let { if (it == Int.MIN_VALUE) 0 else Math.abs(it) }
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    /**
     * Post a contact-request notification from [fromName].
     * Tapping opens the Conversations screen (requests are shown there).
     */
    fun notifyContactRequest(context: Context, fromName: String) {
        if (!hasPermission(context)) return

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIF_ID_CONTACT_REQUEST,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, FialkaApplication.NOTIFICATION_CHANNEL_REQUESTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notif_contact_request_title))
            .setContentText(context.getString(R.string.notif_contact_request_text, fromName))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIF_ID_CONTACT_REQUEST, notification)
    }

    /**
     * Cancel the new-message notification for [conversationId].
     * Called when the user opens a chat, so the notification disappears immediately.
     */
    fun cancelConversation(context: Context, conversationId: String) {
        val notifId = conversationId.hashCode().let { if (it == Int.MIN_VALUE) 0 else Math.abs(it) }
        NotificationManagerCompat.from(context).cancel(notifId)
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private fun isPushEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_PUSH_ENABLED, false)

    private fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
    }

    private fun buildOpenChatIntent(
        context: Context,
        conversationId: String,
        contactName: String
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "com.fialkaapp.fialka.ACTION_OPEN_CHAT"
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_CONTACT_NAME, contactName)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        // Use conversationId.hashCode() as the request code to get unique PendingIntents
        // per conversation; avoids collisions between different chat notifications.
        val requestCode = conversationId.hashCode().let { if (it == Int.MIN_VALUE) 0 else Math.abs(it) }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
