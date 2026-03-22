# Security Policy — SecureChat

## Supported Versions

| Version | Supported |
|---------|-----------|
| 3.5     | ✅ Current |
| 3.4.1   | ⚠️ Outdated |
| 3.4.x   | ⚠️ Outdated |
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
| Post-quantum keys | ML-KEM-1024 (CRYSTALS-Kyber) | BouncyCastle 1.80 lightweight API; encaps/decaps for hybrid secret |
| Identity backup | BIP-39 mnemonic (24 words) | 256 bits entropy + 8-bit SHA-256 checksum = 264 bits = 24 × 11-bit words |
| Key restore | X25519 DH with base point u=9 | Private key → DH(priv, basepoint) → public key derivation |
| Key exchange | PQXDH: X25519 + ML-KEM-1024 | Hybrid classic + post-quantum; both sides start classic-only, rootKey upgraded after first KEM exchange (deferred upgrade, zero desync) |
| Key derivation | HKDF-SHA256 | Root key → send/recv chain keys |
| Message keys | HMAC-SHA256 KDF chain | Double Ratchet (DH ratchet + KDF chains, PFS + healing) |
| DH Ratchet | X25519 ephemeral keys | New key pair per direction change → post-compromise healing |
| Message encryption | AES-256-GCM / ChaCha20-Poly1305 | AES-GCM default (hardware accelerated); ChaCha20 auto-selected on devices without ARMv8 Crypto Extension. `cipherSuite` field in wire format. |
| PQ ratchet (SPQR) | ML-KEM-1024 re-encapsulation every 10 messages | HKDF mixes fresh PQ secret into rootKey; reuses `kemCiphertext` field; transparent to both sides |
| Message padding | 256 / 1024 / 4096 / 16384 bytes | 2-byte big-endian length header + random fill |
| Conversation ID | SHA-256 | Hash of sorted public keys |
| Fingerprint emojis | SHA-256 → 64-palette × 16 | 96-bit entropy, anti-MITM, independent verification per user |
| Fingerprint QR code | SHA-256 hex (64 ASCII chars) | Hex encoding for QR scanner compatibility; `getSharedFingerprintHex()` |
| Fingerprint events | Firebase notification (`verified:<ts>`) | Event-based notification only; verification state is strictly local per device |
| senderUid hashing | HMAC-SHA256 | Keyed by conversationId, truncated to 128 bits |
| PIN hashing | PBKDF2-HMAC-SHA256 | 600,000 iterations, 16-byte random salt |
| File encryption | AES-256-GCM (per-file random key) | Encrypted at rest in Firebase Storage |
| Local DB encryption | SQLCipher (AES-256-CBC) | 256-bit passphrase via EncryptedSharedPreferences |
| Hardware key storage | Android StrongBox (if available) | MasterKey.Builder.setRequestStrongBoxBacked(); runtime probe via DeviceSecurityManager |
| Message signing | Ed25519 (BouncyCastle 1.80) | Dedicated signing key pair, signature = sign(ciphertext \|\| conversationId \|\| createdAt) |
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
- ✅ Zero message content in push notifications (opaque sync signal only — no conversationId, no sender name)
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
- ✅ **BouncyCastle 1.80**: `bcprov-jdk18on` registered as JCA provider at position 1 (`Security.removeProvider("BC")` + `Security.insertProviderAt()`)
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

- ✅ **PQXDH hybrid key exchange**: X25519 + ML-KEM-1024 post-quantum key agreement via BouncyCastle 1.80 lightweight API (no JCA provider registration)
- ✅ **ML-KEM-1024 identity key pair**: `generateMLKEMIdentityKeyPair()`, `mlkemEncaps()`, `mlkemDecaps()`, `deriveRootKeyPQXDH(ssClassic, ssPQ)` for hybrid root key derivation
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

### V3.4.1 One-Shot Photos, Restore Redesign & QR Fingerprint

