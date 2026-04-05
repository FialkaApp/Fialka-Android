<div align="right">
  <a href="../fr/CHANGELOG.md">🇫🇷 Français</a> | 🇬🇧 English
</div>

<div align="center">

# 🗺 Changelog & Roadmap

<img src="https://img.shields.io/badge/Current-V4.1.0--alpha-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/versionCode-11-9C4DCC?style=for-the-badge" />

</div>

---

<details>
<summary><h2>✅ V1 — Core</h2></summary>


> Foundations: E2E encryption, contacts via QR, persistent conversations.

- [x] E2E Encryption (X25519 ECDH + AES-256-GCM)
- [x] Perfect Forward Secrecy (Double Ratchet X25519)
- [x] QR Code (generation + scanning)
- [x] Manual public key input
- [x] Contact requests (sending, inbox notification, accept/reject)
- [x] Pending conversations (pending → accepted)
- [x] Real-time acceptance notification
- [x] Profile (editable nickname, copy/share key)
- [x] Full account deletion
- [x] WhatsApp-like design
- [x] Anti-duplicate + anti-replay
- [x] Firebase TTL (7 days)
- [x] Crypto hardening (zeroing, mutex, atomic send)
- [x] Android 15 edge-to-edge support (targetSdk 35)
- [x] Automatic Firebase re-authentication after app kill
- [x] Unread messages badge on conversations list
- [x] "New messages" separator in chat (disappears after reading)
- [x] Real-time message reception on the conversations list
- [x] Opt-in FCM push notifications (Cloud Function + zero message content)
- [x] Settings screen (push ON/OFF, removable token)
- [x] Fingerprint emojis 96-bit (64 palette × 16 positions, anti-MITM)
- [x] Contact profile (fingerprint, manual verification, chat badge)
- [x] SQLCipher — Local Room database encryption (256-bit, EncryptedSharedPreferences)
- [x] Metadata hardening — senderPublicKey + messageIndex removed from Firebase (trial decryption)
- [x] App Lock — 6-digit PIN + opt-in biometric unlock
- [x] Profile improvement — Cards, avatar header, danger zone, modernized UX
- [x] Settings improvement — Lock / notifications / security sections
- [x] Ephemeral messages — Timer on send + on read, duration synced on Firebase
- [x] Dark mode — Full DayNight theme, adaptive colors
- [x] Auto-lock timeout — Configurable (5s → 5min), default 5 seconds
- [x] Fingerprint sub-screen — Visualization + dedicated verification
- [x] Contact profile redesign — Conversation hub (ephemeral, fingerprint, danger zone)
- [x] 5 UI themes — Midnight, Hacker, Phantom (default), Aurora, Daylight + visual selector
- [x] Full animations — Navigation transitions, animated bubbles, cascade list, scrollable toolbar

</details>

---

<details>
<summary><h2>✅ V2 — Crypto Upgrade</h2></summary>


> Full Double Ratchet X25519, replaced P-256 with Curve25519.

- [x] **Full Double Ratchet X25519** — DH ratchet + KDF chains + automatic healing
- [x] **Native X25519** — Curve25519 (API 33+), replaces P-256
- [x] **Initial chains** — Both sides can send immediately after acceptance
- [x] **Natural ephemeral exchange** — Via real messages, no bootstrap message

</details>

---

<details>
<summary><h2>✅ V2.1 — Account Lifecycle</h2></summary>


> BIP-39 backup, restore, full deletion, dead account detection.

- [x] **BIP-39 mnemonic phrase** — X25519 private key backup in 24 words (256 bits + 8-bit SHA-256 checksum)
- [x] **Backup after creation** — Dedicated screen shows 24 words in 3 columns (confirmation checkbox)
- [x] **Account restore** — Input 24 words + nickname → restore private key → derive public key (DH base point u=9)
- [x] **Full account deletion** — Cleans Firebase: profile `/users/{uid}`, `/inbox/{hash}`, `/conversations/{id}`
- [x] **Old profile cleanup** — `removeOldUserByPublicKey()` removes the orphaned old `/users/` node
- [x] **Dead conversation detection** — Clear AlertDialog ("Conversation deleted") with delete option
- [x] **Contact re-invitation** — Stale local contact cleaned up to allow re-invitation
- [x] **Auto-detection on receipt** — Inbox listener checks stale conversations → auto cleanup
- [x] **Conversation Firebase rules** — `.read` and `.write` restricted at `$conversationId` level

</details>

---

<details>
<summary><h2>✅ V2.2 — UI Modernization</h2></summary>


> 5 themes, full animations, CoordinatorLayout, zero hardcoded colors.

- [x] **5 themes** — Midnight (teal/cyan), Hacker (AMOLED Matrix green), Phantom (anthracite purple, default), Aurora (amber/orange), Daylight (clean light blue)
- [x] **22 color attributes** — Full `attrs.xml`: toolbar, bubbles, avatars, badges, input bar, surfaces, dividers
- [x] **Theme selector** — MaterialCardView grid with color preview and selection indicator
- [x] **Dynamic bubbles** — Sent/received bubble colors by theme via `backgroundTint` (white base + tint)
- [x] **Themed avatars/badges** — Avatars, unread badges, FAB, send button colors adapt to theme
- [x] **Themed toolbar** — All toolbars (10+) use `?attr/colorToolbarBackground`, elevation 0dp
- [x] **Navigation transitions** — Right/left slide (forward/back), up/down slide (modals), fade (onboarding)
- [x] **Bubble animations** — Entrance from right (sent) / left (received), new messages only
- [x] **Animated list** — Fall-in cascade on the conversations list (8% delay)
- [x] **CoordinatorLayout** — Toolbar collapses on scroll + snaps back (scroll|enterAlways|snap)
- [x] **Auto-hide FAB** — `HideBottomViewOnScrollBehavior` hides the FAB on scroll
- [x] **Zero hardcoded colors** — All UI colors → `?attr/` (theme-aware)

