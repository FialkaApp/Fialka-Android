<div align="right">
  🇫🇷 Français | <a href="../en/STRUCTURE.md">🇬🇧 English</a>
</div>

<div align="center">

# 📂 Structure du projet

<img src="https://img.shields.io/badge/Fragments-16-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Layouts-29-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/Animations-12-6A1B9A?style=for-the-badge" />

</div>

---

```
SecureChat/
├── .gitignore
├── LICENSE
├── README.md
├── SECURITY.md
├── firebase-rules.json
├── storage.rules                             # Règles Firebase Storage
├── build.gradle.kts                          # Config Gradle racine
├── settings.gradle.kts
├── gradle.properties
│
├── docs/                                     # Documentation détaillée
│   ├── fr/                                   # Documentation française
│   │   ├── ARCHITECTURE.md                   # Architecture, patterns, flux
│   │   ├── CRYPTO.md                         # Protocole cryptographique complet
│   │   ├── SETUP.md                          # Installation + config Firebase
│   │   ├── STRUCTURE.md                      # Ce fichier
│   │   └── CHANGELOG.md                      # Historique des versions
│   └── en/                                   # English documentation
│       ├── ARCHITECTURE.md
│       ├── CRYPTO.md
│       ├── SETUP.md
│       ├── STRUCTURE.md
│       └── CHANGELOG.md
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
│       │   │   ├── CryptoManager.kt          # X25519, ECDH, AES-256-GCM, HKDF, ML-KEM-1024 (PQXDH)
│       │   │   ├── DoubleRatchet.kt          # Full Double Ratchet (DH + KDF chains) + PQXDH upgrade
│       │   │   └── MnemonicManager.kt        # BIP-39 mnemonic encode/decode (24 mots)
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
│       │   │   │   ├── UserLocal.kt          # Identité locale
│       │   │   │   ├── Contact.kt            # Contact (pseudo + pubkey)
│       │   │   ├── Conversation.kt       # Conversation (ephemeral, fingerprint, lastDeliveredAt)
│       │   │   │   ├── MessageLocal.kt       # Message (plaintext, ephemeral)
│       │   │   │   ├── FirebaseMessage.kt    # Message chiffré (Firebase)
│       │   │   │   └── RatchetState.kt       # État du ratchet par conversation
│       │   │   │
│       │   │   ├── remote/
│       │   │   └── FirebaseRelay.kt      # Auth anonyme + relay + ephemeral sync + fingerprint events
│       │   │   │
│       │   │   └── repository/
│       │   │       └── ChatRepository.kt     # Source de vérité unique (Mutex)
│       │   │
│       │   ├── util/
│       │   │   ├── QrCodeGenerator.kt        # Génération QR codes (ZXing)
│       │   │   ├── SecureFileManager.kt      # Suppression sécurisée de fichiers (écrasement 2 passes : aléatoire + zéros)
│       │   │   ├── ThemeManager.kt           # 5 thèmes (Midnight/Hacker/Phantom/Aurora/Daylight)
│       │   │   ├── AppLockManager.kt         # PIN, biométrie, auto-lock timeout
│       │   │   ├── EphemeralManager.kt       # Durées éphémères (30s → 1 mois)
│       │   │   ├── DummyTrafficManager.kt    # Faux trafic (anti analyse de trafic)
│       │   │   └── DeviceSecurityManager.kt  # Sonde StrongBox, niveaux sécurité MAXIMUM/STANDARD
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
│       │           ├── PrivacyFragment.kt        # Sous-écran Confidentialité (dummy traffic, éphémère)
│       │           ├── EphemeralSettingsFragment.kt
│       │           └── PinSetupDialogFragment.kt
│       │
│       └── res/
│           ├── anim/                         # 10 animations (slide, fade, bubble, cascade)
│           ├── drawable/                     # Bulles, badges, icônes, backgrounds, ic_attach, ic_add
│           ├── layout/                       # 29 layouts XML (fragments + items)
│           ├── menu/                         # Menu conversations
│           ├── navigation/nav_graph.xml      # 16 destinations, transitions animées
│           ├── raw/bip39_english.txt         # Wordlist BIP-39 (2048 mots)
│           ├── xml/file_paths.xml            # FileProvider paths (partage fichiers)
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
