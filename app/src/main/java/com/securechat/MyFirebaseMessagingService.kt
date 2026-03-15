package com.securechat

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.securechat.data.local.SecureChatDatabase
import com.securechat.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * FCM service — handles incoming push notifications and token refresh.
 *
 * Push notifications are OPT-IN (disabled by default).
 * NEVER contains message content — only metadata (conversationId, sender name).
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Only process data-only messages from our Cloud Function
        val data = remoteMessage.data
        if (data.isEmpty()) return

        val conversationId = data["conversationId"] ?: return
        val senderDisplayName = data["senderDisplayName"] ?: "Un contact"

        // Don't show notification if the user is currently viewing this conversation
        if (com.securechat.data.repository.ChatRepository.currentlyViewedConversation == conversationId) {
            return
        }

        // Look up local contact name (may differ from Firebase displayName)
        serviceScope.launch {
            val db = SecureChatDatabase.getInstance(applicationContext)
            val conversation = db.conversationDao().getConversationById(conversationId)
            val displayName = conversation?.contactDisplayName ?: senderDisplayName

            showNotification(conversationId, displayName)
        }
    }

    override fun onNewToken(token: String) {
        // If push notifications are enabled, update the token on Firebase
        val prefs = getSharedPreferences("securechat_settings", MODE_PRIVATE)
        val pushEnabled = prefs.getBoolean("push_notifications_enabled", false)
        if (pushEnabled) {
            serviceScope.launch {
                try {
                    com.securechat.data.remote.FirebaseRelay.storeFcmToken(token)
                } catch (_: Exception) { }
            }
        }
    }

    private fun showNotification(conversationId: String, senderName: String) {
        // Check notification permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, conversationId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("SecureChat")
            .setContentText("Nouveau message de $senderName")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        NotificationManagerCompat.from(this).notify(conversationId.hashCode(), notification)
    }

    companion object {
        const val CHANNEL_ID = "securechat_messages"
    }
}