</details>

---

<details>
<summary><h2>✅ V3.0 — Security Hardening</h2></summary>


> Complete security hardening: reinforced encryption, traffic analysis countermeasures, E2E file sharing.

### 🛡️ Build & Obfuscation
- [x] **R8/ProGuard** — `isMinifyEnabled=true`, `isShrinkResources=true`, repackaging in release builds
- [x] **Log stripping** — `Log.d()`, `Log.v()`, `Log.i()` removed by ProGuard (`assumenosideeffects`)

### 🔐 Crypto & Metadata
- [x] **Delete-after-delivery** — Ciphertext removed from Firebase RTDB immediately after successful decryption
- [x] **Message padding** — Plaintext padded to fixed-size buckets (256/1K/4K/16K bytes) with 2-byte header + SecureRandom fill
- [x] **senderUid HMAC** — `senderUid` = HMAC-SHA256(conversationId, UID) truncated to 128 bits — Firebase cannot correlate the same user across conversations
- [x] **PBKDF2 PIN** — PBKDF2-HMAC-SHA256 (600K iterations, 16-byte salt); 6-digit PIN enforced

### 👻 Traffic Analysis Countermeasures
- [x] **Dummy traffic** — Periodic cover messages (45–120s random interval) via real Double Ratchet — indistinguishable from real messages on the wire
- [x] **Configurable toggle** — Enable/disable in Settings → Security → Cover Traffic
- [x] **Opaque prefix** — Dummy marker uses non-printable control bytes (`\u0007\u001B\u0003`)

### 📎 E2E File Sharing
- [x] **Per-file encryption** — Random AES-256-GCM key per file, encrypted client-side
- [x] **Firebase Storage** — Upload encrypted, metadata (URL + key + IV + name + size) sent via the ratchet
- [x] **Auto-receive** — Download + local decryption + app-private storage; Storage file deleted after delivery
- [x] **Attach UI** — 📎 button in chat, file picker, 25 MB limit, tap to open
- [x] **Storage rules** — Authenticated-only access, 50 MB max, restricted to `/encrypted_files/` path

### 🗄️ Database
- [x] **Room indexes** — Composite indexes: messages(conversationId, timestamp), messages(expiresAt), conversations(accepted), contacts(publicKey)
- [x] **Double-listener guard** — `processedFirebaseKeys` prevents ratchet desync when 2 listeners process the same message

</details>

---

<details>
<summary><h2>✅ V3.1 — Settings Redesign & PIN Upgrade</h2></summary>


> Signal/Telegram-style settings, 6-digit PIN, Privacy sub-screen, PIN performance.

### ⚙️ Settings
- [x] **Full redesign** — Signal-like hierarchy: General (Appearance, Notifications), Privacy, Security, About
- [x] **Privacy sub-screen** — Ephemeral messages, delete-after-delivery, dummy traffic grouped together
- [x] **PrivacyFragment** — Dedicated fragment with integrated navigation
- [x] **About section** — Dynamic version, encryption info, GPLv3 license

### 🔐 PIN Security
- [x] **6-digit PIN** — Replaced 4-digit code, 6 dots on lock screen
- [x] **Legacy removal** — Removed SHA-256 support and 4-digit backward compatibility
- [x] **PIN coroutines** — PBKDF2 verification (600K iterations) on `Dispatchers.Default`, zero UI freeze
- [x] **Cached EncryptedSharedPreferences** — Double-checked locking, no repeated Keystore init
- [x] **Single verification** — Check only at 6th digit (no intermediate checks)

</details>

---

<details>
<summary><h2>✅ V3.2 — Ed25519 Message Signing</h2></summary>


> Per-message Ed25519 signatures, ✅/⚠️ badge, Firebase rules hardening, signing key cleanup.

### ✍️ Message Signing
- [x] **Ed25519 (BouncyCastle 1.78.1)** — Dedicated signing key pair (separate from X25519)
- [x] **Signed data** — `ciphertext_UTF8 || conversationId_UTF8 || createdAt_bigEndian8` — anti-forgery + anti-replay
- [x] **JCA Provider** — `Security.removeProvider("BC")` + `insertProviderAt(BouncyCastleProvider(), 1)` in Application.onCreate()
- [x] **Key storage** — Private key in EncryptedSharedPreferences; public key at `/signing_keys/{SHA256_hash}` and `/users/{uid}/signingPublicKey`
- [x] **Verification on receive** — Fetches Ed25519 public key by identity hash, badge ✅ (valid) or ⚠️ (invalid/missing)
- [x] **Client timestamp** — `createdAt` = `System.currentTimeMillis()` (not `ServerValue.TIMESTAMP`) for signature consistency

### 🛡️ Firebase Hardening
- [x] **Scoped participants** — `/conversations/$id/participants` readable only by members (no longer by all authenticated users)
- [x] **Signing key cleanup** — `/signing_keys/{hash}` deleted on account deletion

</details>

---

<details>
<summary><h2>✅ V3.3 — Material 3, Tor Integration, Attachment UX & Log Hardening</h2></summary>


> Full Material Design 3 migration, Tor integration (SOCKS5 + VPN TUN), Session-style inline attachment icons, Android 13+ permissions, Firebase & log hardening.

