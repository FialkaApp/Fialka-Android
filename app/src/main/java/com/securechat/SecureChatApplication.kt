package com.securechat

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.securechat.crypto.CryptoManager
import com.securechat.crypto.MnemonicManager
import com.securechat.util.ThemeManager

/**
 * Application class for SecureChat.
 * Initializes Firebase, notification channels, and applies saved theme on startup.
 */
class SecureChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        CryptoManager.init(this)
        MnemonicManager.init(this)
        ThemeManager.applySavedTheme(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                MyFirebaseMessagingService.CHANNEL_ID,
                "Messages SecureChat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications de nouveaux messages"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }
}
