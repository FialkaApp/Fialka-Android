<div align="right">
  <a href="../fr/CRYPTO.md">🇫🇷 Français</a> | 🇬🇧 English
</div>

<div align="center">

# 🔐 Cryptographic Protocol

<img src="https://img.shields.io/badge/Key_Exchange-X25519_ECDH-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Post--Quantum-PQXDH_(ML--KEM--768)-4A148C?style=for-the-badge" />
<img src="https://img.shields.io/badge/Encryption-AES--256--GCM-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/PFS-Double_Ratchet-6A1B9A?style=for-the-badge" />

</div>

---

## Overview

```
Alice                                         Bob
  │                                             │
  │◄──────── Public Key Exchange ─────────────►│
  │           (QR code v2: X25519 + ML-KEM)      │
  │                                             │
  │  shared_secret = X25519(sk_A, pk_B)         │  shared_secret = X25519(sk_B, pk_A)
  │  root_key = HKDF(shared_secret)            │  root_key = HKDF(shared_secret)
  │  send_chain = HKDF(root, "init-send")      │  recv_chain = HKDF(root, "init-send")
  │  recv_chain = HKDF(root, "init-recv")      │  send_chain = HKDF(root, "init-recv")
  │                                             │
  │  ┌─ PQXDH (first message) ──────────────┐  │
  │  │ kem_ct = ML-KEM-768.Encaps(pk_kem_B)   │  │
  │  │ kem_ss = ML-KEM shared secret           │  │
  │  │ root_key' = HKDF(root_key || kem_ss)   │  │
  │  └──────────────────────────────────────┘  │
  │                                             │
  │  msg_key = HMAC(send_chain, 0x01)          │
  │  send_chain = HMAC(send_chain, 0x02)       │
  │  ct = AES-GCM(msg_key, iv, plaintext)      │
  │                                             │
  │  ──── {ct, iv, ephKey, kemCiphertext} ────►│
  │           (Firebase relay)                  │
  │                                             │
  │                                             │  kem_ss = ML-KEM-768.Decaps(sk_kem_B, kem_ct)
  │                                             │  root_key' = HKDF(root_key || kem_ss)
  │                                             │  msg_key = HMAC(recv_chain, 0x01)
  │                                             │  recv_chain = HMAC(recv_chain, 0x02)
  │                                             │  plaintext = AES-GCM-dec(msg_key, iv, ct)
```

---

## Identity

1. **X25519** key pair generated on first launch
2. Private key → **EncryptedSharedPreferences** (AES-256-GCM, Android Keystore-backed)
3. Public key → Base64 + QR code for sharing
4. Backup: private key → **24-word BIP-39** (256 bits + 8-bit SHA-256 checksum)
5. Restore: 24 words → private key → public key derivation (DH with X25519 base point u=9)

---

## Key Exchange

1. Alice shows her **QR code** (or shares public key)
2. Bob scans QR → Alice's nickname is auto-populated → creates contact
3. Both sides compute: `shared_secret = X25519(my_private_key, contact_public_key)`
4. The role (initiator/responder) is determined by the **lexicographic order** of the public keys
5. QR v2 also encodes the **ML-KEM-768** public key for PQXDH upgrade

> **QR v2 format:** `securechat://contact?key=<X25519_base64>&kem=<ML-KEM-768_base64>&name=<displayName>`

---

## Fingerprint Emojis (96-bit, anti-MITM)

Each conversation has a **shared fingerprint** computed from both public keys:

```
sorted_keys = sort_lexicographic(pubKeyA, pubKeyB)
hash = SHA-256(sorted_keys[0] + sorted_keys[1])
fingerprint = 16 emojis chosen from a 64-palette
            = 16 × log2(64) = 96 bits of entropy
```

**Format:** `🔥🐱🦄🍕 🌟🚀💎⚡ 🎸📱🔔🎉 🌈🐶🎯🍀` (4 × 4 emojis)

Both phones calculate the **same** fingerprint. Users compare it visually (in person or via video call) to detect a MITM attack.

