<div align="right">
  <a href="../fr/CRYPTO.md">🇫🇷 Français</a> | 🇬🇧 English
</div>

<div align="center">

# 🔐 Cryptographic Protocol

<img src="https://img.shields.io/badge/Key_Exchange-X25519_ECDH-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Post--Quantum-PQXDH_(ML--KEM--1024)-4A148C?style=for-the-badge" />
<img src="https://img.shields.io/badge/Encryption-AES--256--GCM_|_ChaCha20--Poly1305-9C4DCC?style=for-the-badge" />
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
  │  │ kem_ct = ML-KEM-1024.Encaps(pk_kem_B)   │  │
  │  │ kem_ss = ML-KEM shared secret           │  │
  │  │ root_key' = HKDF(root_key || kem_ss)   │  │
  │  └──────────────────────────────────────┘  │
  │                                             │
  │  msg_key = HMAC(send_chain, 0x01)          │
  │  send_chain = HMAC(send_chain, 0x02)       │
  │  ct = AES-GCM(msg_key, iv, plaintext)      │
  │                                             │
  │  ──── {ct, iv, ephKey, kemCiphertext} ────►│
  │           (Tor P2P .onion)                  │
  │                                             │
  │                                             │  kem_ss = ML-KEM-1024.Decaps(sk_kem_B, kem_ct)
  │                                             │  root_key' = HKDF(root_key || kem_ss)
  │                                             │  msg_key = HMAC(recv_chain, 0x01)
  │                                             │  recv_chain = HMAC(recv_chain, 0x02)
  │                                             │  plaintext = AES-GCM-dec(msg_key, iv, ct)
```

---

## Identity

1. **Ed25519 seed** (32 bytes) generated on first launch — single master secret
2. Seed → **Android Keystore** (StrongBox when available)
3. From seed, derive:
   - **Ed25519** key pair (signing + master identity)
   - **Account ID** = SHA3-256(pubkey) → Base58 (e.g. `Fa3x...9Z`)
   - **.onion address** (Tor v3 encoding from Ed25519 pubkey)
   - **X25519** DH key (birational map Edwards → Montgomery)
   - **ML-KEM-1024** key pair (HKDF(seed, "fialka-ml-kem", 64 bytes) → deterministic KeyGen)
   - **Fingerprint** = SHA-256 → 16 emojis (96-bit)
4. Public key → Base64 + QR code for sharing
5. Backup: seed → **24-word BIP-39** (256 bits + 8-bit SHA-256 checksum)
6. Restore: 24 words → seed → re-derive all keys (Ed25519, X25519, ML-KEM, .onion)

---

## Key Exchange

1. Alice shows her **QR code** (or shares public key)
2. Bob scans QR → Alice's nickname is auto-populated → creates contact
3. Both sides compute: `shared_secret = X25519(my_private_key, contact_public_key)`
4. The role (initiator/responder) is determined by the **lexicographic order** of the public keys
5. QR v2 also encodes the **ML-KEM-1024** public key for PQXDH upgrade

> **QR v2 format:** `fialka://contact?key=<X25519_base64>&kem=<ML-KEM-1024_base64>&name=<displayName>`

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
- ✅ Event-based notification via Tor P2P — notifies peer, does not sync state

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
  root_key     = HKDF(shared_secret, "Fialka-DR-root")
  send_chain   = HKDF(root_key, "Fialka-DR-chain-init-send")
  recv_chain   = HKDF(root_key, "Fialka-DR-chain-init-recv")  (swapped for responder)
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
  → sent in message via Tor: { ..., "signature": Base64(signature) }

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
- ✅ **Cleanup**: signing key wiped locally on account deletion

---

## PQXDH — Post-Quantum Upgrade (V3.4)

Fialka implements a **hybrid** key exchange combining X25519 (classic) and ML-KEM-1024 (post-quantum) via the PQXDH protocol.

### Principle

```
On contact add (QR scan):
  Both X25519 AND ML-KEM-1024 public keys are exchanged via QR code v2.
  Conversation starts in classic X25519-only mode (classic root_key).

First message (initiator):
  kem_ct, kem_ss = ML-KEM-1024.Encaps(contact_kem_publicKey)
  root_key' = HKDF(root_key || kem_ss, "pqxdh-upgrade")
  → message via Tor includes { ..., "kemCiphertext": Base64(kem_ct) }
  → root_key upgraded locally (chains recalculated)

First message reception (responder):
  kem_ss = ML-KEM-1024.Decaps(my_kem_privateKey, kemCiphertext)
  root_key' = HKDF(root_key || kem_ss, "pqxdh-upgrade")
  → root_key upgraded locally (chains recalculated)

Subsequent messages:
  kemCiphertext no longer sent (one-time upgrade)
  Double Ratchet continues with root_key' (hybrid)
```

