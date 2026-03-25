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

import com.fialkaapp.fialka.crypto.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.security.SecureRandom
import kotlin.math.abs

/**
 * P2P binary transport over Tor hidden services.
 *
 * Frame wire format:
 *   [0xF1][0xA1][type:1B][length:4B BE][payload:N bytes]
 *
 * Authenticated payload format (for signed commands):
 *   [pubkey:32B][timestamp:8B][nonce:16B][signature:64B][extra:remaining]
 *
 * Used by both NORMAL mode (send/receive P2P messages) and
 * MAILBOX mode (serve deposit/fetch/admin requests).
 */
object TorTransport {

    // ── Frame header constants ──
    const val MAGIC_HIGH: Byte = 0xF1.toByte()
    const val MAGIC_LOW: Byte = 0xA1.toByte()
    private const val HEADER_SIZE = 7
    private const val MAX_PAYLOAD_SIZE = 10 * 1024 * 1024

    private const val CONNECT_TIMEOUT_MS = 60_000
    private const val READ_TIMEOUT_MS = 60_000

    // ── Frame types: P2P direct ──
    const val TYPE_MESSAGE: Byte = 0x01
    const val TYPE_FILE_CHUNK: Byte = 0x02
    const val TYPE_KEY_BUNDLE: Byte = 0x03
    const val TYPE_ACK: Byte = 0x04
    const val TYPE_PING: Byte = 0x05
    const val TYPE_CONTACT_REQ: Byte = 0x06

    // ── Frame types: Mailbox operations ──
    const val TYPE_DEPOSIT: Byte = 0x07
    const val TYPE_FETCH: Byte = 0x08
    const val TYPE_JOIN: Byte = 0x09
    const val TYPE_LEAVE: Byte = 0x0A.toByte()

    // ── Frame types: Owner commands ──
    const val TYPE_INVITE_REQ: Byte = 0x0B
    const val TYPE_REVOKE: Byte = 0x0C
    const val TYPE_LIST_MEMBERS: Byte = 0x0D

    // ── Frame types: Server responses ──
    const val TYPE_JOIN_RESP: Byte = 0x0E
    const val TYPE_INVITE_RESP: Byte = 0x0F
    const val TYPE_MEMBER_LIST: Byte = 0x10
    const val TYPE_FETCH_RESP: Byte = 0x11
    const val TYPE_ERROR: Byte = 0x12

    // ── Status codes ──
    const val ACK_OK: Byte = 0x00
    const val ACK_ERROR: Byte = 0x01
    const val JOIN_ACCEPTED: Byte = 0x00
    const val JOIN_REJECTED: Byte = 0x01
    const val ROLE_OWNER: Byte = 0x00
    const val ROLE_MEMBER: Byte = 0x01

    // ── Anti-replay ──
    private const val TIMESTAMP_WINDOW_MS = 5 * 60 * 1000L