- ✅ 64 emoji palette (power of 2 → zero modulo bias)
- ✅ 96 bits of entropy (7.9 × 10²⁸ combinations)
- ✅ Chat badge: ✅ Verified / ⚠️ Unverified
- ✅ **Independent** verification per user (local Room state only)
- ✅ System messages in chat on verify/un-verify (with clickable "View fingerprint" link)
- ✅ Event-based Firebase notification (`fingerprintEvent: "verified:<timestamp>"`) — notifies peer, does not sync state

### QR Code Fingerprint (V3.4.1)

In addition to visual emoji comparison, users can verify the fingerprint via **QR code**:

```
sorted_keys = sort_lexicographic(pubKeyA, pubKeyB)
hash = SHA-256(sorted_keys[0] + sorted_keys[1])
qr_data = hex(hash)   // 64 ASCII characters (a-f0-9)
```

- ✅ QR encodes the **SHA-256 as hexadecimal** (not emojis) to avoid Unicode encoding issues
- ✅ `getSharedFingerprintHex()` method in CryptoManager
- ✅ Scanner uses `CustomScannerActivity` (same as contact invitation)
- ✅ Hex comparison with `ignoreCase = true` (case-insensitive)
- ✅ Automatic verification: scan → match → ✅ dialog; mismatch → ❌ MITM warning dialog

---

## Double Ratchet (PFS + Healing)

```
Initialization (on contact acceptance):
  root_key     = HKDF(shared_secret, "SecureChat-DR-root")
  send_chain   = HKDF(root_key, "SecureChat-DR-chain-init-send")
  recv_chain   = HKDF(root_key, "SecureChat-DR-chain-init-recv")  (swapped for responder)
  ephemeral    = X25519.generateKeyPair()

For each message N (KDF chain):
  message_key[N]  = HMAC-SHA256(chain_key[N], 0x01)   ← unique key
  chain_key[N+1]  = HMAC-SHA256(chain_key[N], 0x02)   ← irreversible advancement

DH Ratchet (healing) — when remote ephemeral changes:
  dh_secret    = X25519(local_ephemeral_priv, remote_ephemeral_pub)
  new_root_key = HKDF(root_key || dh_secret, "root-ratchet")
  new_chain    = HKDF(root_key || dh_secret, "chain-ratchet")
  → New local ephemeral key generated

  plaintext → pad(plaintext) → AES-256-GCM(message_key[N], random_iv_12B) → ciphertext
```

### Padding (size analysis countermeasure)

Before encryption, each message is padded to the next bucket:

| Message size | Bucket |
|--------------|--------|
| ≤ 256 B      | 256 B  |
| ≤ 1 KB       | 1 KB   |
| ≤ 4 KB       | 4 KB   |
| > 4 KB       | 16 KB  |

- Header: 2 bytes (Big-Endian) = actual plaintext length
- Fill: `SecureRandom` bytes up to bucket size
- Unpadding on receive via 2-byte header

### Properties

- ✅ Each message has its own encryption key (KDF chain)
- ✅ Chain advancement is **irreversible** (one-way function)
- ✅ **Healing**: chain key compromise → DH ratchet heals it on next exchange
- ✅ Compromising the current key **does not reveal** past keys
- ✅ Intermediate keys are **zeroed** from memory after use
- ✅ HKDF IKM, PRK, and expandInput **zeroed** after each derivation
- ✅ Mnemonic encode/decode **zeros** all intermediate byte arrays and clears StringBuilder
- ✅ X25519 ephemeral keys renewed at each direction change

---

## Message Signing (Ed25519, V3.2)

Every message is signed with a dedicated **Ed25519** key pair (separate from the X25519 identity key) via BouncyCastle 1.80.

