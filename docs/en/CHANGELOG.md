<div align="right">
  <a href="../fr/CHANGELOG.md">🇫🇷 Français</a> | 🇬🇧 English
</div>

<div align="center">

# 🗺 Changelog & Roadmap

<img src="https://img.shields.io/badge/Current-V3.0-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Next-V3.1-9C4DCC?style=for-the-badge" />

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
- [x] App Lock — 4-digit PIN + opt-in biometric unlock
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
- [x] **PBKDF2 PIN** — SHA-256 replaced with PBKDF2-HMAC-SHA256 (600K iterations, 16-byte salt); auto-migrates legacy hashes

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

## 🔜 V3.1 — Planned

- [ ] **ECDSA Signature** — Dedicated signature key (PURPOSE_SIGN) to authenticate each message
- [ ] **Groups** — 3+ participant conversations
- [ ] **Delete for everyone** — Delete a message on local + Firebase
- [ ] **Typing indicators** — "Typing..."
- [ ] **Private relay** — Dedicated relay server to reduce Firebase dependency

---

<div align="center">

[← Back to README](../../README-en.md)

</div>