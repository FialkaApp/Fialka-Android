<div align="right">
  <a href="../fr/STRUCTURE.md">🇫🇷 Français</a> | 🇬🇧 English
</div>

<div align="center">

# 📂 Project Structure

<img src="https://img.shields.io/badge/Fragments-21-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Layouts-43-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/Animations-14-6A1B9A?style=for-the-badge" />

</div>

---

```
Fialka/
├── .gitignore
├── LICENSE
├── README-en.md
├── README.md
├── PRIVACY.md
├── TERMS.md
├── SECURITY.md
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
│       ├── SETUP.md                          # Installation + build
│       ├── STRUCTURE.md                      # This file
│       └── CHANGELOG.md                      # Version history
│
├── app/
│   ├── build.gradle.kts                      # App dependencies
│   ├── proguard-rules.pro
│   │
│   └── src/main/
│       ├── AndroidManifest.xml
│       │
│       ├── java/com/fialkaapp/fialka/
│       │   ├── FialkaApplication.kt      # App Init + Tor bootstrap
│       │   ├── MainActivity.kt               # Single-activity (NavHost)
│       │   ├── LockScreenActivity.kt         # PIN + biometrics lock screen
│       │   │
│       │   ├── crypto/
│       │   │   ├── CryptoManager.kt          # Crypto orchestration — delegates to FialkaNative (Rust JNI)
│       │   │   ├── FialkaNative.kt           # JNI bridge — 30 extern functions to libfialka_core.so
│       │   │   ├── DoubleRatchet.kt          # Full Double Ratchet (DH + KDF chains) + PQXDH upgrade
│       │   │   └── MnemonicManager.kt        # BIP-39 mnemonic encode/decode (24 words)
│       │   │
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── FialkaDatabase.kt # Room DB v24 (SQLCipher)
│       │   │   │   ├── UserLocalDao.kt
│       │   │   │   ├── ContactDao.kt
│       │   │   │   ├── ConversationDao.kt
│       │   │   │   ├── MessageLocalDao.kt
│       │   │   │   ├── OutboxDao.kt          # Outbox messages + `getExhaustedMessages()`
│       │   │   │   └── RatchetStateDao.kt
│       │   │   │
│       │   │   ├── model/
│       │   │   │   ├── UserLocal.kt          # Local identity
│       │   │   │   ├── Contact.kt            # Contact (nickname + pubkey)
│       │   │   ├── Conversation.kt       # Conversation (ephemeral, fingerprint, lastDeliveredAt)
│       │   │   │   ├── MessageLocal.kt       # Message (plaintext, ephemeral)
│       │   │   ├── EncryptedMessage.kt   # Encrypted message (wire format)
│       │   │   └── RatchetState.kt       # Ratchet state per conversation
│       │   │   │
│       │   │   ├── remote/
│       │   │   └── TorTransport.kt       # Tor Hidden Services P2P transport + Mailbox
│       │   │   │
│       │   │   └── repository/
│       │   │       └── ChatRepository.kt     # Single source of truth (Mutex)
│       │   │
│       │   ├── util/
│       │   │   ├── QrCodeGenerator.kt        # QR codes generation (ZXing)
│       │   │   ├── SecureFileManager.kt      # Secure file deletion (2-pass overwrite: random + zeros)
│       │   │   ├── FialkaSecurePrefs.kt      # Direct Android Keystore AES-256-GCM storage (replaces security-crypto)
│       │   │   ├── ThemeManager.kt           # 5 themes (Midnight/Hacker/Phantom/Aurora/Daylight)
│       │   │   ├── AppLockManager.kt         # PIN, biometrics, auto-lock timeout (FialkaSecurePrefs)
│       │   │   ├── EphemeralManager.kt       # Ephemeral durations (30s → 1 month)
│       │   │   ├── DummyTrafficManager.kt    # Dummy traffic (traffic analysis countermeasure)
│       │   │   └── DeviceSecurityManager.kt  # StrongBox probe, MAXIMUM/STANDARD security levels
│       │   │
│       │   └── ui/
│       │       ├── onboarding/               # Identity creation + backup + restore
│       │       │   ├── OnboardingFragment.kt
│       │       │   ├── OnboardingViewModel.kt
│       │       │   ├── BackupPhraseFragment.kt
│       │       │   ├── RestoreFragment.kt
│       │       │   └── SeedVerificationFragment.kt  # 3-word confirmation after seed backup
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
│       │           ├── SettingsAdapter.kt            # Settings RecyclerView with search + category filters
│       │           ├── SettingsViewModel.kt          # Settings items StateFlow
│       │           ├── AppearanceFragment.kt
│       │           ├── NotificationsFragment.kt
│       │           ├── SecurityFragment.kt
│       │           ├── PrivacyFragment.kt        # Privacy sub-screen (dummy traffic, ephemeral)
│       │           ├── EphemeralSettingsFragment.kt
│       │           ├── PinSetupDialogFragment.kt
│       │           ├── ThemeSelectorBottomSheet.kt   # Visual 5-theme picker
│       │           ├── DurationSelectorBottomSheet.kt # Ephemeral message duration selector
│       │           ├── StorageFragment.kt            # Storage management (real-time stats, cleanup, danger zone)
│       │           ├── BackupExportFragment.kt       # Encrypted .fialka backup export (PBKDF2 + AES-256-GCM)
│       │           └── BackupImportFragment.kt       # .fialka backup import + validation
│       │
│       ├── wallet/                           # Non-custodial Monero XMR wallet
│       │   ├── MoneroWallet.kt               # JNI facade to libfialka_monero.so
│       │   ├── WalletRepository.kt           # Wallet source of truth (dynamic network via WalletPreferences)
│       │   ├── WalletPreferences.kt          # Wallet prefs: network (STAGENET/MAINNET), node, restore height
│       │   ├── WalletSnapshot.kt             # Snapshot state (balance, sync, enabled, hasSeed)
│       │   └── DonationKeys.kt               # XMR donation address (network independent from user wallet)
│       │
│       ├── ui/
│       │   ├── wallet/                       # Wallet screens
│       │   │   ├── WalletHomeFragment.kt     # Wallet dashboard + network badge (red/green)
│       │   │   ├── WalletSettingsFragment.kt # Wallet settings + Stagenet/Mainnet selection + delete dialog
│       │   │   ├── WalletSeedBackupFragment.kt # Wallet seed backup + network badge
│       │   │   └── WalletHomeViewModel.kt
│       │   └── donation/
│       │       └── DonationFragment.kt       # XMR donation send + transaction history
│       │
│       └── res/
│           ├── anim/                         # 14 animations (slide, fade, bubble, cascade, bottom sheet)
│           ├── drawable/                     # Bubbles, badges, icons, backgrounds, brand orbs, panels
│           ├── layout/                       # 43 XML layouts (fragments + items + bottom sheets)
│           ├── menu/                         # Conversations menu
│           ├── navigation/nav_graph.xml      # 21 destinations, animated transitions
│           ├── raw/bip39_english.txt         # BIP-39 Wordlist (2048 words)
│           ├── xml/file_paths.xml            # FileProvider paths (file sharing)
│           ├── values/                       # Colors, strings, themes, 22 custom attrs
│           └── values-night/                 # Dark mode colors
```

---

<div align="center">

[← Back to README](../../README-en.md)

</div>