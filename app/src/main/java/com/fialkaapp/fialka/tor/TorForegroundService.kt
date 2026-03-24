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
package com.fialkaapp.fialka.tor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Foreground service that keeps the Tor daemon alive.
 *
 * Shows a persistent notification reflecting Tor state.
 * Holds a partial WakeLock to prevent Doze from killing the Tor process.
 * No VPN, no TUN interface — the SOCKS5 ProxySelector handles routing.
 */
class TorForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "tor_service_channel"
        private const val NOTIFICATION_ID = 42
        private const val WAKELOCK_TAG = "Fialka::TorWakeLock"

        private const val ACTION_START = "com.fialkaapp.fialka.tor.START"
        private const val ACTION_STOP = "com.fialkaapp.fialka.tor.STOP"

        fun start(context: Context) {
            val intent = Intent(context, TorForegroundService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TorForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                releaseWakeLock()
                scope.cancel()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification("Tor : connexion…"))
                acquireWakeLock()
                observeState()
            }
        }
        return START_STICKY
    }

    private fun observeState() {
        scope.launch {
            TorManager.state.collectLatest { state ->
                val text = when (state) {
                    is TorState.CONNECTED -> "Tor : connecté \uD83E\uDDC5"
                    is TorState.PUBLISHING_ONION -> "Tor : publication .onion…"
                    is TorState.ONION_PUBLISHED -> "Tor : ${state.address}"
                    is TorState.BOOTSTRAPPING -> "Tor : ${state.percent}%"
                    is TorState.STARTING -> "Tor : démarrage…"
                    is TorState.ERROR -> "Tor : erreur"
                    else -> "Tor : déconnecté"
                }
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification(text))
            }
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Fialka")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tor Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Tor anonymity network connection status"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }
}
