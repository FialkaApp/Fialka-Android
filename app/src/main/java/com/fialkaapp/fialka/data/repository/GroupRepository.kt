/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.data.repository

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.lifecycle.LiveData
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.crypto.FialkaNative
import com.fialkaapp.fialka.data.local.FialkaDatabase
import com.fialkaapp.fialka.data.model.*
import com.fialkaapp.fialka.tor.OutboxManager
import com.fialkaapp.fialka.tor.TorTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.spec.SecretKeySpec

/**
 * GroupRepository — single source of truth for group chat state.
 *
 * Security model:
 *   - groupKey: AES-256 (32-byte random) shared to each member via their Double
 *     Ratchet channel (TYPE_MESSAGE carrying "GROUP_INVITE|<json>"). This means
 *     the group key inherits PFS from the individual DR sessions at invite time.
 *   - Message encryption: AES-256-GCM(plaintext, groupKey) via Rust fialka-core.
 *   - Message authentication: Ed25519 signature by sender over (ciphertext+groupId+ts).
 *     Receivers verify using the sender's Ed25519 key stored in their Contact row.
 *   - Admin actions (kick, rename) are signed by the actor's Ed25519 key so any
 *     member can verify legitimacy without a central authority.
 *
 * Note: Group key rotation (after kick) is intentionally deferred to V4.2.
 * For MVP the design matches Signal's pre-rotation behaviour: kicked members
 * can still decrypt past messages they already received (they hold the key),
 * but remaining members are informed via TYPE_GROUP_ADMIN/KICK and stop delivering
 * future messages to the kicked .onion.
 */
class GroupRepository(private val appContext: Context) {

    private val db     = FialkaDatabase.getInstance(appContext)
    private val groupDao   = db.groupDao()
    private val messageDao = db.messageLocalDao()
    private val contactDao = db.contactDao()
    private val userDao    = db.userLocalDao()
    private val crypto     = CryptoManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        private const val TAG = "GroupRepository"

        /** Prefix carried inside a TYPE_MESSAGE Double Ratchet payload. */
        const val GROUP_INVITE_PREFIX = "GROUP_INVITE|"
        /** Acceptance notification sent back to the inviter. */
        const val GROUP_INVITE_ACCEPT_PREFIX = "GROUP_INVITE_ACCEPT|"
        /** Decline notification sent back to the inviter. */
        const val GROUP_INVITE_DECLINE_PREFIX = "GROUP_INVITE_DECLINE|"
        /** Admin action frame prefix in TYPE_GROUP_ADMIN payloads. */
        const val GROUP_ADMIN_ACTION  = "GROUP_ADMIN|"

        @Volatile
        private var INSTANCE: GroupRepository? = null

        fun getInstance(context: Context): GroupRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GroupRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // =========================================================================
    // READ (LiveData — observed by ViewModels)
    // =========================================================================

    fun getAllGroups(): LiveData<List<GroupLocal>> = groupDao.getAllGroups()

    fun getMembersLive(groupId: String): LiveData<List<GroupMember>> =
        groupDao.getMembersLive(groupId)

    suspend fun getGroup(groupId: String): GroupLocal? = groupDao.getGroupById(groupId)

    suspend fun getMembers(groupId: String): List<GroupMember> =
        groupDao.getMembersForGroup(groupId)

    fun getMessagesForGroup(groupId: String): LiveData<List<MessageLocal>> =
        messageDao.getMessagesForConversation(groupId)          // reuses existing DAO

    // =========================================================================
    // CREATE GROUP
    // =========================================================================

