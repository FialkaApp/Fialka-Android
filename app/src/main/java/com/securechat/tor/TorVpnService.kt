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
package com.securechat.tor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.securechat.R
import com.securechat.ui.MainActivity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.io.File

/**
 * VPN service that creates a TUN interface and routes all app traffic
 * through the Tor SOCKS5 proxy at 127.0.0.1:9050.
 *
 * Uses hev-socks5-tunnel (native binary) to perform the actual
 * TUN → SOCKS5 packet forwarding. The binary is packaged as libhev-socks5-tunnel.so
 * and executed as a child process, inheriting the TUN file descriptor.
 *
 * Architecture: App traffic → TUN interface → hev-socks5-tunnel → SOCKS5 → Tor → Internet
 */
class TorVpnService : VpnService() {

    companion object {
        private const val TAG = "TorVpnService"
        private const val CHANNEL_ID = "tor_vpn_channel"
        private const val NOTIFICATION_ID = 42
        private const val VPN_ADDRESS = "10.10.10.1"
        private const val VPN_ROUTE = "0.0.0.0"
        private const val VPN_DNS = "1.1.1.1"
        private const val VPN_MTU = 8500

        const val ACTION_START = "com.securechat.tor.START_VPN"
        const val ACTION_STOP = "com.securechat.tor.STOP_VPN"

        fun start(context: Context) {
            val intent = Intent(context, TorVpnService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, TorVpnService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var tunnelProcess: Process? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                tearDown()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, buildNotification())
                scope.launch { startVpn() }
            }
        }
        return START_STICKY
    }

    private suspend fun startVpn() {
        // Wait until Tor SOCKS5 is ready
        TorManager.state.collectLatest { state ->
            if (state is TorState.CONNECTED) {
                establishVpn()
                return@collectLatest
            }
        }
    }

    private fun establishVpn() {
        try {
            val builder = Builder()
            builder.setSession("SecureChat Tor VPN")
            builder.setMtu(VPN_MTU)
            builder.addAddress(VPN_ADDRESS, 24)
            builder.addRoute(VPN_ROUTE, 0)
            builder.addDnsServer(VPN_DNS)
            builder.addAllowedApplication(packageName)
            builder.setBlocking(false)

            vpnInterface = builder.establish()
            if (vpnInterface == null) {
                Log.e(TAG, "VPN interface could not be established")
                return
            }

            startHevTunnel()
            Log.i(TAG, "VPN established, traffic routing through Tor")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to establish VPN", e)
        }
    }

    private fun startHevTunnel() {
        val tunFd = vpnInterface?.fd ?: return

        // Write hev-socks5-tunnel config
        val configFile = File(filesDir, "hev-socks5-tunnel.yml")
        configFile.writeText(buildString {
            appendLine("tunnel:")
            appendLine("  mtu: $VPN_MTU")
            appendLine("")
            appendLine("socks5:")
            appendLine("  port: ${TorManager.socksPort}")
            appendLine("  address: 127.0.0.1")
            appendLine("")
            appendLine("misc:")
            appendLine("  task-stack-size: 20480")
            appendLine("  connect-timeout: 5000")
            appendLine("  read-write-timeout: 60000")
            appendLine("  log-file: ${File(filesDir, "hev-socks5-tunnel.log").absolutePath}")
            appendLine("  log-level: warn")
            appendLine("  pid-file: ${File(filesDir, "hev-socks5-tunnel.pid").absolutePath}")
            appendLine("  limit-nofile: 65535")
        })

        // Execute directly from nativeLibraryDir (SELinux blocks execution from /data/user/)
        val tunnelBinary = try {
            TorManager.getNativeBinary("libhev-socks5-tunnel.so")
        } catch (e: Exception) {
            Log.e(TAG, "hev-socks5-tunnel binary not found", e)
            return
        }

        tunnelProcess = ProcessBuilder(
            tunnelBinary.absolutePath,
            "--tunfd", tunFd.toString(),
            "--config", configFile.absolutePath
        ).apply {
            directory(filesDir)
            redirectErrorStream(true)
        }.start()

        // Monitor process output in background
        scope.launch {
            try {
                tunnelProcess?.inputStream?.bufferedReader()?.use { reader ->
                    reader.forEachLine { line ->
                        Log.d(TAG, "hev-socks5-tunnel: $line")
                    }
                }
            } catch (_: Exception) {
                // Process stream closed
            }
        }
    }

    private fun tearDown() {
        tunnelProcess?.destroy()
        tunnelProcess = null
        vpnInterface?.close()
        vpnInterface = null
        scope.cancel()
    }

    override fun onDestroy() {
        tearDown()
        super.onDestroy()
    }

    override fun onRevoke() {
        tearDown()
        stopSelf()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Tor VPN",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Connexion Tor VPN active"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SecureChat")
            .setContentText("Connexion Tor active 🧅")
            .setSmallIcon(R.drawable.ic_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