- ✅ **One-shot ephemeral photos**: view-once images for both sender and receiver; 2-phase secure deletion (immediate DB flag + delayed file deletion)
- ✅ **Anti-navigation bypass**: `flagOneShotOpened()` DAO flags DB immediately on click; physical file deleted after 5s coroutine delay; no `Handler.postDelayed` vulnerability
- ✅ **One-shot file metadata**: `FILE|url|key|iv|fileName|fileSize|1` format; `oneShotOpened` column in Room DB
- ✅ **QR code fingerprint**: fingerprint encoded as SHA-256 hex (64 ASCII chars) for QR; avoids Unicode emoji encoding mismatches
- ✅ **QR fingerprint scanner**: uses `CustomScannerActivity` (same as contact invitation); hex comparison with `ignoreCase`; auto-verify on match
- ✅ **`getSharedFingerprintHex()` method**: raw SHA-256 hex of sorted concatenated public keys (deterministic, encoding-safe)
- ✅ **Restore screen BIP-39 grid**: 24 `AutoCompleteTextView` cells with BIP-39 autocomplete (2048 words); replaces single text field
- ✅ **PIN recovery**: forgot PIN flow via mnemonic phrase verification
- ✅ **Send confirmation dialog**: user confirms before sending files
- ✅ **DB version 17**: added `oneShotOpened` column to MessageLocal entity (fallbackToDestructiveMigration)

### V3.4.1 Security Audit & Hardening (42+ vulnerabilities fixed)

#### Firebase Rules Hardening
- ✅ **Write-once signing keys**: `/signing_keys/{hash}` now enforced `!data.exists()` — prevents key overwrite attacks
- ✅ **Write-once ML-KEM keys**: `/mlkem_keys/{hash}` now enforced `!data.exists()` — prevents post-quantum key replacement
- ✅ **Write-once inbox**: `/inbox/{hash}/{convId}` now enforced `!data.exists()` — prevents contact request replay/overwrite
- ✅ **senderUid validation**: `senderUid.length === 32` enforced in Firebase rules (HMAC-SHA256 truncated to 128 bits = 32 hex chars)
- ✅ **ciphertext validation**: non-empty, max 65536 characters enforced in rules
- ✅ **IV validation**: non-empty, max 100 characters enforced in rules
- ✅ **createdAt validation**: `<= now + 60000` (1 minute clock skew tolerance) — prevents timestamp spoofing

#### Crypto Memory Zeroing
- ✅ **HKDF IKM zeroing**: `CryptoManager.hkdfExtractExpand()` now calls `ikm.fill(0)` after use
- ✅ **HKDF PRK zeroing**: `DoubleRatchet.hkdfExpand()` now zeros both `prk` and `expandInput` after use
- ✅ **Mnemonic encode zeroing**: `MnemonicManager.privateKeyToMnemonic()` now zeros `data`, `checksum`, and clears `bits` StringBuilder
- ✅ **Mnemonic decode zeroing**: `MnemonicManager.mnemonicToPrivateKey()` now zeros `allBytes` and clears `bits` StringBuilder
- ✅ **PQXDH input validation**: `deriveRootKeyPQXDH()` now requires both inputs to be exactly 32 bytes
- ✅ **ConversationId separator**: `deriveConversationId()` uses `"|"` separator between keys to prevent collision between `(AB, C)` and `(A, BC)`

#### UI Security
- ✅ **FLAG_SECURE**: `WindowManager.LayoutParams.FLAG_SECURE` on `MainActivity`, `LockScreenActivity`, and `RestoreFragment` — blocks screenshots, screen recording, and task switcher preview
- ✅ **Mnemonic masking**: forgot-PIN mnemonic dialog input uses `TYPE_TEXT_VARIATION_PASSWORD` — prevents shoulder surfing
- ✅ **Dialog FLAG_SECURE**: mnemonic dialog window also has `FLAG_SECURE` applied
- ✅ **Autocomplete threshold**: BIP-39 autocomplete threshold raised from 1 → 3 characters — reduces information leakage during restore
- ✅ **RestoreFragment memory wipe**: all 24 word inputs are wiped in `onDestroyView()` before clearing

#### Deep Link Hardening
- ✅ **parseInvite() rewrite**: complete rewrite with parameter whitelist (`v`, `x25519`, `mlkem`, `name`)
- ✅ **Length limits**: x25519 key max 60 chars, mlkem key max 2000 chars, name max 200 raw / 100 decoded chars
- ✅ **Duplicate parameter rejection**: prevents parameter pollution attacks
- ✅ **Control character rejection**: rejects keys and names containing control characters
- ✅ **Total link size limit**: 4000 character maximum
- ✅ **Base64 validation**: x25519 and mlkem keys validated as proper Base64
- ✅ **ML-KEM size validation**: decoded ML-KEM public key must be 1500–1650 bytes