    /**
     * Create a new group, store it locally, and invite every contact in [memberContacts].
     *
     * @param name          Display name for the group.
     * @param memberContacts Contacts to invite (current user is always added as OWNER).
     * @return The [GroupLocal] that was stored, or null if identity is unavailable.
     */
    suspend fun createGroup(name: String, memberContacts: List<Contact>): GroupLocal? {
        val me = userDao.getUser() ?: run {
            Log.e(TAG, "createGroup: no local user"); return null
        }

        // Generate a fresh 32-byte AES-256 group key
        val rawKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val groupKeyBase64 = Base64.encodeToString(rawKey, Base64.NO_WRAP)
        rawKey.fill(0)

        val groupId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val group = GroupLocal(
            groupId        = groupId,
            name           = name,
            groupKey       = groupKeyBase64,
            createdAt      = now,
            createdByPubKey = me.publicKey,
            myRole         = GroupLocal.ROLE_OWNER,
            memberCount    = memberContacts.size + 1,
            lastMessageTimestamp = now
        )
        groupDao.insertGroup(group)

        // Insert myself as OWNER
        val myOnion = try { crypto.getOnionAddress() } catch (_: Exception) { "" }
        groupDao.insertMember(
            GroupMember(
                groupId     = groupId,
                publicKey   = me.publicKey,
                displayName = me.displayName,
                onionAddress = myOnion,
                role        = GroupLocal.ROLE_OWNER,
                joinedAt    = now
            )
        )

        // Insert all invited members
        val membersForInvite = mutableListOf<GroupMember>()
        for (contact in memberContacts) {
            val member = GroupMember(
                groupId     = groupId,
                publicKey   = contact.publicKey,
                displayName = contact.displayName,
                onionAddress = contact.onionAddress ?: "",
                mailboxOnion = contact.mailboxOnion ?: "",
                role        = GroupLocal.ROLE_MEMBER,
                joinedAt    = now
            )
            groupDao.insertMember(member)
            membersForInvite.add(member)
        }

        // System message: "Groupe créé"
        insertSystemMessage(groupId, appContext.getString(com.fialkaapp.fialka.R.string.sys_group_created, name))

        // Send invitations to all members (except self)
        val allMembers = listOf(
            GroupMember(groupId, me.publicKey, me.displayName, myOnion,
                        "", GroupLocal.ROLE_OWNER, now)
        ) + membersForInvite

        for (contact in memberContacts) {
            sendInvitation(group, allMembers, contact)
        }

        return group
    }

    /**
     * Deliver the group invitation to one contact via their Double Ratchet channel.
     * The actual transport is TYPE_MESSAGE (existing DR session) wrapping
     * "GROUP_INVITE|<base64-json>" so the groupKey is E2E protected.
     */
    private suspend fun sendInvitation(
        group: GroupLocal,
        allMembers: List<GroupMember>,
        recipient: Contact
    ) {
        val membersJson = JSONArray().apply {
            allMembers.forEach { m ->
                put(JSONObject().apply {
                    put("publicKey",   m.publicKey)
                    put("displayName", m.displayName)
                    put("onionAddress", m.onionAddress)
                    put("mailboxOnion", m.mailboxOnion)
                    put("role",        m.role)
                })
            }
        }
        val payload = JSONObject().apply {
            put("groupId",       group.groupId)
            put("groupName",     group.name)
            put("groupKey",      group.groupKey)
            put("members",       membersJson)
            put("createdByPubKey", group.createdByPubKey)
            put("createdAt",     group.createdAt)
        }
        val inviteMsg = GROUP_INVITE_PREFIX + Base64.encodeToString(
            payload.toString().toByteArray(Charsets.UTF_8), Base64.NO_WRAP
        )

        // Delegate to ChatRepository for Double Ratchet encryption + delivery
        try {
            // The conversationId is a UUID, not the publicKey — look it up properly
            val conv = db.conversationDao().getConversationByParticipantPublicKey(recipient.publicKey)
            val convId = conv?.conversationId
            if (convId == null) {
                Log.e(TAG, "sendInvitation: no conversation found for ${recipient.displayName} (${recipient.publicKey.take(8)})")
                return
            }
            ChatRepository(appContext).sendMessage(
                conversationId = convId,
                plaintext      = inviteMsg
            )
        } catch (e: Exception) {
            Log.e(TAG, "sendInvitation failed for ${recipient.displayName}: ${e.message}")
        }
    }

    // =========================================================================
    // ACCEPT / DECLINE INVITE (recipient side)
    // =========================================================================