### 🎨 Material Design 3
- [x] **M2 → M3 Migration** — All 5 themes migrated from `Theme.MaterialComponents` to `Theme.Material3.Dark.NoActionBar` / `Theme.Material3.Light.NoActionBar`
- [x] **Full M3 color roles** — Added `colorPrimaryContainer`, `colorOnPrimary`, `colorSecondary`, `colorSurfaceVariant`, `colorOutline`, `colorSurfaceContainerHigh/Medium/Low`, `colorError`, etc. across all 5 themes
- [x] **M3 TextInputLayout** — Migrated to `Widget.Material3.TextInputLayout.OutlinedBox` (Onboarding, Restore, AddContact)
- [x] **M3 Buttons** — Migrated to `Widget.Material3.Button.TextButton` / `OutlinedButton` (TorBootstrap, Onboarding, Profile)
- [x] **Predictive back gesture** — `enableOnBackInvokedCallback="true"` in manifest for Android 13+

### 📎 Inline Attachment Icons (Session-style)
- [x] **BottomSheet replaced** — 3 options (File 📁, Photo 🖼, Camera 📷) appear as animated vertical icons above the + button
- [x] **Slide-up + fade-in animation** — Icons slide up with fade, + button rotates to × (45° rotation)
- [x] **Dismiss overlay** — Full-screen transparent view to dismiss icons on tap anywhere
- [x] **ic_add.xml** — New vector + icon for attachment button

### 📱 Android 13+ Permissions
- [x] **READ_MEDIA_IMAGES** — Android 13+ permission for photo access
- [x] **READ_MEDIA_AUDIO** — Android 13+ permission for audio file access
- [x] **READ_EXTERNAL_STORAGE** — Fallback with `maxSdkVersion="32"` for Android 12 and below
- [x] **Permission launchers** — Full permission request logic with denial dialog

### 🔥 Firebase Fixes
- [x] **Firebase sign-out** — Removed `database.goOnline()` after `auth.signOut()` (fixes Firebase permission error)
- [x] **Firebase locale** — Replaced `useAppLanguage()` with explicit `setLanguageCode(Locale.getDefault().language)` (fixes X-Firebase-Locale null)
- [x] **Double signing key publish** — `signingKeyPublished` flag + `markSigningKeyPublished()` eliminates redundant publish between OnboardingViewModel and ConversationsViewModel

### 🛡️ Log Hardening
- [x] **Complete ProGuard stripping** — Added `Log.w()`, `Log.e()`, `Log.wtf()` to `assumenosideeffects` (on top of d/v/i) — total log suppression in release
- [x] **Log sanitization** — Removed Firebase UIDs, key hashes and key prefixes from debug log messages
- [x] **Zero sensitive data** — `FirebaseRelay.kt` and `ChatRepository.kt` no longer print Firebase paths or identifiers in logs

### 🧅 Tor Integration
- [x] **TorManager.kt** — Singleton with `StateFlow<TorState>` (`IDLE`, `STARTING`, `BOOTSTRAPPING(%)`, `CONNECTED`, `ERROR`, `DISCONNECTED`)
- [x] **TorVpnService.kt** — VPN TUN service → hev-socks5-tunnel → SOCKS5 :9050 → Tor → Internet
- [x] **libtor.so + libhev-socks5-tunnel.so** — Native arm64-v8a binaries embedded
- [x] **Global ProxySelector** — All HTTP traffic routed via SOCKS5 `127.0.0.1:9050` when Tor enabled
- [x] **Conditional startup** — `FialkaApplication.onCreate()` starts Tor if enabled
- [x] **TorBootstrapFragment** — `startDestination` of nav graph, Tor/Normal choice on first launch
- [x] **Animated circular progress** — Real-time percentage, dynamic status text, pulse animation
- [x] **Respects all 5 themes** — Colors via `?attr/` from active theme
- [x] **Tor toggle** — ON/OFF in Settings → Security with manual reconnect
- [x] **Real-time status** — "Connected via Tor" / "Reconnecting..." / "Disconnected"
- [x] **Per-conversation dummy traffic** — Individual cover messages per active conversation

</details>

---

<details>
<summary><h2>✅ V3.4 — Post-Quantum & Device Security</h2></summary>


> Hybrid PQXDH (ML-KEM-1024 + X25519), StrongBox DeviceSecurityManager, QR deep link v2, independent fingerprint verification, ratchet desync fixes.

### 🔐 Post-Quantum Cryptography (PQXDH)
- [x] **ML-KEM-1024 (Kyber)** — Post-quantum encapsulation via BouncyCastle 1.80, dedicated encaps/decaps key pair
- [x] **Hybrid PQXDH** — Classic X25519 key exchange + ML-KEM-1024 encapsulation in parallel
- [x] **Deferred rootKey upgrade** — Initial conversation starts classic X25519-only; rootKey is upgraded with ML-KEM secret on the first message (no bootstrap message)
- [x] **kemCiphertext in first message** — ML-KEM ciphertext sent once, in the first Firebase message of the conversation
- [x] **QR deep link v2** — Format `fialka://contact?key=<X25519>&kem=<ML-KEM-1024-pubKey>&name=<displayName>` — ML-KEM key encoded in QR
- [x] **Auto-fill name from QR** — Contact nickname auto-populated from QR scan data

### 🛡️ Device Security
- [x] **DeviceSecurityManager** — StrongBox hardware probe, MAXIMUM/STANDARD security levels
- [x] **StrongBox banner** — Visual indicator in Settings → About based on detected security level
- [x] **displayName hidden** — Nickname no longer stored on Firebase (`storeDisplayName` → no-op), removed from Firebase rules
- [x] **Settings reorganized** — Security card moved to About section, encryption text updated

