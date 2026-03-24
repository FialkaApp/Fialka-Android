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

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import com.fialkaapp.fialka.crypto.CryptoManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import net.freehaven.tor.control.TorControlConnection
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
 * Tor binary comes from the Guardian Project's tor-android Gradle dependency
 * (info.guardianproject:tor-android) — reproducible builds, 4 ABI support.
 * Executed directly from nativeLibraryDir (SELinux allows this on Android 10+).
 *
 * Control port (jtorctl) is used for authenticated communication with the
 * Tor daemon: graceful shutdown, and Hidden Service management.
 * The Hidden Service keypair is derived from the Ed25519 identity seed,
 * so the .onion address is deterministic (same seed = same .onion).
 *
 * SOCKS5 proxy at 127.0.0.1:9050 handles all outbound traffic via Tor.
 * Firebase/Google domains temporarily bypass Tor (they block exit nodes)
 * until Firebase is replaced by .onion P2P in a future release.
 */
object TorManager {

    private const val TOR_SOCKS_PORT = 9050
    private const val TOR_CONTROL_PORT = 9051
    private const val HIDDEN_SERVICE_PORT = 7333
    private const val PREFS_NAME = "tor_prefs"

    private val _state = MutableStateFlow<TorState>(TorState.IDLE)
    val state: StateFlow<TorState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var torProcess: Process? = null
    private var controlConnection: TorControlConnection? = null
    private var monitorJob: Job? = null
    private lateinit var appContext: Context

    /** The published .onion address (null until Hidden Service is live). */
    private val _onionAddress = MutableStateFlow<String?>(null)
    val onionAddress: StateFlow<String?> = _onionAddress.asStateFlow()

    /** Circuit info (relays + countries), populated once Tor is connected. */
    private val _circuitInfo = MutableStateFlow<CircuitInfo?>(null)
    val circuitInfo: StateFlow<CircuitInfo?> = _circuitInfo.asStateFlow()

    /** All active circuits (3+ hops, no ONEHOP_TUNNEL). */
    private val _circuits = MutableStateFlow<List<TorCircuit>>(emptyList())
    val circuits: StateFlow<List<TorCircuit>> = _circuits.asStateFlow()

    val socksPort: Int get() = TOR_SOCKS_PORT
    val hiddenServicePort: Int get() = HIDDEN_SERVICE_PORT

    private val prefs: SharedPreferences
        get() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun isTorEnabled(): Boolean = true

    fun start() {
        val s = _state.value
        if (s is TorState.STARTING || s is TorState.BOOTSTRAPPING) return
        // If already connected/published, skip re-start
        if (s is TorState.CONNECTED || s is TorState.ONION_PUBLISHED) return
        _state.value = TorState.STARTING

        // Keep the process alive with a foreground service + WakeLock
        TorForegroundService.start(appContext)

        scope.launch {
            try {
                startTorDaemon()
            } catch (e: Exception) {
                _state.value = TorState.ERROR(e.message ?: "Unknown error")
            }
        }
    }

    fun stop() {
        disableProxy()
        monitorJob?.cancel()
        monitorJob = null

        // Graceful shutdown via control port if available
        try {
            controlConnection?.shutdownTor("SHUTDOWN")
        } catch (_: Exception) { }
        // Always kill the process to avoid stale daemon
        try {
            torProcess?.destroyForcibly()
        } catch (_: Exception) { }
        controlConnection = null
        torProcess = null
        _onionAddress.value = null
        _circuitInfo.value = null
        _state.value = TorState.DISCONNECTED

        TorForegroundService.stop(appContext)
    }

    fun restart() {
        // Don't stop the foreground service — just kill Tor and re-launch
        disableProxy()
        monitorJob?.cancel()
        monitorJob = null
        try {
            controlConnection?.shutdownTor("SHUTDOWN")
        } catch (_: Exception) { }
        try {
            torProcess?.destroyForcibly()
        } catch (_: Exception) { }
        controlConnection = null
        torProcess = null
        _onionAddress.value = null
        _circuitInfo.value = null
        _state.value = TorState.IDLE

        // Re-start without touching the foreground service
        start()
    }

    /**
     * Suspends until Tor reaches CONNECTED or ONION_PUBLISHED state.
     */
    suspend fun awaitConnection() {
        val s = _state.value
        if (s is TorState.CONNECTED || s is TorState.ONION_PUBLISHED) return
        _state.first { it is TorState.CONNECTED || it is TorState.ONION_PUBLISHED }
    }

