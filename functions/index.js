const { onValueCreated } = require("firebase-functions/v2/database");
const { initializeApp } = require("firebase-admin/app");
const { getDatabase } = require("firebase-admin/database");
const { getMessaging } = require("firebase-admin/messaging");
const crypto = require("crypto");

initializeApp();

/**
 * Compute HMAC-SHA256(conversationId, uid) truncated to 16 bytes hex.
 * Must match CryptoManager.hashSenderUid() on the Android client.
 */
function hashSenderUid(conversationId, uid) {
  const hmac = crypto.createHmac("sha256", conversationId);
  hmac.update(uid);
  return hmac.digest("hex").substring(0, 32);
}

/**
 * Cloud Function triggered when a new message is written to Firebase RTDB.
 *
 * Sends a push notification to all participants EXCEPT the sender.
 * The notification contains ZERO message content (E2EE preserved).
 * Only includes: conversationId + sender display name.
 *
 * Requirements:
 *   - Message must include "senderUid" field (HMAC-hashed per conversation)
 *   - Recipient must have "fcm_token" stored at /users/{uid}/fcm_token
 *   - Sender's display name stored at /users/{uid}/displayName
 */
exports.onNewMessage = onValueCreated(
  {
    ref: "/conversations/{conversationId}/messages/{messageId}",
    instance: "chat-3129d-default-rtdb",
    region: "europe-west1",
  },
  async (event) => {
    const conversationId = event.params.conversationId;
    const message = event.data.val();

    if (!message || !message.senderUid) {
      return null;
    }

    const senderHashedUid = message.senderUid;

    // Input validation: senderUid must be exactly 32 hex characters
    if (typeof senderHashedUid !== "string" || !/^[0-9a-f]{32}$/.test(senderHashedUid)) {
      console.warn("Invalid senderUid format, dropping notification");
      return null;
    }

    // Validate conversationId is a reasonable string (hex SHA-256 = 64 chars)
    if (typeof conversationId !== "string" || conversationId.length > 128 || !/^[0-9a-f]+$/.test(conversationId)) {
      console.warn("Invalid conversationId format, dropping notification");
      return null;
    }

    const db = getDatabase();

    // Get all participants of this conversation
    const participantsSnap = await db
      .ref(`conversations/${conversationId}/participants`)
      .once("value");
    const participants = participantsSnap.val();

    if (!participants) {
      return null;
    }

    // Identify the real sender by matching HMAC hash against participants
    let realSenderUid = null;
    for (const uid of Object.keys(participants)) {
      if (hashSenderUid(conversationId, uid) === senderHashedUid) {
        realSenderUid = uid;
        break;
      }
    }

    // Get sender display name (if sender identified)
    let senderName = "Un contact";
    if (realSenderUid) {
      const senderSnap = await db
        .ref(`users/${realSenderUid}/displayName`)
        .once("value");
      senderName = senderSnap.val() || "Un contact";
    }

    const sendPromises = [];

    for (const uid of Object.keys(participants)) {
      // Skip the sender
      if (uid === realSenderUid) {
        continue;
      }

      // Check if recipient has an FCM token (push enabled)
      const tokenSnap = await db
        .ref(`users/${uid}/fcm_token`)
        .once("value");
      const token = tokenSnap.val();

      if (!token) {
        continue; // Push not enabled for this user
      }

      // Send data-only message (no "notification" key — handled client-side)
      // Note: conversationId is a SHA-256 hash (not PII) but we minimize metadata exposure
      const pushPayload = {
        token: token,
        data: {
          type: "new_message",
          sync: "1",
        },
        android: {
          priority: "high",
        },
      };

      sendPromises.push(
        getMessaging()
          .send(pushPayload)
          .catch((err) => {
            // If token is invalid/expired, clean it up
            if (
              err.code === "messaging/registration-token-not-registered" ||
              err.code === "messaging/invalid-registration-token"
            ) {
              return db
                .ref(`users/${uid}/fcm_token`)
                .remove();
            }
            console.error(`FCM send error for ${uid}:`, err.message);
            return null;
          })
      );
    }

    return Promise.all(sendPromises);
  });