    /**
     * Called when the recipient taps "Rejoindre".
     * Joins the group locally and sends an acceptance notification back to the inviter.
     *
     * @param messageLocalId  The localId of the GROUP_INVITE message in the 1:1 conversation.
     * @param conversationId  The 1:1 conversation with the inviter.
     */
    suspend fun acceptInvite(messageLocalId: String, conversationId: String) {
        val msg = messageDao.getMessageById(messageLocalId) ?: return
        val base64Json = msg.plaintext.removePrefix(GROUP_INVITE_PREFIX)

        // Parse groupId and groupName for the notification
        val json = runCatching {
            val str = Base64.decode(base64Json, Base64.NO_WRAP).toString(Charsets.UTF_8)
            org.json.JSONObject(str)
        }.getOrNull() ?: return

        val groupId   = json.optString("groupId", "")
        val groupName = json.optString("groupName", "")

        // Join the group (idempotent)
        handleGroupInvite(base64Json)

        // Update the invite message to show "accepted" status
        messageDao.updateMessagePlaintext(
            messageLocalId,
            "$GROUP_INVITE_ACCEPT_PREFIX$groupId|$groupName"
        )

        // Send acceptance back to inviter via DR
        try {
            ChatRepository(appContext).sendMessage(
                conversationId = conversationId,
                plaintext      = "$GROUP_INVITE_ACCEPT_PREFIX$groupId|$groupName"
            )
        } catch (e: Exception) {
            Log.e(TAG, "acceptInvite: failed to notify inviter: ${e.message}")
        }
    }

    /**
     * Called when the recipient taps "Refuser".
     * Updates the local message and notifies the inviter.
     *
     * @param messageLocalId  The localId of the GROUP_INVITE message in the 1:1 conversation.
     * @param conversationId  The 1:1 conversation with the inviter.
     */
    suspend fun declineInvite(messageLocalId: String, conversationId: String) {
        val msg = messageDao.getMessageById(messageLocalId) ?: return
        val base64Json = msg.plaintext.removePrefix(GROUP_INVITE_PREFIX)

        val json = runCatching {
            val str = Base64.decode(base64Json, Base64.NO_WRAP).toString(Charsets.UTF_8)
            org.json.JSONObject(str)
        }.getOrNull() ?: return

        val groupId   = json.optString("groupId", "")
        val groupName = json.optString("groupName", "")

        // Update the invite message to show "declined" status
        messageDao.updateMessagePlaintext(
            messageLocalId,
            "$GROUP_INVITE_DECLINE_PREFIX$groupId|$groupName"
        )

        // Send decline back to inviter via DR
        try {
            ChatRepository(appContext).sendMessage(
                conversationId = conversationId,
                plaintext      = "$GROUP_INVITE_DECLINE_PREFIX$groupId|$groupName"
            )
        } catch (e: Exception) {
            Log.e(TAG, "declineInvite: failed to notify inviter: ${e.message}")
        }
    }

    /**
     * Called on the inviter's side when they receive an ACCEPT or DECLINE notification.
     * Finds the original GROUP_INVITE message in the conversation and updates its plaintext
     * so the sent card reflects the response.
     *
     * @param plaintext      The raw GROUP_INVITE_ACCEPT|... or GROUP_INVITE_DECLINE|... text.
     * @param conversationId The 1:1 conversation with the recipient.
     */
    suspend fun handleInviteResponse(plaintext: String, conversationId: String) {
        val isAccept = plaintext.startsWith(GROUP_INVITE_ACCEPT_PREFIX)
        val payload  = if (isAccept) plaintext.removePrefix(GROUP_INVITE_ACCEPT_PREFIX)
                       else          plaintext.removePrefix(GROUP_INVITE_DECLINE_PREFIX)
        val parts    = payload.split("|", limit = 2)
        val groupId  = parts.getOrElse(0) { "" }
        val groupName= parts.getOrElse(1) { "" }

        if (groupId.isEmpty()) return

        // Find the original sent invite message in this conversation
        val sentInvite = messageDao.getGroupInviteMessageForGroup(conversationId, groupId)
        if (sentInvite != null) {
            val newStatus = if (isAccept) GROUP_INVITE_ACCEPT_PREFIX else GROUP_INVITE_DECLINE_PREFIX
            messageDao.updateMessagePlaintext(
                sentInvite.localId,
                "$newStatus$groupId|$groupName"
            )
        }

        // Also insert a system message in the group if it exists
        if (groupId.isNotEmpty() && isAccept) {
            val memberName = conversationId.let {
                db.conversationDao().getConversationById(it)?.let { conv ->
                    db.contactDao().getContactByPublicKey(conv.participantPublicKey)?.displayName
                }
            } ?: "Un membre"
            if (groupDao.getGroupById(groupId) != null) {
                insertSystemMessage(groupId, "$memberName a rejoint le groupe")
            }
        }
    }

