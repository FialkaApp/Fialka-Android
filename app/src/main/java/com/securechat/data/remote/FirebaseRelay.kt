package com.securechat.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.securechat.data.model.FirebaseMessage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
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

    // TODO: Replace with your Firebase Realtime Database URL from Firebase Console
    // Go to: Firebase Console > Realtime Database > copy the URL at the top
    private const val DATABASE_URL = "https://chat-3129d-default-rtdb.europe-west1.firebasedatabase.app"

    private val database: FirebaseDatabase by lazy {
        FirebaseDatabase.getInstance(DATABASE_URL)
    }

    private val auth: FirebaseAuth by lazy {
        FirebaseAuth.getInstance()
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
    ): String = suspendCancellableCoroutine { cont ->
        val ref = database.reference
            .child("conversations")
            .child(conversationId)
            .child("messages")
            .push()

        val messageId = ref.key ?: ""

        // Use ServerValue.TIMESTAMP for consistent ordering
        val messageMap = message.toMap().toMutableMap()
        messageMap["createdAt"] = ServerValue.TIMESTAMP

        ref.setValue(messageMap)
            .addOnSuccessListener {
                cont.resume(messageId)
            }
            .addOnFailureListener { exception ->
                cont.resumeWithException(exception)
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
                    trySend(message)
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

        database.reference
            .child("users")
            .child(uid)
            .child("publicKey")
            .setValue(publicKey)
            .addOnSuccessListener { cont.resume(Unit) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    /**
     * Store the user's display name on Firebase so Cloud Functions can include it
     * in push notifications. Path: /users/{firebaseUid}/displayName
     */
    suspend fun storeDisplayName(displayName: String) {
        val uid = auth.currentUser?.uid ?: return
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("users")
                .child(uid)
                .child("displayName")
                .setValue(displayName)
                .addOnSuccessListener { cont.resume(Unit) }
                .addOnFailureListener { cont.resume(Unit) }
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
        conversationId: String
    ) {
        val recipientHash = hashPublicKey(recipientPublicKey)

        suspendCancellableCoroutine { cont ->
            val ref = database.reference
                .child("inbox")
                .child(recipientHash)
                .child(conversationId) // Use conversationId as key to avoid duplicates

            val requestMap = mapOf(
                "senderPublicKey" to senderPublicKey,
                "senderDisplayName" to senderDisplayName,
                "conversationId" to conversationId,
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
                val senderPublicKey = snapshot.child("senderPublicKey").getValue(String::class.java) ?: return
                val senderDisplayName = snapshot.child("senderDisplayName").getValue(String::class.java) ?: "Inconnu"
                val conversationId = snapshot.child("conversationId").getValue(String::class.java) ?: return
                val createdAt = snapshot.child("createdAt").getValue(Long::class.java) ?: 0L

                trySend(ContactRequest(senderPublicKey, senderDisplayName, conversationId, createdAt))
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
     * Listen for acceptance notifications for pending conversations.
     * Returns a Flow that emits conversationIds when they are accepted.
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
                                messages.add(message)
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
     * Find and remove any existing /users/{uid} node that has the given publicKey.
     * Used during restore to clean up the orphaned old profile.
     */
    suspend fun removeOldUserByPublicKey(publicKey: String) {
        suspendCancellableCoroutine { cont ->
            database.reference
                .child("users")
                .orderByChild("publicKey")
                .equalTo(publicKey)
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
    fun signOut() {
        auth.signOut()
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
        val createdAt: Long
    )
}