### Properties

- ✅ **Post-quantum resistance**: even if X25519 is broken by a quantum computer, ML-KEM-1024 protects the root_key
- ✅ **Deferred upgrade**: no bootstrap message — upgrade happens on the first real message
- ✅ **No regression**: if ML-KEM fails, the conversation remains protected by classic X25519
- ✅ **BouncyCastle 1.80**: certified ML-KEM-1024 implementation (`org.bouncycastle.pqc.crypto.mlkem` package)
- ✅ **StrongBox probe**: `DeviceSecurityManager` detects hardware StrongBox support for key protection

---

## SPQR — Periodic PQ Re-encapsulation (V3.5)

After the initial PQXDH upgrade, the classic Double Ratchet resumes with X25519-only exchanges. SPQR (Supplementary Post-Quantum Ratchet) adds **periodic ML-KEM-1024 re-encapsulation** to maintain post-quantum resistance over time.

### How it works

```
Every PQ_RATCHET_INTERVAL = 10 sent messages:

Sender (Alice):
  kem_ct, kem_ss = ML-KEM-1024.Encaps(contact_kem_publicKey)
  root_key' = HKDF(root_key, kem_ss, info="Fialka-SPQR-pq-ratchet")
  → message via Tor includes { ..., "kemCiphertext": Base64(kem_ct) }
  → pqRatchetCounter reset to 0

Receiver (Bob):
  If pqxdhInitialized AND kemCiphertext present AND not an initial PQXDH:
    kem_ss = ML-KEM-1024.Decaps(my_kem_privateKey, kemCiphertext)
    root_key' = HKDF(root_key, kem_ss, info="Fialka-SPQR-pq-ratchet")
    → pqRatchetCounter reset to 0
```

### Properties

- ✅ **Continuous PQ healing**: even if a PQ secret is compromised, it is renewed 10 messages later
- ✅ **Backward compatible**: reuses the existing `kemCiphertext` field (distinguished from initial PQXDH by `pqxdhInitialized`)
- ✅ **Zero network overhead**: ML-KEM ciphertext is sent only every 10 messages (not every message)
- ✅ **Persistent counter**: `pqRatchetCounter` in `RatchetState` (Room), survives restarts

---

## ChaCha20-Poly1305 — Alternative Cipher (V3.5)

Fialka automatically selects the optimal symmetric cipher based on hardware:

| Hardware | Cipher | Reason |
|----------|--------|--------|
| ARMv8 with Crypto Extension (API 33+) | AES-256-GCM | Hardware acceleration available |
| Without AES acceleration | ChaCha20-Poly1305 | Faster in pure software |

### Detection

```
hasHardwareAes():
  → Initialize AES-GCM with a test key
  → If init takes < 1ms → hardware AES present → AES-256-GCM
  → Otherwise → ChaCha20-Poly1305 (BouncyCastle)
```

### Wire format

The `cipherSuite` field in the message indicates which algorithm was used:
- `0` (or absent) = AES-256-GCM (default, backward compatible)
- `1` = ChaCha20-Poly1305

The receiver decrypts automatically with the correct algorithm.

### Properties

- ✅ **Transparent selection**: the user doesn't choose — hardware dictates
- ✅ **Backward compatible**: old messages (without `cipherSuite`) are decrypted with AES-GCM
- ✅ **Same security level**: AES-256-GCM and ChaCha20-Poly1305 both provide 256-bit AEAD security

---

## What traverses the network (Tor P2P)

### Messages (encrypted)

```json
{
  "ciphertext": "a3F4bWx...",
  "iv": "dG9rZW4...",
  "createdAt": 1700000000000,
  "senderHash": "HMAC-SHA256(accountId, conversationId)",
  "signature": "Ed25519(ciphertext || conversationId || createdAt)"
}
```

