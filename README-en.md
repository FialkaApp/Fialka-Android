<div align="right">
  <a href="README.md">🇫🇷 Français</a> | 🇬🇧 English
</div>

<br/>

<div align="center">

# 🔐 SecureChat

### End-to-end encrypted chat for Android — free, anonymous, serverless

<br/>

[![Android](https://img.shields.io/badge/Android-33%2B-a855f7?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7c3aed?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![E2E](https://img.shields.io/badge/PQXDH-X25519%20%2B%20ML--KEM--768-6d28d9?style=for-the-badge&logo=letsencrypt&logoColor=white)](docs/en/CRYPTO.md)
[![License](https://img.shields.io/badge/GPLv3-License-8b5cf6?style=for-the-badge)](LICENSE)
[![Terms](https://img.shields.io/badge/Terms-Conditions-8b5cf6?style=for-the-badge)](TERMS.md)
[![Privacy](https://img.shields.io/badge/Privacy-Policy-8b5cf6?style=for-the-badge)](PRIVACY.md)

<br/>

<table>
<tr>
<td>

```
  Messages are encrypted BEFORE they are sent.
  Firebase only sees noise.

  No phone number. No email.
  Just a nickname and a key.
```

</td>
</tr>
</table>

<br/>

</div>

---

<div align="center">

## ⚡ At a Glance

</div>

<table>
<tr>
<td width="50%">

### 🔐 Crypto

- **PQXDH**: X25519 + **ML-KEM-768** (post-quantum)
- **AES-256-GCM** + **Double Ratchet** with PFS + healing
- **Fingerprint emojis** 96-bit anti-MITM + **QR code scanner**
- **Independent verification** per user + system messages
- **BIP-39** backup (24 words) + autocomplete grid
- **One-shot photos** — view once, 2-phase secure deletion
- Private key in **Android Keystore** (StrongBox when available)
- Encrypted local DB **SQLCipher**
- **Message padding** fixed-size (256/1K/4K/16K)
- **PBKDF2** PIN (600K iterations)
- **Dummy traffic** (per-conversation cover traffic)
- **E2E file sharing** AES-256-GCM encrypted
- **Ed25519 signatures** per-message anti-forgery
- **Tor built-in** — SOCKS5, VPN TUN, hidden IP

</td>
<td width="50%">

### 🎨 UI/UX

- **Material Design 3** — Full migration of all 5 themes
- **5 themes**: Midnight · Hacker · **Phantom** · Aurora · Daylight
- **Fluid animations** — transitions, bubbles, cascade
- **Inline attachment icons** — Session-style, slide-up animation
- **Tor Bootstrap screen** — Tor/Normal choice, animated progress, 5 themes
- **Scrollable toolbar** + auto-hide FAB
- **Dynamic bubbles** colored by theme
- **App Lock** PIN + biometrics
- **Disappearing messages** (30s → 1 month)
- **One-shot photos** view once 🔥

</td>
</tr>
</table>

---

<div align="center">

## ✨ Features

</div>

<table>
<tr><td>

<details open>
<summary><b>🔒 Security & Crypto</b></summary>
<br/>

| | Feature | Details |
|---|---------|---------|
| 🔐 | **E2E Encryption** | PQXDH: X25519 + ML-KEM-768 + AES-256-GCM |
| 🔄 | **Perfect Forward Secrecy** | Double Ratchet (DH + KDF chains) |
| 🔏 | **Fingerprint emojis + QR** | 96-bit, 16 emojis + QR code SHA-256, built-in scanner |
| ✅ | **Independent verification** | Each user verifies separately, system message + clickable link |
| 🛡️ | **DeviceSecurityManager** | StrongBox detection, MAXIMUM/STANDARD level |
| 🕵️ | **Metadata hardening** | senderUid HMAC-hashed + messageIndex encrypted |
| 🛡️ | **Zero-knowledge relay** | Firebase only sees ciphertext |
| 🔑 | **Keystore-backed** | Private key in EncryptedSharedPreferences |
| 🗄️ | **SQLCipher** | Room DB encrypted with AES-256 |
| 🧹 | **Memory zeroing** | Intermediate keys filled with zeros |
| 📏 | **Message padding** | Fixed-size (256/1K/4K/16K) anti-traffic analysis |
| 🗑️ | **Delete-after-delivery** | Messages removed from Firebase after decryption |
| 👻 | **Dummy traffic** | Periodic cover messages (configurable toggle) |
| 📎 | **E2E file sharing** | Per-file AES-256-GCM via Firebase Storage |
| 🔒 | **PBKDF2 PIN** | 600K iterations + salt (replaces SHA-256) |
| ✍️ | **Ed25519 Signatures** | Every message signed, ✅/⚠️ badge anti-forgery |
| 📸 | **One-shot photos** | View once (sender + receiver), 2-phase secure deletion |

</details>

<details>
<summary><b>💬 Messaging</b></summary>
<br/>

| | Feature | Details |
|---|---------|---------|
| 📷 | **QR Code** | Scan → auto-fill public key & nickname (deep link v2) |
| 📨 | **Contact requests** | Invite → notification → accept/reject |
| 🔴 | **Unread messages** | Badge counter + separator in chat |
| 🔄 | **Real-time** | Receive messages even in background |
| 🔔 | **Push notifications** | Opt-in, zero message content |
| ⏱️ | **Disappearing msgs** | 10 durations (30s → 1 mo), Firebase sync |
| � | **E2E file sharing** | AES-256-GCM encrypted, 25 MB max |
| 👻 | **Dummy traffic** | Indistinguishable cover messages to mask activity |
| 🗑️ | **Delete-after-delivery** | Ciphertext removed from Firebase after receipt |
| �💀 | **Dead convo detection** | Auto-detect + clean up + re-invite |

</details>

<details>
<summary><b>🎨 Interface</b></summary>
<br/>

| | Feature | Details |
|---|---------|---------|
| 🌙 | **5 themes** | Midnight · Hacker · Phantom · Aurora · Daylight |
| ✨ | **Animations** | Slide/fade transitions, animated bubbles |
| 📜 | **Scrollable toolbar** | Collapses on scroll, snaps back |
| 🔽 | **Auto-hide FAB** | Hides when scrolling down |
| 🫧 | **Dynamic bubbles** | Colors adapt to the active theme |
| 🎭 | **Visual selector** | MaterialCardView grid with preview |
| 📎 | **Inline icons** | Session-style attachment (file/photo/camera) animated |

</details>

<details>
<summary><b>🔒 Protection</b></summary>
<br/>

| | Feature | Details |
|---|---------|---------|
| 🔒 | **App Lock** | 6-digit PIN + opt-in biometrics |
| ⏰ | **Auto-lock** | Configurable timeout (5s → 5min) |
| 🔑 | **BIP-39 Backup** | 24 words to backup identity key |
| ♻️ | **Restore** | Autocomplete 24-word grid + recover on new device |
| 🗑️ | **Full deletion** | Cleans Firebase (profile, inbox, convos, signing keys) |
| 📵 | **Anonymous** | Zero number, zero email, zero tracking |

</details>

</td></tr>
</table>

---

<div align="center">

## 🏗 Architecture

</div>

```
┌──────────────────────────────────────────────────┐
│                    UI Layer                       │
│         Fragments · ViewModels · Adapters         │
├──────────────────────────────────────────────────┤
│               Repository Layer                    │
│      ChatRepository — single source of truth      │
├────────────────┬────────────────┬────────────────┤
│    Room DB     │     Crypto     │    Firebase     │
│   (SQLCipher)  │  PQXDH + DR    │  Relay + FCM    │
└────────────────┴────────────────┴────────────────┘
```

> 📖 **Details** — [Full Architecture](docs/en/ARCHITECTURE.md) · [Crypto Protocol](docs/en/CRYPTO.md) · [Project Structure](docs/en/STRUCTURE.md)

---

<div align="center">

## 🛠 Quick Start

</div>

```bash
# 1. Clone
git clone https://github.com/DevBot667/SecureChat.git
cd SecureChat

# 2. Add google-services.json to app/ (see docs/SETUP-en.md)

# 3. Build
./gradlew assembleDebug
```

> 📖 **Full Guide** — [Installation & Firebase Config](docs/en/SETUP.md)

---

<div align="center">

## 🔐 Security

</div>

| Measure | Status |
|---------|--------|
| E2E Encryption (PQXDH: X25519 + ML-KEM-768 + AES-256-GCM) | ✅ |
| Double Ratchet with PFS + healing | ✅ |
| Memory zeroing (intermediate keys) | ✅ |
| Atomic sending (ratchet + Firebase) | ✅ |
| Conversation Mutex (thread-safe) | ✅ |
| SQLCipher (local DB AES-256 encrypted) | ✅ |
| Metadata hardening (trial decryption) | ✅ |
| senderUid HMAC-SHA256 hashed per conversation | ✅ |
| Fixed-size message padding (anti traffic analysis) | ✅ |
| Delete-after-delivery (Firebase auto-cleanup) | ✅ |
| Configurable dummy traffic (cover traffic) | ✅ |
| E2E file sharing (AES-256-GCM + Firebase Storage) | ✅ |
| PBKDF2 PIN (600K iterations + salt) | ✅ |
| R8/ProGuard obfuscation + complete log stripping (d/v/i/w/e/wtf) | ✅ |
| Fingerprint emojis 96-bit anti-MITM + QR code SHA-256 scanner | ✅ |
| App Lock (PIN + biometrics) | ✅ |
| Restrictive Firebase security rules | ✅ |
| BIP-39 backup/restore (24 words) | ✅ |
| `allowBackup=false`, zero sensitive logs | ✅ |
| Material Design 3 — full migration of all 5 themes | ✅ |
| Inline attachment icons with animation (Session-style) | ✅ |
| Android 13+ permissions (READ_MEDIA_IMAGES/AUDIO) | ✅ |
| Predictive back gesture (enableOnBackInvokedCallback) | ✅ |
| Built-in Tor routing (SOCKS5 + VPN TUN + libtor.so) | ✅ |
| Tor bootstrap screen (choice + progress + 5 themes) | ✅ |
| Tor toggle in Security Settings + reconnect | ✅ |
| Per-conversation dummy traffic | ✅ |
| Ed25519 per-message signatures (anti-forgery) | ✅ |
| PQXDH: X25519 + ML-KEM-768 (post-quantum resistance) | ✅ |
| Deferred PQXDH upgrade (rootKey-only, zero desync) | ✅ |
| StrongBox hardware key storage (when available) | ✅ |
| DeviceSecurityManager (StrongBox probe + user profile) | ✅ |
| QR deep link v2 (X25519 + ML-KEM + name, auto-fill) | ✅ |
| displayName hidden from Firebase (zero server-side PII) | ✅ |
| Independent fingerprint verification per user | ✅ |
| Verification system messages + clickable link | ✅ |
| lastDeliveredAt (skip already-processed messages on restart) | ✅ |
| Delete-after-failure (cleanup failed messages from Firebase) | ✅ |
| Atomic dual-listener deduplication (ConcurrentHashMap) | ✅ |
| Signing key cleanup on account deletion | ✅ |
| One-shot photos (view once, 2-phase secure deletion) | ✅ |
| QR code fingerprint scanner (SHA-256 hex, CustomScannerActivity) | ✅ |
| BIP-39 autocomplete 24-word grid (restore redesign) | ✅ |
| Forgot PIN (recovery via mnemonic phrase) | ✅ |
| **V3.4.1 Security Audit — 42+ vulnerabilities fixed** | ✅ |
| Firebase rules: write-once (signing_keys, mlkem_keys, inbox) | ✅ |
| senderUid/ciphertext/iv/createdAt validation in Firebase rules | ✅ |
| HKDF memory zeroing (IKM, PRK, expandInput) | ✅ |
| MnemonicManager memory zeroing (encode + decode) | ✅ |
| FLAG_SECURE (MainActivity, LockScreen, RestoreFragment, dialogs) | ✅ |
| Tapjacking protection (filterTouchesWhenObscured) | ✅ |
| usesCleartextTraffic=false (zero HTTP traffic) | ✅ |
| Deep link hardening (whitelist, limits, anti-injection) | ✅ |
| Clipboard EXTRA_IS_SENSITIVE + 30s auto-clear | ✅ |
| SecureFileManager (2-pass wipe: random + zeros) | ✅ |
| Opaque FCM payload (zero metadata in push notifications) | ✅ |
| Firebase Storage: delete restricted to uploader only | ✅ |
| ML-KEM size + Base64 client-side validation | ✅ |
| FirebaseRelay.sendMessage input validation (require guards) | ✅ |
| Cloud Function: regex validation senderUid + conversationId | ✅ |

> 📖 **Full Analysis** — [`SECURITY.md`](SECURITY.md) · [Crypto Protocol](docs/en/CRYPTO.md)

---

<div align="center">

## 🗺 Roadmap

</div>

| Version | Theme | Status |
|---------|-------|--------|
| **V1** | Core — E2E, contacts, chats, push, fingerprint, SQLCipher, App Lock, ephemeral | ✅ Done |
| **V2** | Crypto Upgrade — Full Double Ratchet X25519, native Curve25519 | ✅ Done |
| **V2.1** | Account Lifecycle — BIP-39 backup, restore, delete, dead convo | ✅ Done |
| **V2.2** | UI Modernization — 5 themes, animations, CoordinatorLayout, zero hardcoded colors | ✅ Done |
| **V3** | Security Hardening — R8, delete-after-delivery, padding, HMAC UID, PBKDF2, dummy traffic, E2E files | ✅ Done |
| **V3.1** | Settings Redesign — Signal-like settings, 6-digit PIN, Privacy sub-screen, PIN coroutines | ✅ Done |
| **V3.2** | Ed25519 Signing — Per-message signatures, ✅/⚠️ badge, Firebase rules hardening, signing key cleanup | ✅ Done |
| **V3.3** | Material 3 + Tor + Attachment UX — M3 migration, full Tor integration, Session-style inline icons, Android 13+ permissions, log hardening | ✅ Done |
| **V3.4** | PQXDH + Security — Post-quantum ML-KEM-768, deep link v2, QR name auto-fill, displayName hidden from Firebase, DeviceSecurityManager StrongBox, independent fingerprint verification, system messages, PQXDH desync fix, dual-listener fix, lastDeliveredAt | ✅ Done |
| **V3.4.1** | One-Shot + UX + Security Audit — Ephemeral photos, BIP-39 grid, QR fingerprint, 29 layout audit, forgot PIN, **comprehensive security audit (42+ fixes)**: Firebase rules write-once, HKDF/mnemonic memory zeroing, FLAG_SECURE, deep link hardening, SecureFileManager, opaque FCM, Storage owner-only delete, input validation | ✅ Done |
| **V3.5** | Planned — App disguise + cover screen, Dual PIN, panic button, E2E voice messages, sealed sender, reply/quote | 🔜 |

> 📖 **Details** — [Full Changelog](docs/en/CHANGELOG.md)

---

<div align="center">

## 🤝 Contributing

</div>

1. Fork the repo
2. Create your branch (`git checkout -b feature/my-feature`)
3. Commit (`git commit -m 'Add my feature'`)
4. Push (`git push origin feature/my-feature`)
5. Open a **Pull Request**

> ⚠️ For any crypto modification, please open an **issue** first to discuss it.

---

<div align="center">

## 📖 Documentation

| Document | Content |
|----------|---------|
| [**Architecture**](docs/en/ARCHITECTURE.md) | Patterns, layers, request flows, lifecycle |
| [**Crypto Protocol**](docs/en/CRYPTO.md) | X25519, Double Ratchet, fingerprint, threat model |
| [**Setup**](docs/en/SETUP.md) | Prerequisites, Firebase, build, dependencies |
| [**Structure**](docs/en/STRUCTURE.md) | Full project tree |
| [**Changelog**](docs/en/CHANGELOG.md) | V1 → V3.4.1 history |
| [**Security**](SECURITY.md) | Full audit, known limitations |

</div>

---

<div align="center">

This project is licensed under [GPLv3](LICENSE). See the [Terms of Service](TERMS.md) before use.

Provided for **educational** purposes. Use it as a definitive base to understand E2E encryption on mobile.

<br/>

> **⚠️ Disclaimer** : This software is a personal and educational project. The cryptographic implementation has **NOT been audited** by a third-party security firm. No guarantee of absolute security is provided. Do not rely on it as your sole means of secure communication in critical situations. Use of this software is **at your own risk**. See [TERMS.md](TERMS.md).

<br/>

<img src="https://img.shields.io/badge/SecureChat-V3.4.1-7c3aed?style=for-the-badge&logo=android&logoColor=white" />

<br/><br/>

*"Your messages, your keys, your privacy."*

<br/>

</div>