### 🔧 Critical Fixes
- [x] **PQXDH desync fix** — `syncExistingMessages()` on contact acceptance to properly trigger PQXDH init
- [x] **Delete-after-failure** — Failed decryption messages cleaned from Firebase (prevents infinite error loop)
- [x] **lastDeliveredAt** — New field on Conversation entity for Firebase message lower-bound filtering (prevents re-processing)
- [x] **Dual-listener fix** — `ConcurrentHashMap.putIfAbsent()` + LRU eviction to prevent race conditions on Firebase listeners
- [x] **Decryption-on-accept fix** — Responder now triggers existing message sync on acceptance

### 🔏 Fingerprint Verification
- [x] **Independent verification** — Each user verifies on their own side (local Room state only, no state sync)
- [x] **Firebase events** — Event-based notification `fingerprintEvent: "verified:<timestamp>"` (push only, no state sync)
- [x] **System messages** — Info message in chat when a participant verifies/un-verifies
- [x] **Clickable link** — "View fingerprint" in system messages navigates to the fingerprint screen
- [x] **Verify/un-verify toggle** — Button in FingerprintFragment to mark verified or remove verification
- [x] **Updated badges** — ✅ Verified / ⚠️ Unverified (replaces old green/orange format)

### 🗄️ Database
- [x] **Room v16** — Migration v15→v16: added `lastDeliveredAt` column on Conversation
- [x] **Version 3.4.0** — `versionCode 5`, `versionName "3.4.0"`

</details>

---

<details>
<summary><h2>✅ V3.4.1 — One-Shot Photos, Restore Redesign & QR Fingerprint</h2></summary>


> One-shot ephemeral photos, redesigned restore screen with BIP-39 grid, QR code fingerprint verification, UI improvements.

### 📸 One-Shot Ephemeral Photos
- [x] **One-shot send** — "Ephemeral photo" option: photo can only be viewed once by both the recipient AND the sender
- [x] **2-phase secure deletion** — Phase 1: immediate `oneShotOpened=1` flag in Room (prevents re-viewing); Phase 2: physical file deletion after 5 seconds (delay for viewer app to load)
- [x] **Anti-navigation bypass** — DB flag is set immediately on click (not in `Handler.postDelayed`), preventing back-navigation circumvention
- [x] **Sender UI** — 4 states: one-shot expired (🔥 locked, grayed), one-shot ready (🔥 "Open once"), normal file, text message
- [x] **Receiver UI** — 6 states with integrated one-shot handling in received bubbles
- [x] **Send indicator** — ✓ check icon confirmation in sent bubbles

### 🔑 Redesigned Restore Screen
- [x] **Professional BIP-39 grid** — 24 `AutoCompleteTextView` cells in 3×8 grid with numbering
- [x] **BIP-39 autocomplete** — Each cell suggests from 2048 BIP-39 words with 1-character threshold
- [x] **Auto-advance** — Word selection or Enter automatically moves to the next cell
- [x] **Focus coloring** — Green = valid BIP-39 word, red = invalid word
- [x] **Word counter** — Real-time "X / 24 words" display
- [x] **Visual validation** — Invalid words highlighted in red on restore attempt

### 🔏 QR Code Fingerprint
- [x] **Emoji/QR toggle** — Animated toggle (180° rotation + fade) between 16-char emojis and QR code
- [x] **QR SHA-256 hex** — QR encodes the fingerprint as SHA-256 hex (64 ASCII chars, not emojis) to avoid Unicode encoding issues
- [x] **QR fingerprint scanner** — Uses the same `CustomScannerActivity` as contact invitation (torch, free orientation)
- [x] **Automatic verification** — QR scan → hex comparison `ignoreCase` → ✅ match dialog or ❌ MITM warning dialog
- [x] **`getSharedFingerprintHex()` method** — New CryptoManager method returning raw SHA-256 hex of sorted public keys

### 🎨 UI Improvements
- [x] **Send confirmation dialog** — Confirmation before sending files
- [x] **Progress bar** — File upload/download indicator
- [x] **Retry button** — Retry send on failure
- [x] **Protocol display** — "PQXDH · X25519 + ML-KEM-1024 · AES-256-GCM · Double Ratchet" shown in contact profile
- [x] **Timestamp fix** — Fixed timestamp display in message bubbles
- [x] **maxWidth fix** — Corrected maximum bubble width
- [x] **29 layout audit** — Complete review and fixes of all 29 layout files
- [x] **Forgot PIN** — PIN recovery flow via mnemonic phrase

### 🗄️ Database
- [x] **Room v17** — Migration v16→v17: added `oneShotOpened` column on MessageLocal
- [x] **`flagOneShotOpened()`** — New DAO query: `UPDATE messages SET oneShotOpened = 1 WHERE localId = :messageId`
- [x] **Version 3.4.1** — `versionCode 6`, `versionName "3.4.1"`