#### Clipboard Security
- ✅ **EXTRA_IS_SENSITIVE flag**: clipboard data marked as sensitive (Android 13+ redacts from clipboard previews)
- ✅ **30-second auto-clear**: clipboard automatically cleared after 30 seconds via `Handler.postDelayed`

#### Storage Security
- ✅ **SecureFileManager**: new utility class for secure file deletion (2-pass overwrite: random data + zeros, `fd.sync()` after each pass)
- ✅ **File bytes zeroing**: `saveFileLocally()` calls `fileBytes.fill(0)` after writing to disk
- ✅ **Secure one-shot deletion**: one-shot files use `SecureFileManager.secureDelete()` instead of simple `File.delete()`
- ✅ **Stale conversation cleanup**: `deleteStaleConversation()` securely wipes the conversation files directory
- ✅ **Expired message file wipe**: `deleteExpiredMessages()` securely deletes associated files before removing DB entries

#### Firebase Input Validation
- ✅ **FirebaseRelay guards**: `sendMessage()` has `require()` checks on conversationId (non-blank), ciphertext (non-blank), iv (non-blank), senderUid (exactly 32 chars), createdAt (> 0)
- ✅ **Cloud Function validation**: `functions/index.js` validates senderUid against `/^[0-9a-f]{32}$/` regex and conversationId format (hex, max 128 chars)
- ✅ **FCM payload minimized**: push notification data reduced to `{type: "new_message", sync: "1"}` — no conversationId or senderDisplayName leaked
- ✅ **Generic notification**: `MyFirebaseMessagingService` shows "Nouveau message reçu" (no sender name, no conversation metadata)

#### Manifest & Network Security
- ✅ **usesCleartextTraffic=false**: enforced on `<application>` — blocks all unencrypted HTTP traffic
- ✅ **filterTouchesWhenObscured**: enabled on `MainActivity` and `LockScreenActivity` — prevents tapjacking/overlay attacks

#### Firebase Storage Rules
- ✅ **Owner-only delete**: storage.rules now requires `resource.metadata['uploaderUid'] == request.auth.uid` for delete operations
- ✅ **Upload metadata**: `uploaderUid` metadata tag required on write, must match `request.auth.uid`
- ✅ **Upload with metadata**: `FirebaseRelay.uploadEncryptedFile()` now attaches `StorageMetadata` with `uploaderUid`

---

## Threat Model

### Adversary tiers

| Tier | Description | Examples |
|------|-------------|----------|
| **T1 — Curious individual** | Physical access to unlocked phone or cloud backup | Roommate, partner, lost device finder |
| **T2 — Network observer** | Can intercept and record traffic (no modification) | ISP, café Wi-Fi operator, workplace proxy |
| **T3 — Active network attacker** | Can intercept, modify, and inject traffic in real time | Rogue access point, compromised CDN, nation-state MITM |
| **T4 — Service operator** | Full read/write access to Firebase RTDB, Storage, Auth | Firebase admin, Google employee, law enforcement with subpoena |
| **T5 — Device compromise** | Root access or runtime exploit on the target device | Spyware (Pegasus-class), rooted device with Frida, physical forensic extraction |
| **T6 — Quantum adversary** | Future large-scale fault-tolerant quantum computer | "Harvest now, decrypt later" strategy targeting X25519/ECDH |

### What SecureChat protects against

