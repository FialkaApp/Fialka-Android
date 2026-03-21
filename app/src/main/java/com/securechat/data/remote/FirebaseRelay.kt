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
package com.securechat.data.remote

import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.securechat.data.model.FirebaseMessage
import com.securechat.crypto.CryptoManager
import com.securechat.tor.TorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * FirebaseRelay — handles all communication with Firebase Realtime Database.
 *
 * Firebase is used ONLY as a relay:
 *  - Messages are encrypted before being sent here.
 *  - Firebase stores only ciphertext + metadata.
 *  - No private keys or plaintext ever touch Firebase.
 *
 * Database structure:
 *   /conversations/{conversationId}/messages/{messageId}
 *     - ciphertext: String (Base64, contains embedded messageIndex)
 *     - iv: String (Base64)
 *     - createdAt: Long (server timestamp)
 *     - senderUid: String (Firebase anonymous UID, for push routing)
 */
object FirebaseRelay {

    private const val TAG = "FirebaseRelay"

    // TODO: Replace with your Firebase Realtime Database URL from Firebase Console
    // Go to: Firebase Console > Realtime Database > copy the URL at the top
    private const val DATABASE_URL = "https://chat-3129d-default-rtdb.europe-west1.firebasedatabase.app"

    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(DATABASE_URL)
    }

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance().also {
            it.setLanguageCode(java.util.Locale.getDefault().language.ifEmpty { "en" })
        }
    }

    // ========================================================================
    // AUTHENTICATION
    // ========================================================================

    /**
     * Sign in anonymously to Firebase.
     * This provides a Firebase UID for security rules without requiring
     * any personal information (no email, no phone number).
     */
    suspend fun signInAnonymously(): String = suspendCancellableCoroutine { cont ->
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                cont.resume(result.user?.uid ?: "")
            }
            .addOnFailureListener { exception ->
                cont.resumeWithException(exception)
            }
    }

    /**
     * Check if user is currently authenticated.
     */
    fun isAuthenticated(): Boolean = auth.currentUser != null

    /**
     * Get current Firebase UID.
     */
    fun getCurrentUid(): String? = auth.currentUser?.uid

    // ========================================================================
    // PARTICIPANT REGISTRATION
    // ========================================================================

    /**
     * Register the current user as a participant of a conversation.
     * Required by Firebase security rules to allow read/write on messages.
     *
     * Each user writes ONLY their own UID entry:
     *   /conversations/{conversationId}/participants/{uid} = true
     */
    suspend fun registerParticipant(conversationId: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            // Auth not ready — try to authenticate first
            signInAnonymously()
        }
        val finalUid = auth.currentUser?.uid ?: return
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("conversations")
                .child(conversationId)
                .child("participants")
                .child(finalUid)
                .setValue(true)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) } // Best effort
        }
    }

    // ========================================================================
    // SEND MESSAGE
    // ========================================================================

    /**
     * Send an encrypted message to Firebase.
     *
     * @param conversationId The conversation/thread ID.
     * @param message The FirebaseMessage (must contain ciphertext, NOT plaintext).
     * @return The generated message ID.
     */
    suspend fun sendMessage(
        conversationId: String,
        message: FirebaseMessage
    ): String {
        // Validate message fields before sending
        require(conversationId.isNotBlank()) { "conversationId must not be blank" }
        require(message.ciphertext.isNotBlank()) { "ciphertext must not be blank" }
        require(message.iv.isNotBlank()) { "iv must not be blank" }
        require(message.senderUid.isNotBlank() && message.senderUid.length == 32) { "senderUid must be 32-char hex" }
        require(message.createdAt > 0) { "createdAt must be positive" }

        // Wait for Tor connection before sending
        TorManager.awaitConnection()

        return suspendCancellableCoroutine { cont ->
        val ref = database.reference
            .child("conversations")
            .child(conversationId)
            .child("messages")
            .push()

        val messageId = ref.key ?: ""

        // Use the client-supplied timestamp (already in message.createdAt)
        // so it matches what was signed by Ed25519.
        val messageMap = message.toMap()

        ref.setValue(messageMap)
            .addOnSuccessListener {
                cont.resume(messageId)
            }
            .addOnFailureListener { exception ->
                cont.resumeWithException(exception)
            }
        }
    }

    // ========================================================================
    // RECEIVE MESSAGES (REAL-TIME LISTENER)
    // ========================================================================

    /**
     * Listen for new messages in a conversation in real-time.
     * Returns a Flow of FirebaseMessage that emits whenever a new message arrives.
     *
     * @param conversationId The conversation/thread ID.
     * @param sinceTimestamp Only return messages after this timestamp (for incremental sync).
     */
    fun listenForMessages(
        conversationId: String,
        sinceTimestamp: Long = 0
    ): Flow<FirebaseMessage> = callbackFlow {
        val ref = database.reference
            .child("conversations")
            .child(conversationId)
            .child("messages")
            .orderByChild("createdAt")

        val query = if (sinceTimestamp > 0) {
            ref.startAfter(sinceTimestamp.toDouble())
        } else {
            ref
        }

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val message = snapshot.getValue(FirebaseMessage::class.java)
                if (message != null) {
                    // Attach Firebase node key so receiver can delete after decryption
                    trySend(message.copy(firebaseKey = snapshot.key ?: ""))
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        query.addChildEventListener(listener)

        awaitClose {
            query.removeEventListener(listener)
        }
    }

    // ========================================================================
    // USER REGISTRATION ON FIREBASE
    // ========================================================================

    /**
     * Register the user's public key on Firebase so others can discover
     * which conversations they belong to.
     *
     * Path: /users/{firebaseUid}/publicKey
     */
    suspend fun registerPublicKey(publicKey: String) = suspendCancellableCoroutine { cont ->
        val uid = auth.currentUser?.uid
        if (uid == null) {
            cont.resumeWithException(IllegalStateException("Not authenticated"))
            return@suspendCancellableCoroutine
        }

        // Strip the fixed 12-byte X.509 header so all stored keys look unique from byte 1
        val storedKey = stripX25519Header(publicKey)

        database.reference
            .child("users")
            .child(uid)
            .child("publicKey")
            .setValue(storedKey)
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /**
     * No-op — display name is no longer stored in cleartext on Firebase.
     * The real name is transmitted only inside the ECIES-encrypted inbox payload.
     * Kept for API compatibility with existing call sites.
     */
    suspend fun storeDisplayName(@Suppress("UNUSED_PARAMETER") displayName: String) {
        // Intentionally empty — do not store PII in cleartext
    }

    /**
     * Store the Ed25519 signing public key on Firebase.
     * Path: /users/{firebaseUid}/signingPublicKey
     */
    suspend fun storeSigningPublicKey(signingPublicKeyBase64: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.e(TAG, "storeSigningPublicKey: uid is null, cannot store")
            return
        }
        Log.d(TAG, "storeSigningPublicKey: writing to Firebase")
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("users")
                .child(uid)
                .child("signingPublicKey")
                .setValue(signingPublicKeyBase64)
                .addOnSuccessListener {
                    Log.d(TAG, "storeSigningPublicKey: SUCCESS")
                    cont.resume(Unit)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "storeSigningPublicKey: FAILED", e)
                    cont.resume(Unit)
                }
        }
    }

    /**
     * Fetch a user's Ed25519 signing public key from Firebase by their identity public key.
     * Uses the same pubKeyHash used for inbox routing.
     * Path: /signing_keys/{pubKeyHash}
     */
    suspend fun fetchSigningPublicKeyByIdentity(identityPublicKeyBase64: String): String? {
        val pubKeyHash = hashPublicKey(identityPublicKeyBase64)
        return suspendCancellableCoroutine { cont ->
            database.reference
                .child("signing_keys")
                .child(pubKeyHash)
                .get()
                .addOnSuccessListener { snapshot ->
                    cont.resume(snapshot.getValue(String::class.java))
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }

    /**
     * Store the Ed25519 signing public key indexed by identity public key hash.
     * Path: /signing_keys/{pubKeyHash}
     * This allows contacts to fetch the signing key using the identity public key.
     */
    suspend fun storeSigningPublicKeyByIdentity(identityPublicKeyBase64: String, signingPublicKeyBase64: String) {
        val pubKeyHash = hashPublicKey(identityPublicKeyBase64)
        Log.d(TAG, "storeSigningPublicKeyByIdentity: writing to Firebase")
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("signing_keys")
                .child(pubKeyHash)
                .setValue(signingPublicKeyBase64)
                .addOnSuccessListener {
                    Log.d(TAG, "storeSigningPublicKeyByIdentity: SUCCESS")
                    cont.resume(Unit)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "storeSigningPublicKeyByIdentity: FAILED", e)
                    cont.resume(Unit)
                }
        }
    }

    // ========================================================================
    // FCM PUSH TOKEN
    // ========================================================================

    /**
     * Store the FCM token on Firebase so the Cloud Function can send pushes.
     * Path: /users/{uid}/fcm_token
     */
    suspend fun storeFcmToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("users")
                .child(uid)
                .child("fcm_token")
                .setValue(token)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) }
        }
    }

    /**
     * Delete the FCM token from Firebase (user opted out of push notifications).
     */
    suspend fun deleteFcmToken() {
        val uid = auth.currentUser?.uid ?: return
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("users")
                .child(uid)
                .child("fcm_token")
                .removeValue()
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) }
        }
    }

    // ========================================================================
    // INBOX — CONTACT REQUESTS
    // ========================================================================

    /**
     * Send a contact request to a recipient's inbox on Firebase.
     * This notifies the recipient that someone wants to chat with them.
     *
     * Path: /inbox/{recipientPubKeyHash}/{requestId}
     *   - senderPublicKey: the sender's public key (so recipient can add them)
     *   - senderDisplayName: the sender's display name
     *   - conversationId: the derived conversation ID
     *   - createdAt: server timestamp
     */
    suspend fun sendContactRequest(
        recipientPublicKey: String,
        senderPublicKey: String,
        senderDisplayName: String,
        conversationId: String,
        senderSigningPublicKey: String? = null
    ) {
        val recipientHash = hashPublicKey(recipientPublicKey)

        // Encrypt all identifying fields — only the recipient can decrypt
        val json = org.json.JSONObject().apply {
            put("p", senderPublicKey)
            put("n", senderDisplayName)
            put("c", conversationId)
            if (senderSigningPublicKey != null) put("s", senderSigningPublicKey)
        }
        val encryptedPayload = CryptoManager.encryptInboxPayload(
            json.toString().toByteArray(Charsets.UTF_8),
            recipientPublicKey
        )

        suspendCancellableCoroutine { cont ->
            val ref = database.reference
                .child("inbox")
                .child(recipientHash)
                .child(conversationId) // opaque UUID key — no longer derivable

            val requestMap = mapOf(
                "e" to encryptedPayload,
                "createdAt" to ServerValue.TIMESTAMP
            )

            ref.setValue(requestMap)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }
    }

    /**
     * Listen for incoming contact requests in the user's inbox.
     * Returns a Flow that emits each new request as it arrives.
     *
     * @param myPublicKey The local user's public key (to derive inbox path).
     */
    fun listenForContactRequests(myPublicKey: String): Flow<ContactRequest> = callbackFlow {
        val myHash = hashPublicKey(myPublicKey)
        val ref = database.reference
            .child("inbox")
            .child(myHash)

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L
                val encryptedPayload = snapshot.child("e").getValue(String::class.java)

                if (encryptedPayload != null) {
                    // Encrypted format (v4+)
                    try {
                        val plaintext = CryptoManager.decryptInboxPayload(encryptedPayload)
                        val json = org.json.JSONObject(String(plaintext, Charsets.UTF_8))
                        val senderPublicKey = json.getString("p")
                        val senderDisplayName = json.optString("n", "Inconnu")
                        val conversationId = json.getString("c")
                        val senderSigningPublicKey = json.optString("s", "").takeIf { it.isNotEmpty() }
                        trySend(ContactRequest(senderPublicKey, senderDisplayName, conversationId, createdAt, senderSigningPublicKey))
                    } catch (_: Exception) {
                        // Decryption failed — ignore corrupt entry
                    }
                } else {
                    // Legacy plaintext format (backward compat)
                    val senderPublicKey = snapshot.child("senderPublicKey").getValue(String::class.java) ?: return
                    val senderDisplayName = snapshot.child("senderDisplayName").getValue(String::class.java) ?: "Inconnu"
                    val conversationId = snapshot.child("conversationId").getValue(String::class.java) ?: return
                    val senderSigningPublicKey = snapshot.child("senderSigningPublicKey").getValue(String::class.java)
                    trySend(ContactRequest(senderPublicKey, senderDisplayName, conversationId, createdAt, senderSigningPublicKey))
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {
                close(error.toException())
            }
        }

        ref.addChildEventListener(listener)

        awaitClose {
            ref.removeEventListener(listener)
        }
    }

    /**
     * Remove a contact request from the inbox after it's been accepted.
     */
    suspend fun removeContactRequest(myPublicKey: String, conversationId: String) {
        val myHash = hashPublicKey(myPublicKey)

        suspendCancellableCoroutine { cont ->
            database.reference
                .child("inbox")
                .child(myHash)
                .child(conversationId)
                .removeValue()
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) } // Best effort
        }
    }

    /**
     * Notify the sender that their contact request has been accepted.
     * Writes to /accepted/{conversationId} so the sender can detect it.
     */
    suspend fun notifyRequestAccepted(conversationId: String, accepterPublicKey: String) {
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("accepted")
                .child(conversationId)
                .setValue(mapOf(
                    "acceptedBy" to accepterPublicKey,
                    "createdAt" to ServerValue.TIMESTAMP
                ))
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) } // Best effort
        }
    }

    /**
     * Listen for acceptance notification for ONE specific conversation.
     * Uses a ValueEventListener on /accepted/{conversationId} directly — this works
     * with the per-conversation Firebase read rule (participant check) and avoids the
     * PERMISSION_DENIED that a parent-level ChildEventListener would get.
     */
    fun listenForAcceptance(conversationId: String): Flow<String> = callbackFlow {
        val ref = database.reference.child("accepted").child(conversationId)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) trySend(conversationId)
            }
            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "listenForAcceptance($conversationId) cancelled: ${error.message}")
                close(error.toException())
            }
        }

        ref.addValueEventListener(listener)
        awaitClose { ref.removeEventListener(listener) }
    }

    /**
     * @deprecated Use listenForAcceptance(conversationId) instead.
     * This method listens on the /accepted parent which requires a root-level read rule.
     */
    fun listenForAcceptances(): Flow<String> = callbackFlow {
        val ref = database.reference.child("accepted")

        val listener = object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                val conversationId = snapshot.key ?: return
                trySend(conversationId)
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}
        }

        ref.addChildEventListener(listener)

        awaitClose {
            ref.removeEventListener(listener)
        }
    }

    /**
     * Remove an acceptance notification after it's been processed.
     */
    suspend fun removeAcceptanceNotification(conversationId: String) {
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("accepted")
                .child(conversationId)
                .removeValue()
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) }
        }
    }

    // ========================================================================
    // FETCH EXISTING MESSAGES (ONE-SHOT)
    // ========================================================================

    /**
     * Fetch all existing messages for a conversation in a single read.
     * Used when accepting a contact request to retrieve messages sent before acceptance.
     *
     * @param conversationId The conversation to fetch messages from.
     * @return List of FirebaseMessage sorted by createdAt.
     */
    suspend fun fetchExistingMessages(conversationId: String): List<FirebaseMessage> =
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("conversations")
                .child(conversationId)
                .child("messages")
                .orderByChild("createdAt")
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val messages = mutableListOf<FirebaseMessage>()
                        for (child in snapshot.children) {
                            val message = child.getValue(FirebaseMessage::class.java)
                            if (message != null) {
                                messages.add(message.copy(firebaseKey = child.key ?: ""))
                            }
                        }
                        cont.resume(messages)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(emptyList())
                    }
                })
        }

    // ========================================================================
    // DELETE-AFTER-DELIVERY
    // ========================================================================

    /**
     * Delete a single message from Firebase after successful decryption.
     * Minimizes ciphertext retention on Google servers.
     */
    fun deleteMessage(conversationId: String, messageKey: String) {
        if (messageKey.isEmpty()) return
        database.reference
            .child("conversations")
            .child(conversationId)
            .child("messages")
            .child(messageKey)
            .removeValue()
    }

    // ========================================================================
    // ENCRYPTED FILE STORAGE
    // ========================================================================

    private val storage = FirebaseStorage.getInstance()

    /**
     * Upload pre-encrypted file bytes to Firebase Storage.
     * Path: /encrypted_files/{conversationId}/{uuid}
     * Returns the download URL (safe — file is AES-256-GCM encrypted client-side).
     */
    suspend fun uploadEncryptedFile(
        conversationId: String,
        encryptedBytes: ByteArray,
        fileExtension: String
    ): String {
        val fileId = java.util.UUID.randomUUID().toString()
        val ref = storage.reference
            .child("encrypted_files")
            .child(conversationId)
            .child("$fileId.$fileExtension.enc")

        val metadata = StorageMetadata.Builder()
            .setCustomMetadata("uploaderUid", auth.currentUser!!.uid)
            .build()

        ref.putBytes(encryptedBytes, metadata).await()
        return ref.downloadUrl.await().toString()
    }

    /**
     * Download encrypted file bytes from Firebase Storage URL.
     * Max 50 MB to prevent abuse.
     */
    suspend fun downloadEncryptedFile(downloadUrl: String): ByteArray {
        val ref = storage.getReferenceFromUrl(downloadUrl)
        val maxSize = 50L * 1024 * 1024  // 50 MB
        return ref.getBytes(maxSize).await()
    }

    /**
     * Delete an encrypted file from Firebase Storage after download.
     */
    fun deleteEncryptedFile(downloadUrl: String) {
        try {
            storage.getReferenceFromUrl(downloadUrl).delete()
        } catch (_: Exception) { }
    }

    // ========================================================================
    // CLEANUP
    // ========================================================================

    /**
     * Delete messages older than the given threshold from Firebase.
     * This prevents unlimited accumulation of ciphertext on the server.
     * Messages are already saved locally, so removing old ones from Firebase is safe.
     *
     * @param conversationId The conversation to clean up.
     * @param maxAgeMs Maximum age in milliseconds (default: 7 days).
     */
    suspend fun deleteOldMessages(
        conversationId: String,
        maxAgeMs: Long = 7 * 24 * 60 * 60 * 1000L
    ) {
        val cutoff = System.currentTimeMillis() - maxAgeMs

        suspendCancellableCoroutine { cont ->
            database.reference
                .child("conversations")
                .child(conversationId)
                .child("messages")
                .orderByChild("createdAt")
                .endBefore(cutoff.toDouble())
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (child in snapshot.children) {
                            child.ref.removeValue()
                        }
                        cont.resume(Unit)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(Unit) // Don't crash on cleanup failure
                    }
                })
        }
    }

    // ========================================================================
    // SIGN OUT / ACCOUNT CLEANUP
    // ========================================================================

    /**
     * Delete the current user's profile from Firebase (/users/{uid}).
     */
    suspend fun deleteUserProfile() {
        val uid = auth.currentUser?.uid ?: return
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("users")
                .child(uid)
                .removeValue()
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) }
        }
    }

    /**
     * Delete a conversation from Firebase (/conversations/{id}).
     */
    suspend fun deleteConversation(conversationId: String) {
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("conversations")
                .child(conversationId)
                .removeValue()
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) }
        }
    }

    /**
     * Delete inbox entries for a given public key hash.
     */
    suspend fun deleteInbox(publicKey: String) {
        val hash = hashPublicKey(publicKey)
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("inbox")
                .child(hash)
                .removeValue()
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) }
        }
    }

    /**
     * Delete the signing key entry for a given identity public key.
     * Path: /signing_keys/{pubKeyHash}
     */
    suspend fun deleteSigningKey(identityPublicKey: String) {
        val hash = hashPublicKey(identityPublicKey)
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("signing_keys")
                .child(hash)
                .removeValue()
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) }
        }
    }

    /**
     * Find and remove any existing /users/{uid} node that has the given publicKey.
     * Used during restore to clean up the orphaned old profile.
     */
    suspend fun removeOldUserByPublicKey(publicKey: String) {
        val storedKey = stripX25519Header(publicKey)
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("users")
                .orderByChild("publicKey")
                .equalTo(storedKey)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (child in snapshot.children) {
                            child.ref.removeValue()
                        }
                        cont.resume(Unit)
                    }
                    override fun onCancelled(error: DatabaseError) {
                        cont.resume(Unit)
                    }
                })
        }
    }

    /**
     * Check if a conversation node exists on Firebase.
     * PERMISSION_DENIED means the conversation was deleted (participant check fails) → false.
     */
    suspend fun conversationExists(conversationId: String): Boolean =
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("conversations")
                .child(conversationId)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        cont.resume(snapshot.exists())
                    }
                    override fun onCancelled(error: DatabaseError) {
                        // Permission denied = conversation deleted or not a participant = dead
                        cont.resume(false)
                    }
                })
        }

    /**
     * Sign out from Firebase Authentication.
     */
    // ======================================================================
    // PRIVATE HELPERS
    // ======================================================================

    /** Strip the fixed 12-byte X.509 DER header from an X25519 public key.
     *  Returns the 32 raw bytes as Base64. Falls back to the original if the key
     *  is already stripped or doesn't match the expected size. */
    private fun stripX25519Header(fullBase64: String): String {
        return try {
            val full = Base64.decode(fullBase64, Base64.NO_WRAP)
            if (full.size == 44) Base64.encodeToString(full.copyOfRange(12, 44), Base64.NO_WRAP)
            else fullBase64  // already stripped or unexpected format
        } catch (e: Exception) {
            fullBase64
        }
    }

    fun signOut() {
        database.goOffline()
        auth.signOut()
        // Don't call goOnline() — avoids permission errors from orphaned listeners
        // reconnecting without auth. The process restarts after sign-out anyway.
    }

    // ========================================================================
    // EPHEMERAL SETTINGS (SYNCED BETWEEN BOTH USERS)
    // ========================================================================

    /**
     * Write ephemeral duration setting to Firebase so both participants see it.
     * Path: /conversations/{conversationId}/settings/ephemeralDuration
     */
    suspend fun setEphemeralDuration(conversationId: String, durationMs: Long) {
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("conversations")
                .child(conversationId)
                .child("settings")
                .child("ephemeralDuration")
                .setValue(durationMs)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) }
        }
    }

    /**
     * Listen for changes to the ephemeral duration setting on Firebase.
     * Emits the new duration whenever either participant changes it.
     */
    fun listenForEphemeralDuration(conversationId: String): Flow<Long> = callbackFlow {
        val ref = database.reference
            .child("conversations")
            .child(conversationId)
            .child("settings")
            .child("ephemeralDuration")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val duration = snapshot.getValue(Long::class.java) ?: 0L
                trySend(duration)
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        ref.addValueEventListener(listener)

        awaitClose {
            ref.removeEventListener(listener)
        }
    }

    // ========================================================================
    // FINGERPRINT VERIFICATION EVENT
    // ========================================================================

    /**
     * Push a fingerprint verification event to Firebase so the other participant
     * is notified. Each event is a unique string: "verified:<timestamp>" or
     * "unverified:<timestamp>". Verification state itself is local-only —
     * this is purely a notification mechanism.
     * Path: /conversations/{id}/settings/fingerprintEvent
     */
    suspend fun pushFingerprintEvent(conversationId: String, event: String) {
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("conversations")
                .child(conversationId)
                .child("settings")
                .child("fingerprintEvent")
                .setValue(event)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) }
        }
    }

    /**
     * Listen for fingerprint verification events on Firebase.
     * Emits the event string whenever either participant verifies/unverifies.
     */
    fun listenForFingerprintEvent(conversationId: String): Flow<String> = callbackFlow {
        val ref = database.reference
            .child("conversations")
            .child(conversationId)
            .child("settings")
            .child("fingerprintEvent")

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val event = snapshot.getValue(String::class.java) ?: ""
                if (event.isNotEmpty()) trySend(event)
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        ref.addValueEventListener(listener)

        awaitClose {
            ref.removeEventListener(listener)
        }
    }

    // ========================================================================
    // ML-KEM PUBLIC KEY (PQXDH)
    // ========================================================================

    /**
     * Store the user's ML-KEM-768 public key on Firebase so contacts can fetch it
     * when initiating a PQXDH conversation.
     * Path: /users/{uid}/mlkemPublicKey
     */
    suspend fun registerMLKEMPublicKey(mlkemPublicKey: String) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.e(TAG, "registerMLKEMPublicKey: uid is null, cannot store")
            return
        }
        Log.d(TAG, "registerMLKEMPublicKey: writing to Firebase")
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("users")
                .child(uid)
                .child("mlkemPublicKey")
                .setValue(mlkemPublicKey)
                .addOnSuccessListener {
                    Log.d(TAG, "registerMLKEMPublicKey: SUCCESS")
                    cont.resume(Unit)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "registerMLKEMPublicKey: FAILED", e)
                    cont.resume(Unit) // Best effort
                }
        }
    }

    /**
     * Fetch a contact's ML-KEM-768 public key from Firebase using their identity public key.
     * Path: /mlkem_keys/{pubKeyHash} → mlkemPublicKey
     * Returns null if not found (contact has not yet upgraded to PQXDH).
     */
    suspend fun fetchMLKEMPublicKeyByIdentity(identityPublicKeyBase64: String): String? {
        val pubKeyHash = hashPublicKey(identityPublicKeyBase64)
        return suspendCancellableCoroutine { cont ->
            database.reference
                .child("mlkem_keys")
                .child(pubKeyHash)
                .get()
                .addOnSuccessListener { snapshot ->
                    cont.resume(snapshot.getValue(String::class.java))
                }
                .addOnFailureListener {
                    cont.resume(null)
                }
        }
    }

    /**
     * Store the ML-KEM public key indexed by identity public key hash.
     * Path: /mlkem_keys/{pubKeyHash}
     * Allows contacts to fetch the ML-KEM key using the identity public key (from QR / invite).
     */
    suspend fun storeMLKEMPublicKeyByIdentity(identityPublicKeyBase64: String, mlkemPublicKeyBase64: String) {
        val pubKeyHash = hashPublicKey(identityPublicKeyBase64)
        Log.d(TAG, "storeMLKEMPublicKeyByIdentity: writing to Firebase")
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("mlkem_keys")
                .child(pubKeyHash)
                .setValue(mlkemPublicKeyBase64)
                .addOnSuccessListener {
                    Log.d(TAG, "storeMLKEMPublicKeyByIdentity: SUCCESS")
                    cont.resume(Unit)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "storeMLKEMPublicKeyByIdentity: FAILED", e)
                    cont.resume(Unit)
                }
        }
    }

    /**
     * Delete the ML-KEM key entry for a given identity public key (used on account deletion).
     */
    suspend fun deleteMLKEMKey(identityPublicKey: String) {
        val hash = hashPublicKey(identityPublicKey)
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("mlkem_keys")
                .child(hash)
                .removeValue()
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) }
        }
    }

    // ========================================================================
    // UTILITIES
    // ========================================================================

    /**
     * Hash a public key to use as an inbox path.
     * Uses SHA-256 truncated to 32 hex chars for a shorter path.
     */
    private fun hashPublicKey(publicKey: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(publicKey.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }.take(32)
    }

    /**
     * Data class for an incoming contact request.
     */
    data class ContactRequest(
        val senderPublicKey: String,
        val senderDisplayName: String,
        val conversationId: String,
        val createdAt: Long,
        val senderSigningPublicKey: String? = null
    )
}
