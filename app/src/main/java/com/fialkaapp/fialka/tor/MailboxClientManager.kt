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
import android.net.Uri
import android.util.Base64
import com.fialkaapp.fialka.util.FialkaSecurePrefs
import com.fialkaapp.fialka.crypto.CryptoManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

/**
 * Client-side mailbox manager for NORMAL mode users.
 * Stores the connected mailbox info and handles JOIN/FETCH/LEAVE operations.
 */
object MailboxClientManager {

    private const val PREFS_NAME = "fialka_mailbox_client"
    private const val KEY_ONION = "mailbox_onion"
    private const val KEY_PUBKEY = "mailbox_pubkey"
    private const val KEY_TYPE = "mailbox_type"
    private const val KEY_JOINED = "mailbox_joined"
    private const val KEY_ROLE = "mailbox_role"

    private lateinit var appContext: Context

    private const val FETCH_INTERVAL_MS = 10_000L      // 10 s baseline
    private const val FETCH_FAST_MS     =  5_000L      // 5 s when messages were received
    private const val MAX_FETCH_BACKOFF_MS = 60_000L    // 60 s max on repeated errors

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _mailboxOnion = MutableStateFlow<String?>(null)
    val mailboxOnion: StateFlow<String?> = _mailboxOnion.asStateFlow()

    private val _role = MutableStateFlow<String?>(null)
    val role: StateFlow<String?> = _role.asStateFlow()

    /** True while actively fetching messages from the mailbox */
    private val _fetching = MutableStateFlow(false)
    val fetching: StateFlow<Boolean> = _fetching.asStateFlow()

