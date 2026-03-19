# Security Policy — SecureChat

## Supported Versions

| Version | Supported |
|---------|-----------|
| 3.4.x   | ✅ Current |
| 3.3.x   | ⚠️ Outdated |
| 3.2.x   | ⚠️ Outdated |
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
| Identity keys | X25519 (Curve25519) | Software JCA, private in EncryptedSharedPreferences (Keystore-backed, StrongBox when available) |
| Post-quantum keys | ML-KEM-768 (CRYSTALS-Kyber) | BouncyCastle 1.78.1 lightweight API; encaps/decaps for hybrid secret |
| Identity backup | BIP-39 mnemonic (24 words) | 256 bits entropy + 8-bit SHA-256 checksum = 264 bits = 24 × 11-bit words |
| Key restore | X25519 DH with base point u=9 | Private key → DH(priv, basepoint) → public key derivation |
| Key exchange | PQXDH: X25519 + ML-KEM-768 | Hybrid classic + post-quantum; both sides start classic-only, rootKey upgraded after first KEM exchange (deferred upgrade, zero desync) |
| Key derivation | HKDF-SHA256 | Root key → send/recv chain keys |
| Message keys | HMAC-SHA256 KDF chain | Double Ratchet (DH ratchet + KDF chains, PFS + healing) |
| DH Ratchet | X25519 ephemeral keys | New key pair per direction change → post-compromise healing |
| Message encryption | AES-256-GCM | 12-byte random IV, 128-bit auth tag, padded to fixed-size buckets |
| Message padding | 256 / 1024 / 4096 / 16384 bytes | 2-byte big-endian length header + random fill |
| Conversation ID | SHA-256 | Hash of sorted public keys |
| Fingerprint emojis | SHA-256 → 64-palette × 16 | 96-bit entropy, anti-MITM, independent verification per user |
| Fingerprint events | Firebase notification (`verified:<ts>`) | Event-based notification only; verification state is strictly local per device |
| senderUid hashing | HMAC-SHA256 | Keyed by conversationId, truncated to 128 bits |
| PIN hashing | PBKDF2-HMAC-SHA256 | 600,000 iterations, 16-byte random salt |
| File encryption | AES-256-GCM (per-file random key) | Encrypted at rest in Firebase Storage |
| Local DB encryption | SQLCipher (AES-256-CBC) | 256-bit passphrase via EncryptedSharedPreferences |
| Hardware key storage | Android StrongBox (if available) | MasterKey.Builder.setRequestStrongBoxBacked(); runtime probe via DeviceSecurityManager |
| Message signing | Ed25519 (BouncyCastle 1.78.1) | Dedicated signing key pair, signature = sign(ciphertext \|\| conversationId \|\| createdAt) |
| Signing key storage | Firebase RTDB `/signing_keys/{hash}` | SHA-256 truncated to 32 hex chars as key, Base64 Ed25519 public key as value |
| Build hardening | R8/ProGuard | Code obfuscation + resource shrinking + complete log stripping (d/v/i/w/e/wtf) |

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

### V3.3 Material 3 Migration, Tor Integration, Attachment UX & Log Hardening

- ✅ **Material Design 3 migration**: all 5 themes migrated from `Theme.MaterialComponents` to `Theme.Material3.Dark.NoActionBar` / `Theme.Material3.Light.NoActionBar` with full M3 color roles
- ✅ **Complete ProGuard log stripping**: added `Log.w()`, `Log.e()`, `Log.wtf()` to `assumenosideeffects` (on top of d/v/i) — zero log output in release builds
- ✅ **Log sanitization**: removed Firebase UIDs, key hashes, and key prefixes from all debug log messages in `FirebaseRelay.kt` and `ChatRepository.kt`
- ✅ **Firebase sign-out fix**: removed `database.goOnline()` after `auth.signOut()` to prevent orphan listener permission errors
- ✅ **Firebase locale fix**: replaced `useAppLanguage()` with explicit `setLanguageCode(Locale.getDefault().language)` to fix X-Firebase-Locale null header
- ✅ **Redundant signing key publish eliminated**: companion flag `signingKeyPublished` + `markSigningKeyPublished()` prevents double `publishSigningPublicKey` between OnboardingViewModel and ConversationsViewModel
- ✅ **Android 13+ media permissions**: `READ_MEDIA_IMAGES`, `READ_MEDIA_AUDIO` declared; `READ_EXTERNAL_STORAGE` with `maxSdkVersion="32"` fallback
- ✅ **Predictive back gesture**: `enableOnBackInvokedCallback="true"` in AndroidManifest for Android 13+ compatibility
- ✅ **Inline attachment icons**: Session-style animated vertical icons replace BottomSheet (reduced attack surface — no dialog-based file picker)
- ✅ **Tor integration**: embedded `libtor.so` arm64-v8a, SOCKS5 proxy at 127.0.0.1:9050, global `ProxySelector` routes all traffic through Tor
- ✅ **TorVpnService**: VPN TUN interface + hev-socks5-tunnel for system-wide traffic routing through Tor
- ✅ **TorManager**: singleton with `StateFlow<TorState>`, conditional startup, foreground notification
- ✅ **TorBootstrapFragment**: first-launch Tor/Normal choice screen with animated progress, skipped on subsequent launches
- ✅ **Tor Settings toggle**: ON/OFF in Security settings with real-time status and manual reconnect
- ✅ **Per-conversation dummy traffic**: individual cover messages per active conversation