    // =========================================================================
    // SEND GROUP MESSAGE
    // =========================================================================

    /**
     * Encrypt and broadcast a group message to all members.
     *
     * Each member receives a [TorTransport.TYPE_GROUP_MSG] frame directly.
     * The message is stored locally immediately (optimistic).
     *
     * @return The stored [MessageLocal] or null on failure.
     */
    suspend fun sendGroupMessage(
        groupId: String,
        plaintext: String,
        replyToId: String? = null,
        replyToPreview: String? = null
    ): MessageLocal? {
        val group = groupDao.getGroupById(groupId) ?: run {
            Log.e(TAG, "sendGroupMessage: group $groupId not found"); return null
        }
        val me = userDao.getUser() ?: return null

        val ts = System.currentTimeMillis()
        val localId = UUID.randomUUID().toString()

        // Encrypt with shared group key (AES-256-GCM via Rust)
        val keyBytes = Base64.decode(group.groupKey, Base64.NO_WRAP)
        val encResult = FialkaNative.encryptAes(
            plaintext.toByteArray(Charsets.UTF_8), keyBytes
        )
        keyBytes.fill(0)
        // encResult = iv(12) || ciphertext
        val iv = encResult.copyOfRange(0, 12)
        val ct = encResult.copyOfRange(12, encResult.size)
        val ctBase64 = Base64.encodeToString(ct, Base64.NO_WRAP)
        val ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP)

        // Ed25519 signature: sign(ciphertext_base64 || groupId || ts)
        val sigBase64 = signGroupData(ctBase64, groupId, ts)

        // Build wire payload
        val wireJson = JSONObject().apply {
            put("groupId",       groupId)
            put("localId",       localId)
            put("ciphertext",    ctBase64)
            put("iv",            ivBase64)
            put("senderPubKey",  me.publicKey)
            put("signature",     sigBase64)
            put("timestamp",     ts)
            put("replyToId",     replyToId ?: "")
            put("replyToPreview", replyToPreview ?: "")
        }
        val frameBody = wireJson.toString().toByteArray(Charsets.UTF_8)
        val frame = TorTransport.Frame(TorTransport.TYPE_GROUP_MSG, frameBody)

        // Store locally (optimistic)
        val msg = MessageLocal(
            localId          = localId,
            conversationId   = groupId,
            senderPublicKey  = me.publicKey,
            plaintext        = plaintext,
            timestamp        = ts,
            isMine           = true,
            deliveryStatus   = MessageLocal.DELIVERY_SENDING,
            replyToId        = replyToId,
            replyToPreview   = replyToPreview
        )
        messageDao.insertMessage(msg)

        // Update group preview
        groupDao.updateLastMessage(groupId, plaintext.take(60), ts)

        // Broadcast to all members except self
        val myKey = me.publicKey
        val members = groupDao.getMembersForGroup(groupId).filter { it.publicKey != myKey }
        var delivered = false
        for (member in members) {
            if (member.onionAddress.isBlank()) continue
            scope.launch {
                try {
                    val mailbox = member.mailboxOnion.ifBlank { null }
                    OutboxManager.sendOrQueue(member.onionAddress, frame, mailbox)
                    delivered = true
                } catch (e: Exception) {
                    Log.w(TAG, "sendGroupMessage to ${member.displayName}: ${e.message}")
                }
            }
        }

        // Update delivery status after broadcast attempt
        val finalStatus = if (members.isEmpty()) MessageLocal.DELIVERY_SENT
                          else MessageLocal.DELIVERY_SENT
        messageDao.updateDeliveryStatus(localId, finalStatus)
        messageDao.updateDeliveryStatus(localId, MessageLocal.DELIVERY_SENT)