### 🛡️ Security Audit (42+ vulnerabilities fixed)
- [x] **Firebase rules write-once** — `/signing_keys/{hash}`, `/mlkem_keys/{hash}`, `/inbox/{hash}/{convId}` now enforce `!data.exists()` — prevents key overwrite and contact request replay
- [x] **Firebase rules validation** — `senderUid.length === 32`, ciphertext non-empty + max 65536, iv non-empty + max 100, `createdAt <= now + 60000`
- [x] **HKDF memory zeroing** — `hkdfExtractExpand()` zeros IKM, `hkdfExpand()` zeros PRK + expandInput after use
- [x] **Mnemonic memory zeroing** — `privateKeyToMnemonic()` and `mnemonicToPrivateKey()` zero all intermediate byte arrays and clear StringBuilder
- [x] **PQXDH input validation** — `deriveRootKeyPQXDH()` requires both inputs exactly 32 bytes
- [x] **ConversationId separator** — `deriveConversationId()` uses `"|"` separator to prevent key concatenation collisions
- [x] **FLAG_SECURE** — Applied on `MainActivity`, `LockScreenActivity`, `RestoreFragment`, and mnemonic dialog — blocks screenshots, screen recording, task switcher
- [x] **Mnemonic masking** — Forgot-PIN mnemonic input uses `TYPE_TEXT_VARIATION_PASSWORD`
- [x] **Autocomplete threshold** — BIP-39 autocomplete threshold raised from 1 → 3 characters
- [x] **RestoreFragment wipe** — All 24 word inputs wiped in `onDestroyView()`
- [x] **Deep link hardening** — Complete rewrite of `parseInvite()`: parameter whitelist, length limits, duplicate rejection, control char rejection, Base64 validation, 4000-char max
- [x] **ML-KEM validation** — Client-side ML-KEM public key validation (length < 2500, Base64 decode, decoded size 1500–1650 bytes)
- [x] **Clipboard security** — `EXTRA_IS_SENSITIVE` flag + 30-second auto-clear via `Handler.postDelayed`
- [x] **SecureFileManager** — New utility: 2-pass overwrite (random data + zeros, `fd.sync()`) before `File.delete()`
- [x] **File bytes zeroing** — `saveFileLocally()` calls `fileBytes.fill(0)` after writing
- [x] **Secure one-shot delete** — One-shot files use `SecureFileManager.secureDelete()`
- [x] **Stale conversation wipe** — `deleteStaleConversation()` securely wipes conversation files directory
- [x] **Expired message wipe** — `deleteExpiredMessages()` securely deletes associated files first
- [x] **FirebaseRelay guards** — `sendMessage()` has `require()` on all fields (conversationId, ciphertext, iv, senderUid length, createdAt)
- [x] **Cloud Function validation** — Regex validation for senderUid (`/^[0-9a-f]{32}$/`) and conversationId format
- [x] **Opaque FCM payload** — Push data reduced to `{type: "new_message", sync: "1"}` — zero metadata leakage
- [x] **Generic notification** — `MyFirebaseMessagingService` shows "Nouveau message reçu" (no sender name, no conversation ID)
- [x] **usesCleartextTraffic=false** — Enforced on `<application>` — blocks all unencrypted HTTP
- [x] **filterTouchesWhenObscured** — Enabled on `MainActivity` and `LockScreenActivity` — tapjacking protection
- [x] **Storage rules owner-only delete** — `resource.metadata['uploaderUid'] == request.auth.uid` required for delete
- [x] **Upload metadata** — `uploadEncryptedFile()` attaches `uploaderUid` StorageMetadata

</details>

---

<details>
<summary><h2>✅ V3.5 — SPQR, ChaCha20-Poly1305 & Threat Model</h2></summary>


> Post-quantum Triple Ratchet (SPQR), alternative ChaCha20-Poly1305 cipher, documented threat model.

### 🔐 SPQR — Periodic PQ Re-encapsulation (Triple Ratchet)
- [x] **PQ Ratchet Step** — New `DoubleRatchet.pqRatchetStep()` function: mixes a fresh ML-KEM secret into rootKey via HKDF (info: `Fialka-SPQR-pq-ratchet`)
- [x] **Re-encapsulation interval** — `PQ_RATCHET_INTERVAL = 10` messages: every 10 messages, the sender performs ML-KEM encaps and upgrades rootKey
- [x] **Sender-side** — In `sendMessage()`, when counter reaches 10 and PQXDH is initialized: `mlkemEncaps(remoteMlkemPublicKey)` → `pqRatchetStep(rootKey, ssPQ)` → new rootKey + `kemCiphertext` attached to message
- [x] **Receiver-side** — In `receiveMessage()`, detects `kemCiphertext` on an already PQ-initialized session: `mlkemDecaps()` → `pqRatchetStep()` → rootKey upgraded, counter reset
- [x] **Persistent counter** — New `pqRatchetCounter` field in `RatchetState` (Room entity), incremented on every sent message
- [x] **Compatibility** — Transparent mechanism: no extra wire field (reuses `kemCiphertext`), distinguished from initial PQXDH by `pqxdhInitialized` flag

### 🔒 ChaCha20-Poly1305 — Alternative Cipher
- [x] **`encryptChaCha()` / `decryptChaCha()`** — Full implementation via BouncyCastle `ChaCha20Poly1305` AEAD (12-byte nonce, 16-byte tag)
- [x] **Hardware AES detection** — `hasHardwareAes()` detects ARMv8 Crypto Extension; ChaCha20 is auto-selected on devices without hardware AES acceleration
- [x] **Dynamic selection** — Each message uses AES-256-GCM (default) or ChaCha20-Poly1305 based on sender hardware
- [x] **`cipherSuite` field** — New field in `FirebaseMessage` (0 = AES-GCM, 1 = ChaCha20); receiver decrypts with the correct algorithm automatically
- [x] **Backward compatibility** — Old messages without `cipherSuite` (= 0) are decrypted with AES-GCM as before

### 📋 Documented Threat Model
- [x] **SECURITY.md** — Added comprehensive Threat Model section with 6 adversary tiers (T1 curious → T6 quantum)
- [x] **Protection/residual matrix** — Detailed table of protections and residual risks per tier
- [x] **Documented limitations** — Explicit section "What Fialka does NOT protect against"
- [x] **Design principles** — 7 principles: defense in depth, hybrid PQ, forward secrecy, post-compromise healing, zero trust transport, minimal metadata, fail-safe defaults

### 🗄️ Database
- [x] **Room v18** — Migration v17→v18: added `pqRatchetCounter` column to RatchetState
- [x] **Version 3.5** — `versionCode 7`, `versionName "3.5"`

</details>

---

