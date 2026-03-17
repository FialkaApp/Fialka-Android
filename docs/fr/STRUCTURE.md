<div align="right">
  🇫🇷 Français | <a href="../en/STRUCTURE.md">🇬🇧 English</a>
</div>

<div align="center">

# 📂 Structure du projet

<img src="https://img.shields.io/badge/Fragments-15-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Layouts-22-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/Animations-10-6A1B9A?style=for-the-badge" />

</div>

---

```
SecureChat/
├── .gitignore
├── LICENSE
├── README.md
├── SECURITY.md
├── firebase-rules.json
├── build.gradle.kts                          # Config Gradle racine
├── settings.gradle.kts
├── gradle.properties
│
├── docs/                                     # Documentation détaillée
│   ├── ARCHITECTURE.md                       # Architecture, patterns, flux
│   ├── CRYPTO.md                             # Protocole cryptographique complet
│   ├── SETUP.md                              # Installation + config Firebase
│   ├── STRUCTURE.md                          # Ce fichier
│   └── CHANGELOG.md                          # Historique des versions
│
├── app/
│   ├── build.gradle.kts                      # Dépendances app
│   ├── proguard-rules.pro
│   ├── google-services.json                  # ← À AJOUTER (gitignored)
│   ├── google-services.json.template         # Structure de référence
│   │
│   └── src/main/
│       ├── AndroidManifest.xml
│       │
│       ├── java/com/securechat/
│       │   ├── SecureChatApplication.kt      # Init Firebase
│       │   ├── MainActivity.kt               # Single-activity (NavHost)
│       │   ├── LockScreenActivity.kt         # Écran de verrouillage PIN + biométrie
│       │   ├── MyFirebaseMessagingService.kt  # FCM push handler
│       │   │
│       │   ├── crypto/
│       │   │   ├── CryptoManager.kt          # X25519, ECDH, AES-256-GCM, HKDF
│       │   │   ├── DoubleRatchet.kt          # Full Double Ratchet (DH + KDF chains)
│       │   │   └── MnemonicManager.kt        # BIP-39 mnemonic encode/decode (24 mots)
│       │   │
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── SecureChatDatabase.kt # Room DB v10 (SQLCipher)
│       │   │   │   ├── UserLocalDao.kt
│       │   │   │   ├── ContactDao.kt
│       │   │   │   ├── ConversationDao.kt
│       │   │   │   ├── MessageLocalDao.kt
│       │   │   │   └── RatchetStateDao.kt
│       │   │   │
│       │   │   ├── model/
│       │   │   │   ├── UserLocal.kt          # Identité locale
│       │   │   │   ├── Contact.kt            # Contact (pseudo + pubkey)
│       │   │   │   ├── Conversation.kt       # Conversation (ephemeral, fingerprint)
│       │   │   │   ├── MessageLocal.kt       # Message (plaintext, ephemeral)
│       │   │   │   ├── FirebaseMessage.kt    # Message chiffré (Firebase)
│       │   │   │   └── RatchetState.kt       # État du ratchet par conversation
│       │   │   │
│       │   │   ├── remote/
│       │   │   │   └── FirebaseRelay.kt      # Auth anonyme + relay + ephemeral sync
│       │   │   │
│       │   │   └── repository/
│       │   │       └── ChatRepository.kt     # Source de vérité unique (Mutex)
│       │   │
│       │   ├── util/
│       │   │   ├── QrCodeGenerator.kt        # Génération QR codes (ZXing)
│       │   │   ├── ThemeManager.kt           # 5 thèmes (Midnight/Hacker/Phantom/Aurora/Daylight)
│       │   │   ├── AppLockManager.kt         # PIN, biométrie, auto-lock timeout
│       │   │   └── EphemeralManager.kt       # Durées éphémères (30s → 1 mois)
│       │   │
│       │   └── ui/
│       │       ├── onboarding/               # Création d'identité + backup + restauration
│       │       │   ├── OnboardingFragment.kt
│       │       │   ├── OnboardingViewModel.kt
│       │       │   ├── BackupPhraseFragment.kt
│       │       │   └── RestoreFragment.kt
│       │       ├── conversations/            # Liste des chats + demandes de contact
│       │       │   ├── ConversationsFragment.kt
│       │       │   ├── ConversationsViewModel.kt
│       │       │   ├── ConversationsAdapter.kt
│       │       │   └── ContactRequestsAdapter.kt
│       │       ├── addcontact/               # Scanner QR + saisie manuelle
│       │       │   ├── AddContactFragment.kt
│       │       │   ├── AddContactViewModel.kt
│       │       │   └── CustomScannerActivity.kt
│       │       ├── chat/                     # Messages E2E + bulles
│       │       │   ├── ChatFragment.kt
│       │       │   ├── ChatViewModel.kt
│       │       │   ├── MessagesAdapter.kt
│       │       │   ├── ConversationProfileFragment.kt
│       │       │   └── FingerprintFragment.kt
│       │       ├── profile/                  # QR code, copier/partager, supprimer
│       │       └── settings/                 # Hub paramètres + sous-écrans
│       │           ├── SettingsFragment.kt
│       │           ├── AppearanceFragment.kt
│       │           ├── NotificationsFragment.kt
│       │           ├── SecurityFragment.kt
│       │           ├── EphemeralSettingsFragment.kt
│       │           └── PinSetupDialogFragment.kt
│       │
│       └── res/
│           ├── anim/                         # 10 animations (slide, fade, bubble, cascade)
│           ├── drawable/                     # Bulles, badges, icônes, backgrounds
│           ├── layout/                       # 22 layouts XML (fragments + items)
│           ├── menu/                         # Menu conversations
│           ├── navigation/nav_graph.xml      # 15 destinations, transitions animées
│           ├── raw/bip39_english.txt         # Wordlist BIP-39 (2048 mots)
│           ├── values/                       # Couleurs, strings, thèmes, 22 attrs custom
│           └── values-night/                 # Couleurs dark mode
│
├── functions/                                # Firebase Cloud Function (push)
│   ├── index.js
│   ├── package.json
│   └── .gitignore
```

---

<div align="center">

[← Retour au README](../../README.md)

</div>