    private var fetchJob: Job? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = prefs()
        _connected.value = prefs.getBoolean(KEY_JOINED, false)
        _mailboxOnion.value = prefs.getString(KEY_ONION, null)
        _role.value = prefs.getString(KEY_ROLE, null)
        // Auto-start fetch loop if already joined
        if (_connected.value) startFetchLoop()
    }

    /** Start the periodic fetch loop that polls the mailbox for deposited messages. */
    fun startFetchLoop() {
        if (fetchJob?.isActive == true) return
        fetchJob = CoroutineScope(Dispatchers.IO).launch {
            var backoff = FETCH_INTERVAL_MS
            // Immediate first fetch on startup
            try {
                if (isJoined()) {
                    val n = fetchFromMailbox()
                    backoff = when {
                        n < 0  -> minOf(backoff * 2, MAX_FETCH_BACKOFF_MS) // error
                        n > 0  -> FETCH_FAST_MS                            // got messages
                        else   -> FETCH_INTERVAL_MS                        // empty
                    }
                    if (n > 0) {
                        try { OutboxManager.flushNow() } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {
                backoff = minOf(backoff * 2, MAX_FETCH_BACKOFF_MS)
            }
            while (true) {
                delay(backoff)
                try {
                    if (isJoined()) {
                        val n = fetchFromMailbox()
                        backoff = when {
                            n < 0  -> minOf(backoff * 2, MAX_FETCH_BACKOFF_MS)
                            n > 0  -> FETCH_FAST_MS
                            else   -> FETCH_INTERVAL_MS
                        }
                        if (n > 0) {
                            try { OutboxManager.flushNow() } catch (_: Exception) {}
                        }
                    } else {
                        backoff = FETCH_INTERVAL_MS
                    }
                } catch (_: Exception) {
                    backoff = minOf(backoff * 2, MAX_FETCH_BACKOFF_MS)
                }
            }
        }
    }

    /** Stop the periodic fetch loop. */
    fun stopFetchLoop() {
        fetchJob?.cancel()
        fetchJob = null
    }

    private fun prefs() = FialkaSecurePrefs.open(
        appContext,
        PREFS_NAME
    )

    /** Save mailbox config (onion, pubkey, type) — does NOT mark as joined yet. */
    fun saveMailboxInfo(onion: String, pubkey: String, type: String) {
        prefs().edit()
            .putString(KEY_ONION, onion)
            .putString(KEY_PUBKEY, pubkey)
            .putString(KEY_TYPE, type)
            .apply()
        _mailboxOnion.value = onion
    }

    fun getMailboxOnion(): String? = prefs().getString(KEY_ONION, null)
    fun getMailboxPubKey(): String? = prefs().getString(KEY_PUBKEY, null)
    fun getMailboxType(): String? = prefs().getString(KEY_TYPE, null)
    fun isJoined(): Boolean = prefs().getBoolean(KEY_JOINED, false)

    /**
     * Send a JOIN request to the mailbox.
     * @param inviteCode optional invite code for PRIVATE mailboxes.
     * @return pair of (success, role or error message)
     */
    suspend fun joinMailbox(inviteCode: String = ""): Pair<Boolean, String> {
        val onion = getMailboxOnion() ?: return Pair(false, "No mailbox configured")

        val authPayload = try {
            TorTransport.buildAuthenticatedPayload(
                "JOIN",
                if (inviteCode.isNotEmpty()) inviteCode.toByteArray(Charsets.UTF_8) else ByteArray(0)
            )
        } catch (_: IllegalStateException) {
            return Pair(false, "Identité non configurée — configurez d'abord votre compte")
        }
        val frame = TorTransport.Frame(TorTransport.TYPE_JOIN, authPayload)
        val response = TorTransport.sendFrame(onion, frame = frame)
            ?: return Pair(false, "Connection failed")

        if (response.type == TorTransport.TYPE_JOIN_RESP && response.payload.isNotEmpty()) {
            val status = response.payload[0]
            if (status == TorTransport.JOIN_ACCEPTED) {
                val role = if (response.payload.size > 1 && response.payload[1] == TorTransport.ROLE_OWNER)
                    "OWNER" else "MEMBER"
                prefs().edit()
                    .putBoolean(KEY_JOINED, true)
                    .putString(KEY_ROLE, role)
                    .apply()
                _connected.value = true
                _role.value = role
                startFetchLoop()
                return Pair(true, role)
            } else {
                // Simple format: [JOIN_REJECTED][reason_bytes...]
                val msg = if (response.payload.size > 1)
                    String(response.payload, 1, response.payload.size - 1, Charsets.UTF_8)
                else "Rejected"
                return Pair(false, msg)
            }
        }

        if (response.type == TorTransport.TYPE_ERROR) {
            val msg = String(response.payload, Charsets.UTF_8)
            return Pair(false, msg)
        }

        // P2PServer (not a mailbox) returns TYPE_ACK for unknown frames
        if (response.type == TorTransport.TYPE_ACK && response.payload.isNotEmpty()
            && response.payload[0] == TorTransport.ACK_ERROR) {
            val msg = if (response.payload.size > 3) {
                val len = java.nio.ByteBuffer.wrap(response.payload, 1, 2).short.toInt()
                    .coerceIn(0, response.payload.size - 3)
                String(response.payload, 3, len, Charsets.UTF_8)
            } else "Server error"
            return Pair(false, msg)
        }

        return Pair(false, "Unexpected response (type=0x${String.format("%02X", response.type)})")
    }

    /**
     * Send a LEAVE request to the mailbox.
     */
    suspend fun leaveMailbox(): Boolean {
        val onion = getMailboxOnion() ?: return false

        val authPayload = TorTransport.buildAuthenticatedPayload("LEAVE", ByteArray(0))
        val frame = TorTransport.Frame(TorTransport.TYPE_LEAVE, authPayload)
        val response = TorTransport.sendFrame(onion, frame = frame)

        // Stop fetch loop and clear local state regardless of server response
        stopFetchLoop()
        disconnect()
        return response?.type == TorTransport.TYPE_ACK
    }

    /** Clear all mailbox connection data locally. */
    fun disconnect() {
        prefs().edit().clear().apply()
        _connected.value = false
        _mailboxOnion.value = null
        _role.value = null
    }

    /**
     * Ping the mailbox to check if it's reachable.
     * Uses a short timeout (10s) so UI doesn't hang.
     */
    suspend fun pingMailbox(): Boolean {
        val onion = getMailboxOnion() ?: return false
        return try {
            kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                val frame = TorTransport.Frame(TorTransport.TYPE_PING, ByteArray(0))
                val response = TorTransport.sendFrame(onion, frame = frame)
                response?.type == TorTransport.TYPE_ACK
            } ?: false
        } catch (_: Exception) { false }
    }

    /**
     * List members (OWNER only). Sends LIST_MEMBERS to remote mailbox.
     * @return list of (pubKeyB64, role, joinedAt) or null on failure.
     */
    suspend fun listMembers(): List<Triple<String, String, Long>>? {
        val onion = getMailboxOnion() ?: return null
        val authPayload = TorTransport.buildAuthenticatedPayload("LIST_MEMBERS", ByteArray(0))
        val frame = TorTransport.Frame(TorTransport.TYPE_LIST_MEMBERS, authPayload)
        val response = TorTransport.sendFrame(onion, frame = frame) ?: return null
        if (response.type != TorTransport.TYPE_MEMBER_LIST) return null

        val buf = ByteBuffer.wrap(response.payload)
        val count = buf.short.toInt()
        val members = mutableListOf<Triple<String, String, Long>>()
        for (i in 0 until count) {
            val pub = ByteArray(32)
            buf.get(pub)
            val role = if (buf.get() == TorTransport.ROLE_OWNER) "OWNER" else "MEMBER"
            val joinedAt = buf.long
            val pubB64 = Base64.encodeToString(pub, Base64.NO_WRAP)
            members.add(Triple(pubB64, role, joinedAt))
        }
        return members
    }

    /**
     * Revoke/kick a member (OWNER only). Sends REVOKE to remote mailbox.
     */
    suspend fun revokeMember(targetPubKeyB64: String): Boolean {
        val onion = getMailboxOnion() ?: return false
        val targetPub = Base64.decode(targetPubKeyB64, Base64.NO_WRAP)
        val authPayload = TorTransport.buildAuthenticatedPayload("REVOKE_MEMBER", targetPub)
        val frame = TorTransport.Frame(TorTransport.TYPE_REVOKE, authPayload)
        val response = TorTransport.sendFrame(onion, frame = frame)
        return response?.type == TorTransport.TYPE_ACK
    }

    /**
     * Request a new invite code (OWNER only, PRIVATE mailboxes).
     * @return the invite code string, or null on failure.
     */
    suspend fun requestInvite(): String? {
        val onion = getMailboxOnion() ?: return null
        val authPayload = TorTransport.buildAuthenticatedPayload("INVITE_REQUEST", ByteArray(0))
        val frame = TorTransport.Frame(TorTransport.TYPE_INVITE_REQ, authPayload)
        val response = TorTransport.sendFrame(onion, frame = frame) ?: return null
        if (response.type != TorTransport.TYPE_INVITE_RESP) return null
        val buf = ByteBuffer.wrap(response.payload)
        val codeLen = buf.short.toInt()
        val codeBytes = ByteArray(codeLen)
        buf.get(codeBytes)
        return String(codeBytes, Charsets.UTF_8)
    }

    /**
     * Build a deep link URL for this mailbox (for sharing).
     */
    fun buildMailboxLink(inviteCode: String? = null): String? {
        val onion = getMailboxOnion() ?: return null
        val type = getMailboxType() ?: "PERSONAL"
        val pubkey = getMailboxPubKey().orEmpty()
        var base = "fialka://mailbox?onion=${Uri.encode(onion)}&type=${Uri.encode(type)}"
        if (pubkey.isNotEmpty()) base += "&pubkey=${Uri.encode(pubkey)}"
        return if (!inviteCode.isNullOrEmpty()) "$base&invite=${Uri.encode(inviteCode)}" else base
    }

    /**
     * Deposit an opaque blob on a remote mailbox for a specific recipient.
     * The blob is: [frameType:1B][payload] — so the recipient can reconstruct the frame on fetch.
     * @param mailboxOnion the .onion of the mailbox server
     * @param recipientEd25519PubKey Base64-encoded Ed25519 public key of the recipient (mailbox member)
     * @param frameType original frame type (e.g. TYPE_MESSAGE, TYPE_CONTACT_REQ)
     * @param payload original frame payload
     * @return true if mailbox accepted the deposit
     */
    suspend fun depositToMailbox(
        mailboxOnion: String,
        recipientEd25519PubKey: ByteArray,
        frameType: Byte,
        payload: ByteArray
    ): Boolean {
        // Blob = [frameType:1B][payload]
        val blob = ByteArray(1 + payload.size)
        blob[0] = frameType
        System.arraycopy(payload, 0, blob, 1, payload.size)

        // Extra = [recipientPub:32B][blob]
        val extra = ByteArray(32 + blob.size)
        System.arraycopy(recipientEd25519PubKey, 0, extra, 0, 32)
        System.arraycopy(blob, 0, extra, 32, blob.size)

        val authPayload = TorTransport.buildAuthenticatedPayload("DEPOSIT", extra)
        val frame = TorTransport.Frame(TorTransport.TYPE_DEPOSIT, authPayload)
        val response = TorTransport.sendFrame(mailboxOnion, frame = frame)
        return response?.type == TorTransport.TYPE_ACK &&
                response.payload.isNotEmpty() &&
                response.payload[0] == TorTransport.ACK_OK
    }

    /**
     * Fetch all pending blobs from our mailbox.
     * Parses them back into frames and feeds them to P2PServer.onFrame() for normal processing.
     * @return number of messages processed, or -1 on failure
     */
    suspend fun fetchFromMailbox(): Int {
        val onion = getMailboxOnion() ?: return -1
        if (!isJoined()) return -1

        _fetching.value = true
        try {
            val authPayload = TorTransport.buildAuthenticatedPayload("FETCH", ByteArray(0))
            val frame = TorTransport.Frame(TorTransport.TYPE_FETCH, authPayload)
            val response = TorTransport.sendFrame(onion, frame = frame) ?: return -1
            if (response.type != TorTransport.TYPE_FETCH_RESP) return -1

            val buf = ByteBuffer.wrap(response.payload)
            val count = buf.short.toInt()
            if (count == 0) return 0
            var processed = 0

            for (i in 0 until count) {
                try {
                    val idLen = buf.short.toInt()
                    val idBytes = ByteArray(idLen)
                    buf.get(idBytes)
                    val senderPub = ByteArray(32)
                    buf.get(senderPub)
                    val depositedAt = buf.long
                    val blobLen = buf.int
                    val blob = ByteArray(blobLen)
                    buf.get(blob)

                    // Blob format: [frameType:1B][payload]
                    if (blob.isNotEmpty()) {
                        val frameType = blob[0]
                        val framePayload = blob.copyOfRange(1, blob.size)
                        val reconstructedFrame = TorTransport.Frame(frameType, framePayload)
                        P2PServer.onFrame(reconstructedFrame)
                        processed++
                    }
                } catch (_: Exception) {
                    // Skip malformed blob, continue with next
                }
            }
            return processed
        } finally {
            _fetching.value = false
        }
    }
}