<details open>
<summary><h2>✅ V4.0 — Kill Firebase & Pure P2P via Tor</h2></summary>

> Complete Firebase removal, pure P2P infrastructure via Tor Hidden Services, 4-mode offline Mailbox system, "1 Seed → Everything" identity, ML-DSA-44 hybrid post-quantum handshake, delivery pipeline with status badges, 2026 UI refresh.
> `versionCode 8` · `versionName "4.0"` · Room v24

### 🔥 Kill Firebase — Pure P2P
- [x] **Complete Firebase removal** — Firebase BoM, Auth, RTDB, Storage, FCM, Cloud Functions fully removed. Zero central server dependency.
- [x] **TorTransport** — Binary frame protocol (magic `0xF1 0xA1`), 13 frame types (P2P + Mailbox commands), read/write with timeout
- [x] **P2PServer** — Incoming frame listener, dispatch for `TYPE_MESSAGE`, `TYPE_CONTACT_REQ`, `TYPE_KEY_BUNDLE`, `TYPE_CONTACT_REQ_RESPONSE`, etc.
- [x] **OutboxManager** — Retry loop with exponential backoff (max 50 attempts, 30-min cap), `DeliveryResult` enum (DIRECT / MAILBOX / QUEUED)
- [x] **UnifiedPush** — Notifications without Firebase dependency

### 🆔 Identity "1 Seed → Everything"
- [x] **Single Ed25519 seed** — Derives: Account ID, .onion address, X25519, ML-KEM-1024, ML-DSA-44, emoji fingerprint
- [x] **ML-DSA-44** — Post-quantum hybrid signature at every session handshake (Ed25519 + ML-DSA-44 simultaneously)
- [x] **AccountID** — `SHA3-256(Ed25519 pubkey)` → Base58 (e.g. `Fa3x...9Z`)
- [x] **Deterministic .onion address** — Derived from seed, stable across reinstalls
- [x] **SeedVerificationFragment** — 3-word confirmation after seed backup

### 🌐 Tor V4
- [x] **Guardian Project Tor** — `libtor.so` for real Tor v3 Hidden Services
- [x] **Multi-circuit** — Active circuits shown in real-time in the UI
- [x] **`killOrphanedTor()`** — Reads auth cookie BEFORE deletion (`AUTHENTICATE <hex>`), prevents orphaned daemons
- [x] **Anti-orphan messages** — `getPendingMessages()` includes messages stuck in `STATUS_SENDING`

### 📬 Mailbox System (4 modes)
- [x] **Direct P2P** — Direct communication, no relay
- [x] **Personal** — Personal `.onion` as async mailbox
- [x] **Private Node** — Private node with member whitelist
- [x] **Public Node** — Open public node
- [x] **MailboxServer** — Opaque encrypted blob storage, zero server-side decryption, OWNER access control, 7-day TTL
- [x] **MailboxClientManager** — 60s polling, `_fetching: StateFlow<Boolean>` to block UI during fetch
- [x] **`mailboxOnion` propagation** — `senderMailboxOnion` included in all outgoing messages; P2PServer updates `participantMailboxOnion` on receipt
- [x] **Cumulative stats** — `totalDeposited`, `totalFetched`, `totalDataProcessed` persisted in SharedPreferences (blobs deleted after delivery)
- [x] **Dashboard auto-refresh** — Every 30 seconds

### 📦 Delivery Pipeline with Status Badges
- [x] **`MessageLocal.deliveryStatus`** — `SENT(0)` / `MAILBOX(1)` / `FAILED(2)` / `PENDING(3)`
- [x] **`OutboxMessage.messageLocalId`** — Back-link to `MessageLocal` for status updates
- [x] **Delivery badges** — ✓ Sent · 📬 Mailbox · ⏳ Pending · ❌ Failed — displayed in outgoing message bubbles
- [x] **Resend button** — `FAILED` messages show a Resend button; `OutboxDao.resetRetryForMessage()` re-queues the attempt
- [x] **Fetch banner** — `fetchBanner` + `ProgressBar` in chat, input disabled during Mailbox fetch

### 🎨 2026 UI Refresh
- [x] **Settings overhaul** — `SettingsAdapter` + `SettingsViewModel`, search bar + category filters (Appearance, Notifications, Privacy, Security, Network, About)
- [x] **ThemeSelectorBottomSheet** — Visual 5-theme picker with live preview
- [x] **DurationSelectorBottomSheet** — Ephemeral message duration selector
- [x] **Conversations screen** — "MESSAGERIE CHIFFRÉE" eyebrow (9sp monospace) + 24sp bold title, 3dp colorPrimary accent strip on each item
- [x] **Add contact** — "CONNEXION SÉCURISÉE" hero section, visual OR divider, `ConstraintLayout` → `LinearLayout`
- [x] **Profile** — 108dp avatar ring, "GHOST IDENTITY" eyebrow, `Ed25519 · ML-DSA-44 · ML-KEM-1024` monospace caption
- [x] **Contact profile** — "NODE INFO" eyebrow, E2E badge pill with `bg_key_box` background

### 🔧 Critical P2P Pipeline Fixes
- [x] **Orphan message fix** — `getPendingMessages()` includes `STATUS_SENDING` stuck messages (recovery after crash/reboot)
- [x] **`resolveRecipientEd25519` fix** — Fallback to `publicKey` if `signingPublicKey` absent
- [x] **`sendContactRequest` fix** — Returns `Boolean`, full logging
- [x] **`killOrphanedTor` fix** — Reads auth cookie BEFORE deleting the file

