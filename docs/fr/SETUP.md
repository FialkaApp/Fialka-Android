<div align="right">
  🇫🇷 Français | <a href="../en/SETUP.md">🇬🇧 English</a>
</div>

<div align="center">

# 🛠 Installation & Configuration

<img src="https://img.shields.io/badge/IDE-Android_Studio-7B2D8E?style=for-the-badge&logo=android-studio" />
<img src="https://img.shields.io/badge/JDK-17-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/Transport-Tor_P2P-6A1B9A?style=for-the-badge" />

</div>

---

## Prérequis

- **Android Studio** Hedgehog (2023.1.1) ou plus récent
- **JDK 17**
- **Rust toolchain** 1.70+ — `rustup target add aarch64-linux-android x86_64-linux-android`
- **Android NDK** 27.2+ — installé via Android Studio SDK Manager
- **cargo-ndk** — `cargo install cargo-ndk`

> Fialka n'a **aucune dépendance à un service externe**. Pas de Firebase, pas de compte Google, pas de clé API.

> **Fialka-Core (Rust JNI)** — Les fichiers `.so` pré-compilés (`arm64-v8a` + `x86_64`) sont inclus dans `app/src/main/jniLibs/`. Pour les recompiler après modification de `Fialka-Core/` :
> ```bash
> cd Fialka-Core
> $env:ANDROID_NDK_HOME = "<chemin NDK>"
> cargo ndk -t arm64-v8a -t x86_64-linux-android -o "../app/src/main/jniLibs" build --release
> ```

---

## 1. Cloner le repo

```bash
git clone --recurse-submodules https://github.com/FialkaApp/Fialka-Android.git
cd Fialka-Android
```

---

## 2. Compiler

```bash
./gradlew assembleDebug
```

Ou ouvrir dans Android Studio → **Run** sur un émulateur ou device physique.

> Fialka embarque Tor (`libtor.so`) directement. Au premier lancement, l'app bootstrap une connexion Tor et génère l'identité de l'utilisateur à partir d'un unique seed Ed25519.

> **Note SQLCipher** — `sqlcipher-android:4.14.1` ne dispose plus d'initialiseur statique. `FialkaApplication.onCreate()` appelle `System.loadLibrary("sqlcipher")` en tout premier, avant tout accès à la base Room.

---

## Notes d'architecture

- **Aucun serveur à configurer** — toute communication est P2P via Tor Hidden Services (.onion)
- **Aucun serveur push** — les notifications sont délivrées via UnifiedPush + ntfy.sh (auto-hébergeable)
- **Aucune création de compte** — l'identité est dérivée localement du seed Ed25519 (backup BIP-39, 24 mots)
- **Fialka Mailbox** — gère la livraison hors-ligne (4 modes : Direct P2P, Personnel, Nœud privé, Nœud public)

---

## Dépendances

| Dépendance | Version | Usage |
|------------|---------|-------|
| Kotlin | 2.3.0 | Langage |
| AndroidX Core / AppCompat / Material | Latest | UI Material Design |
| AndroidX Navigation | 2.9.7 | Navigation single-activity |
| AndroidX Lifecycle | 2.10.0 | ViewModels, LiveData, coroutines |
| Room + KSP | 2.8.4 | Base de données locale SQLite |
| SQLCipher | 4.14.1 | Chiffrement AES-256 de la base Room |
| **Fialka-Core** (Rust JNI) | submodule | Toute la crypto : AES-GCM, ChaCha20, Ed25519, X25519, ML-KEM-1024, ML-DSA-44, HKDF, Ratchet |
| Tor (libtor.so) | Embedded | Transport P2P Tor Hidden Services |
| UnifiedPush | Latest | Notifications push (compatible ntfy.sh) |
| FialkaSecurePrefs | In-app | Stockage sécurisé (Android Keystore direct AES-256-GCM) |
| AndroidX Biometric | 1.1.0 | BiometricPrompt (empreinte, visage) |
| Kotlinx Coroutines | 1.10.2 | Async + Flow |
| ZXing Android Embedded | 4.3.0 | Génération et scan QR codes |

---

<div align="center">

[← Retour au README](../../README.md)

</div>