        return msg
    }

    // =========================================================================
    // RECEIVE GROUP INVITE (called by P2PServer when DR decrypts GROUP_INVITE|...)
    // =========================================================================

    /**
     * Process an incoming group invitation delivered through the Double Ratchet.
     * Called from P2PServer when decrypted text starts with [GROUP_INVITE_PREFIX].
     */
    suspend fun handleGroupInvite(base64Json: String) {
        try {
            val jsonStr = Base64.decode(base64Json, Base64.NO_WRAP).toString(Charsets.UTF_8)
            val json = JSONObject(jsonStr)

            val groupId     = json.getString("groupId")
            val groupName   = json.getString("groupName")
            val groupKey    = json.getString("groupKey")
            val createdBy   = json.getString("createdByPubKey")
            val createdAt   = json.getLong("createdAt")
            val membersJson = json.getJSONArray("members")

            // Idempotent — ignore if already stored
            if (groupDao.getGroupById(groupId) != null) return

            // Determine my role from member list
            val me = userDao.getUser()
            var myRole = GroupLocal.ROLE_MEMBER
            val members = mutableListOf<GroupMember>()
            for (i in 0 until membersJson.length()) {
                val m = membersJson.getJSONObject(i)
                val pk = m.getString("publicKey")
                val role = m.optString("role", GroupLocal.ROLE_MEMBER)
                if (me != null && pk == me.publicKey) myRole = role
                members.add(
                    GroupMember(
                        groupId      = groupId,
                        publicKey    = pk,
                        displayName  = m.getString("displayName"),
                        onionAddress = m.optString("onionAddress", ""),
                        mailboxOnion = m.optString("mailboxOnion", ""),
                        role         = role,
                        joinedAt     = createdAt
                    )
                )
            }

            val group = GroupLocal(
                groupId         = groupId,
                name            = groupName,
                groupKey        = groupKey,
                createdAt       = createdAt,
                createdByPubKey = createdBy,
                myRole          = myRole,
                memberCount     = members.size,
                lastMessageTimestamp = createdAt
            )
            groupDao.insertGroup(group)
            groupDao.insertMembers(members)
            insertSystemMessage(groupId, "Vous avez rejoint le groupe « $groupName »")
        } catch (e: Exception) {
            Log.e(TAG, "handleGroupInvite failed: ${e.message}", e)
        }
    }

    // =========================================================================
    // RECEIVE GROUP MESSAGE (TYPE_GROUP_MSG frame)
    // =========================================================================

    /**
     * Process an incoming [TorTransport.TYPE_GROUP_MSG] frame payload.
     * Verifies Ed25519 signature, decrypts, stores message.
     */
    suspend fun handleGroupMessage(payload: ByteArray) {
        try {
            val json       = JSONObject(payload.toString(Charsets.UTF_8))
            val groupId    = json.getString("groupId")
            val remoteId   = json.optString("localId", UUID.randomUUID().toString())
            val ctBase64   = json.getString("ciphertext")
            val ivBase64   = json.getString("iv")
            val senderKey  = json.getString("senderPubKey")
            val sigBase64  = json.getString("signature")
            val ts         = json.getLong("timestamp")
            val replyToId  = json.optString("replyToId").ifBlank { null }
            val replyPrev  = json.optString("replyToPreview").ifBlank { null }

            val group = groupDao.getGroupById(groupId) ?: run {
                Log.w(TAG, "handleGroupMessage: unknown group $groupId"); return
            }

            // Verify Ed25519 signature
            val sender = groupDao.getMembersForGroup(groupId).find { it.publicKey == senderKey }
                ?: contactDao.getContactByPublicKey(senderKey)
                    ?.let { GroupMember(groupId, it.publicKey, it.displayName,
                        it.onionAddress ?: "", it.mailboxOnion ?: "") }

            if (sender != null && sender.publicKey.isNotBlank()) {
                val signerPubKey = contactDao.getContactByPublicKey(senderKey)?.signingPublicKey
                if (signerPubKey != null) {
                    val valid = verifyGroupSig(signerPubKey, ctBase64, groupId, ts, sigBase64)
                    if (!valid) {
                        Log.w(TAG, "handleGroupMessage: invalid signature from $senderKey in $groupId")
                        return   // Drop forged messages
                    }
                }
            }

            // Decrypt AES-256-GCM
            val keyBytes = Base64.decode(group.groupKey, Base64.NO_WRAP)
            val iv  = Base64.decode(ivBase64, Base64.NO_WRAP)
            val ct  = Base64.decode(ctBase64, Base64.NO_WRAP)
            val plaintext = FialkaNative.decryptAes(iv, ct, keyBytes).toString(Charsets.UTF_8)
            keyBytes.fill(0)

            // Deduplicate by conversationId + timestamp + senderKey
            val existing = messageDao.getMessageByRemoteId(groupId, remoteId)
            if (existing != null) return

            val senderName = sender?.displayName ?: senderKey.take(8)
            val msg = MessageLocal(
                localId         = remoteId,
                conversationId  = groupId,
                senderPublicKey = senderKey,
                plaintext       = plaintext,
                timestamp       = ts,
                isMine          = false,
                deliveryStatus  = MessageLocal.DELIVERY_SENT,
                replyToId       = replyToId,
                replyToPreview  = replyPrev
            )
            messageDao.insertMessage(msg)

            // Update group preview + unread
            groupDao.updateLastMessage(groupId, "$senderName: ${plaintext.take(50)}", ts)
            groupDao.incrementUnread(groupId)
        } catch (e: Exception) {
            Log.e(TAG, "handleGroupMessage failed: ${e.message}", e)
        }
    }

    // =========================================================================
    // RECEIVE ADMIN ACTIONS (TYPE_GROUP_ADMIN frame)
    // =========================================================================

    /**
     * Process [TorTransport.TYPE_GROUP_ADMIN] frames.
     * Actions: KICK, PROMOTE, DEMOTE, RENAME, LEAVE.
     */
    suspend fun handleGroupAdmin(payload: ByteArray) {
        try {
            val json       = JSONObject(payload.toString(Charsets.UTF_8))
            val groupId    = json.getString("groupId")
            val action     = json.getString("action")
            val actorKey   = json.getString("senderPubKey")
            val targetKey  = json.optString("targetPubKey", "")
            val newName    = json.optString("newName", "")
            val ts         = json.getLong("timestamp")
            val sigBase64  = json.getString("signature")

            // Verify actor has sufficient privilege
            val actor = groupDao.getMember(groupId, actorKey)
            if (actor == null || actor.role == GroupLocal.ROLE_MEMBER) {
                Log.w(TAG, "handleGroupAdmin: $actorKey lacks privilege for $action"); return
            }

            // Verify signature on (action || groupId || targetKey || ts)
            val dataStr = "$action|$groupId|$targetKey|$ts"
            val actorContact = contactDao.getContactByPublicKey(actorKey)
            val actorSignKey = actorContact?.signingPublicKey
            if (actorSignKey != null) {
                val valid = crypto.verifySignature(
                    actorSignKey, dataStr, groupId, ts, sigBase64
                )
                if (!valid) {
                    Log.w(TAG, "handleGroupAdmin: invalid admin sig from $actorKey"); return
                }
            }

            val me2 = userDao.getUser()
            when (action) {
                "KICK" -> {
                    val kickedName = groupDao.getMember(groupId, targetKey)?.displayName ?: targetKey.take(8)
                    groupDao.removeMember(groupId, targetKey)
                    val count = groupDao.getMemberCount(groupId)
                    groupDao.updateMemberCount(groupId, count)
                    if (targetKey == me2?.publicKey) {
                        // I was kicked — remove the whole group from my device
                        groupDao.removeAllMembers(groupId)
                        groupDao.deleteGroup(groupId)
                    } else {
                        insertSystemMessage(groupId, appContext.getString(com.fialkaapp.fialka.R.string.sys_group_member_removed, kickedName))
                    }
                }
                "LEAVE" -> {
                    groupDao.removeMember(groupId, actorKey)
                    val count = groupDao.getMemberCount(groupId)
                    groupDao.updateMemberCount(groupId, count)
                    val name = actor.displayName
                    insertSystemMessage(groupId, appContext.getString(com.fialkaapp.fialka.R.string.sys_group_member_left, name))
                }
                "PROMOTE" -> {
                    groupDao.updateRole(groupId, targetKey, GroupLocal.ROLE_ADMIN)
                    // If I was promoted, reflect the change in my groups table row
                    val me2 = userDao.getUser()
                    if (targetKey == me2?.publicKey) groupDao.updateMyRole(groupId, GroupLocal.ROLE_ADMIN)
                    insertSystemMessage(groupId, "Nouveau administrateur")
                }
                "DEMOTE" -> {
                    groupDao.updateRole(groupId, targetKey, GroupLocal.ROLE_MEMBER)
                    // If I was demoted, reflect the change in my groups table row
                    val me2 = userDao.getUser()
                    if (targetKey == me2?.publicKey) groupDao.updateMyRole(groupId, GroupLocal.ROLE_MEMBER)
                }
                "RENAME" -> {
                    if (newName.isNotBlank()) {
                        groupDao.updateGroupName(groupId, newName)
                        insertSystemMessage(groupId, appContext.getString(com.fialkaapp.fialka.R.string.sys_group_renamed, newName))
                    }
                }
                "SETTINGS" -> {
                    val policy = newName
                    if (policy == GroupLocal.INVITE_ADMIN_ONLY || policy == GroupLocal.INVITE_ALL) {
                        groupDao.updateInvitePolicy(groupId, policy)
                        val label = if (policy == GroupLocal.INVITE_ALL)
                            "Les membres peuvent inviter" else "Seuls les admins peuvent inviter"
                        insertSystemMessage(groupId, label)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleGroupAdmin failed: ${e.message}", e)
        }
    }

    // =========================================================================
    // ADMIN ACTIONS (local user initiating)
    // =========================================================================

    /** Kick a member. Only OWNER or ADMIN can call this. */
    suspend fun kickMember(groupId: String, targetPubKey: String) {
        val me = userDao.getUser() ?: return
        val myMember = groupDao.getMember(groupId, me.publicKey) ?: return
        if (myMember.role == GroupLocal.ROLE_MEMBER) return

        val targetName = groupDao.getMember(groupId, targetPubKey)?.displayName ?: targetPubKey.take(8)

        // Broadcast BEFORE removing so the target still appears in the member list
        // when broadcastAdminAction fetches recipients
        broadcastAdminAction(groupId, "KICK", targetPubKey)

        groupDao.removeMember(groupId, targetPubKey)
        val count = groupDao.getMemberCount(groupId)
        groupDao.updateMemberCount(groupId, count)

        insertSystemMessage(groupId, appContext.getString(com.fialkaapp.fialka.R.string.sys_group_you_removed, targetName))
    }

    /** Promote a member to ADMIN. Only OWNER can call this. */
    suspend fun promoteMember(groupId: String, targetPubKey: String) {
        val me = userDao.getUser() ?: return
        val myMember = groupDao.getMember(groupId, me.publicKey) ?: return
        if (myMember.role != GroupLocal.ROLE_OWNER) return

        groupDao.updateRole(groupId, targetPubKey, GroupLocal.ROLE_ADMIN)
        broadcastAdminAction(groupId, "PROMOTE", targetPubKey)
    }

    /** Demote an ADMIN back to MEMBER. Only OWNER can call this. */
    suspend fun demoteMember(groupId: String, targetPubKey: String) {
        val me = userDao.getUser() ?: return
        val myMember = groupDao.getMember(groupId, me.publicKey) ?: return
        if (myMember.role != GroupLocal.ROLE_OWNER) return

        groupDao.updateRole(groupId, targetPubKey, GroupLocal.ROLE_MEMBER)
        broadcastAdminAction(groupId, "DEMOTE", targetPubKey)
    }

    /** Update the invite policy for the group. OWNER or ADMIN only. */
    suspend fun updateInvitePolicy(groupId: String, policy: String) {
        val me = userDao.getUser() ?: return
        val myMember = groupDao.getMember(groupId, me.publicKey) ?: return
        if (myMember.role == GroupLocal.ROLE_MEMBER) return

        groupDao.updateInvitePolicy(groupId, policy)
        val label = if (policy == GroupLocal.INVITE_ALL)
            "Les membres peuvent inviter" else "Seuls les admins peuvent inviter"
        insertSystemMessage(groupId, label)
        broadcastAdminAction(groupId, "SETTINGS", me.publicKey, newName = policy)
    }

    /**
     * Invite an existing contact to a group the caller is already a member of.
     * Checks invite policy: if INVITE_ADMIN_ONLY, only OWNER/ADMIN can call this.
     */
    suspend fun inviteMember(groupId: String, contact: Contact) {
        val me = userDao.getUser() ?: return
        val myMember = groupDao.getMember(groupId, me.publicKey) ?: return
        val group = groupDao.getGroupById(groupId) ?: return

        // Enforce invite policy
        val canInvite = myMember.role != GroupLocal.ROLE_MEMBER
            || group.invitePolicy == GroupLocal.INVITE_ALL
        if (!canInvite) return

        // Build full member list (including self) for the invitation payload
        val allMembers = groupDao.getMembersForGroup(groupId)
        sendInvitation(group, allMembers, contact)
    }

    /** Leave the group. Broadcasts LEAVE to all members, then removes local data. */
    suspend fun leaveGroup(groupId: String) {
        val me = userDao.getUser() ?: return
        insertSystemMessage(groupId, appContext.getString(com.fialkaapp.fialka.R.string.sys_group_you_left))
        broadcastAdminAction(groupId, "LEAVE", me.publicKey)
        groupDao.removeMember(groupId, me.publicKey)
        groupDao.removeAllMembers(groupId)
        groupDao.deleteGroup(groupId)
    }

    /** Rename the group. OWNER or ADMIN only. */
    suspend fun renameGroup(groupId: String, newName: String) {
        val me = userDao.getUser() ?: return
        val myMember = groupDao.getMember(groupId, me.publicKey) ?: return
        if (myMember.role == GroupLocal.ROLE_MEMBER) return

        groupDao.updateGroupName(groupId, newName)
        insertSystemMessage(groupId, appContext.getString(com.fialkaapp.fialka.R.string.sys_group_renamed, newName))
        broadcastAdminAction(groupId, "RENAME", me.publicKey, newName = newName)
    }

    fun clearUnread(groupId: String) {
        scope.launch { groupDao.clearUnread(groupId) }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private suspend fun broadcastAdminAction(
        groupId: String,
        action: String,
        targetPubKey: String,
        newName: String = ""
    ) {
        val me = userDao.getUser() ?: return
        val ts = System.currentTimeMillis()

        // Sign the action
        val dataStr = "$action|$groupId|$targetPubKey|$ts"
        val sigBase64 = try {
            crypto.signMessage(dataStr, groupId, ts)
        } catch (e: Exception) { ""; }

        val wireJson = JSONObject().apply {
            put("groupId",      groupId)
            put("action",       action)
            put("targetPubKey", targetPubKey)
            put("senderPubKey", me.publicKey)
            put("newName",      newName)
            put("timestamp",    ts)
            put("signature",    sigBase64)
        }
        val frame = TorTransport.Frame(
            TorTransport.TYPE_GROUP_ADMIN,
            wireJson.toString().toByteArray(Charsets.UTF_8)
        )
        val myKey = me.publicKey
        val members = groupDao.getMembersForGroup(groupId).filter { it.publicKey != myKey }
        for (member in members) {
            if (member.onionAddress.isBlank()) continue
            val mailbox = member.mailboxOnion.ifBlank { null }
            try { OutboxManager.sendOrQueue(member.onionAddress, frame, mailbox) }
            catch (e: Exception) { Log.w(TAG, "broadcastAdmin to ${member.displayName}: ${e.message}") }
        }
    }

    private fun signGroupData(ctBase64: String, groupId: String, ts: Long): String {
        return try {
            crypto.signMessage(ctBase64, groupId, ts)
        } catch (e: Exception) {
            Log.e(TAG, "signGroupData failed: ${e.message}")
            ""
        }
    }

    private fun verifyGroupSig(
        signerPubKeyBase64: String,
        ctBase64: String,
        groupId: String,
        ts: Long,
        sigBase64: String
    ): Boolean {
        return crypto.verifySignature(signerPubKeyBase64, ctBase64, groupId, ts, sigBase64)
    }

    private suspend fun insertSystemMessage(groupId: String, text: String) {
        val msg = MessageLocal(
            localId        = UUID.randomUUID().toString(),
            conversationId = groupId,
            senderPublicKey = "",
            plaintext      = text,
            isMine         = false,
            isInfoMessage  = true,
            deliveryStatus = MessageLocal.DELIVERY_SENT
        )
        messageDao.insertMessage(msg)
    }
}
