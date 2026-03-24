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
import android.util.Base64
import com.fialkaapp.fialka.AppMode
import com.fialkaapp.fialka.MailboxType
import com.fialkaapp.fialka.data.local.MailboxDatabase
import com.fialkaapp.fialka.data.model.MailboxBlob
import com.fialkaapp.fialka.data.model.MailboxInvite
import com.fialkaapp.fialka.data.model.MailboxMember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.UUID

/**
 * Mailbox server — runs ONLY in MAILBOX mode.
 * Implements TorTransport.FrameListener to handle all mailbox protocol commands:
 *   JOIN, DEPOSIT, FETCH, LEAVE, INVITE_REQUEST, REVOKE, LIST_MEMBERS.
 *
 * Security model:
 *  - All commands are Ed25519 signed + timestamp-windowed + nonce-protected.
 *  - The mailbox NEVER decrypts blobs — it's opaque storage.
 *  - OWNER = first member to JOIN, irréversible.
 *  - Only OWNER can create invites, revoke members, list members.
 */
object MailboxServer : TorTransport.FrameListener {

    private const val BLOB_TTL_MS = 7L * 24 * 60 * 60 * 1000  // 7 days
    private const val INVITE_TTL_MS = 24L * 60 * 60 * 1000     // 24 hours
    private const val INVITE_CODE_LENGTH = 32
    private const val TIMESTAMP_WINDOW_MS = 5L * 60 * 1000

    private lateinit var appContext: Context

    // ── Dashboard stats (observable) ──
    private val _blobCount = MutableStateFlow(0)
    val blobCount: StateFlow<Int> = _blobCount.asStateFlow()

    private val _memberCount = MutableStateFlow(0)
    val memberCount: StateFlow<Int> = _memberCount.asStateFlow()

    private val _totalSize = MutableStateFlow(0L)
    val totalSize: StateFlow<Long> = _totalSize.asStateFlow()

    // ── Nonce replay protection (in-memory, auto-expiring) ──
    private val seenNonces = LinkedHashMap<String, Long>(500, 0.75f, true)
    private val nonceLock = Any()

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    fun start() {
        TorTransport.startServer(frameListener = this)
    }

    fun stop() {
        TorTransport.stopServer()
    }

    // ══════════════════════════════════════════
    //  FRAME DISPATCH
    // ══════════════════════════════════════════

    override suspend fun onFrame(frame: TorTransport.Frame): TorTransport.Frame? {
        return when (frame.type) {
            TorTransport.TYPE_JOIN -> handleJoin(frame.payload)
            TorTransport.TYPE_DEPOSIT -> handleDeposit(frame.payload)
            TorTransport.TYPE_FETCH -> handleFetch(frame.payload)
            TorTransport.TYPE_LEAVE -> handleLeave(frame.payload)
            TorTransport.TYPE_INVITE_REQ -> handleInviteRequest(frame.payload)
            TorTransport.TYPE_REVOKE -> handleRevoke(frame.payload)
            TorTransport.TYPE_LIST_MEMBERS -> handleListMembers(frame.payload)
            TorTransport.TYPE_PING -> TorTransport.ackOk()
            else -> TorTransport.ackError("unknown frame type")
        }
    }

    // ══════════════════════════════════════════
    //  JOIN — new member wants to join
    // ══════════════════════════════════════════

    private suspend fun handleJoin(payload: ByteArray): TorTransport.Frame {
        val verified = TorTransport.verifyAuthenticatedPayload("JOIN", payload)
            ?: return rejectJoin("auth failed")

        val (pubKey, extra) = verified
        val inviteCode = if (extra.isNotEmpty()) String(extra, Charsets.UTF_8) else ""

        // Anti-replay: extract nonce from original payload
        if (isNonceReplay(payload)) return rejectJoin("nonce replay")

        val db = MailboxDatabase.getInstance(appContext)
        val memberDao = db.memberDao()
        val pubKeyB64 = Base64.encodeToString(pubKey, Base64.NO_WRAP)

        // Already a member? Return current role
        val existing = memberDao.getByPubKey(pubKeyB64)
        if (existing != null) {
            val role = if (existing.role == MailboxMember.ROLE_OWNER)
                TorTransport.ROLE_OWNER else TorTransport.ROLE_MEMBER
            return acceptJoin(role)
        }

        val mailboxType = AppMode.getMailboxType(appContext)

        when (mailboxType) {
            MailboxType.PERSONAL -> {
                // Personal: auto-accept first member as OWNER, reject all others
                if (memberDao.count() >= 1) return rejectJoin("personal mailbox full")
                memberDao.insert(
                    MailboxMember(pubKeyB64, MailboxMember.ROLE_OWNER, System.currentTimeMillis())
                )
                refreshStats()
                return acceptJoin(TorTransport.ROLE_OWNER)
            }

            MailboxType.PRIVATE -> {
                if (memberDao.count() == 0) {
                    // First member = OWNER (auto-accept)
                    memberDao.insert(
                        MailboxMember(pubKeyB64, MailboxMember.ROLE_OWNER, System.currentTimeMillis())
                    )
                    refreshStats()
                    return acceptJoin(TorTransport.ROLE_OWNER)
                }
                // Subsequent members: require valid invite code
                if (inviteCode.isEmpty()) return rejectJoin("invite code required")
                val invite = db.inviteDao().getValidInvite(inviteCode)
                    ?: return rejectJoin("invalid or expired invite")
                db.inviteDao().markUsed(inviteCode, pubKeyB64)
                memberDao.insert(
                    MailboxMember(pubKeyB64, MailboxMember.ROLE_MEMBER, System.currentTimeMillis())
                )
                refreshStats()
                return acceptJoin(TorTransport.ROLE_MEMBER)
            }

            MailboxType.NONE -> return rejectJoin("mailbox not configured")
        }
    }

