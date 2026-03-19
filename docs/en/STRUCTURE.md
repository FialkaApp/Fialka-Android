<div align="right">
  <a href="../fr/STRUCTURE.md">🇫🇷 Français</a> | 🇬🇧 English
</div>

<div align="center">

# 📂 Project Structure

<img src="https://img.shields.io/badge/Fragments-16-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Layouts-23-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/Animations-12-6A1B9A?style=for-the-badge" />

</div>

---

```
SecureChat/
├── .gitignore
├── LICENSE
├── README-en.md
├── README.md
├── SECURITY.md
├── firebase-rules.json
├── storage.rules                             # Firebase Storage rules
├── build.gradle.kts                          # Root Gradle config
├── settings.gradle.kts
├── gradle.properties
│
├── docs/                                     # Detailed Documentation
│   ├── fr/                                   # French documentation
│   │   ├── ARCHITECTURE.md
│   │   ├── CRYPTO.md
│   │   ├── SETUP.md
│   │   ├── STRUCTURE.md
│   │   └── CHANGELOG.md
│   └── en/                                   # English documentation
│       ├── ARCHITECTURE.md                   # Architecture, patterns, flows
│       ├── CRYPTO.md                         # Full cryptographic protocol
│       ├── SETUP.md                          # Installation + Firebase config
│       ├── STRUCTURE.md                      # This file
│       └── CHANGELOG.md                      # Version history
│
├── app/
│   ├── build.gradle.kts                      # App dependencies
│   ├── proguard-rules.pro
│   ├── google-services.json                  # ← TO ADD (gitignored)
│   ├── google-services.json.template         # Reference structure
│   │
│   └── src/main/
│       ├── AndroidManifest.xml
│       │
│       ├── java/com/securechat/
│       │   ├── SecureChatApplication.kt      # Firebase Init
│       │   ├── MainActivity.kt               # Single-activity (NavHost)
│       │   ├── LockScreenActivity.kt         # PIN + biometrics lock screen
│       │   ├── MyFirebaseMessagingService.kt  # FCM push handler
│       │   │
│       │   ├── crypto/
│       │   │   ├── CryptoManager.kt          # X25519, ECDH, AES-256-GCM, HKDF, ML-KEM-768 (PQXDH)
│       │   │   ├── DoubleRatchet.kt          # Full Double Ratchet (DH + KDF chains) + PQXDH upgrade
│       │   │   └── MnemonicManager.kt        # BIP-39 mnemonic encode/decode (24 words)
│       │   │
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── SecureChatDatabase.kt # Room DB v14 (SQLCipher)
│       │   │   │   ├── UserLocalDao.kt
│       │   │   │   ├── ContactDao.kt
│       │   │   │   ├── ConversationDao.kt
│       │   │   │   ├── MessageLocalDao.kt
│       │   │   │   └── RatchetStateDao.kt
│       │   │   │
│       │   │   ├── model/
│       │   │   │   ├── UserLocal.kt          # Local identity
│       │   │   │   ├── Contact.kt            # Contact (nickname + pubkey)
│       │   │   ├── Conversation.kt       # Conversation (ephemeral, fingerprint, lastDeliveredAt)
│       │   │   │   ├── MessageLocal.kt       # Message (plaintext, ephemeral)
│       │   │   │   ├── FirebaseMessage.kt    # Encrypted message (Firebase)
│       │   │   │   └── RatchetState.kt       # Ratchet state per conversation
│       │   │   │
│       │   │   ├── remote/
│       │   │   └── FirebaseRelay.kt      # Anonymous auth + relay + ephemeral sync + fingerprint events
│       │   │   │
│       │   │   └── repository/
│       │   │       └── ChatRepository.kt     # Single source of truth (Mutex)
│       │   │
│       │   ├── util/
│       │   │   ├── QrCodeGenerator.kt        # QR codes generation (ZXing)
│       │   │   ├── ThemeManager.kt           # 5 themes (Midnight/Hacker/Phantom/Aurora/Daylight)
│       │   │   ├── AppLockManager.kt         # PIN, biometrics, auto-lock timeout
│       │   │   ├── EphemeralManager.kt       # Ephemeral durations (30s → 1 month)
│       │   │   ├── DummyTrafficManager.kt    # Dummy traffic (traffic analysis countermeasure)
│       │   │   └── DeviceSecurityManager.kt  # StrongBox probe, MAXIMUM/STANDARD security levels
│       │   │
│       │   └── ui/
│       │       ├── onboarding/               # Identity creation + backup + restore
│       │       │   ├── OnboardingFragment.kt
│       │       │   ├── OnboardingViewModel.kt
│       │       │   ├── BackupPhraseFragment.kt
│       │       │   └── RestoreFragment.kt
│       │       ├── conversations/            # Chats list + contact requests
│       │       │   ├── ConversationsFragment.kt
│       │       │   ├── ConversationsViewModel.kt
│       │       │   ├── ConversationsAdapter.kt
│       │       │   └── ContactRequestsAdapter.kt
│       │       ├── addcontact/               # Scan QR + manual input
│       │       │   ├── AddContactFragment.kt
│       │       │   ├── AddContactViewModel.kt
│       │       │   └── CustomScannerActivity.kt
│       │       ├── chat/                     # E2E Messages + bubbles
│       │       │   ├── ChatFragment.kt
│       │       │   ├── ChatViewModel.kt
│       │       │   ├── MessagesAdapter.kt
│       │       │   ├── ConversationProfileFragment.kt
│       │       │   └── FingerprintFragment.kt
│       │       ├── profile/                  # QR code, copy/share, delete
│       │       └── settings/                 # Settings hub + sub-screens
│       │           ├── SettingsFragment.kt
│       │           ├── AppearanceFragment.kt
│       │           ├── NotificationsFragment.kt
│       │           ├── SecurityFragment.kt
│       │           ├── PrivacyFragment.kt        # Privacy sub-screen (dummy traffic, ephemeral)
│       │           ├── EphemeralSettingsFragment.kt
│       │           └── PinSetupDialogFragment.kt
│       │
│       └── res/
│           ├── anim/                         # 12 animations (slide, fade, bubble, cascade)
│           ├── drawable/                     # Bubbles, badges, icons, backgrounds, ic_attach, ic_add
│           ├── layout/                       # 23 XML layouts (fragments + items)
│           ├── menu/                         # Conversations menu
│           ├── navigation/nav_graph.xml      # 16 destinations, animated transitions
│           ├── raw/bip39_english.txt         # BIP-39 Wordlist (2048 words)
│           ├── xml/file_paths.xml            # FileProvider paths (file sharing)
│           ├── values/                       # Colors, strings, themes, 22 custom attrs
│           └── values-night/                 # Dark mode colors
│
├── functions/                                # Firebase Cloud Function (push)
│   ├── index.js
│   ├── package.json
│   └── .gitignore
```

---

<div align="center">

[← Back to README](../../README-en.md)

</div>