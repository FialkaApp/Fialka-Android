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
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fialkaapp.fialka.crypto.CryptoManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _mailboxOnion = MutableStateFlow<String?>(null)
    val mailboxOnion: StateFlow<String?> = _mailboxOnion.asStateFlow()

    private val _role = MutableStateFlow<String?>(null)
    val role: StateFlow<String?> = _role.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        val prefs = prefs()
        _connected.value = prefs.getBoolean(KEY_JOINED, false)
        _mailboxOnion.value = prefs.getString(KEY_ONION, null)
        _role.value = prefs.getString(KEY_ROLE, null)
    }

    private fun prefs() = EncryptedSharedPreferences.create(
        appContext,
        PREFS_NAME,
        MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
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

        val authPayload = TorTransport.buildAuthenticatedPayload(
            "JOIN",
            if (inviteCode.isNotEmpty()) inviteCode.toByteArray(Charsets.UTF_8) else ByteArray(0)
        )
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
                return Pair(true, role)
            } else {
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

        return Pair(false, "Unexpected response")
    }

    /**
     * Send a LEAVE request to the mailbox.
     */
    suspend fun leaveMailbox(): Boolean {
        val onion = getMailboxOnion() ?: return false

        val authPayload = TorTransport.buildAuthenticatedPayload("LEAVE", ByteArray(0))
        val frame = TorTransport.Frame(TorTransport.TYPE_LEAVE, authPayload)
        val response = TorTransport.sendFrame(onion, frame = frame)

        // Clear local state regardless of server response
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
        val pubkey = getMailboxPubKey() ?: return null
        val type = getMailboxType() ?: "PERSONAL"
        val base = "fialka://mailbox?onion=${Uri.encode(onion)}&pubkey=${Uri.encode(pubkey)}&type=${Uri.encode(type)}"
        return if (!inviteCode.isNullOrEmpty()) "$base&invite=${Uri.encode(inviteCode)}" else base
    }
}
