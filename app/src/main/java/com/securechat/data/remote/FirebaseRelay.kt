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
 *     - senderPublicKey: String
 *     - ciphertext: String (Base64)
 *     - iv: String (Base64)
 *     - messageIndex: Int (ratchet chain index for PFS)
 *     - createdAt: Long (server timestamp)
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
    // SIGN OUT
    // ========================================================================

    /**
     * Sign out from Firebase Authentication.
     */
    fun signOut() {
        auth.signOut()
    }
}