    /**
     * Call AFTER identity creation (onboarding / restore) to publish the
     * Hidden Service now that the Ed25519 seed exists.
     * If Tor isn't connected yet, waits up to 60s for the control port.
     */
    fun publishOnionIfReady() {
        if (_onionAddress.value != null) return
        scope.launch {
            val conn = awaitControlConnection(60_000)
            if (conn != null) {
                publishHiddenService()
            } else {
            }
        }
    }

    private fun getTorBinary(): File {
        val binary = File(appContext.applicationInfo.nativeLibraryDir, "libtor.so")
        if (!binary.exists()) {
            throw IllegalStateException(
                "libtor.so not found. The Guardian Project tor-android " +
                "dependency should provide it for all supported ABIs."
            )
        }
        return binary
    }

    private suspend fun startTorDaemon() {
        val torDir = File(appContext.filesDir, "tor")
        torDir.mkdirs()

        val dataDir = File(torDir, "data")
        dataDir.mkdirs()

        val torBinary = getTorBinary()
        val cookieFile = File(dataDir, "control_auth_cookie")

        // Delete stale cookie from previous run
        cookieFile.delete()

        // Kill any orphaned Tor process bound to ports
        killOrphanedTor()

        // Write torrc with cookie authentication for control port security
        val torrc = File(torDir, "torrc")
        torrc.writeText(buildString {
            appendLine("SocksPort $TOR_SOCKS_PORT")
            appendLine("ControlPort $TOR_CONTROL_PORT")
            appendLine("DataDirectory ${dataDir.absolutePath}")
            appendLine("RunAsDaemon 0")
            appendLine("CookieAuthentication 1")
            appendLine("CookieAuthFile ${cookieFile.absolutePath}")
        })

        _state.value = TorState.BOOTSTRAPPING(0)

        val processBuilder = ProcessBuilder(
            torBinary.absolutePath, "-f", torrc.absolutePath
        )
        processBuilder.directory(torDir)
        processBuilder.redirectErrorStream(true)

        torProcess = processBuilder.start()

        // Monitor Tor stdout for bootstrap progress
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

        // Connect jtorctl to control port — retry until cookie file appears and port is ready
        scope.launch {
            // Wait for fresh cookie file
            var attempts = 0
            while (!cookieFile.exists() && attempts < 60) {
                delay(500)
                attempts++
            }
            if (!cookieFile.exists()) {
                return@launch
            }
            // Cookie exists — now retry connecting (port may not be ready yet)
            for (attempt in 1..10) {
                try {
                    connectControlPort(cookieFile)
                    return@launch
                } catch (e: Exception) {
                    if (attempt == 10) {
                    } else {
                        delay(1500)
                    }
                }
            }
        }

        // Fallback: poll SOCKS port if bootstrap log is buffered
        scope.launch {
            var attempts = 0
            while (attempts < 120 && _state.value !is TorState.CONNECTED) {
                delay(500)
                attempts++
            }
            if (_state.value !is TorState.CONNECTED && _state.value !is TorState.ONION_PUBLISHED) {
                if (isSocksReady()) {
                    enableProxy()
                    _state.value = TorState.CONNECTED
                    // Fallback path: also try publishing
                    scope.launch {
                        val conn = awaitControlConnection(30_000)
                        if (conn != null) {
                            publishHiddenService()
                            for (attempt in 1..30) {
                                if (_circuitInfo.value != null) return@launch
                                delay(2000)
                                fetchCircuitInfo()
                            }
                        }
                    }
                } else {
                    _state.value = TorState.ERROR("Tor connection timeout")
                }
            }
        }
    }

    /**
     * Kill any orphaned Tor daemon from a previous run by checking if
     * our control or SOCKS ports are still occupied.
     */
    private fun killOrphanedTor() {
        try {
            // Try connecting to old control port and sending SHUTDOWN
            Socket().use { s ->
                s.connect(InetSocketAddress("127.0.0.1", TOR_CONTROL_PORT), 500)
                s.getOutputStream().write("AUTHENTICATE\r\nSIGNAL SHUTDOWN\r\n".toByteArray())
            }
            Thread.sleep(500)
        } catch (_: Exception) {
            // No orphan or can't connect — that's fine
        }
    }

