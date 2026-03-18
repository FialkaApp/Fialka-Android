# Security Policy — SecureChat

## Supported Versions

| Version | Supported |
|---------|-----------|
| 3.2.x   | ✅ Current |
| 3.1.x   | ⚠️ Outdated |
| 3.0.x   | ⚠️ Outdated |
| 2.x     | ⚠️ Outdated |
| 1.x     | ❌ Unsupported |

## Reporting a Vulnerability

If you discover a security vulnerability in SecureChat, **please do NOT open a public issue**.

Instead, contact the maintainer privately:
- Open a **private security advisory** on GitHub (Settings → Security → Advisories)
- Or send a private message to the repository owner

## Cryptographic Design

SecureChat uses the following cryptographic primitives:

| Component | Algorithm | Notes |
|-----------|-----------|-------|
| Identity keys | X25519 (Curve25519) | Software JCA, private in EncryptedSharedPreferences (Keystore-backed) |
| Identity backup | BIP-39 mnemonic (24 words) | 256 bits entropy + 8-bit SHA-256 checksum = 264 bits = 24 × 11-bit words |
| Key restore | X25519 DH with base point u=9 | Private key → DH(priv, basepoint) → public key derivation |
| Key exchange | X25519 ECDH | Shared secret derived from Curve25519 keys |
| Key derivation | HKDF-SHA256 | Root key → send/recv chain keys |
| Message keys | HMAC-SHA256 KDF chain | Double Ratchet (DH ratchet + KDF chains, PFS + healing) |
| DH Ratchet | X25519 ephemeral keys | New key pair per direction change → post-compromise healing |
| Message encryption | AES-256-GCM | 12-byte random IV, 128-bit auth tag, padded to fixed-size buckets |
| Message padding | 256 / 1024 / 4096 / 16384 bytes | 2-byte big-endian length header + random fill |
| Conversation ID | SHA-256 | Hash of sorted public keys |
| Fingerprint emojis | SHA-256 → 64-palette × 16 | 96-bit entropy, anti-MITM |
| senderUid hashing | HMAC-SHA256 | Keyed by conversationId, truncated to 128 bits |
| PIN hashing | PBKDF2-HMAC-SHA256 | 600,000 iterations, 16-byte random salt |
| File encryption | AES-256-GCM (per-file random key) | Encrypted at rest in Firebase Storage |
| Local DB encryption | SQLCipher (AES-256-CBC) | 256-bit passphrase via EncryptedSharedPreferences |
| Message signing | Ed25519 (BouncyCastle 1.78.1) | Dedicated signing key pair, signature = sign(ciphertext \|\| conversationId \|\| createdAt) |
| Signing key storage | Firebase RTDB `/signing_keys/{hash}` | SHA-256 truncated to 32 hex chars as key, Base64 Ed25519 public key as value |
| Build hardening | R8/ProGuard | Code obfuscation + resource shrinking + log stripping (d/v/i) |

## Known Limitations (V1)