- `senderHash` = `HMAC-SHA256(accountId, conversationId)` → raw ID not visible
- Padded message (see Padding section) is encrypted → uniform size on the wire
- `signature` = Ed25519 over `ciphertext_UTF8 || conversationId_UTF8 || createdAt_bigEndian8bytes` → anti-forgery + anti-replay
- `createdAt` = client `System.currentTimeMillis()` for signature consistency
- All messages transit via **Tor Hidden Services** (.onion to .onion) — zero central relay

### Ephemeral Settings Sync

Ephemeral duration is synced directly between peers via the encrypted Tor channel.

### Fingerprint Events

- Event-based notification only — **does not sync** the verification state
- Each user manages their `fingerprintVerified` state locally in Room

### Removed from wire format (V1.1 metadata hardening)

- `senderPublicKey` — useless in 1-to-1 (recipient already knows contact's key)
- `messageIndex` — encrypted in AES-GCM payload (trial decryption on receiver side)

**Never sent:** plaintext, private keys, chain keys, ratchet position.

### File Encryption

```
Send:
  file → random AES-256-GCM key (fileKey)
  → encrypt file (encryptFile) → send cipherBytes P2P via Tor
  → message = "FILE|" + fileId + "|" + Base64(fileKey) + "|" + fileName
  → encrypt message with Double Ratchet → send via Tor

Receive:
  → decrypt message → detect "FILE|" prefix
  → receive encrypted file via Tor P2P
  → decrypt with fileKey → save to internal storage
```

### Contact request (via Tor / Mailbox)

```json
{
  "senderPublicKey": "Ed25519_base64...",
  "senderDisplayName": "Alice",
  "conversationId": "conv_abc123",
  "createdAt": 1700000000000
}
```

Contact requests are delivered directly to the recipient's .onion service, or stored in a Fialka Mailbox if the recipient is offline.

---

## Threat Model

| Threat | Protected? | Detail |
|--------|------------|--------|
| Server reads messages | ✅ | No central server — all P2P via Tor Hidden Services, E2E encrypted |
| Central server compromise | N/A | No central server exists |
| Message key compromise | ✅ | PFS — each message has its own key |
| Old messages replay | ✅ | messageIndex embedded in ciphertext, per-conversation state |
| Ratchet race conditions | ✅ | Mutex per conversation (thread-safe) |
| MITM Attack | ✅ | 96-bit fingerprint emojis (independent visual check) + ML-DSA-44 handshake |
| Phone stolen unlocked | ✅ | Keystore, SQLCipher, App Lock PIN + biometrics, auto-lock |
| Sensitive messages left | ✅ | Disappearing messages (timer on send / read) |
| Message forgery | ✅ | Per-message Ed25519 signature — badge ✅/⚠️ |
| Metadata (who/when) | ✅ | Tor Hidden Services (sealed sender), uniform padding, dummy traffic |
| Traffic analysis | ✅ | Dummy traffic (30–90 s, same pipeline), bucket padding, Tor cover |
| Intercepted files | ✅ | Per-file AES-256-GCM encryption, key transmitted inside E2E channel, P2P via Tor |
| Phone lost | ✅ | 24-word mnemonic (BIP-39) to restore entire identity |
| Contact deletes account | ✅ | Auto-detect dead convo + cleanup + re-invite |
| Quantum computer (future) | ✅ | Hybrid PQXDH ML-KEM-1024 + SPQR periodic re-encapsulation + ML-DSA-44 handshake |
| Ratchet desynchronization | ✅ | Sync on acceptance, per-conversation mutex |
| Perf without AES acceleration | ✅ | ChaCha20-Poly1305 auto-selected on devices without ARMv8 Crypto Extension |
| Screenshot / screen recording | ✅ | FLAG_SECURE on all sensitive windows + dialogs |
| Tapjacking / overlay attack | ✅ | filterTouchesWhenObscured on sensitive activities |
| Deep link injection | ✅ | Parameter whitelist, length limits, Base64 validation, control char rejection |
| Clipboard leakage | ✅ | EXTRA_IS_SENSITIVE + 30s auto-clear |
| File forensic recovery | ✅ | SecureFileManager 2-pass overwrite (random + zeros) before delete |
| IP address exposure | ✅ | All traffic via Tor — real IP never exposed to peers or network |
| Push notification metadata | ✅ | UnifiedPush + ntfy.sh — zero message content, self-hostable |

> See [`SECURITY.md`](../../SECURITY.md) for full security analysis.

---

<div align="center">

[← Back to README](../../README-en.md)

</div>