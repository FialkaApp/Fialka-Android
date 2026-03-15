# Security Policy — SecureChat

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.0.x   | ✅ Current |

## Reporting a Vulnerability

If you discover a security vulnerability in SecureChat, **please do NOT open a public issue**.

Instead, contact the maintainer privately:
- Open a **private security advisory** on GitHub (Settings → Security → Advisories)
- Or send a private message to the repository owner

## Cryptographic Design

SecureChat uses the following cryptographic primitives:

| Component | Algorithm | Notes |
|-----------|-----------|-------|
| Identity keys | EC P-256 (secp256r1) | Stored in Android Keystore (hardware-backed) |
| Key exchange | ECDH | Shared secret derived from P-256 keys |
| Key derivation | HKDF-SHA256 | Root key → send/recv chain keys |
| Message keys | HMAC-SHA256 KDF chain | Symmetric Ratchet (PFS) |
| Message encryption | AES-256-GCM | 12-byte random IV, 128-bit auth tag |
| Conversation ID | SHA-256 | Hash of sorted public keys |

## Known Limitations (V1)

1. **Symmetric Ratchet only** — No DH ratchet rotation (unlike Signal's full Double Ratchet). If an entire chain key is compromised, future messages in that chain direction are exposed until the next conversation reset.

2. **No key verification** — Users cannot verify that a scanned public key truly belongs to the intended contact (vulnerable to MITM during initial key exchange).

3. **Plaintext in local DB** — Decrypted messages are stored in Room (SQLite) without encryption. A rooted device or full disk backup could expose message history.

4. **Metadata visible** — Firebase sees who communicates with whom and when (conversation IDs, timestamps).

5. **No message authentication beyond GCM** — Messages are not signed with the sender's identity key, only authenticated by GCM tag (which proves knowledge of the shared secret).

## Security Hardening Implemented

- ✅ All intermediate key material (shared secrets, chain keys, message keys) is zeroed from memory after use
- ✅ SecureRandom singleton for IV generation (never reused)
- ✅ Private key deleted from Keystore on account reset
- ✅ Ratchet state persisted only after successful Firebase send (atomic)
- ✅ Mutex per conversation to prevent ratchet race conditions
- ✅ Firebase re-authentication on app resume
- ✅ No sensitive data logged (public keys, plaintexts removed from Logcat)
- ✅ Firebase TTL cleanup (messages older than 7 days auto-deleted)
- ✅ Anti-replay: sinceTimestamp + messageIndex filtering
- ✅ `android:allowBackup="false"` in AndroidManifest