    private fun acceptJoin(role: Byte): TorTransport.Frame {
        val buf = ByteBuffer.allocate(2)
        buf.put(TorTransport.JOIN_ACCEPTED)
        buf.put(role)
        return TorTransport.Frame(TorTransport.TYPE_JOIN_RESP, buf.array())
    }

    private fun rejectJoin(reason: String): TorTransport.Frame {
        val reasonBytes = reason.toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(1 + 1 + 2 + reasonBytes.size)
        buf.put(TorTransport.JOIN_REJECTED)
        buf.put(0.toByte())
        buf.putShort(reasonBytes.size.toShort())
        buf.put(reasonBytes)
        return TorTransport.Frame(TorTransport.TYPE_JOIN_RESP, buf.array())
    }

    // ══════════════════════════════════════════
    //  DEPOSIT — store encrypted blob
    // ══════════════════════════════════════════

    private suspend fun handleDeposit(payload: ByteArray): TorTransport.Frame {
        val verified = TorTransport.verifyAuthenticatedPayload("DEPOSIT", payload)
            ?: return TorTransport.ackError("auth failed")
        if (isNonceReplay(payload)) return TorTransport.ackError("nonce replay")

        val (senderPub, extra) = verified
        if (extra.size < 32) return TorTransport.ackError("missing recipient")

        val recipientPub = extra.copyOfRange(0, 32)
        val blob = extra.copyOfRange(32, extra.size)

        val recipientB64 = Base64.encodeToString(recipientPub, Base64.NO_WRAP)

        // Recipient must be a member of this mailbox
        val db = MailboxDatabase.getInstance(appContext)
        if (db.memberDao().getByPubKey(recipientB64) == null) {
            return TorTransport.ackError("recipient not a member")
        }

        val senderB64 = Base64.encodeToString(senderPub, Base64.NO_WRAP)
        val now = System.currentTimeMillis()
        db.blobDao().insert(
            MailboxBlob(
                blobId = UUID.randomUUID().toString(),
                recipientPubKey = recipientB64,
                senderPubKey = senderB64,
                data = blob,
                depositedAt = now,
                expiresAt = now + BLOB_TTL_MS
            )
        )
        refreshStats()
        return TorTransport.ackOk()
    }

    // ══════════════════════════════════════════
    //  FETCH — retrieve pending blobs
    // ══════════════════════════════════════════

    private suspend fun handleFetch(payload: ByteArray): TorTransport.Frame {
        val verified = TorTransport.verifyAuthenticatedPayload("FETCH", payload)
            ?: return TorTransport.ackError("auth failed")
        if (isNonceReplay(payload)) return TorTransport.ackError("nonce replay")

        val (requesterPub, _) = verified
        val requesterB64 = Base64.encodeToString(requesterPub, Base64.NO_WRAP)

        val db = MailboxDatabase.getInstance(appContext)
        val member = db.memberDao().getByPubKey(requesterB64)
            ?: return TorTransport.ackError("not a member")

        // Update last seen
        db.memberDao().insert(member.copy(lastSeenAt = System.currentTimeMillis()))

        val blobs = db.blobDao().getBlobsForRecipient(requesterB64)

        // Build response: [count:2B] for each: [idLen:2B][id][senderPub:32B][ts:8B][blobLen:4B][blob]
        var totalSize = 2
        for (b in blobs) {
            totalSize += 2 + b.blobId.toByteArray(Charsets.UTF_8).size + 32 + 8 + 4 + b.data.size
        }

        val buf = ByteBuffer.allocate(totalSize)
        buf.putShort(blobs.size.toShort())
        for (b in blobs) {
            val idBytes = b.blobId.toByteArray(Charsets.UTF_8)
            val senderPub = Base64.decode(b.senderPubKey, Base64.NO_WRAP)
            buf.putShort(idBytes.size.toShort())
            buf.put(idBytes)
            buf.put(senderPub)
            buf.putLong(b.depositedAt)
            buf.putInt(b.data.size)
            buf.put(b.data)
        }

        // Delete fetched blobs (delivered)
        for (b in blobs) {
            db.blobDao().deleteById(b.blobId)
        }
        refreshStats()

        return TorTransport.Frame(TorTransport.TYPE_FETCH_RESP, buf.array())
    }

