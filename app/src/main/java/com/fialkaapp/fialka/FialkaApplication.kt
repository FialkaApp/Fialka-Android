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
package com.fialkaapp.fialka

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.crypto.MnemonicManager
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.tor.MailboxClientManager
import com.fialkaapp.fialka.tor.MailboxServer
import com.fialkaapp.fialka.tor.OutboxManager
import com.fialkaapp.fialka.tor.P2PServer
import com.fialkaapp.fialka.tor.TorManager
import com.fialkaapp.fialka.util.DeviceSecurityManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Application class for Fialka.
 * Initializes notification channels. Theme applied per-Activity.
 */
class FialkaApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Load SQLCipher native library — required before ANY Room database is opened.
        // sqlcipher-android 4.14.1 removed the static initializer; app must load explicitly.
        System.loadLibrary("sqlcipher")
        // BC provider NOT registered — Ed25519 uses BC lightweight API directly,
        // and registering platform's stripped BC at position 1 breaks TLS.
        // Pre-warm DeviceSecurityManager on IO thread so the StrongBox probe
        // (100–300 ms) doesn't block the main thread when CryptoManager.init() calls it.
        CoroutineScope(Dispatchers.IO).launch {
            DeviceSecurityManager.getSecurityProfile(applicationContext)
        }
        CryptoManager.init(this)
        MnemonicManager.init(this)
        TorManager.init(this)
        // Tor is mandatory — always start
        TorManager.start()
        createNotificationChannel()

        // Mode-conditional initialization
        when (AppMode.getMode(this)) {
            AppModeType.MAILBOX -> {
                MailboxServer.init(this)
                MailboxServer.start()
            }
            else -> {
                // NORMAL or NOT_SET — init outbox + P2P server
                OutboxManager.init(this)
                OutboxManager.start()
                P2PServer.init(this)
                P2PServer.start()
                MailboxClientManager.init(this)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            // Channel for chat messages
            val msgChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notif_channel_messages_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notif_channel_messages_desc)
                enableVibration(true)
            }
            nm.createNotificationChannel(msgChannel)

            // Channel for contact requests
            val reqChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_REQUESTS,
                getString(R.string.notif_channel_requests_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notif_channel_requests_desc)
                enableVibration(true)
            }
            nm.createNotificationChannel(reqChannel)
        }
    }

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "fialka_messages"
        const val NOTIFICATION_CHANNEL_REQUESTS = "fialka_requests"
    }
}