    /**
     * Connect to Tor's control port with cookie authentication.
     * Used for graceful shutdown and Hidden Service management.
     */
    private fun connectControlPort(cookieFile: File) {
        val socket = Socket("127.0.0.1", TOR_CONTROL_PORT)
        val conn = TorControlConnection(socket)
        conn.authenticate(cookieFile.readBytes())
        controlConnection = conn
    }

    /**
     * Publish a Tor v3 Hidden Service using the Ed25519 identity key.
     *
     * Uses ADD_ONION with "ED25519-V3:<base64 expanded key>" so the .onion
     * is deterministic: same seed → same .onion address, every time.
     *
     * Port mapping: external port [HIDDEN_SERVICE_PORT] → 127.0.0.1:[HIDDEN_SERVICE_PORT]
     * (future: local messaging server will listen there)
     */
    private fun publishHiddenService() {
        if (_onionAddress.value != null) {
            return
        }
        val conn = controlConnection ?: run {
            return
        }
        try {
            // Get expanded Ed25519 key (64 bytes) from CryptoManager
            val expandedKey = CryptoManager.getExpandedEd25519KeyForTor()
            val keyBlob = "ED25519-V3:" + Base64.encodeToString(expandedKey, Base64.NO_WRAP)
            expandedKey.fill(0)

            // Port mapping: virtual port → local target
            val portMap = mapOf<Int, String>(
                HIDDEN_SERVICE_PORT to "127.0.0.1:$HIDDEN_SERVICE_PORT"
            )

            // addOnion returns {"ServiceID" → "<56-char onion without .onion>"}
            val result = conn.addOnion(keyBlob, portMap)
            val serviceId = result["ServiceID"]

            if (serviceId != null) {
                val fullAddress = "$serviceId.onion"
                _onionAddress.value = fullAddress
                // Update circuit info with onion address
                _circuitInfo.value = _circuitInfo.value?.copy(onionAddress = fullAddress)
                _state.value = TorState.ONION_PUBLISHED(fullAddress)
            } else {
            }
        } catch (e: IllegalStateException) {
            // Identity not initialized yet — skip Hidden Service for now
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("Onion address collision", ignoreCase = true)) {
                // Already published by Tor — extract .onion from circuit-status REND_QUERY
                try {
                    val circuitStatus = controlConnection?.getInfo("circuit-status") ?: ""
                    val rendQuery = Regex("REND_QUERY=(\\S+)").find(circuitStatus)?.groupValues?.get(1)
                    if (rendQuery != null) {
                        val fullAddress = "$rendQuery.onion"
                        _onionAddress.value = fullAddress
                        _circuitInfo.value = _circuitInfo.value?.copy(onionAddress = fullAddress)
                        _state.value = TorState.ONION_PUBLISHED(fullAddress)
                    }
                } catch (ex: Exception) {
                }
            } else {
            }
        }
    }

    /**
     * Fetch active circuit info from Tor via control port.
     * Parses GETINFO circuit-status to extract relay fingerprints,
     * then resolves each relay's IP and country via ip-to-country.
     * Populates both _circuitInfo (first 3-hop circuit) and _circuits (all valid circuits).
     */
    private fun fetchCircuitInfo() {
        val conn = controlConnection ?: run {
            return
        }
        try {
            val circuitStatus = conn.getInfo("circuit-status")
            if (circuitStatus.isNullOrBlank()) {
                return
            }

            // Parse all BUILT circuits with 3+ hops (skip ONEHOP_TUNNEL)
            val builtLines = circuitStatus.lines().filter { line ->
                line.contains(" BUILT ") && !line.contains("ONEHOP_TUNNEL") &&
                    line.split(" ").getOrNull(2)?.split(",")?.size?.let { it >= 3 } == true
            }
            if (builtLines.isEmpty()) {
                return
            }

            // Cache resolved relays to avoid duplicate control port lookups
            val relayCache = mutableMapOf<String, TorRelay>()

            fun resolveRelay(hop: String): TorRelay {
                val clean = hop.removePrefix("$")
                val fp = clean.substringBefore("~")
                relayCache[fp]?.let { return it }
                val nickname = clean.substringAfter("~", "relay")
                var ip = "?"
                try {
                    val nsInfo = conn.getInfo("ns/id/$fp")
                    if (nsInfo != null) {
                        val rLine = nsInfo.lines().firstOrNull { it.startsWith("r ") }
                        if (rLine != null) {
                            val fields = rLine.split(" ")
                            if (fields.size >= 7) ip = fields[6]
                        }
                    }
                } catch (_: Exception) { }
                var country = "??"
                if (ip != "?") {
                    try {
                        val cc = conn.getInfo("ip-to-country/$ip")
                        if (cc != null) country = cc.trim().uppercase()
                    } catch (_: Exception) { }
                }
                val relay = TorRelay(name = nickname, ip = ip, country = country)
                relayCache[fp] = relay
                return relay
            }

            // Extract PURPOSE from each line
            val purposeRegex = Regex("""PURPOSE=(\S+)""")

            val allCircuits = builtLines.take(10).mapNotNull { line ->
                val parts = line.split(" ")
                if (parts.size < 3) return@mapNotNull null
                val circuitId = parts[0]
                val hops = parts[2].split(",")
                if (hops.size < 3) return@mapNotNull null
                val purpose = purposeRegex.find(line)?.groupValues?.get(1) ?: "GENERAL"
                val relays = hops.map { resolveRelay(it) }
                TorCircuit(id = circuitId, relays = relays, purpose = purpose)
            }

            _circuits.value = allCircuits

            // First circuit populates backward-compatible _circuitInfo
            val first = allCircuits.firstOrNull()
            if (first != null && first.relays.size >= 3) {
                _circuitInfo.value = CircuitInfo(
                    guard = first.relays[0],
                    middle = first.relays[1],
                    exit = first.relays[2],
                    onionAddress = _onionAddress.value
                )
            }
        } catch (e: Exception) {
        }
    }

    private fun parseBootstrapProgress(line: String) {
        val regex = Regex("""Bootstrapped (\d+)%""")
        val match = regex.find(line)
        if (match != null) {
            val percent = match.groupValues[1].toIntOrNull() ?: return
            _state.value = if (percent >= 100) {
                enableProxy()
                // Wait for control connection, THEN publish + fetch circuit
                scope.launch {
                    val conn = awaitControlConnection(30_000)
                    if (conn != null) {
                        publishHiddenService()
                    } else {
                    }
                }
                scope.launch {
                    awaitControlConnection(30_000) ?: return@launch
                    for (attempt in 1..30) {
                        if (_circuitInfo.value != null) return@launch
                        delay(2000)
                        fetchCircuitInfo()
                    }
                }
                TorState.CONNECTED
            } else {
                TorState.BOOTSTRAPPING(percent)
            }
        }
    }

    /**
     * Suspend until controlConnection is non-null, or timeout.
     */
    private suspend fun awaitControlConnection(timeoutMs: Long): TorControlConnection? {
        val start = System.currentTimeMillis()
        while (controlConnection == null && System.currentTimeMillis() - start < timeoutMs) {
            delay(500)
        }
        return controlConnection
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

    // ── SOCKS5 Proxy ──
    // Firebase/Google bypass Tor temporarily — they block Tor exit nodes.
    // This will be removed when Firebase is replaced by .onion P2P transport.

    private val FIREBASE_BYPASS_DOMAINS = setOf(
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

    private fun enableProxy() {
        originalProxySelector = ProxySelector.getDefault()

        val torProxy = Proxy(
            Proxy.Type.SOCKS,
            InetSocketAddress.createUnresolved("127.0.0.1", TOR_SOCKS_PORT)
        )

        ProxySelector.setDefault(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> {
                val host = uri?.host ?: return listOf(Proxy.NO_PROXY)
                // Localhost always direct
                if (host == "localhost" || host == "127.0.0.1") {
                    return listOf(Proxy.NO_PROXY)
                }
                // Firebase/Google domains go direct (temporary — until P2P replaces Firebase)
                if (FIREBASE_BYPASS_DOMAINS.any { host == it || host.endsWith(".$it") }) {
                    return listOf(Proxy.NO_PROXY)
                }
                // Everything else goes through Tor SOCKS5
                return listOf(torProxy)
            }

            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
            }
        })
    }

    private fun disableProxy() {
        originalProxySelector?.let { ProxySelector.setDefault(it) }
        originalProxySelector = null
    }
}