### 🗄️ Database
- [x] **Room v24** — `deliveryStatus` on `MessageLocal`, `messageLocalId` + `fallbackOnion` on `OutboxMessage`, migrations v18→v24
- [x] **MailboxDatabase v1** — `MailboxBlob`, `MailboxMember`, `MailboxInvite` entities (MAILBOX mode only)
- [x] **Version 4.0** — `versionCode 8`, `versionName "4.0"`

</details>

---

<details open>
<summary><h2>✅ V4.0.2 — Fialka-Core Rust JNI Bridge</h2></summary>


> Native Rust cryptography migration — all crypto is now executed inside the `fialka-core` Rust library, exposed via a JNI bridge. Complete removal of BouncyCastle.

### 🦀 Rust JNI Bridge (Fialka-Core)
- [x] **30 JNI functions** implemented in `Fialka-Core/src/ffi/mod.rs` via the `jni = 0.21` crate
- [x] **`FialkaNative.kt`** — Kotlin bridge object, `System.loadLibrary("fialka_core")`, 30 `external fun`
- [x] **`libfialka_core.so`** — compiled with `cargo-ndk` for `arm64-v8a` (906 KB) and `x86_64` (984 KB)
- [x] **git submodule** — `Fialka-Core/` integrated as a submodule in Fialka-Android

### 🔐 Cryptographic migration
- [x] **`CryptoManager.kt`** — 100% migrated: all BouncyCastle calls replaced with `FialkaNative`
  - `identityDerive` → 8704-byte bundle (Ed25519, X25519, ML-KEM-1024, ML-DSA-44)
  - `encryptAes` / `decryptAes` → FialkaNative.encryptAes / decryptAes
  - `encryptChaCha` / `decryptChaCha` → FialkaNative (12-byte nonce, 16-byte tag)
  - `encryptFile` / `decryptFile` → FialkaNative (format: key[32] || iv[12] || CT)
  - `hmacSha256` → FialkaNative.hmacSha256
  - `hkdfZeroSalt` / key derivation → FialkaNative.hkdfZeroSalt
  - `ed25519Sign` / `ed25519Verify` → FialkaNative
  - `x25519Dh` → FialkaNative (strip JCA ASN.1 prefixes: X509 12 bytes, PKCS8 16 bytes)
  - `mlkemEncaps` / `mlkemDecaps` → FialkaNative
  - `mldsaSign` / `mldsaVerify` → FialkaNative
  - `deriveRootKeyPqxdh` → FialkaNative
  - `computeOnion` / `ed25519ToX25519Raw` → FialkaNative
- [x] **`TorTransport.kt`** — `signEd25519` / `verifyEd25519` migrated to FialkaNative
- [x] **`build.gradle.kts`** — `org.bouncycastle:bcprov-jdk18on:1.83` dependency removed
- [x] **Robolectric + JVM tests** removed (incompatible with JNI); tests migrated to `androidTest`

### 🧩 Cross-platform
- [x] Same Rust library used on Android (JNI) and on any other target (Windows/Linux/iOS via FFI)
- [x] **Version V4.0.2** — `versionCode 10`

</details>

---

<details open>
<summary><h2>🟡 V4.1.0-alpha — Ed25519 Contact Auth, Unit Tests & Migration Safety</h2></summary>

> `versionCode 11` · `versionName "4.1.0-alpha"` · Security fix + test coverage + UX safety

### 🔐 Security — Ed25519 Signature Verification on Contact Requests
- [x] **`P2PServer.handleContactRequest()`** — Now verifies the Ed25519 signature on incoming contact requests. Previously, `senderSigningPublicKey` was received but never verified — anyone could spoof an identity.
- [x] **Canonical signed data** — `senderPubKey(UTF-8) || 0x00 || conversationId(UTF-8) || createdAt(big-endian 8 bytes)` — domain-separated, deterministic
- [x] **`sendContactRequest()`** — Now signs the payload with `FialkaNative.ed25519Sign()` and includes `requestSignature` field
- [x] **Backward compatible** — Old clients without `requestSignature` are still accepted (graceful migration window)

### 🧪 Unit Tests — Crypto & Ratchet Coverage
- [x] **Robolectric 4.13** added as test dependency
- [x] **`CryptoManagerPureTest`** — 20 tests: `deriveConversationId` determinism/commutativity/uniqueness, `buildSignedData` timestamp encoding, `hashSenderUid` cross-conversation pseudonymity
- [x] **`RatchetSimulationTest`** — 16 tests: bidirectional ratchet, 500-step key uniqueness, SPQR interval=10, `pqRatchetStep` correctness, initiator/responder symmetry
- [x] **`DoubleRatchetTest`** — 2 JNI-dependent tests marked `@Ignore` (require `libfialka_core.so` — must run as instrumented tests)
- [x] **45 unit tests total, 0 failures**

### 🛡️ UX Safety — Destructive Migration Warning
- [x] **`FialkaDatabase.needsDestructiveMigration()`** — Detects when a Room schema upgrade would trigger `fallbackToDestructiveMigration` (DROP ALL TABLES)
- [x] **`MainActivity`** — Shows a blocking `AlertDialog` before any DB access on upgrade: user must explicitly confirm data loss or quit
- [x] **`FialkaDatabase.recordCurrentVersion()`** — Persists schema version in plain `SharedPreferences` after confirmed open

### 🛠 Dev Infrastructure
- [x] **`version.properties`** — Single source of truth for `VERSION_CODE` + `VERSION_NAME`; `build.gradle.kts` reads it automatically — only edit `version.properties` to bump version
- [x] **Version V4.1.0-alpha** — `versionCode 11`

</details>


> Replaced `security-crypto` with `FialkaSecurePrefs` (direct Keystore), fixed SQLCipher 4.14.1 crash, fixed `DurationSelectorBottomSheet` NPE, full transport reliability (5 fixes).