| Threat | Protection | Residual risk |
|--------|-----------|---------------|
| **T1 — Screen access** | FLAG_SECURE blocks screenshots/task preview; App Lock with PBKDF2 PIN (600K iter) + biometric; 5s auto-lock | Physical coercion; shoulder surfing during mnemonic backup |
| **T1 — Backup extraction** | `android:allowBackup="false"`; Room DB encrypted with SQLCipher (AES-256-CBC, Keystore-backed passphrase) | Rooted device with Keystore extraction capability (→ T5) |
| **T2 — Traffic sniffing** | TLS 1.3 to Firebase; optional Tor routing (SOCKS5 + TUN VPN); all message content is E2E encrypted (AES-256-GCM or ChaCha20-Poly1305) | Traffic analysis (timing, frequency, packet sizes) partially mitigated by dummy traffic + message padding |
| **T3 — MITM** | E2E encryption with Double Ratchet (keys never transit the wire); emoji fingerprint verification (96-bit shared secret) + QR fingerprint scan for identity binding | Unverified contacts are vulnerable to initial MITM during key exchange (user must verify fingerprints in person) |
| **T4 — Server compromise** | Firebase sees only: encrypted ciphertext, HMAC-hashed senderUid, timestamps, conversation IDs, ephemeral DH keys, KEM ciphertext (all opaque). Message content is never plaintext on server. Delete-after-delivery removes ciphertext post-decryption. | Metadata: Firebase knows who talks to whom (conversation participants) and when (timestamps). Full metadata privacy requires onion routing / mix network. |
| **T4 — Key replacement** | Write-once Firebase rules on `/signing_keys/`, `/mlkem_keys/`, `/inbox/` prevent server-side key overwrite. Ed25519 signatures on every message detect forgery. | A compromised server could serve a wrong key to a NEW contact (TOFU model). Fingerprint verification mitigates this. |
| **T5 — Device compromise** | Private keys in EncryptedSharedPreferences (Keystore-backed, StrongBox when available); intermediate key material zeroed after use; SecureFileManager for secure file deletion (2-pass overwrite) | If an attacker gains root + Keystore access, they can extract identity keys. Double Ratchet PFS limits exposure to future messages after the next DH ratchet step (post-compromise healing). |
| **T5 — Memory forensics** | All key material zeroed explicitly (`fill(0)`) after use; message padding prevents plaintext length inference; `FLAG_SECURE` prevents screenshot/screen capture | JVM garbage collection may retain copies before zeroing; Frida-class runtime instrumentation can intercept keys before zeroing |
| **T6 — Quantum attack** | PQXDH hybrid: X25519 + ML-KEM-1024 (NIST FIPS 203). SPQR re-encapsulation every 10 messages refreshes the PQ secret in the root key. Even if X25519 is broken, ML-KEM-1024 protects the session. | ML-KEM-1024 is a lattice-based scheme that has not yet been deployed at scale for decades; cryptanalytic advances remain possible. Hybrid design ensures we fall back to classical security if ML-KEM is broken. |

### What SecureChat does NOT protect against

| Threat | Reason |
|--------|--------|
| **Compromised sender/receiver device** (T5) | If the attacker has full root + Keystore on BOTH endpoints, they can read messages as the user does. E2E encryption protects the channel, not the endpoints. |
| **Rubber hose cryptanalysis** | Physical coercion to reveal PIN, mnemonic, or unlock phone is outside the crypto threat model. |
| **Metadata correlation by Firebase** (T4) | Firebase sees conversation participant UIDs, message timestamps, and connection patterns. senderUid is HMAC-hashed per conversation, but the server can still correlate via Firebase Auth UIDs. Full metadata privacy requires a decentralized transport. |
| **Ephemeral bypass on modified client** | Ephemeral message deletion is client-enforced. A modified APK can skip deletion. Server-enforced TTL would require Cloud Functions. |
| **Key revocation** | No formal key revocation mechanism exists yet. If an identity key is compromised, there is no automated way to notify contacts that the key is invalid. Planned for V3.6+. |

### Design principles

1. **Defense in depth** — Multiple independent layers (E2E encryption, DB encryption, app lock, FLAG_SECURE, Keystore/StrongBox) so a single failure does not compromise everything.
2. **Hybrid post-quantum** — Classical + PQ in parallel. Both must be broken to compromise the session. If either is broken, the other still provides security.
3. **Forward secrecy** — Double Ratchet with X25519 DH ratchet ensures that compromise of the current key does not expose past messages. Each direction change generates new keys.
4. **Post-compromise healing** — DH ratchet + SPQR re-encapsulation ensure that after a temporary device compromise, security recovers automatically at the next ratchet step (no user action required).
5. **Zero trust on transport** — All security guarantees hold even if Firebase is fully compromised. Firebase is treated as an untrusted message relay.
6. **Minimal metadata** — `senderPublicKey` removed from wire format; `messageIndex` encrypted inside payload; `senderUid` HMAC-hashed per conversation; message padding hides content length.
7. **Fail-safe defaults** — Push notifications off by default; FLAG_SECURE on all screens; auto-lock 5s; no `allowBackup`; `usesCleartextTraffic=false`.
