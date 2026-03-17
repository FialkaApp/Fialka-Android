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
[![E2E](https://img.shields.io/badge/X25519-AES--256--GCM-6d28d9?style=for-the-badge&logo=letsencrypt&logoColor=white)](docs/en/CRYPTO.md)
[![License](https://img.shields.io/badge/GPLv3-License-8b5cf6?style=for-the-badge)](LICENSE)

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

- **X25519 ECDH** + **AES-256-GCM**
- **Double Ratchet** with PFS + healing
- **Fingerprint emojis** 96-bit anti-MITM
- **BIP-39** backup (24 words)
- Private key in **Android Keystore**
- Encrypted local DB **SQLCipher**

</td>
<td width="50%">

### 🎨 UI/UX

- **5 themes**: Midnight · Hacker · **Phantom** · Aurora · Daylight
- **Fluid animations** — transitions, bubbles, cascade
- **Scrollable toolbar** + auto-hide FAB
- **Dynamic bubbles** colored by theme
- **App Lock** PIN + biometrics
- **Disappearing messages** (30s → 1 month)

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
| 🔐 | **E2E Encryption** | X25519 ECDH + AES-256-GCM |
| 🔄 | **Perfect Forward Secrecy** | Double Ratchet (DH + KDF chains) |
| 🔏 | **Fingerprint emojis** | 96-bit, 16 emojis, anti-MITM |
| 🕵️ | **Metadata hardening** | senderPublicKey + messageIndex removed |
| 🛡️ | **Zero-knowledge relay** | Firebase only sees ciphertext |
| 🔑 | **Keystore-backed** | Private key in EncryptedSharedPreferences |
| 🗄️ | **SQLCipher** | Room DB encrypted with AES-256 |
| 🧹 | **Memory zeroing** | Intermediate keys filled with zeros |

</details>

<details>
<summary><b>💬 Messaging</b></summary>
<br/>

| | Feature | Details |
|---|---------|---------|
| 📷 | **QR Code** | Scan → auto-fill public key & nickname |
| 📨 | **Contact requests** | Invite → notification → accept/reject |
| 🔴 | **Unread messages** | Badge counter + separator in chat |
| 🔄 | **Real-time** | Receive messages even in background |
| 🔔 | **Push notifications** | Opt-in, zero message content |
| ⏱️ | **Disappearing msgs** | 10 durations (30s → 1 mo), Firebase sync |
| 💀 | **Dead convo detection** | Auto-detect + clean up + re-invite |

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

</details>

<details>
<summary><b>🔒 Protection</b></summary>
<br/>

| | Feature | Details |
|---|---------|---------|
| 🔒 | **App Lock** | 4-digit PIN + opt-in biometrics |
| ⏰ | **Auto-lock** | Configurable timeout (5s → 5min) |
| 🔑 | **BIP-39 Backup** | 24 words to backup identity key |
| ♻️ | **Restore** | Recover on a new device via mnemonic |
| 🗑️ | **Full deletion** | Cleans Firebase (profile, inbox, convos) |
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
│   (SQLCipher)  │  X25519 + DR   │  Relay + FCM    │
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
| E2E Encryption (X25519 + AES-256-GCM) | ✅ |
| Double Ratchet with PFS + healing | ✅ |
| Memory zeroing (intermediate keys) | ✅ |
| Atomic sending (ratchet + Firebase) | ✅ |
| Conversation Mutex (thread-safe) | ✅ |
| SQLCipher (local DB AES-256 encrypted) | ✅ |
| Metadata hardening (trial decryption) | ✅ |
| Fingerprint emojis 96-bit anti-MITM | ✅ |
| App Lock (PIN + biometrics) | ✅ |
| Restrictive Firebase security rules | ✅ |
| BIP-39 backup/restore (24 words) | ✅ |
| `allowBackup=false`, zero sensitive logs | ✅ |

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
| **V3** | Planned — ECDSA signatures, groups, delete for all, typing indicators | 🔜 |

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
| [**Changelog**](docs/en/CHANGELOG.md) | V1 → V2.2 history + V3 roadmap |
| [**Security**](SECURITY.md) | Full audit, known limitations |

</div>

---

<div align="center">

This project is licensed under [GPLv3](LICENSE).

Provided for **educational** purposes. Use it as a definitive base to understand E2E encryption on mobile.

<br/>

<img src="https://img.shields.io/badge/SecureChat-V2.2-7c3aed?style=for-the-badge&logo=android&logoColor=white" />

<br/><br/>

*"Your messages, your keys, your privacy."*

<br/>

</div>