    // ══════════════════════════════════════════
    //  LEAVE — member leaves the mailbox
    // ══════════════════════════════════════════

    private suspend fun handleLeave(payload: ByteArray): TorTransport.Frame {
        val verified = TorTransport.verifyAuthenticatedPayload("LEAVE", payload)
            ?: return TorTransport.ackError("auth failed")
        if (isNonceReplay(payload)) return TorTransport.ackError("nonce replay")

        val (requesterPub, _) = verified
        val requesterB64 = Base64.encodeToString(requesterPub, Base64.NO_WRAP)

        val db = MailboxDatabase.getInstance(appContext)
        val member = db.memberDao().getByPubKey(requesterB64)
            ?: return TorTransport.ackError("not a member")

        // Owner cannot leave remotely — must reset from device
        if (member.role == MailboxMember.ROLE_OWNER) {
            return TorTransport.ackError("owner cannot leave remotely")
        }

        db.memberDao().deleteByPubKey(requesterB64)
        // Clean up pending blobs for this member
        for (blob in db.blobDao().getBlobsForRecipient(requesterB64)) {
            db.blobDao().deleteById(blob.blobId)
        }
        refreshStats()
        return TorTransport.ackOk()
    }

    // ══════════════════════════════════════════
    //  INVITE_REQUEST — owner requests new code
    // ══════════════════════════════════════════

    private suspend fun handleInviteRequest(payload: ByteArray): TorTransport.Frame {
        val verified = TorTransport.verifyAuthenticatedPayload("INVITE_REQUEST", payload)
            ?: return TorTransport.ackError("auth failed")
        if (isNonceReplay(payload)) return TorTransport.ackError("nonce replay")

        val (requesterPub, _) = verified
        val requesterB64 = Base64.encodeToString(requesterPub, Base64.NO_WRAP)

        val db = MailboxDatabase.getInstance(appContext)
        val member = db.memberDao().getByPubKey(requesterB64)
            ?: return TorTransport.ackError("not a member")

        if (member.role != MailboxMember.ROLE_OWNER) {
            return TorTransport.ackError("only owner can create invites")
        }

        if (AppMode.getMailboxType(appContext) != MailboxType.PRIVATE) {
            return TorTransport.ackError("invites only for private mailbox")
        }

        // Generate cryptographically secure invite code
        val codeBytes = ByteArray(INVITE_CODE_LENGTH)
        SecureRandom().nextBytes(codeBytes)
        val code = Base64.encodeToString(
            codeBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )

        val now = System.currentTimeMillis()
        db.inviteDao().insert(
            MailboxInvite(
                code = code,
                createdAt = now,
                expiresAt = now + INVITE_TTL_MS
            )
        )
        // Housekeeping
        db.inviteDao().deleteExpired()

        val codeUtf8 = code.toByteArray(Charsets.UTF_8)
        val buf = ByteBuffer.allocate(2 + codeUtf8.size)
        buf.putShort(codeUtf8.size.toShort())
        buf.put(codeUtf8)
        return TorTransport.Frame(TorTransport.TYPE_INVITE_RESP, buf.array())
    }

    // ══════════════════════════════════════════
    //  REVOKE — owner removes a member
    // ══════════════════════════════════════════

