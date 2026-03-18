package com.securechat.tor

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import android.os.Build
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.Socket
import java.net.SocketAddress
import java.net.URI

/**
 * Singleton managing the embedded Tor daemon lifecycle.
 *
 * Tor binary is extracted from assets and executed as a process.
 * Exposes a SOCKS5 proxy at 127.0.0.1:9050.
 * StateFlow broadcasts connection state to all observers.
 */
object TorManager {

    private const val TAG = "TorManager"
    private const val TOR_SOCKS_PORT = 9050
    private const val TOR_CONTROL_PORT = 9051
    private const val PREFS_NAME = "tor_prefs"
    private const val KEY_TOR_ENABLED = "tor_enabled"
    private const val KEY_TOR_CHOICE_MADE = "tor_choice_made"

    private val _state = MutableStateFlow<TorState>(TorState.IDLE)
    val state: StateFlow<TorState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var torProcess: Process? = null
    private var monitorJob: Job? = null
    private lateinit var appContext: Context

    val socksPort: Int get() = TOR_SOCKS_PORT

    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isTorEnabled(): Boolean = prefs.getBoolean(KEY_TOR_ENABLED, true)

    /** Whether the user has already made a Tor enable/disable choice (first launch). */
    fun isTorChoiceMade(): Boolean = prefs.getBoolean(KEY_TOR_CHOICE_MADE, false)

    fun setTorEnabled(enabled: Boolean) {
        prefs.edit()
            .putBoolean(KEY_TOR_ENABLED, enabled)
            .putBoolean(KEY_TOR_CHOICE_MADE, true)
            .apply()
        if (enabled) start() else stop()
    }

    fun start() {
        if (_state.value is TorState.CONNECTED || _state.value is TorState.STARTING) return
        _state.value = TorState.STARTING

        scope.launch {
            try {
                startTorDaemon()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start Tor", e)
                _state.value = TorState.ERROR(e.message ?: "Unknown error")
            }
        }
    }

    fun stop() {
        disableProxy()
        monitorJob?.cancel()
        monitorJob = null
        torProcess?.destroy()
        torProcess = null
        _state.value = TorState.DISCONNECTED
    }

    fun restart() {
        stop()
        start()
    }

    /**
     * Suspends until Tor reaches CONNECTED state.
     * If Tor is disabled, returns immediately.
     */
    suspend fun awaitConnection() {
        if (!isTorEnabled()) return
        if (_state.value is TorState.CONNECTED) return
        _state.first { it is TorState.CONNECTED }
    }

    /**
     * Returns the path of a native binary from nativeLibraryDir.
     * This is the only directory with SELinux context allowing execution on Android 10+.
     * No copy, no chmod — execute directly from here.
     */
    fun getNativeBinary(name: String): File {
        val binary = File(appContext.applicationInfo.nativeLibraryDir, name)
        if (!binary.exists()) {
            val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
            throw IllegalStateException(
                "$name introuvable pour l'ABI $abi. " +
                "Binaires disponibles uniquement pour arm64-v8a."
            )
        }
        return binary
    }