1. ~~**Symmetric Ratchet only** — No DH ratchet rotation (unlike Signal's full Double Ratchet). If an entire chain key is compromised, future messages in that chain direction are exposed until the next conversation reset.~~ **FIXED in V2:** Full Double Ratchet with X25519 ephemeral DH keys. Compromise of a chain key is healed at the next direction change (DH ratchet step).

2. **No key verification** — ~~Users cannot verify that a scanned public key truly belongs to the intended contact (vulnerable to MITM during initial key exchange).~~ **FIXED in V1:** Emoji fingerprint verification (96-bit shared fingerprint, visual comparison, badge system).

3. **Plaintext in local DB** — ~~Decrypted messages are stored in Room (SQLite) without encryption. A rooted device or full disk backup could expose message history.~~ **FIXED in V1:** SQLCipher encrypts the entire Room database with a 256-bit passphrase stored in EncryptedSharedPreferences (Keystore-backed AES-256-GCM).

4. **Metadata visible** — ~~Firebase sees who communicates with whom and when (conversation IDs, timestamps).~~ **PARTIALLY FIXED in V1.1:** `senderPublicKey` and `messageIndex` removed from wire format. **IMPROVED in V3:** `senderUid` is now HMAC-SHA256 hashed per conversation (not the raw Firebase UID). Messages are padded to fixed-size buckets (256/1K/4K/16K bytes) to prevent size-based traffic analysis. Dummy traffic sends periodic indistinguishable cover messages. Firebase still sees: hashed senderUid, `createdAt` (timestamps), conversation IDs, participant UIDs. Full metadata privacy would require a mix network or onion routing.

5. ~~No message authentication beyond GCM~~ — ~~Messages are not signed with the sender's identity key, only authenticated by GCM tag (which proves knowledge of the shared secret). In 1-to-1 conversations this is acceptable (only 2 participants share the key). For future group chats, ECDSA signatures will be required (V3 planned).~~ **FIXED in V3.2:** Every message is signed with a dedicated Ed25519 key pair (BouncyCastle 1.78.1). Signature covers `ciphertext || conversationId || createdAt` (anti-forgery + anti-replay). Badge ✅/⚠️ displayed per message.

6. **Ephemeral timer client-enforced** — Ephemeral message deletion is performed client-side (local Room delete + Firebase remove). A modified client could skip deletion. Server-enforced TTL per-message would require Cloud Functions (V3+).

7. **Mnemonic phrase unencrypted** — The 24-word BIP-39 backup phrase is displayed in plaintext on screen. If someone sees the screen during backup, or if the user stores the phrase insecurely, the identity key is compromised. The user is responsible for secure physical storage of their mnemonic.

## Security Hardening Implemented

- ✅ All intermediate key material (shared secrets, chain keys, message keys, DH ephemeral private keys) is zeroed from memory after use
- ✅ SecureRandom singleton for IV generation (never reused)
- ✅ Private key stored in EncryptedSharedPreferences (Keystore-backed AES-256-GCM) on account reset
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
- ✅ App Lock: 6-digit PIN (PBKDF2-HMAC-SHA256, 600K iterations, 16-byte random salt, stored in EncryptedSharedPreferences)
- ✅ Biometric unlock: opt-in via BiometricPrompt (BIOMETRIC_STRONG | BIOMETRIC_WEAK)
- ✅ Auto-lock timeout: configurable (5s, 15s, 30s, 1min, 5min), default 5 seconds
- ✅ Lock screen bypasses disabled (`onBackPressed` → `finishAffinity`)
- ✅ Ephemeral messages: timer starts on send (sender) or on chat open (receiver READ)
- ✅ Ephemeral duration synced between participants via Firebase RTDB (`/settings/ephemeralDuration`)
- ✅ Ephemeral deletion: local Room delete + Firebase node removal
- ✅ Firebase security rules: read/write restricted to conversation participants (messages, settings)
- ✅ Firebase security rules: conversation-level delete restricted to participants (`!newData.exists()`)
- ✅ Dark mode: full DayNight theme with adaptive colors (backgrounds, bubbles, badges)
- ✅ Theme-aware drawables: info boxes, backgrounds use `@color/` resources with night variants
- ✅ BIP-39 mnemonic backup: 24 words encode 256-bit X25519 private key + 8-bit SHA-256 checksum
- ✅ Mnemonic restore: private key → DH with base point u=9 → deterministic public key derivation
- ✅ Account deletion: full Firebase cleanup (profile `/users/{uid}`, inbox `/inbox/{hash}`, signing keys `/signing_keys/{hash}`, all conversations)
- ✅ Dead conversation detection: `conversationExists()` returns false on PERMISSION_DENIED (deleted conversation)
- ✅ Stale contact cleanup: dead conversations cleaned from local DB (messages, ratchet state, contact) before re-invitation
- ✅ Orphaned profile cleanup: `removeOldUserByPublicKey()` removes old `/users/` node on account restore

### V3 Security Additions

- ✅ **R8/ProGuard**: code obfuscation, resource shrinking, repackaging in release builds
- ✅ **Log stripping**: `Log.d()`, `Log.v()`, `Log.i()` removed by ProGuard in release (assumenosideeffects)
- ✅ **Delete-after-delivery**: ciphertext removed from Firebase RTDB immediately after successful decryption
- ✅ **Message padding**: plaintext padded to fixed-size buckets (256/1024/4096/16384 bytes) with 2-byte length header + SecureRandom fill, preventing size-based traffic analysis
- ✅ **senderUid HMAC hashing**: `senderUid` field is HMAC-SHA256(conversationId, raw UID) truncated to 128 bits — Firebase cannot correlate the same user across different conversations
- ✅ **Room DB indexes**: composite indexes on messages(conversationId, timestamp), messages(expiresAt), conversations(accepted), contacts(publicKey) for performance
- ✅ **PBKDF2 PIN**: PBKDF2-HMAC-SHA256 (600,000 iterations, 16-byte random salt); 6-digit PIN enforced
- ✅ **Dummy traffic**: periodic cover messages (45–120s random interval) sent via real Double Ratchet — indistinguishable from real messages on the wire; configurable toggle in security settings; receiver detects and silently drops after decryption
- ✅ **E2E file sharing**: files encrypted client-side with random AES-256-GCM key, uploaded to Firebase Storage; metadata (URL + key + IV + filename + size) sent via the ratchet; receiver downloads, decrypts locally, stores to app-private storage; 25 MB limit; encrypted file deleted from Storage after delivery
- ✅ **Firebase Storage security rules**: authenticated-only access, 50 MB max upload, restricted to `/encrypted_files/` path
- ✅ **Double-listener guard**: `processedFirebaseKeys` set prevents ratchet desynchronization when global and per-chat Firebase listeners process the same message simultaneously
- ✅ **Opaque dummy prefix**: dummy message marker uses non-printable control bytes (`\u0007\u001B\u0003`) instead of readable text, not identifiable in memory dumps

### V3.1 Settings & PIN Improvements

- ✅ **Settings redesign**: Signal/Telegram-style settings hierarchy (Général, Confidentialité, Sécurité, À propos)
- ✅ **Privacy sub-screen**: dedicated Confidentialité screen (ephemeral messages, delete-after-delivery, dummy traffic)
- ✅ **6-digit PIN**: upgraded from 4-digit, removed legacy 4-digit/SHA-256 backward compatibility
- ✅ **PIN coroutines**: PBKDF2 verification runs on `Dispatchers.Default` (off UI thread), zero freeze on digit entry
- ✅ **Cached EncryptedSharedPreferences**: double-checked locking pattern avoids repeated Keystore initialization

### V3.2 Ed25519 Message Signing

- ✅ **Ed25519 signatures**: every message is signed with a dedicated Ed25519 key pair (separate from X25519 identity key)
- ✅ **BouncyCastle 1.78.1**: `bcprov-jdk18on` registered as JCA provider at position 1 (`Security.removeProvider("BC")` + `Security.insertProviderAt()`)
- ✅ **Signed data**: `signature = Ed25519.sign(ciphertext_UTF8 || conversationId_UTF8 || createdAt_bigEndian8bytes)` — anti-forgery + anti-replay
- ✅ **Signing key storage**: Ed25519 public key stored at `/signing_keys/{SHA256_hash}` and `/users/{uid}/signingPublicKey` on Firebase; private key in EncryptedSharedPreferences
- ✅ **Verification on receive**: receiver fetches sender's Ed25519 public key by identity key hash, verifies signature, displays ✅ (valid) or ⚠️ (invalid/missing)
- ✅ **Client timestamp preserved**: `createdAt` uses client `System.currentTimeMillis()` (not Firebase `ServerValue.TIMESTAMP`) to ensure signature consistency
- ✅ **Firebase rules hardening**: `/conversations/$id/participants` read restricted to conversation members only (no longer readable by all authenticated users)
- ✅ **Signing key cleanup**: `/signing_keys/{hash}` deleted on account deletion alongside profile, inbox, and conversations
