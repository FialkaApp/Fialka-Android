<div align="right">
  <a href="README.md">🇫🇷 Français</a> | 🇬🇧 English
</div>

<br/>

<div align="center">

# 🔐 Fialka

### End-to-end encrypted chat for Android — free, anonymous, serverless

<br/>

🔐 &nbsp;&nbsp; 🧅 &nbsp;&nbsp; 📬 &nbsp;&nbsp; 🛡️ &nbsp;&nbsp; 🔑 &nbsp;&nbsp; 📷 &nbsp;&nbsp; 📱

<br/>

[![Android](https://img.shields.io/badge/Android-33%2B-a855f7?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.0-7c3aed?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![E2E](https://img.shields.io/badge/PQXDH-X25519%20%2B%20ML--KEM--1024-6d28d9?style=for-the-badge&logo=letsencrypt&logoColor=white)](docs/en/CRYPTO.md)
[![License](https://img.shields.io/badge/GPLv3-License-8b5cf6?style=for-the-badge)](LICENSE)
[![Terms](https://img.shields.io/badge/Terms-Conditions-8b5cf6?style=for-the-badge)](TERMS.md)
[![Privacy](https://img.shields.io/badge/Privacy-Policy-8b5cf6?style=for-the-badge)](PRIVACY.md)

<br/>

[![Tor](https://img.shields.io/badge/Tor-Guardian_Project-7c3aed?style=flat-square&logo=torproject&logoColor=white)](https://guardianproject.info/)
[![BouncyCastle](https://img.shields.io/badge/BouncyCastle-1.83-7c3aed?style=flat-square)](https://www.bouncycastle.org/)
[![SQLCipher](https://img.shields.io/badge/SQLCipher-4.14.1-7c3aed?style=flat-square)](https://www.zetetic.net/sqlcipher/)
[![Room](https://img.shields.io/badge/Room-2.8.4-7c3aed?style=flat-square)](https://developer.android.com/jetpack/androidx/releases/room)
[![Material3](https://img.shields.io/badge/Material_Design-3-7c3aed?style=flat-square&logo=materialdesign&logoColor=white)](https://m3.material.io/)
[![ZXing](https://img.shields.io/badge/ZXing-4.3.0-7c3aed?style=flat-square)](https://github.com/journeyapps/zxing-android-embedded)
[![Coroutines](https://img.shields.io/badge/Coroutines-1.10.2-7c3aed?style=flat-square&logo=kotlin&logoColor=white)](https://github.com/Kotlin/kotlinx.coroutines)

<br/>

<table>
<tr>
<td>

```
  Messages are encrypted BEFORE they are sent.
  No one can read them. No central server.

  No phone number. No email. No Google.
  Just a key and Tor.
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

- **1 Ed25519 seed → everything**: identity, .onion, X25519, ML-KEM, fingerprint
- **PQXDH**: X25519 + **ML-KEM-1024** (post-quantum hybrid)
- **ML-DSA-44**: PQ signature on handshake
- **SPQR**: periodic PQ re-encapsulation (every 10 msgs)
- **AES-256-GCM** / **ChaCha20-Poly1305** (auto) + **Double Ratchet** with PFS + healing
- **Fingerprint emojis** 96-bit anti-MITM + **QR code scanner**
- **BIP-39** backup (24 words) → restores entire identity
- **One-shot photos** — view once, 2-phase secure deletion
- Seed in **Android Keystore** (StrongBox when available)
- Encrypted local DB **SQLCipher**
- **Message padding** fixed-size (256/1K/4K/16K)
- **Dummy traffic** (per-conversation cover traffic)
- **E2E file sharing** AES-256-GCM (P2P transfer via Tor)
- **Ed25519 signatures** per-message anti-forgery
- **Zero Google, zero Firebase** — full P2P via **Tor Hidden Services**

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
| 🌱 | **1 Seed → Everything** | Ed25519 seed → identity, .onion, X25519, ML-KEM, fingerprint |
| 🔐 | **E2E Encryption** | PQXDH: X25519 + ML-KEM-1024 + AES-256-GCM / ChaCha20-Poly1305 + SPQR |
| 🤝 | **ML-DSA-44 Handshake** | Post-quantum signature on session establishment |
| 🔄 | **Perfect Forward Secrecy** | Double Ratchet (DH + KDF chains) |
| 🔏 | **Fingerprint emojis + QR** | 96-bit, 16 emojis + QR code SHA-256, built-in scanner |
| 🧅 | **Tor P2P** | .onion to .onion — zero relay, zero middleman |
| 📬 | **Fialka Mailbox** | Distinct server mode (requires 2 phones) — 4 modes (Personal, Private, Public) — E2E store/transit/purge — ZERO decryption |
| 🛡️ | **DeviceSecurityManager** | StrongBox detection, MAXIMUM/STANDARD level |
| 🕵️ | **Sealed Sender** | Tor Hidden Services — recipient cannot see sender IP |
| 🔑 | **Keystore-backed** | Ed25519 seed in Android Keystore (StrongBox when available) |
| 🗄️ | **SQLCipher** | Room DB encrypted with AES-256 |
| 🧹 | **Memory zeroing** | Intermediate keys filled with zeros |
| 📏 | **Message padding** | Fixed-size (256/1K/4K/16K) anti-traffic analysis |
| 👻 | **Dummy traffic** | Periodic cover messages (configurable toggle) |
| 📎 | **E2E file sharing** | Per-file AES-256-GCM, P2P transfer via Tor |
| ✍️ | **Ed25519 Signatures** | Every message signed, ✅/⚠️ badge anti-forgery |
| 📸 | **One-shot photos** | View once (sender + receiver), 2-phase secure deletion |
| 🚫 | **Zero Google** | No Firebase, no FCM, no Google services |

</details>

<details>
<summary><b>💬 Messaging</b></summary>
<br/>

| | Feature | Details |
|---|---------|---------|
| 📷 | **QR Code** | Scan → auto-fill public key & nickname (deep link v2) |
| 📨 | **Contact requests** | Invite → notification → accept/reject |
| 🔴 | **Unread messages** | Badge counter + separator in chat |
| 🔄 | **Real-time** | Receive messages even in background (Tor P2P) |
| 🔔 | **Push notifications** | UnifiedPush + ntfy.sh, zero message content |
| ⏱️ | **Disappearing msgs** | 10 durations (30s → 1 mo) |
| 📁 | **E2E file sharing** | AES-256-GCM encrypted, P2P via Tor |
| 👻 | **Dummy traffic** | Indistinguishable cover messages to mask activity |
| 📬 | **Fialka Mailbox** | Distinct server mode (requires 2 phones) — 4 modes — E2E store/transit/purge — ZERO decryption |
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
| 🗑️ | **Full deletion** | Wipes local data + Mailbox + signing keys |
| 📵 | **Anonymous** | Zero number, zero email, zero Google, zero tracking |

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
│    Room DB     │     Crypto     │   Transport     │
│   (SQLCipher)  │ PQXDH + DR +   │ Tor P2P .onion  │
│                │ ML-DSA-44      │ + Mailbox       │
└────────────────┴────────────────┴────────────────┘
```

> 📖 **Details** — [Full Architecture](docs/en/ARCHITECTURE.md) · [Crypto Protocol](docs/en/CRYPTO.md) · [Project Structure](docs/en/STRUCTURE.md)

---

<div align="center">

## 🛠 Quick Start

</div>

```bash
# 1. Clone
git clone https://github.com/FialkaApp/Fialka-Android.git
cd Fialka-Android

# 2. Build
./gradlew assembleDebug
```

> 📖 **Full Guide** — [Installation & Build](docs/en/SETUP.md)

---

<div align="center">

## 🔐 Security

</div>

<details>
<summary><b>View all security measures (34 items)</b></summary>
<br/>

| Measure | Status |
|---------|--------|
| **Identity: 1 Ed25519 seed → everything** (Account ID, .onion, X25519, ML-KEM, fingerprint) | ✅ |
| **Zero central server** — all communication P2P via Tor Hidden Services | ✅ |
| **Zero Google, zero Firebase** — no FCM, no RTDB, no Cloud Functions | ✅ |
| E2E Encryption (PQXDH: X25519 + ML-KEM-1024 + AES-256-GCM / ChaCha20) | ✅ |
| **ML-DSA-44** post-quantum signature on handshake | ✅ |
| Double Ratchet with PFS + healing | ✅ |
| SPQR: ML-KEM re-encapsulation every 10 messages | ✅ |
| ChaCha20-Poly1305 alternative (auto hardware AES detection) | ✅ |
| **Tor Hidden Services** P2P (.onion → .onion, zero relay) | ✅ |
| **Fialka Mailbox** (4 modes: Direct P2P, Personal, Private Node, Public Node) | ✅ |
| **UnifiedPush + ntfy.sh** (self-hostable, replaces FCM) | ✅ |
| **Sealed Sender** via Tor (recipient cannot see sender IP) | ✅ |
| Memory zeroing (intermediate keys) | ✅ |
| Conversation Mutex (thread-safe) | ✅ |
| SQLCipher (local DB AES-256 encrypted) | ✅ |
| Fixed-size message padding (anti traffic analysis) | ✅ |
| Per-conversation dummy traffic (cover traffic) | ✅ |
| E2E file sharing (AES-256-GCM, P2P via Tor) | ✅ |
| R8/ProGuard obfuscation + complete log stripping (d/v/i/w/e/wtf) | ✅ |
| Fingerprint emojis 96-bit anti-MITM + QR code SHA-256 scanner | ✅ |
| App Lock (PIN + biometrics) | ✅ |
| BIP-39 backup/restore (24 words → full identity) | ✅ |
| `allowBackup=false`, zero sensitive logs | ✅ |
| Ed25519 per-message signatures (anti-forgery) | ✅ |
| StrongBox hardware key storage (when available) | ✅ |
| DeviceSecurityManager (StrongBox probe + user profile) | ✅ |
| One-shot photos (view once, 2-phase secure deletion) | ✅ |
| HKDF memory zeroing (IKM, PRK, expandInput) | ✅ |
| MnemonicManager memory zeroing (encode + decode) | ✅ |
| FLAG_SECURE (MainActivity, LockScreen, RestoreFragment, dialogs) | ✅ |
| Tapjacking protection (filterTouchesWhenObscured) | ✅ |
| Deep link hardening (whitelist, limits, anti-injection) | ✅ |
| Clipboard EXTRA_IS_SENSITIVE + 30s auto-clear | ✅ |
| SecureFileManager (2-pass wipe: random + zeros) | ✅ |
| **V3.4.1 Security Audit — 42+ vulnerabilities fixed** | ✅ |

</details>

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
| **V3** | Security Hardening — R8, delete-after-delivery, padding, HMAC UID, dummy traffic, E2E files | ✅ Done |
| **V3.1** | Settings Redesign — Signal-like settings, 6-digit PIN, Privacy sub-screen | ✅ Done |
| **V3.2** | Ed25519 Signing — Per-message signatures, ✅/⚠️ badge | ✅ Done |
| **V3.3** | Material 3 + Tor + Attachment UX — M3 migration, full Tor integration, Session-style icons | ✅ Done |
| **V3.4** | PQXDH + Security — Post-quantum ML-KEM-1024, deep link v2, DeviceSecurityManager StrongBox, fingerprint verification | ✅ Done |
| **V3.4.1** | One-Shot + Security Audit — Ephemeral photos, BIP-39 grid, **comprehensive security audit (42+ fixes)** | ✅ Done |
| **V3.5** | SPQR + ChaCha20 — PQ Triple Ratchet (ML-KEM re-encapsulation), ChaCha20-Poly1305 alternative, documented threat model | ✅ Done |
| **V4.0** | Kill Firebase — P2P .onion + Mailbox store-and-forward, invite QR, deep links | ✅ Done |
| **V4.0.1** | Direct Keystore (FialkaSecurePrefs), SQLCipher 4.14.1, transport reliability (15s retry, Mailbox fallback, adaptive fetch) | ✅ Done |
| **V4.1** | UX — App disguise (icon + cover screen), Dual PIN + Panic Button, E2E voice messages (Opus), reply/quote | 🔜 |
| **V4.2** | Sealed Sender (VXEdDSA), multi-device Sesame, **third-party security audit** (Cure53 / Trail of Bits) | 🔜 |
| **V5.0** | Long-term — **Falcon-512** per-message PQ signatures, decentralized Mailbox network, Bluetooth/WiFi fallback | 🔮 |

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
| [**Setup**](docs/en/SETUP.md) | Prerequisites, build, dependencies |
| [**Structure**](docs/en/STRUCTURE.md) | Full project tree |
| [**Changelog**](docs/en/CHANGELOG.md) | V1 → V4.0.1 history |
| [**Security**](SECURITY.md) | Full audit, known limitations |

</div>

---

---

<div align="center">

## 🙏 Acknowledgments — Libraries & Dependencies

</div>

<table>
<tr>
<td width="50%">

### 🔐 Cryptography

| Library | Version | Role |
|---|---|---|
| [**BouncyCastle**](https://www.bouncycastle.org/) | `1.80` | ML-KEM-1024, ML-DSA-44, Ed25519, ChaCha20-Poly1305 |
| **Android Keystore** (AOSP) | — | Ed25519 seed in hardware (StrongBox) |
| [**Security Crypto**](https://developer.android.com/jetpack/androidx/releases/security) | `1.1.0-alpha06` | EncryptedSharedPreferences, MasterKey |
| [**Biometric**](https://developer.android.com/jetpack/androidx/releases/biometric) | `1.1.0` | Biometric authentication (fingerprint, face) |

### 🧅 Networking & Anonymity

| Library | Version | Role |
|---|---|---|
| [**tor-android**](https://github.com/guardianproject/tor-android) — Guardian Project | `0.4.9.5` | Embedded Tor binary, v3 Hidden Services |
| [**jtorctl**](https://github.com/guardianproject/jtorctl) — Guardian Project | `0.4.5.7` | Tor daemon control (Java) |
| [**socks-socket**](https://code.briarproject.org/briar/briar) — Briar Project | `0.1` | SOCKS5 connections through Tor |
| [**lyrebird-android**](https://code.briarproject.org/briar/briar) — Briar Project | `0.6.2` | obfs4/Snowflake transports (anti-censorship) |
| [**moat-api**](https://code.briarproject.org/briar/briar) — Briar Project | `0.4` | Tor bridge bootstrapping |

### 🗄️ Database

| Library | Version | Role |
|---|---|---|
| [**Room**](https://developer.android.com/jetpack/androidx/releases/room) | `2.7.1` | SQLite ORM (local persistence) |
| [**SQLCipher**](https://www.zetetic.net/sqlcipher/) — Zetetic | `4.5.4` | AES-256 encryption of the Room database |
| [**KSP**](https://github.com/google/ksp) — Google | — | Compile-time Room code generation |

</td>
<td width="50%">

### 📱 Android SDK & UI

| Library | Version | Role |
|---|---|---|
| [**AndroidX Core KTX**](https://developer.android.com/kotlin/ktx) | `1.15.0` | Kotlin extensions for Android |
| [**AppCompat**](https://developer.android.com/jetpack/androidx/releases/appcompat) | `1.7.0` | Backwards compatibility |
| [**Fragment / Activity KTX**](https://developer.android.com/jetpack/androidx/releases/activity) | `1.8.6 / 1.10.1` | Screen lifecycle and navigation |
| [**Navigation**](https://developer.android.com/jetpack/androidx/releases/navigation) | `2.8.9` | Fragment navigation + Deep Links |
| [**Lifecycle**](https://developer.android.com/jetpack/androidx/releases/lifecycle) | `2.8.7` | ViewModel, LiveData, coroutines |
| [**RecyclerView**](https://developer.android.com/jetpack/androidx/releases/recyclerview) | `1.4.0` | Message lists |
| [**ConstraintLayout**](https://developer.android.com/develop/ui/views/layout/constraint-layout) | `2.2.1` | Complex layouts |
| [**Material Design 3**](https://m3.material.io/) — Google | `1.12.0` | M3 components, 5 themes |
| [**Splash Screen**](https://developer.android.com/develop/ui/views/launch/splash-screen) | `1.0.1` | Startup screen |

### 📷 QR Code & Async

| Library | Version | Role |
|---|---|---|
| [**ZXing Android Embedded**](https://github.com/journeyapps/zxing-android-embedded) — JourneyApps | `4.3.0` | QR code generation + scanning |
| [**Kotlinx Coroutines**](https://github.com/Kotlin/kotlinx.coroutines) — JetBrains | `1.9.0` | Non-blocking async, structured concurrency |
| [**Kotlin**](https://kotlinlang.org/) — JetBrains | `2.3.0` | Primary programming language |

</td>
</tr>
</table>

<br/>

<div align="center">

### 📋 Cryptographic Standards & Protocols

| Standard | Reference | Usage in Fialka |
|---|---|---|
| **ML-KEM-1024** | NIST FIPS 203 | Post-quantum Key Encapsulation Mechanism (hybrid PQXDH) |
| **ML-DSA-44** | NIST FIPS 204 | Post-quantum digital signature (handshake) |
| **Ed25519** | RFC 8032 | Per-message signature + identity seed |
| **X25519** | RFC 7748 | Elliptic Curve Diffie-Hellman key exchange |
| **PQXDH** | Signal Specification | Post-quantum hybrid key agreement protocol |
| **Double Ratchet** | Signal Specification | Perfect Forward Secrecy + auto-healing |
| **ChaCha20-Poly1305** | RFC 8439 | Authenticated symmetric encryption (AES fallback) |
| **AES-256-GCM** | NIST SP 800-38D | Authenticated symmetric encryption (primary) |
| **HKDF** | RFC 5869 | Cryptographic key derivation |
| **PBKDF2** | RFC 8018 | PIN-based key derivation (600,000 iterations) |
| **BIP-39** | Bitcoin Core | 24-word mnemonic for identity backup |

</div>

---

<div align="center">

This project is licensed under [GPLv3](LICENSE). See the [Terms of Service](TERMS.md) and [Privacy Policy](PRIVACY.md) before use.

<br/>

> **⚠️ Disclaimer** : **Fialka is a tool.** The developers do not operate any infrastructure, do not store any user data, and cannot access any messages. The cryptographic implementation has **NOT been audited** by a third-party security firm. No guarantee of absolute security is provided. Use of this software is **at your own risk** and **under your sole responsibility**. See [TERMS.md](TERMS.md).

<br/>

### 📜 Cryptography Export Notice

<sub>
This distribution includes cryptographic software. The country in which you currently reside may have restrictions on the import, possession, use, and/or re-export to another country, of encryption software. BEFORE using any encryption software, please check your country’s laws, regulations and policies concerning the import, possession, or use, and re-export of encryption software, to see if this is permitted. See <a href="https://www.wassenaar.org/">https://www.wassenaar.org/</a> for more information.
<br/><br/>
The U.S. Government Department of Commerce, Bureau of Industry and Security (BIS), has classified this software as Export Commodity Control Number (ECCN) <strong>5D002.C.1</strong>, which includes information security software using or performing cryptographic functions with asymmetric algorithms. The form and manner of this distribution makes it eligible for export under the License Exception ENC Technology Software Unrestricted (TSU) exception (see the BIS Export Administration Regulations, Section 740.13) for both object code and source code.
<br/><br/>
<strong>European Union — Regulation (EU) 2021/821 (Dual-Use Regulation):</strong> This software falls under Category 5, Part 2 ("Information Security") of Annex I of Regulation (EU) 2021/821. As publicly available open-source software (GPLv3 licence, available on GitHub), it is exempt from export control requirements under the General Software Note (GSN) and the Cryptography Note (Note 3), which exclude software "generally available to the public" from control. No specific export authorisation is required within the European Union for this software.
<br/><br/>
<strong>France — LCEN 2004 (Art. 30, Law No. 2004-575 of 21 June 2004):</strong> In France, the use of cryptographic means has been freely permitted since the Law for Confidence in the Digital Economy (LCEN) of 21 June 2004, now codified in the French Postal and Electronic Communications Code. No prior declaration or authorisation is required to use this software in France.
</sub>

<br/>

© 2024-2026 FialkaApp Contributors. Licensed under [GPLv3](LICENSE).

<br/>

<img src="https://img.shields.io/badge/Fialka-V4.0.1-7c3aed?style=for-the-badge&logo=android&logoColor=white" />

<br/><br/>

*"Your messages, your keys, your privacy."*

<br/>

</div>