```
Send:
  signingKeyPair = getOrDeriveSigningKeyPair()   (EncryptedSharedPreferences)
  dataToSign = ciphertext.UTF8 || conversationId.UTF8 || createdAt.bigEndian8bytes
  signature  = Ed25519.sign(signingKeyPair.private, dataToSign)
  → sent in Firebase message: { ..., "signature": Base64(signature) }

Receive:
  signingPublicKey = fetchSigningPublicKeyByIdentity(contact.publicKey)
  dataToVerify = ciphertext.UTF8 || conversationId.UTF8 || createdAt.bigEndian8bytes
  valid = Ed25519.verify(signingPublicKey, dataToVerify, signature)
  → Badge: ✅ (valid=true) or ⚠️ (valid=false or key missing)
```

### Properties

- ✅ **Anti-forgery**: only the holder of the Ed25519 private key can sign
- ✅ **Anti-replay across conversations**: `conversationId` included in signed data
- ✅ **Anti-timestamp manipulation**: `createdAt` (client timestamp) included in signed data
- ✅ **Separate signing key** from X25519 identity key (no key use mixing)
- ✅ **Cleanup**: signing key removed from Firebase (`/signing_keys/{hash}`) on account deletion

---

## PQXDH — Post-Quantum Upgrade (V3.4)

SecureChat implements a **hybrid** key exchange combining X25519 (classic) and ML-KEM-768 (post-quantum) via the PQXDH protocol.

### Principle

```
On contact add (QR scan):
  Both X25519 AND ML-KEM-768 public keys are exchanged via QR code v2.
  Conversation starts in classic X25519-only mode (classic root_key).

First message (initiator):
  kem_ct, kem_ss = ML-KEM-768.Encaps(contact_kem_publicKey)
  root_key' = HKDF(root_key || kem_ss, "pqxdh-upgrade")
  → Firebase message includes { ..., "kemCiphertext": Base64(kem_ct) }
  → root_key upgraded locally (chains recalculated)

First message reception (responder):
  kem_ss = ML-KEM-768.Decaps(my_kem_privateKey, kemCiphertext)
  root_key' = HKDF(root_key || kem_ss, "pqxdh-upgrade")
  → root_key upgraded locally (chains recalculated)

Subsequent messages:
  kemCiphertext no longer sent (one-time upgrade)
  Double Ratchet continues with root_key' (hybrid)
```

### Properties

- ✅ **Post-quantum resistance**: even if X25519 is broken by a quantum computer, ML-KEM-768 protects the root_key
- ✅ **Deferred upgrade**: no bootstrap message — upgrade happens on the first real message
- ✅ **No regression**: if ML-KEM fails, the conversation remains protected by classic X25519
- ✅ **BouncyCastle 1.80**: certified ML-KEM-768 implementation (`org.bouncycastle.pqc.crypto.mlkem` package)
- ✅ **StrongBox probe**: `DeviceSecurityManager` detects hardware StrongBox support for key protection

---

## What transverses Firebase

### Messages (encrypted)

```json
{
  "ciphertext": "a3F4bWx...",
  "iv": "dG9rZW4...",
  "createdAt": 1700000000000,
  "senderUid": "HMAC-SHA256(uid, conversationId)",
  "signature": "Ed25519(ciphertext || conversationId || createdAt)"
}
```

- **V3.0**: `senderUid` = `HMAC-SHA256(firebaseUid, conversationId)` → raw UID no longer visible
- **V3.0**: Messages are **deleted from Firebase** upon receipt (`deleteMessageFromFirebase()`)
- **V3.0**: Padded message (see Padding section) is encrypted → uniform size on the wire
- **V3.2**: `signature` = Ed25519 over `ciphertext_UTF8 || conversationId_UTF8 || createdAt_bigEndian8bytes` → anti-forgery + anti-replay
- **V3.2**: `createdAt` = client `System.currentTimeMillis()` (not `ServerValue.TIMESTAMP`) for signature consistency

### Ephemeral Sync Settings

```
/conversations/{id}/settings/ephemeralDuration = 3600000
```

### Fingerprint Events (V3.4)

```
/conversations/{id}/settings/fingerprintEvent = "verified:<timestamp>"
```

- Event-based notification only — **does not sync** the verification state
- Each user manages their `fingerprintVerified` state locally in Room

### Removed from wire format (V1.1 metadata hardening)

