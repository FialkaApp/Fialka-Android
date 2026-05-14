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
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.fialkaapp.fialka.R

import com.fialkaapp.fialka.util.AppLockManager
import com.fialkaapp.fialka.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * Foreground service that keeps the Tor daemon alive.
 *
 * Shows a persistent notification reflecting Tor state in real time.
 * Exposes two quick-action buttons directly in the notification shade:
 *   • "Verrouiller"   — locks the app immediately (starts LockScreenActivity)
 *   • "Tout arrêter"  — stops Tor + P2P/Mailbox services and removes the notification
 *
 * Holds a partial WakeLock to prevent Doze from killing the Tor process.
 * No VPN, no TUN interface — the SOCKS5 ProxySelector handles routing.
 */
class TorForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID      = "tor_service_channel"
        private const val NOTIFICATION_ID = 42
        private const val WAKELOCK_TAG    = "Fialka::TorWakeLock"

        private const val ACTION_START    = "com.fialkaapp.fialka.tor.START"
        private const val ACTION_STOP     = "com.fialkaapp.fialka.tor.STOP"
        private const val ACTION_STOP_ALL = "com.fialkaapp.fialka.tor.STOP_ALL"
        private const val ACTION_LOCK     = "com.fialkaapp.fialka.tor.LOCK"
        private const val ACTION_RENOTIFY = "com.fialkaapp.fialka.tor.RENOTIFY"

        // Stable request codes for PendingIntents
        private const val REQ_OPEN  = 10
        private const val REQ_LOCK  = 11
        private const val REQ_STOP  = 12

        fun start(context: Context) {
            context.startForegroundService(
                Intent(context, TorForegroundService::class.java).apply { action = ACTION_START }
            )
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, TorForegroundService::class.java).apply { action = ACTION_STOP }
            )
        }

        /**
         * Re-issues the foreground notification after POST_NOTIFICATIONS is granted.
         * Call this from the permission result callback so the notification becomes
         * visible immediately without requiring a kill + relaunch.
         */
        fun renotify(context: Context) {
            context.startService(
                Intent(context, TorForegroundService::class.java).apply { action = ACTION_RENOTIFY }
            )
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
        return when (intent?.action) {
            // "Tout arrêter" button — or external stop request
            ACTION_STOP, ACTION_STOP_ALL -> {
                stopEverything()
                START_NOT_STICKY
            }
            // "Verrouiller" button
            ACTION_LOCK -> {
                lockApp()
                START_NOT_STICKY
            }
            // Normal start
            ACTION_START -> {
                promoteToForeground()
                acquireWakeLock()
                observeState()
                START_STICKY
            }
            // POST_NOTIFICATIONS was just granted — re-issue the notification so it
            // becomes visible immediately without a kill + relaunch
            ACTION_RENOTIFY -> {
                promoteToForeground()
                START_STICKY
            }
            // Android restarted the service after a kill (null intent) — re-promote immediately
            else -> {
                promoteToForeground()
                if (wakeLock == null) acquireWakeLock()
                observeState()
                START_STICKY
            }
        }
    }

    // ── Foreground promotion ─────────────────────────────────────────────────

    /**
     * Calls startForeground() with the mandatory 3-arg form (API 29+, required on API 34+).
     * Without FOREGROUND_SERVICE_TYPE_SPECIAL_USE, Android 14+ throws
     * MissingForegroundServiceTypeException and the notification never appears.
     */
    private fun promoteToForeground() {
        startForeground(
            NOTIFICATION_ID,
            buildNotification(TorManager.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    // ── State observer ───────────────────────────────────────────────────────

    private fun observeState() {
        scope.launch {
            TorManager.state.collectLatest { state ->
                val nm = getSystemService(NotificationManager::class.java)
                nm.notify(NOTIFICATION_ID, buildNotification(state))
            }
        }
    }

    // ── Notification builder ─────────────────────────────────────────────────

    private fun buildNotification(state: TorState): Notification {
        // Tap notification → open app
        val openIntent = PendingIntent.getActivity(
            this, REQ_OPEN,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // "Verrouiller" quick action → sends ACTION_LOCK to this service (no activity opened)
        val lockPendingIntent = PendingIntent.getService(
            this, REQ_LOCK,
            Intent(this, TorForegroundService::class.java).apply { action = ACTION_LOCK },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // "Tout arrêter" quick action
        val stopPendingIntent = PendingIntent.getService(
            this, REQ_STOP,
            Intent(this, TorForegroundService::class.java).apply { action = ACTION_STOP_ALL },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val statusLine = buildStatusLine(state)
        val subtitle   = getString(R.string.notif_body_subtitle)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notif_title_running))
            .setContentText(statusLine)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$statusLine\n$subtitle")
            )
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            // "Verrouiller" : affiché seulement si PIN configuré ET app pas déjà locked
            // (évite de demander le code une 2e fois si déjà verrouillé via la notif)
            .apply {
                if (AppLockManager.isPinSet(this@TorForegroundService)
                    && !AppLockManager.isLockedByNotification(this@TorForegroundService)) {
                    addAction(0, getString(R.string.notif_action_lock), lockPendingIntent)
                }
            }
            .addAction(0, getString(R.string.notif_action_stop_all), stopPendingIntent)
            .build()
    }

    /** One-line status reflecting current Tor state (shown in collapsed notification). */
    private fun buildStatusLine(state: TorState): String = when (state) {
        is TorState.ONION_PUBLISHED -> {
            // Show first 8 chars of the .onion for quick identification
            val short = state.address.take(8)
            getString(R.string.notif_tor_onion_short, short)
        }
        is TorState.CONNECTED       -> getString(R.string.notif_tor_connected)
        is TorState.PUBLISHING_ONION -> getString(R.string.notif_tor_publishing)
        is TorState.BOOTSTRAPPING   -> getString(R.string.notif_tor_bootstrapping, state.percent)
        is TorState.STARTING        -> getString(R.string.notif_tor_starting)
        is TorState.ERROR           -> getString(R.string.notif_tor_error)
        else                        -> getString(R.string.notif_tor_disconnected)
    }

    // ── Actions ──────────────────────────────────────────────────────────────

    /**
     * Cleanly shuts down Fialka and exits the process.
     *
     * Why this order matters:
     * 1. stopSelf() must be called BEFORE killProcess so that Android receives
     *    START_NOT_STICKY and does NOT auto-restart the service (START_STICKY restart
     *    would bring the app back in a broken state with orphaned Tor ports).
     * 2. killNativeTorProcess() kills the native Tor binary so its ports (9050/9051)
     *    are free on the next launch (killProcess only kills the JVM, not child processes).
     * 3. exitProcess(0) is posted on the main looper to run AFTER onStartCommand() has
     *    returned — if we call it inline, onStartCommand never returns START_NOT_STICKY
     *    and Android treats the death as a crash, triggering a service restart.
     */
    private fun stopEverything() {
        releaseWakeLock()
        scope.cancel()
        // Kill the native Tor binary (separate OS process — not killed by exitProcess)
        TorManager.killNativeTorProcess()
        // Remove the persistent notification
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Tell Android: this service stopped intentionally — do NOT restart it
        stopSelf()
        // Exit the JVM AFTER onStartCommand() has returned (so START_NOT_STICKY is processed)
        Handler(Looper.getMainLooper()).postDelayed({
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 300)
    }

    /**
     * Locks the app silently — no activity is opened.
     * Sets a flag that MainActivity reads on next onResume() and shows LockScreenActivity
     * exactly once via its own lockLauncher (preventing any double-prompt).
     * The notification is immediately updated to hide the "Verrouiller" button.
     */
    private fun lockApp() {
        AppLockManager.lockNow(this)
        promoteToForeground() // re-issues notification: "Verrouiller" disappears
    }

    // ── Channel & WakeLock ───────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tor Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Fialka background status and Tor connection"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }
}