    /** A transport-level frame. */
    data class Frame(val type: Byte, val payload: ByteArray) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Frame) return false
            return type == other.type && payload.contentEquals(other.payload)
        }

        override fun hashCode() = 31 * type.hashCode() + payload.contentHashCode()
    }

    /** Listener for incoming frames on the server socket. */
    interface FrameListener {
        suspend fun onFrame(frame: Frame): Frame?
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listener: FrameListener? = null

    // ══════════════════════════════════════════
    //  SERVER — listen for incoming connections
    // ══════════════════════════════════════════

    fun startServer(
        port: Int = TorManager.hiddenServicePort,
        frameListener: FrameListener
    ) {
        if (serverSocket != null) {
            if (listener === frameListener) return   // same listener, already running
            // Different listener (e.g. mode change NOT_SET→MAILBOX) — swap
            stopServer()
        }
        listener = frameListener
        serverJob = scope.launch {
            try {
                val ss = ServerSocket(port, 50, InetAddress.getByName("127.0.0.1"))
                serverSocket = ss
                while (isActive) {
                    val client = ss.accept()
                    launch { handleClient(client) }
                }
            } catch (_: IOException) {
                // Server socket closed
            }
        }
    }

    fun stopServer() {
        serverJob?.cancel()
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        serverJob = null
        listener = null
    }

    private suspend fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = READ_TIMEOUT_MS
            val input = DataInputStream(socket.getInputStream().buffered())
            val output = DataOutputStream(socket.getOutputStream().buffered())

            val frame = readFrame(input) ?: return
            val response = listener?.onFrame(frame)

            if (response != null) {
                writeFrame(output, response)
                output.flush()
            }
        } catch (_: Exception) {
            // Connection error — silently close
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    // ══════════════════════════════════════════
    //  CLIENT — send frame via Tor SOCKS5
    // ══════════════════════════════════════════

    /**
     * Send a frame to a .onion address via Tor SOCKS5 proxy.
     * Returns the response frame, or null on failure.
     */
    suspend fun sendFrame(
        onionAddress: String,
        port: Int = TorManager.hiddenServicePort,
        frame: Frame
    ): Frame? = withContext(Dispatchers.IO) {
        var socket: Socket? = null
        try {
            val proxy = Proxy(
                Proxy.Type.SOCKS,
                InetSocketAddress("127.0.0.1", TorManager.socksPort)
            )
            socket = Socket(proxy)
            socket.connect(
                InetSocketAddress.createUnresolved(onionAddress, port),
                CONNECT_TIMEOUT_MS
            )
            socket.soTimeout = READ_TIMEOUT_MS

            val output = DataOutputStream(socket.getOutputStream().buffered())
            val input = DataInputStream(socket.getInputStream().buffered())

            writeFrame(output, frame)
            output.flush()

            readFrame(input)
        } catch (_: Exception) {
            null
        } finally {
            try { socket?.close() } catch (_: Exception) {}
        }
    }

    // ══════════════════════════════════════════
    //  FRAME ENCODING / DECODING
    // ══════════════════════════════════════════

    fun encodeFrame(frame: Frame): ByteArray {
        val buf = ByteBuffer.allocate(HEADER_SIZE + frame.payload.size)
        buf.put(MAGIC_HIGH)
        buf.put(MAGIC_LOW)
        buf.put(frame.type)
        buf.putInt(frame.payload.size)
        buf.put(frame.payload)
        return buf.array()
    }

    private fun writeFrame(output: DataOutputStream, frame: Frame) {
        output.writeByte(MAGIC_HIGH.toInt())
        output.writeByte(MAGIC_LOW.toInt())
        output.writeByte(frame.type.toInt())
        output.writeInt(frame.payload.size)
        output.write(frame.payload)
    }

    private fun readFrame(input: DataInputStream): Frame? {
        val magic1 = input.readByte()
        val magic2 = input.readByte()
        if (magic1 != MAGIC_HIGH || magic2 != MAGIC_LOW) return null

        val type = input.readByte()
        val length = input.readInt()
        if (length < 0 || length > MAX_PAYLOAD_SIZE) return null

        val payload = ByteArray(length)
        input.readFully(payload)
        return Frame(type, payload)
    }

    // ══════════════════════════════════════════
    //  AUTHENTICATED PAYLOAD BUILDERS
    // ══════════════════════════════════════════

    private const val AUTH_HEADER_SIZE = 32 + 8 + 16 + 64 // pub + ts + nonce + sig = 120

    /**
     * Build an authenticated payload signed with the local Ed25519 identity.
     *   [pubkey:32B][timestamp:8B][nonce:16B][signature:64B][extra:remaining]
     *
     * Signature covers: commandTag || timestamp || nonce || extra
     */
    fun buildAuthenticatedPayload(
        commandTag: String,
        extra: ByteArray = ByteArray(0)
    ): ByteArray {
        val keyPair = CryptoManager.getOrDeriveSigningKeyPair()
        val pubKey = keyPair.public.encoded
        val timestamp = System.currentTimeMillis()
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }

        val toSign = buildSigningData(commandTag, timestamp, nonce, extra)
        val signature = signEd25519(keyPair.private.encoded, toSign)
        toSign.fill(0)

        val buf = ByteBuffer.allocate(AUTH_HEADER_SIZE + extra.size)
        buf.put(pubKey)
        buf.putLong(timestamp)
        buf.put(nonce)
        buf.put(signature)
        buf.put(extra)
        return buf.array()
    }

    /**
     * Verify an authenticated payload.
     * Returns (pubKey, extra) if valid, null if invalid.
     */
    fun verifyAuthenticatedPayload(
        commandTag: String,
        payload: ByteArray,
        expectedPubKey: ByteArray? = null
    ): Pair<ByteArray, ByteArray>? {
        if (payload.size < AUTH_HEADER_SIZE) return null

        val buf = ByteBuffer.wrap(payload)
        val pubKey = ByteArray(32).also { buf.get(it) }
        val timestamp = buf.getLong()
        val nonce = ByteArray(16).also { buf.get(it) }
        val signature = ByteArray(64).also { buf.get(it) }
        val extra = ByteArray(buf.remaining()).also { buf.get(it) }

        // Check timestamp window (anti-replay)
        if (abs(System.currentTimeMillis() - timestamp) > TIMESTAMP_WINDOW_MS) return null

        // Check expected pubkey if specified
        if (expectedPubKey != null && !pubKey.contentEquals(expectedPubKey)) return null

        // Verify Ed25519 signature
        val toVerify = buildSigningData(commandTag, timestamp, nonce, extra)
        val valid = verifyEd25519(pubKey, toVerify, signature)
        toVerify.fill(0)

        if (!valid) return null
        return Pair(pubKey, extra)
    }

    private fun buildSigningData(
        tag: String,
        timestamp: Long,
        nonce: ByteArray,
        extra: ByteArray
    ): ByteArray {
        val tagBytes = tag.toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(tagBytes.size + 8 + nonce.size + extra.size)
        buf.put(tagBytes)
        buf.putLong(timestamp)
        buf.put(nonce)
        buf.put(extra)
        return buf.array()
    }

    // ══════════════════════════════════════════
    //  Ed25519 CRYPTO HELPERS
    // ══════════════════════════════════════════

    private fun signEd25519(privateKey: ByteArray, data: ByteArray): ByteArray {
        val privParams = Ed25519PrivateKeyParameters(privateKey, 0)
        val signer = Ed25519Signer()
        signer.init(true, privParams)
        signer.update(data, 0, data.size)
        return signer.generateSignature()
    }

    fun verifyEd25519(
        publicKey: ByteArray,
        data: ByteArray,
        signature: ByteArray
    ): Boolean {
        return try {
            val pubParams = Ed25519PublicKeyParameters(publicKey, 0)
            val verifier = Ed25519Signer()
            verifier.init(false, pubParams)
            verifier.update(data, 0, data.size)
            verifier.verifySignature(signature)
        } catch (_: Exception) {
            false
        }
    }

    // ══════════════════════════════════════════
    //  CONVENIENCE BUILDERS
    // ══════════════════════════════════════════

    fun ackOk(): Frame = Frame(TYPE_ACK, byteArrayOf(ACK_OK))

    fun ackError(message: String = ""): Frame {
        val msgBytes = message.toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(1 + 2 + msgBytes.size)
        buf.put(ACK_ERROR)
        buf.putShort(msgBytes.size.toShort())
        buf.put(msgBytes)
        return Frame(TYPE_ACK, buf.array())
    }

    fun errorFrame(code: Short, message: String): Frame {
        val msgBytes = message.toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(2 + 2 + msgBytes.size)
        buf.putShort(code)
        buf.putShort(msgBytes.size.toShort())
        buf.put(msgBytes)
        return Frame(TYPE_ERROR, buf.array())
    }
}
