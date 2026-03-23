# Security Policy — Fialka

*Last updated: March 23, 2026*

---

## ⚠️ Important Notice

**Fialka is a tool.** The developers do not operate any infrastructure, do not store any user data, and cannot access any messages. There is no central server — all communication is peer-to-peer via Tor Hidden Services. Users are solely responsible for their use of the software.

---

## Supported Versions

| Version | Supported |
|---------|-----------|
| V3.5.x  | ✅ Current |
| V3.4.x  | ✅ Security fixes |
| < V3.4  | ❌ Unsupported |

---

## Reporting a Vulnerability

If you discover a security vulnerability, **do NOT open a public issue**.

Instead:

1. Open a **private security advisory** on GitHub:
   [github.com/FialkaApp/Fialka-Android/security/advisories/new](https://github.com/FialkaApp/Fialka-Android/security/advisories/new)

2. Or email the maintainers through GitHub (via the organization profile).

**Please include:**
- Description of the vulnerability
- Steps to reproduce
- Potential impact assessment
- Suggested fix (if applicable)

Expected response time: **48–72 hours** for acknowledgment.

---

## Identity Architecture — "1 Seed → Everything"

Fialka uses a **single Ed25519 seed** (32 bytes, backed up as 24 BIP-39 words) to derive the entire cryptographic identity:

```
Ed25519 seed (32 bytes)  ←  BIP-39 24 words restores this
│
├── Ed25519 keypair → sign messages (authentication)
│
├── Ed25519 pubkey → SHA3-256 → Base58     = Account ID "Fa3x...9Z"
│
├── Ed25519 keypair → Tor v3 encoding      = .onion address
│
├── Ed25519 pubkey → SHA-256 → 16 emojis   = Fingerprint anti-MITM (96-bit)
│
├── Ed25519 pubkey → birational map         = X25519 DH pubkey
│   (Edwards → Montgomery)                    (mathematical derivation, not HKDF)
│                                             Contact only needs Ed25519 pubkey
│                                             → X25519 computes automatically
│
└── HKDF(seed, "fialka-ml-kem", 64 bytes)  = ML-KEM-1024 keypair
    (d=32 + z=32 → deterministic KeyGen)      (post-quantum key derived from seed)
```

### Why Birational Map for X25519 (Not HKDF)

| | HKDF(seed) → X25519 | Birational map Ed25519 → X25519 |
|---|---|---|
| Pubkeys to distribute | **2** (Ed25519 + X25519 separately) | **1** (Ed25519 only) |
| Contact can verify link | ❌ No provable link | ✅ Mathematical derivation |
| "1 key → everything" | ❌ Broken | ✅ Preserved |

The birational map converts `EdwardsPoint → MontgomeryPoint` — a standard conversion between two forms of the same curve (Curve25519). Equivalent to libsodium's `crypto_sign_ed25519_pk_to_curve25519`.

### Key Result

**BIP-39 24 words restore EVERYTHING**: Ed25519, X25519, ML-KEM-1024, Account ID, .onion address, fingerprint.

---

## Cryptographic Design

### Algorithms Summary

| Component | Algorithm | Key Size | Library | Purpose |
|-----------|-----------|----------|---------|---------|
| **Identity key** | Ed25519 | 256-bit | BouncyCastle 1.80 | Master identity, message signing, derives all other keys |
| **DH key agreement** | X25519 | 256-bit | Android built-in | Double Ratchet (derived from Ed25519 via birational map) |
| **Post-quantum KEM** | ML-KEM-1024 | NIST Level 5 | BouncyCastle 1.80 | PQXDH hybrid key exchange + SPQR (derived from seed via HKDF) |
| **PQ signature (handshake)** | ML-DSA-44 | NIST Level 2 | BouncyCastle | Hybrid handshake authentication (Ed25519 + ML-DSA-44) |
| **Symmetric encryption** | AES-256-GCM | 256-bit | Android built-in | Message encryption (hardware AES) |
| **Symmetric encryption (alt)** | ChaCha20-Poly1305 | 256-bit | BouncyCastle 1.80 | Message encryption (no hardware AES) |
| **Key derivation** | HKDF-SHA256 | — | Custom (RFC 5869) | Root key, chain keys, ML-KEM seed |
| **KDF chain** | HMAC-SHA256 | 256-bit | Android built-in | Double Ratchet chain advancement |
| **Fingerprint** | SHA-256 → 16 emojis | 96-bit | Android built-in | Visual MITM detection |
| **Account ID** | SHA3-256 → Base58 | 256-bit | BouncyCastle | Human-readable identity |
| **PIN hashing** | PBKDF2-HMAC-SHA256 | 600K iterations | Android built-in | App Lock |
| **Local database** | SQLCipher | AES-256 | SQLCipher 4.5.4 | Encrypted local storage |
| **Key storage** | Android Keystore | Hardware-backed | Android (StrongBox/TEE) | Seed protection |
| **Configuration** | EncryptedSharedPreferences | AES-256-GCM | AndroidX Security | Sensitive settings |
| **Backup** | BIP-39 | 256-bit + checksum | Custom | 24-word recovery phrase |
| **File encryption** | AES-256-GCM per file | 256-bit | Android built-in | E2E file sharing |
| **File deletion** | 2-pass overwrite | — | SecureFileManager | Random bytes + zeros |
| **Transport** | Tor Hidden Services | — | libtor.so | P2P .onion communication |
| **Offline delivery** | Fialka Mailbox | — | Custom | Encrypted blob store via Tor |
| **Push** | UnifiedPush + ntfy.sh | — | Open-source | Wake-up signal (zero content) |

### Protocol Stack

```
Layer 1 — Identity:        Ed25519 seed (32 bytes) → derives everything
Layer 2 — Key Exchange:    PQXDH (X25519 + ML-KEM-1024 hybrid)
Layer 3 — Handshake Auth:  Ed25519 + ML-DSA-44 hybrid signature
Layer 4 — Ratcheting:      Double Ratchet (DH ratchet + KDF chains)
Layer 5 — PQ Maintenance:  SPQR (ML-KEM re-encapsulation every 10 messages)
Layer 6 — Encryption:      AES-256-GCM or ChaCha20-Poly1305 (auto-selected)
Layer 7 — Signing:         Ed25519 per-message signature
Layer 8 — Padding:         Fixed-size buckets (256B / 1KB / 4KB / 16KB)
Layer 9 — Transport:       Tor Hidden Services (.onion P2P — no relay)
Layer 10 — Cover Traffic:  Dummy messages (30–90s interval, per-conversation)
Layer 11 — Offline:        Fialka Mailbox (encrypted blob store via Tor)
```

### Session Establishment

```
PQXDH Hybrid Key Exchange:
━━━━━━━━━━━━━━━━━━━━━━━━━━
Alice                                   Bob
  │                                       │
  ├── X25519 DH(Alice, Bob) ─────────────►│  = ssClassic (32 bytes)
  ├── ML-KEM encaps(Bob pubkey) ─────────►│  = ssPQ (32 bytes) + kemCiphertext
  ├── Ed25519.sign(handshake data) ──────►│  = classicSig (64 bytes)
  ├── ML-DSA-44.sign(handshake data) ────►│  = pqSig (2420 bytes, one-time)
  │                                       │
  └── HKDF(ssClassic || ssPQ) ──────────→ rootKey initial
                                          (hybrid classic + post-quantum)
```

### Per-Message Encryption

```
rootKey ──→ DH Ratchet (X25519 ephemeral) ──→ new rootKey
         └─→ chainKey ──→ HMAC ──→ messageKey

messageKey ──→ AES-256-GCM(plaintext padded) ──→ ciphertext
Ed25519.sign(ciphertext || conversationId || createdAt) ──→ signature 64 bytes

Every 10 messages (SPQR):
  ML-KEM encaps ──→ ssPQ (32 bytes)
  HKDF(rootKey || ssPQ) ──→ new rootKey (continuous PQ protection)
```

---

## Network Architecture — Zero Central Server

### Tor Hidden Services (P2P)

| Aspect | Detail |
|--------|--------|
| **Transport** | Direct .onion to .onion — no relay, no middleman |
| **IP visibility** | Never exposed — to contacts, to observers, to anyone |
| **Metadata** | Zero — no central server sees who talks to whom |
| **Address derivation** | Ed25519 identity keypair → Tor v3 .onion address |

### Fialka Mailbox (Offline Delivery)

| Mode | Description | Privacy | Setup |
|------|-------------|---------|-------|
| **0** — Direct P2P | .onion to .onion directly | ★★★★★ | None |
| **1** — Personal Mailbox | Your own device (old phone, RPi) | ★★★★★ | Second device |
| **2** — Private Node | Friend hosts for trusted group (whitelist) | ★★★★☆ | QR scan |
| **3** — Public Node | Community volunteers, auto-selected (default) | ★★★☆☆ | None ✅ |

- Mailbox nodes **only see encrypted blobs** — zero plaintext, zero metadata
- Messages deleted after delivery (or TTL expiration)
- X3DH prekeys on Mailbox enable asynchronous key exchange (no simultaneous online required)

### Sealed Sender via Tor

| | Signal | Fialka |
|---|---|---|
| Server sees sender | ❌ (delivery token) but sees **IP** | ❌ Anonymous blob via .onion |
| IP visible | ✅ Yes | ❌ No (Tor) |
| Central server required | ✅ Yes | ❌ No |

---

## Signature Strategy — Classic + Post-Quantum

### The Two Problems

| | Protects | Algorithm | PQ? |
|---|---|---|---|
| **SPQR** (every 10 msgs) | **Confidentiality** — no one can read | ML-KEM-1024 | ✅ |
| **Signatures** (every msg) | **Authentication** — proves who sent it | Ed25519 (+ ML-DSA-44 on handshake) | ✅ Handshake / ❌ Per-msg |

### Roadmap

| Version | Classic Signature | PQ Signature | Where | Size |
|---------|---|---|---|---|
| **V3.5** (current) | Ed25519 / every msg | ❌ None | — | 64 B |
| **V3.6b** | Ed25519 / every msg | **ML-DSA-44** | **Handshake only** | +2 420 B (one-time) |
| **V5.0** (2028+) | Ed25519 / every msg | **FN-DSA Falcon-512** | **Every message** | +666 B / msg |

### Why ML-DSA-44 Handshake Only (V3.6b)

ML-DSA-44 = **2 420 bytes** per signature. On every message over Tor, unacceptable (38x larger than Ed25519). But on the **handshake** it's 2 420 bytes **once** — negligible. And if the handshake is PQ-authenticated, the entire session is protected.

### Why FN-DSA Falcon-512 Per Message (V5.0, 2028+)

Falcon-512 = **~666 bytes** → 10x smaller than ML-DSA-44. Viable per message. But Android libraries are not mature in 2026 (floating-point arithmetic, side-channel concerns). Ready by 2028+.

---

## Security Hardening Measures

### V3.4.1 Comprehensive Audit (42+ fixes)

| Category | Measure | Status |
|----------|---------|--------|
| **Screen security** | FLAG_SECURE on all sensitive activities and dialogs | ✅ |
| **Tapjacking** | filterTouchesWhenObscured on sensitive views | ✅ |
| **Network** | usesCleartextTraffic=false (zero HTTP) | ✅ |
| **Deep links** | Parameter whitelist, length limits, Base64 validation, control char rejection | ✅ |
| **Clipboard** | EXTRA_IS_SENSITIVE + 30s auto-clear | ✅ |
| **File security** | SecureFileManager 2-pass overwrite (random + zeros) | ✅ |
| **Push** | Opaque push payload — zero metadata | ✅ |
| **Memory** | HKDF zeroing (IKM, PRK, expandInput) | ✅ |
| **Memory** | MnemonicManager zeroing (encode + decode paths) | ✅ |
| **Input** | ML-KEM size + Base64 client-side validation | ✅ |
| **Backup** | allowBackup=false | ✅ |
| **Logging** | R8/ProGuard complete log stripping (d/v/i/w/e/wtf) | ✅ |
| **Obfuscation** | R8 full obfuscation in release builds | ✅ |

---

## Threat Model

| Threat | Mitigated | How |
|--------|-----------|-----|
| Central server compromise | ✅ | **No central server** — fully P2P via Tor |
| ISP/network surveillance | ✅ | All traffic via Tor — IP never exposed |
| Metadata analysis (who talks to whom) | ✅ | No relay sees both endpoints — Tor .onion P2P |
| Message key compromise | ✅ | PFS — each message has its own key (Double Ratchet) |
| Replay attacks | ✅ | messageIndex embedded in ciphertext + per-msg Ed25519 signature |
| Ratchet race conditions | ✅ | Mutex per conversation + ConcurrentHashMap + LRU eviction |
| MITM attack | ✅ | 96-bit fingerprint emojis + QR code verification |
| Phone stolen (unlocked) | ✅ | Android Keystore + SQLCipher + App Lock (PIN + biometrics) + auto-lock |
| Sensitive messages left | ✅ | Disappearing messages (timer on send/read) |
| Message forgery | ✅ | Ed25519 per-message signature (badge ✅/⚠️) |
| Traffic analysis | ✅ | Dummy traffic (30–90s) + bucket padding + Tor routing |
| File interception | ✅ | Per-file AES-256-GCM, key inside E2E channel |
| Phone loss | ✅ | BIP-39 24-word mnemonic recovery |
| PIN brute force | ✅ | PBKDF2 600K iterations + biometric lock |
| Contact deletes account | ✅ | Auto-detect dead conversation + cleanup + re-invite |
| Quantum computer — "harvest now, decrypt later" | ✅ | Hybrid PQXDH (ML-KEM-1024) + SPQR periodic re-encapsulation |
| Quantum computer — forged signatures | ✅ | ML-DSA-44 on handshake (V3.6b) + Falcon-512 per-msg (V5.0) |
| Ratchet desync | ✅ | syncExistingMessages, delete-after-failure |
| No AES hardware | ✅ | ChaCha20-Poly1305 auto-selected on non-AES devices |
| Screenshot/recording | ✅ | FLAG_SECURE on all sensitive windows |
| Tapjacking/overlay | ✅ | filterTouchesWhenObscured |
| Deep link injection | ✅ | Parameter whitelist + length limits + validation |
| Clipboard leakage | ✅ | EXTRA_IS_SENSITIVE + 30s auto-clear |
| File forensic recovery | ✅ | SecureFileManager 2-pass overwrite |
| Mailbox node compromise | ✅ | Node only stores encrypted blobs — zero plaintext, zero metadata |

---

## Known Limitations

| # | Limitation | Risk | Mitigation |
|---|-----------|------|------------|
| 1 | **No third-party crypto audit (yet)** | Implementation bugs may exist | Open-source (GPLv3) — community review; professional audit planned (Cure53 / Trail of Bits) |
| 2 | **Single-device** | No multi-device sync | BIP-39 backup allows restore on new device; multi-device Sesame planned (V4.1) |
| 3 | **Tor dependency** | Tor network availability, connection latency | Bluetooth/WiFi P2P fallback planned (V5.0) |
| 4 | **Trust on first use (TOFU)** | Key exchange could be intercepted | 96-bit fingerprint verification (emoji + QR code) + ML-DSA-44 handshake auth |
| 5 | **Client timestamps** | User can manipulate local clock | Ed25519 signature includes timestamp — tampering detectable |
| 6 | **No group chat** | 1-to-1 only for now | Planned for future version |
| 7 | **Android only** | No iOS or desktop client | iOS version planned (see FialkaApp/Fialka-iOS) |
| 8 | **Per-message sigs not PQ until V5.0** | Quantum attacker could forge messages in real-time | SPQR protects confidentiality; real-time signature forgery is far less probable than "harvest now, decrypt later" |

---

## Honest Security Scores

| Messenger | Estimated Score | Notes |
|-----------|----------------|-------|
| Signal | ~90% | Audited, battle-tested, millions of users |
| Session | ~73% | Decentralized but limited crypto |
| **Fialka V3.5** (transitional) | **~70%** | Strong crypto, but Firebase = metadata leak |
| **Fialka V4.0** (target, pre-audit) | **~93%** | Zero Google, full Tor P2P, hybrid PQ |
| **Fialka V5.0** (post-audit, Falcon) | **~95%** | Full PQ signatures, audited, mesh fallback |

---

## Security Best Practices for Users

1. **Verify fingerprints** — Always compare emoji fingerprints (or scan QR) in person or via trusted video call
2. **Save your 24-word phrase** — Store it offline, never digitally, never share it
3. **Enable App Lock** — Use a strong PIN + biometric authentication
4. **Use Tor** (default) — All traffic is routed through Tor for IP anonymization
5. **Enable disappearing messages** — For sensitive conversations, set ephemeral timers
6. **Keep the app updated** — Security fixes are applied in new releases
7. **Self-host your Mailbox** — For maximum privacy, run your own Mailbox node (Mode 1)

---

## Responsible Disclosure

We follow a **coordinated disclosure** model. If you report a vulnerability:
- We will acknowledge it within **72 hours**
- We will work on a fix and coordinate a disclosure timeline
- Credit will be given in the Changelog and release notes (unless you prefer anonymity)

---

**Fialka is a tool. The developers do not operate any infrastructure, do not store any user data, and cannot access any messages. There is no central server — all communication is peer-to-peer via Tor. Users are solely responsible for their use of the software.**

© 2024-2026 FialkaApp Contributors. Licensed under [GPLv3](LICENSE).
