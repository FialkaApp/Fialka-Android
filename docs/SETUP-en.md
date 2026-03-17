<div align="right">
  <a href="SETUP.md">🇫🇷 Français</a> | 🇬🇧 English
</div>

<div align="center">

# 🛠 Installation & Setup

<img src="https://img.shields.io/badge/IDE-Android_Studio-7B2D8E?style=for-the-badge&logo=android-studio" />
<img src="https://img.shields.io/badge/JDK-17-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/Firebase-Blaze_(free)-6A1B9A?style=for-the-badge&logo=firebase" />

</div>

---

## Prerequisites

- **Android Studio** Hedgehog (2023.1.1) or newer
- **JDK 17**
- A **Firebase** project (Blaze plan — free up to 2M invocations/month)
- **Node.js 18+** (to deploy the Cloud Function)

---

## 1. Clone the repo

```bash
git clone https://github.com/DevBot667/SecureChat.git
cd SecureChat
```

---

## 2. Firebase Configuration

### Step 1 — Create the project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. **Create a project** (disable Google Analytics if desired)
3. **Add an Android app**:
   - Package name: `com.securechat`
   - Download `google-services.json`

### Step 2 — Configure services

1. **Copy** `google-services.json` into `app/`
2. Firebase Console → **Authentication** → Sign-in method → **Anonymous** → Enable
3. Firebase Console → **Realtime Database** → Create (closest region)
4. **Rules** tab → paste the content of [`firebase-rules.json`](../firebase-rules.json)

### Step 3 — Deploy the Cloud Function (push notifications)

```bash
# Install Firebase CLI
npm install -g firebase-tools

# Login
firebase login

# From the root of the project
cd functions
npm install
cd ..
firebase deploy --only functions
```

The Cloud Function triggers automatically on each new message and sends a push notification to recipients who have opted in.

> ⚠️ **`google-services.json` is in `.gitignore`** — it will never be pushed to GitHub.
> The file `app/google-services.json.template` shows the expected structure.

---

## 3. Build

```bash
./gradlew assembleDebug
```

Or open in Android Studio → **Run** on an emulator or physical device.

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
| Firebase BOM | 34.10.0 | Anonymous auth + Realtime Database + Cloud Messaging |
| firebase-functions (Node.js) | 7.0.0 | Cloud Function trigger (push notifications) |
| firebase-admin (Node.js) | 13.6.0 | Admin SDK for RTDB + FCM server-side |
| AndroidX Security Crypto | 1.1.0-alpha06 | Secure storage (EncryptedSharedPreferences) |
| AndroidX Biometric | 1.1.0 | BiometricPrompt (fingerprint, face) |
| Kotlinx Coroutines | 1.9.0 | Async + Flow |
| ZXing Android Embedded | 4.3.0 | Generation and scanning of QR codes |

---

<div align="center">

[← Back to README](../README-en.md)

</div>