- `senderPublicKey` — useless in 1-to-1 (recipient already knows contact's key)
- `messageIndex` — encrypted in AES-GCM payload (trial decryption on receiver side)

**Never sent:** plaintext, private keys, chain keys, ratchet position.

### File Encryption (V3.0)

```
Send:
  file → random AES-256-GCM key (fileKey)
  → encrypt file (encryptFile) → upload Firebase Storage (/chat_files/{convId}/{uuid})
  → message = "FILE|" + downloadUrl + "|" + Base64(fileKey) + "|" + fileName
  → encrypt message with Double Ratchet → send to RTDB

Receive:
  → decrypt message → detect "FILE|" prefix
  → download encrypted file from Storage
  → decrypt with fileKey → save to internal storage
```

### Contact request (Firebase inbox)

```json
{
  "senderPublicKey": "MFkwEwYHKoZ...",
  "senderDisplayName": "Alice",
  "conversationId": "conv_abc123",
  "createdAt": 1700000000000
}
```

---

## Threat Model

| Threat | Protected? | Detail |
|--------|------------|--------|
| Firebase reads messages | ✅ | E2E encrypted, Firebase only sees ciphertext |
| Message key compromise | ✅ | PFS — each message has its own key |
| Old messages replay | ✅ | sinceTimestamp + lastDeliveredAt + messageIndex (embedded in ciphertext) |
| Ratchet race conditions | ✅ | Mutex per conversation + ConcurrentHashMap.putIfAbsent() + LRU eviction |
| MITM Attack | ✅ | 96-bit fingerprint emojis (independent visual check) |
| Phone stolen unlocked | ✅ | Keystore, SQLCipher, App Lock PIN + biometrics, auto-lock |
| Sensitive messages left | ✅ | Disappearing messages (timer on send / read) |
| Message forgery | ✅ | Per-message Ed25519 signature (V3.2) — badge ✅/⚠️ |
| Metadata (who/when) | ✅ | senderUid → HMAC-SHA256, uniform padding, dummy traffic, delete-after-delivery |
| Traffic analysis | ✅ | Dummy traffic (30–90 s, same pipeline), bucket padding, delete on receipt |
| Intercepted files | ✅ | Per-file AES-256-GCM encryption, key transmitted inside E2E channel |
| Phone lost | ✅ | 24-word mnemonic (BIP-39) to restore identity |
| App Lock brute-force | ✅ | PBKDF2 600,000 iterations + biometric lock |
| Contact deletes account | ✅ | Auto-detect dead convo + cleanup + re-invite |
| Quantum computer (future) | ✅ | Hybrid PQXDH ML-KEM-768 — root_key upgraded with post-quantum secret (V3.4) |
| Ratchet desynchronization | ✅ | syncExistingMessages on acceptance, delete-after-failure, lastDeliveredAt lower-bound |
| Screenshot / screen recording | ✅ | FLAG_SECURE on all sensitive windows + dialogs (V3.4.1 audit) |
| Tapjacking / overlay attack | ✅ | filterTouchesWhenObscured on sensitive activities (V3.4.1 audit) |
| Deep link injection | ✅ | Parameter whitelist, length limits, Base64 validation, control char rejection (V3.4.1 audit) |
| Clipboard leakage | ✅ | EXTRA_IS_SENSITIVE + 30s auto-clear (V3.4.1 audit) |
| File forensic recovery | ✅ | SecureFileManager 2-pass overwrite (random + zeros) before delete (V3.4.1 audit) |
| FCM metadata leakage | ✅ | Opaque push payload — zero conversationId/senderName (V3.4.1 audit) |
| Firebase key overwrite | ✅ | Write-once rules on signing_keys, mlkem_keys, inbox (V3.4.1 audit) |
| Cleartext HTTP traffic | ✅ | usesCleartextTraffic=false enforced (V3.4.1 audit) |

> See [`SECURITY.md`](../../SECURITY.md) for full security analysis.

---

<div align="center">

[← Back to README](../../README-en.md)

</div>