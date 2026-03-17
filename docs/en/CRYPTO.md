<div align="right">
  <a href="../fr/CRYPTO.md">🇫🇷 Français</a> | 🇬🇧 English
</div>

<div align="center">

# 🔐 Cryptographic Protocol

<img src="https://img.shields.io/badge/Key_Exchange-X25519_ECDH-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Encryption-AES--256--GCM-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/PFS-Double_Ratchet-6A1B9A?style=for-the-badge" />

</div>

---

## Overview

```
Alice                                         Bob
  │                                             │
  │◄──────── Public Key Exchange ─────────────►│
  │           (QR code or copy/paste)           │
  │                                             │
  │  shared_secret = X25519(sk_A, pk_B)         │  shared_secret = X25519(sk_B, pk_A)
  │  root_key = HKDF(shared_secret)            │  root_key = HKDF(shared_secret)
  │  send_chain = HKDF(root, "init-send")      │  recv_chain = HKDF(root, "init-send")
  │  recv_chain = HKDF(root, "init-recv")      │  send_chain = HKDF(root, "init-recv")
  │                                             │
  │  msg_key = HMAC(send_chain, 0x01)          │
  │  send_chain = HMAC(send_chain, 0x02)       │
  │  ct = AES-GCM(msg_key, iv, plaintext)      │
  │                                             │
  │  ──── {ct, iv, ephemeralKey} ──────────────►│
  │           (Firebase relay)                  │
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
2. Bob scans QR → enters Alice's nickname → creates contact
3. Both sides compute: `shared_secret = X25519(my_private_key, contact_public_key)`
4. The role (initiator/responder) is determined by the **lexicographic order** of the public keys

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
- ✅ Chat badge: green (verified ✓) or orange (unverified ➤)
- ✅ Verification state persisted in Room per conversation

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

  plaintext → AES-256-GCM(message_key[N], random_iv_12B) → ciphertext
```

### Properties

- ✅ Each message has its own encryption key (KDF chain)
- ✅ Chain advancement is **irreversible** (one-way function)
- ✅ **Healing**: chain key compromise → DH ratchet heals it on next exchange
- ✅ Compromising the current key **does not reveal** past keys
- ✅ Intermediate keys are **zeroed** from memory after use
- ✅ X25519 ephemeral keys renewed at each direction change

---

## What transverses Firebase

### Messages (encrypted)

```json
{
  "ciphertext": "a3F4bWx...",
  "iv": "dG9rZW4...",
  "createdAt": 1700000000000,
  "senderUid": "firebase-anonymous-uid"
}
```

### Ephemeral Sync Settings

```
/conversations/{id}/settings/ephemeralDuration = 3600000
```

### Removed from wire format (V1.1 metadata hardening)

- `senderPublicKey` — useless in 1-to-1 (recipient already knows contact's key)
- `messageIndex` — encrypted in AES-GCM payload (trial decryption on receiver side)

**Never sent:** plaintext, private keys, chain keys, ratchet position.

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
| Old messages replay | ✅ | sinceTimestamp + messageIndex (embedded in ciphertext) |
| Ratchet race conditions | ✅ | Mutex per conversation |
| MITM Attack | ✅ | 96-bit fingerprint emojis (visual check) |
| Phone stolen unlocked | ✅ | Keystore, SQLCipher, App Lock PIN + biometrics, auto-lock |
| Sensitive messages left | ✅ | Disappearing messages (timer on send / read) |
| Metadata (who/when) | ⚠️ | senderPublicKey + messageIndex removed; senderUid + timestamps remain |
| Phone lost | ✅ | 24-word piece mnemonic (BIP-39) to restore identity |
| Contact deletes account | ✅ | Auto-detect dead convo + cleanup + re-invite |

> See [`SECURITY.md`](../../SECURITY.md) for full security analysis.

---

<div align="center">

[← Back to README](../../README-en.md)

</div>