### V3.4 PQXDH, DeviceSecurity & Fingerprint Verification

- ✅ **PQXDH hybrid key exchange**: X25519 + ML-KEM-768 post-quantum key agreement via BouncyCastle 1.78.1 lightweight API (no JCA provider registration)
- ✅ **ML-KEM-768 identity key pair**: `generateMLKEMIdentityKeyPair()`, `mlkemEncaps()`, `mlkemDecaps()`, `deriveRootKeyPQXDH(ssClassic, ssPQ)` for hybrid root key derivation
- ✅ **Deferred PQXDH upgrade**: both sides start with classic-only chains; `rootKey` upgraded to combined (classic+PQ) secret only after first KEM ciphertext exchange; current chains stay intact to avoid desync
- ✅ **ML-KEM keys on Firebase**: `/mlkem_keys/{hash}` node for public key distribution; included in QR deep links (`securechat://invite?v=2&x25519=<key>&mlkem=<key>`)
- ✅ **QR code v2**: deep link format with X25519 + ML-KEM public keys + displayName; Version 40 + ErrorCorrectionLevel.L automatic for large content; fallback to text if too large
- ✅ **QR auto-fill contact name**: scanned or deep-linked displayName pre-fills the contact name field; helper text indicates auto-fill
- ✅ **displayName hidden from Firebase**: `storeDisplayName()` → no-op; name only travels in ECIES inbox payload (zero server-side PII)
- ✅ **Firebase rules hardened**: `displayName` field removed from `/users/` validate block
- ✅ **DeviceSecurityManager**: runtime StrongBox probe (KeyGenParameterSpec + `setIsStrongBoxBacked(true)`), secondary profile detection (UserHandle), security level MAXIMUM (StrongBox) / STANDARD
- ✅ **StrongBox-backed MasterKey**: `MasterKey.Builder.setRequestStrongBoxBacked(profile.isStrongBoxAvailable)` — hardware-isolated key storage when available
- ✅ **DeviceSecurityManager pre-warm**: initialized on `Dispatchers.IO` before `CryptoManager.init()` to avoid 100-300ms main thread blocking
- ✅ **lastDeliveredAt tracking**: timestamp of last successfully decrypted message stored on `Conversation` entity; used as Firebase query lower-bound on restart to skip already-processed messages
- ✅ **Delete-after-failure**: failed-to-decrypt messages are deleted from Firebase (prevents cascading failures on app restart)
- ✅ **syncExistingMessages on accept**: processes all pre-acceptance messages (including PQXDH seed) before real-time listener starts; fixes PQXDH upgrade not firing on accept
- ✅ **Dual-listener deduplication**: `processedFirebaseKeys` uses `ConcurrentHashMap.putIfAbsent()` for truly atomic dedup (no race window); LRU-style partial eviction replaces dangerous `clear()`
- ✅ **ConversationsViewModel global listener**: uses `lastDeliveredAt` (not `createdAt`) as Firebase lower-bound to match per-chat listener behavior
- ✅ **Fingerprint verification system messages**: local info message on verify/unverify ("🔐 Vous avez vérifié…" / "🔐 Votre contact a vérifié…") with clickable "Voir l'empreinte" link
- ✅ **Independent fingerprint verification**: each user's verified/unverified state is strictly local; Firebase event is a notification only (`fingerprintEvent: "verified:<timestamp>"`), never modifies the other user's state
- ✅ **Fingerprint un-verify**: users can toggle verification status; button switches between "Marquer comme vérifié" / "Retirer la vérification"
- ✅ **DB version 16**: added `lastDeliveredAt` column to Conversation entity (fallbackToDestructiveMigration)
