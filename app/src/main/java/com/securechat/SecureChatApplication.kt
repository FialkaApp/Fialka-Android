package com.securechat

import android.app.Application
import com.google.firebase.FirebaseApp

/**
 * Application class for SecureChat.
 * Initializes Firebase on startup.
 */
class SecureChatApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
    }
}