### 🔐 Security — FialkaSecurePrefs (Direct Android Keystore)
- [x] **`security-crypto` removed** — `androidx.security:security-crypto:1.1.0-alpha06` and `MasterKey.Builder` / `EncryptedSharedPreferences` fully removed
- [x] **`FialkaSecurePrefs`** — New `object` implementation using the Android Keystore directly: AES-256-GCM, key alias `fialka_ks_{name}`, prefs file `{name}_v2`
- [x] **StrongBox with TEE fallback** — Silent attempt on StrongBox, automatic fallback to standard TEE if unavailable
- [x] **6 files migrated** — `CryptoManager`, `FialkaDatabase`, `MailboxDatabase`, `AppMode`, `AppLockManager`, `MailboxClientManager` migrated to `FialkaSecurePrefs.open()`
- [x] **AGP 8.9.1 + compileSdk 36 + Gradle 8.13** — Build toolchain upgrade; zero warnings in release builds

### 🐛 Fix — SQLCipher crash on startup
- [x] **`System.loadLibrary("sqlcipher")`** — `sqlcipher-android:4.14.1` removed the static initializer; `FialkaApplication.onCreate()` now loads the native library first
- [x] **Defensive calls** — `FialkaDatabase.getInstance()` and `MailboxDatabase.buildDatabase()` also call `loadLibrary` as a defensive guard

### 🐛 Fix — DurationSelectorBottomSheet NPE
- [x] **`context` parameter removed** — `DurationSelectorBottomSheet` no longer takes an external `Context`; `BottomSheetDialogFragment` provides its own context
- [x] **`show()` simplified** — Removed `fragmentManager.findFragmentById(R.id.nav_host_fragment)?.requireContext()!!` which returned `null` from `childFragmentManager`

### ⚡ Transport Reliability
- [x] **Reduced retry delay** — `RETRY_INTERVAL_MS` 60 s → 15 s; `MAX_RETRY_DELAY_MS` cap 30 min → 3 min (backoff: 15 s / 30 s / 1 min / 2 min / 3 min)
- [x] **`DELIVERY_FAILED` on exhausted messages** — Messages reaching 50 retries are marked `DELIVERY_FAILED` BEFORE queue deletion (fixes permanent hourglass ⏳)
- [x] **Mailbox fallback in `processOutboxForContact()`** — If P2P deposit fails, automatically deposits via Mailbox and updates to `DELIVERY_MAILBOX`
- [x] **Mailbox sweep in `broadcastPresence()`** — After the offline-contacts loop, `processOutbox()` is called to sweep queued messages
- [x] **Adaptive fetch in `MailboxClientManager`** — Base 10 s, 5 s when messages received (+`OutboxManager.flushNow()`), backoff up to 60 s on error

### 📦 Updated Dependencies
- [x] **BouncyCastle** 1.80 → **1.83** (ML-KEM-1024, Ed25519, ML-DSA-44)
- [x] **SQLCipher** 4.5.4 → **4.14.1**
- [x] **Room** 2.7.1 → **2.8.4**
- [x] **Coroutines** 1.9.0 → **1.10.2**
- [x] **Navigation** 2.8.9 → **2.9.7** | **Lifecycle** 2.8.7 → **2.10.0**

</details>

---

<details>
<summary><h2>🔜 V3.6 — Planned (partially delivered in V4.0)</h2></summary>


> Advanced camouflage, plausible deniability, E2E voice messages, sealed sender, messaging improvements.

### 🎭 App Disguise (Icon & Name Camouflage)
- [ ] **Icon change** — User picks a camouflage icon from presets: Calculator, Notes, News, Weather, Clock, etc.
- [ ] **Display name change** — App name in launcher changes to match the chosen icon (“Calculator”, “Notes”, “News”, etc.)
- [ ] **Icon themes** — Each disguise has a matching icon + name (professional style)
- [ ] **Activity-alias** — Implementation via `<activity-alias>` in manifest (dynamic enable/disable via `PackageManager`)
- [ ] **Confirmation + restart** — Confirmation dialog with preview → “Restart now” → kill + relaunch
- [ ] **Functional cover screen** — Disguised app opens a real functional fake app (calculator, notes, etc.). The real chat is accessible via a secret gesture (hidden long press or special code)
- [ ] **Persistence** — Choice saved in SharedPreferences, restored on startup

### 🔐 Plausible Deniability & Protection
- [ ] **Dual PIN** — Normal PIN opens chat; duress PIN opens an empty profile or triggers a silent wipe (plausible deniability, journalist/activist level)
- [ ] **Panic button** — Shake phone → instant deletion of all conversations + keys + sign-out (full wipe)
- [ ] **Screenshot protection** — ~~`FLAG_SECURE` on all windows~~ ✔️ **Done in V3.4.1 Security Audit**
- [ ] **Keyboard incognito** — `flagNoPersonalizedLearning` on all input fields — keyboard does not learn or log anything

### 🔐 Advanced Crypto
- [ ] **Sealed sender** — Sender identity hidden on Firebase side — recipient deduces sender only after decryption

### 💬 Advanced Messaging
- [ ] **E2E voice messages** — Audio recording, AES-256-GCM encryption, sent via ratchet, inline player in chat
- [ ] **Reply / Quote** — Reply to a specific message with quoted citation (quoted bubble + new message)
- [ ] **Groups** — 3+ participant conversations (Sender Keys)
- [ ] **Delete for everyone** — Delete a message on local + Firebase
- [ ] **Typing indicators** — “Typing...” (E2E encrypted, opt-in)

### 🛡️ Infrastructure
- [ ] **Private relay** — Dedicated relay server to reduce Firebase dependency

</details>

---

<div align="center">

[← Back to README](../../README-en.md)

</div>