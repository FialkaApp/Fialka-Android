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
- **Rust toolchain** 1.70+ — `rustup target add aarch64-linux-android x86_64-linux-android`
- **Android NDK** 27.2+ — install via Android Studio SDK Manager
- **cargo-ndk** — `cargo install cargo-ndk`

> Fialka has **zero external service dependency**. No Firebase, no Google account, no API key needed.

> **Fialka-Core (Rust JNI)** — Pre-compiled `.so` files (`arm64-v8a` + `x86_64`) are included in `app/src/main/jniLibs/`. To rebuild after modifying `Fialka-Core/`:
> ```bash
> cd Fialka-Core
> $env:ANDROID_NDK_HOME = "<NDK path>"
> cargo ndk -t arm64-v8a -t x86_64-linux-android -o "../app/src/main/jniLibs" build --release
> ```

---

## 1. Clone the repo

```bash
git clone --recurse-submodules https://github.com/FialkaApp/Fialka-Android.git
cd Fialka-Android
```

---

## 2. Build

```bash
./gradlew assembleDebug
```

Or open in Android Studio → **Run** on an emulator or physical device.

> Fialka embeds Tor (`libtor.so`) directly. On first launch, the app bootstraps a Tor connection and generates the user's identity from a single Ed25519 seed.

> **SQLCipher note** — `sqlcipher-android:4.14.1` no longer has a static initializer. `FialkaApplication.onCreate()` calls `System.loadLibrary("sqlcipher")` first, before any Room DB access.

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
| Kotlin | 2.3.0 | Language |
| AndroidX Core / AppCompat / Material | Latest | UI Material Design |
| AndroidX Navigation | 2.9.7 | Single-activity navigation |
| AndroidX Lifecycle | 2.10.0 | ViewModels, LiveData, coroutines |
| Room + KSP | 2.8.4 | Local SQLite database |
| SQLCipher | 4.14.1 | AES-256 encryption for Room DB |
| **Fialka-Core** (Rust JNI) | submodule | All crypto: AES-GCM, ChaCha20, Ed25519, X25519, ML-KEM-1024, ML-DSA-44, HKDF, Ratchet |
| Tor (libtor.so) | Embedded | Tor Hidden Services P2P transport |
| UnifiedPush | Latest | Push notifications (ntfy.sh compatible) |
| FialkaSecurePrefs | In-app | Secure storage (direct Android Keystore AES-256-GCM) |
| AndroidX Biometric | 1.1.0 | BiometricPrompt (fingerprint, face) |
| Kotlinx Coroutines | 1.10.2 | Async + Flow |
| ZXing Android Embedded | 4.3.0 | Generation and scanning of QR codes |

---

<div align="center">

[← Back to README](../../README-en.md)

</div>