    private suspend fun startTorDaemon() {
        val torDir = File(appContext.filesDir, "tor")
        torDir.mkdirs()

        val torrc = File(torDir, "torrc")
        val dataDir = File(torDir, "data")
        dataDir.mkdirs()

        // Execute directly from nativeLibraryDir (SELinux allows execution only here)
        val torBinary = getNativeBinary("libtor.so")

        // Write torrc
        torrc.writeText(buildString {
            appendLine("SocksPort $TOR_SOCKS_PORT")
            appendLine("ControlPort $TOR_CONTROL_PORT")
            appendLine("DataDirectory ${dataDir.absolutePath}")
            appendLine("Log notice file ${File(torDir, "tor.log").absolutePath}")
            appendLine("RunAsDaemon 0")
            appendLine("CookieAuthentication 0")
        })

        _state.value = TorState.BOOTSTRAPPING(0)

        // Start Tor process
        val processBuilder = ProcessBuilder(
            torBinary.absolutePath, "-f", torrc.absolutePath
        )
        processBuilder.directory(torDir)
        processBuilder.redirectErrorStream(true)

        torProcess = processBuilder.start()

        // Monitor Tor output for bootstrap progress
        monitorJob = scope.launch {
            try {
                torProcess?.inputStream?.bufferedReader()?.use { reader ->
                    reader.forEachLine { line ->
                        parseBootstrapProgress(line)
                    }
                }
            } catch (_: Exception) {
                // Process stream closed
            }
            // If we get here and not connected, Tor died
            if (_state.value !is TorState.DISCONNECTED) {
                _state.value = TorState.ERROR("Tor process terminated")
            }
        }

        // Also poll the SOCKS port as fallback (bootstrap log might miss 100%)
        scope.launch {
            var attempts = 0
            while (attempts < 120 && _state.value !is TorState.CONNECTED) {
                delay(500)
                attempts++
            }
            if (_state.value !is TorState.CONNECTED) {
                // Last chance: if SOCKS port is open, consider connected
                if (isSocksReady()) {
                    enableProxy()
                    _state.value = TorState.CONNECTED
                } else {
                    _state.value = TorState.ERROR("Tor connection timeout")
                }
            }
        }
    }

    private fun parseBootstrapProgress(line: String) {
        // Tor logs: "Bootstrapped 50% (loading_descriptors): Loading relay descriptors"
        val regex = Regex("""Bootstrapped (\d+)%""")
        val match = regex.find(line)
        if (match != null) {
            val percent = match.groupValues[1].toIntOrNull() ?: return
            _state.value = if (percent >= 100) {
                enableProxy()
                TorState.CONNECTED
            } else {
                TorState.BOOTSTRAPPING(percent)
            }
        }
    }

    private fun isSocksReady(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", TOR_SOCKS_PORT), 500)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    // Domains that must bypass Tor (Firebase/Google block exit nodes)
    private val DIRECT_DOMAINS = setOf(
        "googleapis.com",
        "firebaseio.com",
        "firebase.com",
        "cloudfunctions.net",
        "google.com",
        "gstatic.com",
        "android.com",
        "google-analytics.com",
        "googleusercontent.com"
    )

    private var originalProxySelector: ProxySelector? = null

    /**
     * Install a custom ProxySelector that routes traffic through Tor's SOCKS5 proxy,
     * EXCEPT Firebase/Google domains which connect directly.
     *
     * socksNonProxyHosts doesn't exist in Java — only ProxySelector
     * can selectively bypass a SOCKS proxy per-connection.
     */
    private fun enableProxy() {
        Log.i(TAG, "Enabling SOCKS5 ProxySelector → 127.0.0.1:$TOR_SOCKS_PORT")
        originalProxySelector = ProxySelector.getDefault()

        val torProxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress("127.0.0.1", TOR_SOCKS_PORT)
        )

        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                val host = uri?.host ?: return listOf(Proxy.NO_PROXY)
                // Localhost always direct
                if (host == "localhost" || host == "127.0.0.1") {
                    return listOf(Proxy.NO_PROXY)
                }
                // Firebase/Google domains go direct (they block Tor)
                if (DIRECT_DOMAINS.any { host == it || host.endsWith(".$it") }) {
                    return listOf(Proxy.NO_PROXY)
                }
                // Everything else goes through Tor
                return listOf(torProxy)
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                Log.w(TAG, "Proxy connect failed: $uri", ioe)
            }
        })
    }

    /**
     * Remove the custom ProxySelector — all traffic goes direct again.
     */
    private fun disableProxy() {
        Log.i(TAG, "Disabling SOCKS5 ProxySelector")
        originalProxySelector?.let { ProxySelector.setDefault(it) }
        originalProxySelector = null
    }
}
