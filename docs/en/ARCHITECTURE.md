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
│    Room DB     │     Crypto     │   Transport     │
│   (SQLCipher)  │  CryptoMgr +   │  Tor Hidden     │
│                │  Ratchet +     │  Services P2P   │
│                │  PQXDH(ML-KEM) │  + Mailbox      │
│                │  + ML-DSA-44   │  + UnifiedPush  │
└────────────────┴────────────────┴────────────────┘
```

---

## Core Principles

| Principle | Detail |
|-----------|--------|
| **Single Activity** | Navigation Component with 15 fragments |
| **Repository Pattern** | `ChatRepository` coordinates Room, Crypto, and Transport |
| **No transport access from UI** | Everything goes through Repository → TorTransport |
| **Mutex per conversation** | Ratchet operations are serialized (thread-safe) |
| **Zero central server** | All communication is peer-to-peer via Tor Hidden Services |
| **1 Seed → Everything** | Ed25519 master seed derives identity, .onion, X25519, ML-KEM, fingerprint |
| **ML-DSA-44 handshake** | Post-quantum signature on session establishment |
| **Fialka Mailbox** | Offline delivery via 4 modes (Direct, Personal, Private Node, Public Node) |
| **Deferred PQXDH** | Post-quantum root_key upgrade on first message (no bootstrap) |
| **Independent verification** | Each user verifies fingerprint on their own side (local Room state only) |
| **Invitation system** | QR scan → Tor contact request → accept → active P2P chat |

---

## Layers

| Layer | Role | Key Files |
|-------|------|-----------|
| **UI** | Screens, navigation, interactions | `ui/` — Fragments, ViewModels, Adapters (Material 3) |
| **Repository** | Local/crypto/remote coordination | `data/repository/ChatRepository.kt` |
| **Crypto** | X25519, ECDH, AES-GCM, Double Ratchet, BIP-39, Ed25519, PQXDH (ML-KEM-1024) | `crypto/CryptoManager.kt`, `DoubleRatchet.kt`, `MnemonicManager.kt` |
| **Local DB** | Room v17 — users, contacts, messages, ratchet (composite indexes) | `data/local/` — DAOs, Database (SQLCipher) |
| **Remote** | Tor Hidden Services P2P (.onion transport, ciphertext only) | `data/remote/TorTransport.kt` |
| **Util** | QR, 5 themes, app lock, ephemeral, dummy traffic, DeviceSecurityManager | `util/ThemeManager.kt`, `AppLockManager.kt`, `DummyTrafficManager.kt`, `DeviceSecurityManager.kt` |

---

## Push Notifications (opt-in)

```
Phone A → sendMessage() → Tor Hidden Service → Phone B
                                                  ↓ (if offline)
                                          Fialka Mailbox stores ciphertext
                                                  ↓
                                          UnifiedPush + ntfy.sh
                                                  ↓
                                          Phone B wakes up → connects to Mailbox
                                          → retrieves + decrypts messages
                                          "New message received"
                                          (ZERO content, ZERO metadata)
```

---

## Contact Request Flow

```
Alice                           Tor Network                           Bob
  │                                   │                                    │
  │  1. Scan QR / paste pub key       │                                    │
  │  2. createConversation(pending)   │                                    │
  │  3. sendContactRequest ──────────►│───► Bob's .onion service           │
  │     (via Tor Hidden Service)      │    (or Mailbox if offline)         │
  │                                   │                                    │
  │                                   │         4. "New request from       │
  │                                   │             Alice" appears         │
  │                                   │                                    │
  │                                   │◄── 5. acceptContactRequest() ──────│
  │                                   │    (ML-DSA-44 signed handshake)    │
  │                                   │                                    │
  │   PQXDH session established ◄─────│                                    │
  │   6. markConversationAccepted()   │                                    │
  │                                   │                                    │
  │◄══════════ E2E P2P Chat active ══►│◄═════════════════════════════════►│
```

---

## Account Lifecycle

```
Creation:
  Onboarding → generateSeed(32 bytes) → derive Ed25519 keypair
  → derive Account ID (SHA3-256 → Base58)
  → derive .onion address (Tor v3)
  → derive X25519 (birational map) + ML-KEM-1024 (HKDF)
  → BackupPhrase (BIP-39, 24 words)

Backup (BIP-39, 24 words):
  seed (32 bytes) → SHA-256 → 1st byte = checksum → 33 bytes → 24 × 11 bits → 24 words

Restore:
  24 words → mnemonicToSeed() → re-derive all keys (Ed25519, X25519, ML-KEM, .onion)
  → identity fully restored on new device

Account Deletion:
  A: wipes local data + Mailbox content + publishes key revocation
  B: detects revoked key → AlertDialog → re-invite if needed
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
      → send via Tor Hidden Service (same .onion route)
      → receiver detects prefix → silent drop (no Room insertion)

Toggle: SecurityFragment → SharedPreferences("fialka_settings") → "dummy_traffic_enabled"
```

---

## E2E File Sharing

```
Send (ChatViewModel.sendFile):
  file → generateFileKey() (AES-256-GCM, random key)
  → encryptFile(fileKey, plainBytes) → cipherBytes
  → send cipherBytes P2P via Tor Hidden Service
  → text message = "FILE|" + fileId + "|" + Base64(fileKey) + "|" + fileName
  → encrypt with Double Ratchet → send via Tor

Receive (ChatRepository):
  → decrypt message → detect "FILE|" prefix
  → parse: fileId, fileKey (Base64), fileName
  → receive cipherBytes via Tor P2P
  → decryptFile(fileKey, cipherBytes) → plainBytes
  → save to app internal storage
  → display clickable link in chat
```

---

## One-Shot Photo (view once)

```
Send:
  photo file → AES-256-GCM encryption → send via Tor P2P
  → message = "FILE|fileId|key|iv|fileName|fileSize|1" (one-shot flag = "1")
  → encrypt with Double Ratchet → send via Tor Hidden Service

Receive:
  → decrypt message → detect one-shot flag (7th field = "1")
  → receive + decrypt file → local storage
  → display bubble with 🔥 icon "Open (1 time)"

Open (2 phases):
  Phase 1 (immediate): flagOneShotOpened() → UPDATE oneShotOpened=1 in Room
                        → bubble switches to "Locked" state immediately
  Phase 2 (5s delay) : coroutine delay(5000)
                        → delete physical file (File.delete())
                        → markOneShotOpened() → UPDATE localFilePath=NULL

Anti-bypass:
  • DB flag is set BEFORE opening the viewer Intent
  • Even if user leaves the conversation, the flag is already in DB
  • On return, bubble shows "Ephemeral already viewed / Expired"
```

---

<div align="center">

[← Back to README](../../README-en.md)

</div>