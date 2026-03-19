<div align="right">
  <a href="../fr/ARCHITECTURE.md">🇫🇷 Français</a> | 🇬🇧 English
</div>

<div align="center">

# 🏗 Architecture

<img src="https://img.shields.io/badge/Pattern-Single_Activity-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Layer-Repository-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/DB-Room_+_SQLCipher-6A1B9A?style=for-the-badge" />

</div>

---

## Overview

```
┌──────────────────────────────────────────────────┐
│                    UI Layer                       │
│         Fragments · ViewModels · Adapters         │
│      5 Themes Material 3 · Animations · Navigation │
├──────────────────────────────────────────────────┤
│               Repository Layer                    │
│      ChatRepository — single source of truth      │
├────────────────┬────────────────┬────────────────┤
│    Room DB     │     Crypto     │    Firebase     │
│   (SQLCipher)  │  CryptoMgr +   │   Relay +       │
│                │  Ratchet +     │    FCM          │
│                │  PQXDH(ML-KEM) │                │
└────────────────┴────────────────┴────────────────┘
                Cloud Function (push triggers)
```

---

## Core Principles

| Principle | Detail |
|-----------|--------|
| **Single Activity** | Navigation Component with 15 fragments |
| **Repository Pattern** | `ChatRepository` coordinates Room, Crypto, and Firebase |
| **No Firebase access from UI** | Everything goes through Repository → FirebaseRelay |
| **Mutex per conversation** | Ratchet operations are serialized (thread-safe) |
| **Atomic sending** | Ratchet advances only if Firebase confirms the send |
| **Delete-after-delivery** | Ciphertext removed from Firebase after decryption |
| **Delete-after-failure** | Failed decryption messages cleaned from Firebase |
| **Double-listener guard** | `ConcurrentHashMap.putIfAbsent()` + LRU eviction prevents duplicate processing |
| **lastDeliveredAt** | Per-conversation lower bound to prevent Firebase message re-processing |
| **Deferred PQXDH** | Post-quantum root_key upgrade on first message (no bootstrap) |
| **Independent verification** | Each user verifies fingerprint on their own side (local Room state only) |
| **Invitation system** | Invite → inbox notification → accept → active chat |

---

## Layers

| Layer | Role | Key Files |
|-------|------|-----------|
| **UI** | Screens, navigation, interactions | `ui/` — Fragments, ViewModels, Adapters (Material 3) |
| **Repository** | Local/crypto/remote coordination | `data/repository/ChatRepository.kt` |
| **Crypto** | X25519, ECDH, AES-GCM, Double Ratchet, BIP-39, Ed25519, PQXDH (ML-KEM-768) | `crypto/CryptoManager.kt`, `DoubleRatchet.kt`, `MnemonicManager.kt` |
| **Local DB** | Room v16 — users, contacts, messages, ratchet (composite indexes) | `data/local/` — DAOs, Database (SQLCipher) |
| **Remote** | Firebase Relay RTDB + Storage (ciphertext only) | `data/remote/FirebaseRelay.kt` |
| **Util** | QR, 5 themes, app lock, ephemeral, dummy traffic, DeviceSecurityManager | `util/ThemeManager.kt`, `AppLockManager.kt`, `DummyTrafficManager.kt`, `DeviceSecurityManager.kt` |

---

## Push Notifications (opt-in)

```
Phone A → sendMessage() → Firebase RTDB
                              ↓
                   Cloud Function (onCreate)
                              ↓
                   /users/{uid}/fcm_token ?
                              ↓ (if token exists)
                   FCM → Phone B notification
                   "New message from Alice"
                   (ZERO message content)
```

---

## Contact Request Flow

```
Alice                              Firebase                              Bob
  │                                   │                                    │
  │  1. Scan QR / paste pub key       │                                    │
  │  2. createConversation(pending)   │                                    │
  │  3. sendContactRequest ──────────►│ inbox/{bob_hash}/{convId}          │
  │                                   │                                    │
  │                                   │   listenForContactRequests() ◄─────│
  │                                   │                                    │
  │                                   │         4. "New request from       │
  │                                   │             Alice" appears         │
  │                                   │                                    │
  │                                   │◄── 5. acceptContactRequest() ──────│
  │                                   │    notifyRequestAccepted()         │
  │                                   │                                    │
  │   listenForAcceptances() ◄────────│ accepted/{convId}                  │
  │   6. markConversationAccepted()   │                                    │
  │                                   │                                    │
  │◄══════════ E2E Chat active ══════►│◄══════════════════════════════════►│
```

---

## Account Lifecycle

```
Creation:
  Onboarding → generateIdentityKeyPair() → registerPublicKey() → BackupPhrase (24 words)

Backup (BIP-39, 24 words):
  privateKey (32 bytes) → SHA-256 → 1st byte = checksum → 33 bytes → 24 × 11 bits → 24 words

Restore:
  24 words → mnemonicToPrivateKey() → restoreIdentityKey() → DH(privKey, basePoint u=9) → pubKey
  → removeOldUserByPublicKey() → registerPublicKey() → ready

Account Deletion (A deletes):
  A: deleteUserProfile(/users/{uid}) + deleteInbox(/inbox/{hash}) + deleteSigningKey(/signing_keys/{hash}) + deleteConversation(all)
  B: sends message → Permission Denied → isConversationAliveOnFirebase()=false → AlertDialog
  B: re-invites A → dead convo detected → deleteStaleConversation() → new invitation

Invitation Receipt (stale local convo):
  Inbox listener → local convo exists? → isConversationAliveOnFirebase()
  → if dead: deleteStaleConversation() → show as new request
```

---

## Dummy Traffic (traffic analysis countermeasure)

```
DummyTrafficManager.start(context):
  → isEnabled(context)? → No: return
  → Yes: loop CoroutineScope(Dispatchers.IO)
    → random delay 30–90 s
    → for each active conversation (Room)
      → generateDummyMessage(): opaque prefix + random bytes
      → encrypt with Double Ratchet (same pipeline)
      → send to Firebase RTDB (/messages/{convId})
      → receiver detects prefix → silent drop (no Room insertion)

Toggle: SecurityFragment → SharedPreferences("securechat_settings") → "dummy_traffic_enabled"
```

---

## E2E File Sharing

```
Send (ChatViewModel.sendFile):
  file → generateFileKey() (AES-256-GCM, random key)
  → encryptFile(fileKey, plainBytes) → cipherBytes
  → uploadToFirebaseStorage(/chat_files/{convId}/{uuid}) → downloadUrl
  → text message = "FILE|" + downloadUrl + "|" + Base64(fileKey) + "|" + fileName
  → encrypt with Double Ratchet → send to Firebase RTDB

Receive (ChatRepository):
  → decrypt message → detect "FILE|" prefix
  → parse: url, fileKey (Base64), fileName
  → downloadFromFirebaseStorage(url) → cipherBytes
  → decryptFile(fileKey, cipherBytes) → plainBytes
  → save to app internal storage
  → display clickable link in chat
```

---

<div align="center">

[← Back to README](../../README-en.md)

</div>