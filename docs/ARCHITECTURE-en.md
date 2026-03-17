<div align="right">
  <a href="ARCHITECTURE.md">🇫🇷 Français</a> | 🇬🇧 English
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
│         5 Themes · Animations · Navigation        │
├──────────────────────────────────────────────────┤
│               Repository Layer                    │
│      ChatRepository — single source of truth      │
├────────────────┬────────────────┬────────────────┤
│    Room DB     │     Crypto     │    Firebase     │
│   (SQLCipher)  │  CryptoMgr +   │   Relay +       │
│                │   Ratchet      │    FCM          │
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
| **Invitation system** | Invite → inbox notification → accept → active chat |

---

## Layers

| Layer | Role | Key Files |
|-------|------|-----------|
| **UI** | Screens, navigation, interactions | `ui/` — Fragments, ViewModels, Adapters |
| **Repository** | Local/crypto/remote coordination | `data/repository/ChatRepository.kt` |
| **Crypto** | X25519, ECDH, AES-GCM, Double Ratchet, BIP-39 | `crypto/CryptoManager.kt`, `DoubleRatchet.kt`, `MnemonicManager.kt` |
| **Local DB** | Room v10 — users, contacts, messages, ratchet | `data/local/` — DAOs, Database (SQLCipher) |
| **Remote** | Firebase Relay (ciphertext only) | `data/remote/FirebaseRelay.kt` |
| **Util** | QR, 5 themes, app lock, ephemeral | `util/ThemeManager.kt`, `AppLockManager.kt`, `EphemeralManager.kt` |

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
  A: deleteUserProfile(/users/{uid}) + deleteInbox(/inbox/{hash}) + deleteConversation(all)
  B: sends message → Permission Denied → isConversationAliveOnFirebase()=false → AlertDialog
  B: re-invites A → dead convo detected → deleteStaleConversation() → new invitation

Invitation Receipt (stale local convo):
  Inbox listener → local convo exists? → isConversationAliveOnFirebase()
  → if dead: deleteStaleConversation() → show as new request
```

---

<div align="center">

[← Back to README](../README-en.md)

</div>