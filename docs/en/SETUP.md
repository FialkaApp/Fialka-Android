<div align="right">
  <a href="../fr/SETUP.md">🇫🇷 Français</a> | 🇬🇧 English
</div>

<div align="center">

# 🛠 Installation & Setup

<img src="https://img.shields.io/badge/IDE-Android_Studio-7B2D8E?style=for-the-badge&logo=android-studio" />
<img src="https://img.shields.io/badge/JDK-17-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/Transport-Tor_P2P-6A1B9A?style=for-the-badge" />

</div>

---

## Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17**

> Fialka has **zero external service dependency**. No Firebase, no Google account, no API key needed.

---

## 1. Clone the repo

```bash
git clone https://github.com/FialkaApp/Fialka-Android.git
cd Fialka-Android
```

---

## 2. Build

```bash
./gradlew assembleDebug
```

Or open in Android Studio → **Run** on an emulator or physical device.

> Fialka embeds Tor (`libtor.so`) directly. On first launch, the app bootstraps a Tor connection and generates the user's identity from a single Ed25519 seed.

---

## Architecture Notes

- **No server to configure** — all communication is peer-to-peer via Tor Hidden Services (.onion)
- **No push server** — notifications are delivered via UnifiedPush + ntfy.sh (self-hostable)
- **No account creation** — identity is derived locally from the Ed25519 seed (BIP-39 24-word backup)
- **Fialka Mailbox** — handles offline delivery (4 modes: Direct P2P, Personal, Private Node, Public Node)

---

## Dependencies

| Dependency | Version | Usage |
|------------|---------|-------|
| Kotlin | 2.1.0 | Language |
| AndroidX Core / AppCompat / Material | Latest | UI Material Design |
| AndroidX Navigation | 2.8.9 | Single-activity navigation |
| AndroidX Lifecycle | 2.8.7 | ViewModels, LiveData, coroutines |
| Room + KSP | 2.7.1 | Local SQLite database |
| SQLCipher | 4.5.4 | AES-256 encryption for Room DB |
| BouncyCastle | 1.80 | Ed25519, ML-KEM-1024, ML-DSA-44 |
| Tor (libtor.so) | Embedded | Tor Hidden Services P2P transport |
| UnifiedPush | Latest | Push notifications (ntfy.sh compatible) |
| AndroidX Security Crypto | 1.1.0-alpha06 | Secure storage (Android Keystore) |
| AndroidX Biometric | 1.1.0 | BiometricPrompt (fingerprint, face) |
| Kotlinx Coroutines | 1.9.0 | Async + Flow |
| ZXing Android Embedded | 4.3.0 | Generation and scanning of QR codes |

---

<div align="center">

[← Back to README](../../README-en.md)

</div>