    private suspend fun handleRevoke(payload: ByteArray): TorTransport.Frame {
        val verified = TorTransport.verifyAuthenticatedPayload("REVOKE_MEMBER", payload)
            ?: return TorTransport.ackError("auth failed")
        if (isNonceReplay(payload)) return TorTransport.ackError("nonce replay")

        val (requesterPub, extra) = verified
        if (extra.size < 32) return TorTransport.ackError("missing target pubkey")

        val requesterB64 = Base64.encodeToString(requesterPub, Base64.NO_WRAP)
        val targetPub = extra.copyOfRange(0, 32)
        val targetB64 = Base64.encodeToString(targetPub, Base64.NO_WRAP)

        val db = MailboxDatabase.getInstance(appContext)
        val member = db.memberDao().getByPubKey(requesterB64)
            ?: return TorTransport.ackError("not a member")

        if (member.role != MailboxMember.ROLE_OWNER) {
            return TorTransport.ackError("only owner can revoke")
        }

        if (requesterB64 == targetB64) {
            return TorTransport.ackError("cannot revoke self")
        }

        db.memberDao().deleteByPubKey(targetB64)
        // Clean up target's blobs
        for (blob in db.blobDao().getBlobsForRecipient(targetB64)) {
            db.blobDao().deleteById(blob.blobId)
        }
        refreshStats()
        return TorTransport.ackOk()
    }

    // ══════════════════════════════════════════
    //  LIST_MEMBERS — owner queries members
    // ══════════════════════════════════════════

    private suspend fun handleListMembers(payload: ByteArray): TorTransport.Frame {
        val verified = TorTransport.verifyAuthenticatedPayload("LIST_MEMBERS", payload)
            ?: return TorTransport.ackError("auth failed")
        if (isNonceReplay(payload)) return TorTransport.ackError("nonce replay")

        val (requesterPub, _) = verified
        val requesterB64 = Base64.encodeToString(requesterPub, Base64.NO_WRAP)

        val db = MailboxDatabase.getInstance(appContext)
        val member = db.memberDao().getByPubKey(requesterB64)
            ?: return TorTransport.ackError("not a member")

        if (member.role != MailboxMember.ROLE_OWNER) {
            return TorTransport.ackError("only owner can list members")
        }

        val members = db.memberDao().getAll()

        // [count:2B] for each: [pub:32B][role:1B][joinedAt:8B]
        val buf = ByteBuffer.allocate(2 + members.size * 41)
        buf.putShort(members.size.toShort())
        for (m in members) {
            val pub = Base64.decode(m.pubKey, Base64.NO_WRAP)
            buf.put(pub)
            buf.put(
                if (m.role == MailboxMember.ROLE_OWNER)
                    TorTransport.ROLE_OWNER else TorTransport.ROLE_MEMBER
            )
            buf.putLong(m.joinedAt)
        }
        return TorTransport.Frame(TorTransport.TYPE_MEMBER_LIST, buf.array())
    }

    // ══════════════════════════════════════════
    //  UTILITIES
    // ══════════════════════════════════════════

    /**
     * Check nonce replay by extracting the 16-byte nonce from an
     * authenticated payload (offset 40 = 32 pub + 8 timestamp).
     */
    private fun isNonceReplay(payload: ByteArray): Boolean {
        if (payload.size < 56) return true // 32+8+16 minimum
        val nonce = payload.copyOfRange(40, 56)
        val key = Base64.encodeToString(nonce, Base64.NO_WRAP)
        val now = System.currentTimeMillis()
        synchronized(nonceLock) {
            // Evict expired nonces
            val iter = seenNonces.entries.iterator()
            while (iter.hasNext()) {
                if (now - iter.next().value > TIMESTAMP_WINDOW_MS) iter.remove()
                else break // LinkedHashMap is insertion-ordered
            }
            if (seenNonces.containsKey(key)) return true
            seenNonces[key] = now
            return false
        }
    }

    /** Refresh dashboard stats from database. */
    suspend fun refreshStats() {
        try {
            val db = MailboxDatabase.getInstance(appContext)
            _blobCount.value = db.blobDao().countAll()
            _memberCount.value = db.memberDao().count()
            _totalSize.value = db.blobDao().totalSize()
        } catch (_: Exception) {}
    }

    /** Purge expired blobs and invites. Called from dashboard or scheduled. */
    suspend fun purgeExpired() {
        val db = MailboxDatabase.getInstance(appContext)
        db.blobDao().deleteExpired()
        db.inviteDao().deleteExpired()
        refreshStats()
    }

    /**
     * Full mailbox reset — wipe all data, stop server, destroy DB.
     * After this, the mailbox type can be re-chosen.
     */
    suspend fun resetMailbox() {
        stop()
        try {
            val db = MailboxDatabase.getInstance(appContext)
            db.blobDao().deleteAll()
            db.memberDao().deleteAll()
            db.inviteDao().deleteAll()
        } catch (_: Exception) {}
        MailboxDatabase.destroyInstance()
        AppMode.resetMailboxType(appContext)
        _blobCount.value = 0
        _memberCount.value = 0
        _totalSize.value = 0
        synchronized(nonceLock) { seenNonces.clear() }
    }
}
