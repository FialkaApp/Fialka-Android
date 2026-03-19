<div align="right">
  <a href="../fr/CHANGELOG.md">🇫🇷 Français</a> | 🇬🇧 English
</div>

<div align="center">

# 🗺 Changelog & Roadmap

<img src="https://img.shields.io/badge/Current-V3.4-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Next-V3.5-9C4DCC?style=for-the-badge" />

</div>

---

## ✅ V1 — Core

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

---

## ✅ V2 — Crypto Upgrade

> Full Double Ratchet X25519, replaced P-256 with Curve25519.

- [x] **Full Double Ratchet X25519** — DH ratchet + KDF chains + automatic healing
- [x] **Native X25519** — Curve25519 (API 33+), replaces P-256
- [x] **Initial chains** — Both sides can send immediately after acceptance
- [x] **Natural ephemeral exchange** — Via real messages, no bootstrap message

---

## ✅ V2.1 — Account Lifecycle

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

---

## ✅ V2.2 — UI Modernization

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

---

## ✅ V3.0 — Security Hardening

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

---

## ✅ V3.1 — Settings Redesign & PIN Upgrade

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

---

## ✅ V3.2 — Ed25519 Message Signing

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

---

## ✅ V3.3 — Material 3, Tor Integration, Attachment UX & Log Hardening

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
- [x] **Conditional startup** — `SecureChatApplication.onCreate()` starts Tor if enabled
- [x] **TorBootstrapFragment** — `startDestination` of nav graph, Tor/Normal choice on first launch
- [x] **Animated circular progress** — Real-time percentage, dynamic status text, pulse animation
- [x] **Respects all 5 themes** — Colors via `?attr/` from active theme
- [x] **Tor toggle** — ON/OFF in Settings → Security with manual reconnect
- [x] **Real-time status** — "Connected via Tor" / "Reconnecting..." / "Disconnected"
- [x] **Per-conversation dummy traffic** — Individual cover messages per active conversation

---

## ✅ V3.4 — Post-Quantum & Device Security

> Hybrid PQXDH (ML-KEM-768 + X25519), StrongBox DeviceSecurityManager, QR deep link v2, independent fingerprint verification, ratchet desync fixes.

### 🔐 Post-Quantum Cryptography (PQXDH)
- [x] **ML-KEM-768 (Kyber)** — Post-quantum encapsulation via BouncyCastle 1.80, dedicated encaps/decaps key pair
- [x] **Hybrid PQXDH** — Classic X25519 key exchange + ML-KEM-768 encapsulation in parallel
- [x] **Deferred rootKey upgrade** — Initial conversation starts classic X25519-only; rootKey is upgraded with ML-KEM secret on the first message (no bootstrap message)
- [x] **kemCiphertext in first message** — ML-KEM ciphertext sent once, in the first Firebase message of the conversation
- [x] **QR deep link v2** — Format `securechat://contact?key=<X25519>&kem=<ML-KEM-768-pubKey>&name=<displayName>` — ML-KEM key encoded in QR
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

---

## 🔜 V3.5 — Planned

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
- [ ] **Screenshot protection** — `FLAG_SECURE` on all windows — blocks screenshots, screen recording, and recent apps preview
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

---

<div align="center">

[← Back to README](../../README-en.md)

</div>