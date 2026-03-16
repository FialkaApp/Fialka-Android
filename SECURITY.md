# Security Policy — SecureChat

## Supported Versions

| Version | Supported |
|---------|-----------|
| 1.2.x   | ✅ Current |
| 1.0.x   | ⚠️ Outdated |

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
| Fingerprint emojis | SHA-256 → 64-palette × 16 | 96-bit entropy, anti-MITM |
| Local DB encryption | SQLCipher (AES-256-CBC) | 256-bit passphrase via EncryptedSharedPreferences |

## Known Limitations (V1)

1. **Symmetric Ratchet only** — No DH ratchet rotation (unlike Signal's full Double Ratchet). If an entire chain key is compromised, future messages in that chain direction are exposed until the next conversation reset.

2. **No key verification** — ~~Users cannot verify that a scanned public key truly belongs to the intended contact (vulnerable to MITM during initial key exchange).~~ **FIXED in V1:** Emoji fingerprint verification (96-bit shared fingerprint, visual comparison, badge system).

3. **Plaintext in local DB** — ~~Decrypted messages are stored in Room (SQLite) without encryption. A rooted device or full disk backup could expose message history.~~ **FIXED in V1:** SQLCipher encrypts the entire Room database with a 256-bit passphrase stored in EncryptedSharedPreferences (Keystore-backed AES-256-GCM).

4. **Metadata visible** — ~~Firebase sees who communicates with whom and when (conversation IDs, timestamps).~~ **PARTIALLY FIXED in V1.1:** `senderPublicKey` and `messageIndex` removed from wire format. Firebase still sees: `senderUid` (anonymous), `createdAt` (timestamps), conversation IDs, participant UIDs. Full metadata privacy would require a mix network or onion routing (V3+).

5. **No message authentication beyond GCM** — Messages are not signed with the sender's identity key, only authenticated by GCM tag (which proves knowledge of the shared secret). In 1-to-1 conversations this is acceptable (only 2 participants share the key). For future group chats, ECDSA signatures will be required (V2 planned).

6. **Ephemeral timer client-enforced** — Ephemeral message deletion is performed client-side (local Room delete + Firebase remove). A modified client could skip deletion. Server-enforced TTL per-message would require Cloud Functions (V2+).

## Security Hardening Implemented

- ✅ All intermediate key material (shared secrets, chain keys, message keys) is zeroed from memory after use
- ✅ SecureRandom singleton for IV generation (never reused)
- ✅ Private key deleted from Keystore on account reset
- ✅ Ratchet state persisted only after successful Firebase send (atomic)
- ✅ Mutex per conversation to prevent ratchet race conditions
- ✅ Firebase re-authentication on app resume
- ✅ No sensitive data logged (public keys, plaintexts removed from Logcat)
- ✅ Firebase TTL cleanup (messages older than 7 days auto-deleted)
- ✅ Anti-replay: sinceTimestamp + messageIndex filtering (index embedded in ciphertext)
- ✅ Metadata hardening: `senderPublicKey` removed from wire format (unnecessary in 1-to-1)
- ✅ Metadata hardening: `messageIndex` encrypted inside AES-GCM payload (trial decryption, MAX_SKIP=100)
- ✅ Trial decryption: constant-time-ish — common case (in-order) = 1 attempt, worst case = 100 attempts
- ✅ `android:allowBackup="false"` in AndroidManifest
- ✅ Push notifications opt-in (disabled by default — no FCM token stored)
- ✅ Zero message content in push notifications (only sender display name)
- ✅ FCM token deleted immediately when user disables push
- ✅ Invalid/expired FCM tokens auto-cleaned by Cloud Function
- ✅ Emoji fingerprint: shared 96-bit (16 emojis from 64-palette, power-of-2 = zero modulo bias)
- ✅ Fingerprint computed from sorted public keys (both sides see the same emojis)
- ✅ Manual verification only (no auto-check — user must visually compare in person)
- ✅ SQLCipher: entire Room database encrypted (AES-256-CBC, 256-bit key)
- ✅ DB passphrase generated via SecureRandom, stored in EncryptedSharedPreferences (Keystore-backed)
- ✅ DB file unreadable without Android Keystore access (protects against rooted device / backup dump)
- ✅ App Lock: 4-digit PIN (SHA-256 hash stored in EncryptedSharedPreferences)
- ✅ Biometric unlock: opt-in via BiometricPrompt (BIOMETRIC_STRONG | BIOMETRIC_WEAK)
- ✅ Auto-lock timeout: configurable (5s, 15s, 30s, 1min, 5min), default 5 seconds
- ✅ Lock screen bypasses disabled (`onBackPressed` → `finishAffinity`)
- ✅ Ephemeral messages: timer starts on send (sender) or on chat open (receiver READ)
- ✅ Ephemeral duration synced between participants via Firebase RTDB (`/settings/ephemeralDuration`)
- ✅ Ephemeral deletion: local Room delete + Firebase node removal
- ✅ Firebase security rules: read/write restricted to conversation participants (messages, settings)
- ✅ Dark mode: full DayNight theme with adaptive colors (backgrounds, bubbles, badges)
- ✅ Theme-aware drawables: info boxes, backgrounds use `@color/` resources with night variants
