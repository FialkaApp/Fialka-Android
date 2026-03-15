const { onValueCreated } = require("firebase-functions/v2/database");
const { initializeApp } = require("firebase-admin/app");
const { getDatabase } = require("firebase-admin/database");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

/**
 * Cloud Function triggered when a new message is written to Firebase RTDB.
 *
 * Sends a push notification to all participants EXCEPT the sender.
 * The notification contains ZERO message content (E2EE preserved).
 * Only includes: conversationId + sender display name.
 *
 * Requirements:
 *   - Message must include "senderUid" field (Firebase anonymous UID)
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

    const senderUid = message.senderUid;
    const db = getDatabase();

    // Get sender display name
    const senderSnap = await db
      .ref(`users/${senderUid}/displayName`)
      .once("value");
    const senderName = senderSnap.val() || "Un contact";

    // Get all participants of this conversation
    const participantsSnap = await db
      .ref(`conversations/${conversationId}/participants`)
      .once("value");
    const participants = participantsSnap.val();

    if (!participants) {
      return null;
    }

    const sendPromises = [];

    for (const uid of Object.keys(participants)) {
      // Skip the sender
      if (uid === senderUid) {
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
      const pushPayload = {
        token: token,
        data: {
          conversationId: conversationId,
          senderDisplayName: senderName,
          type: "new_message",
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
