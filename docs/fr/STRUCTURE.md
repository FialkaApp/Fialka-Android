<div align="right">
  🇫🇷 Français | <a href="../en/STRUCTURE.md">🇬🇧 English</a>
</div>

<div align="center">

# 📂 Structure du projet

<img src="https://img.shields.io/badge/Fragments-21-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Layouts-43-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/Animations-14-6A1B9A?style=for-the-badge" />

</div>

---

```
Fialka/
├── .gitignore
├── .gitmodules
├── LICENSE
├── README.md
├── PRIVACY.md
├── TERMS.md
├── SECURITY.md
├── build.gradle.kts                          # Config Gradle racine
├── settings.gradle.kts
├── gradle.properties
│
├── Fialka-Core/                              # Submodule Rust — bibliothèque native
│   ├── src/ffi/mod.rs                        # 30 fonctions JNI extern C
│   ├── Cargo.toml
│   └── ...                                   # Crypto Rust (AES, ChaCha20, Ed25519, ML-KEM, ML-DSA...)
│
├── docs/                                     # Documentation détaillée
│   ├── fr/                                   # Documentation française
│   │   ├── ARCHITECTURE.md                   # Architecture, patterns, flux
│   │   ├── CRYPTO.md                         # Protocole cryptographique complet
│   │   ├── SETUP.md                          # Installation + compilation
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
│   │
│   └── src/main/
│       ├── AndroidManifest.xml
│       │
│       ├── java/com/fialkaapp/fialka/
│       │   ├── FialkaApplication.kt      # Init App + bootstrap Tor
│       │   ├── MainActivity.kt               # Single-activity (NavHost)
│       │   ├── LockScreenActivity.kt         # Écran de verrouillage PIN + biométrie
│       │   │
│       │   ├── crypto/
│       │   │   ├── CryptoManager.kt          # Orchestration crypto — délègue à FialkaNative (Rust JNI)
│       │   │   ├── FialkaNative.kt           # Pont JNI — 30 fonctions extern vers libfialka_core.so
│       │   │   ├── DoubleRatchet.kt          # Full Double Ratchet (DH + KDF chains) + PQXDH upgrade
│       │   │   └── MnemonicManager.kt        # BIP-39 mnemonic encode/decode (24 mots)
│       │   │
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── FialkaDatabase.kt # Room DB v24 (SQLCipher)
│       │   │   │   ├── UserLocalDao.kt
│       │   │   │   ├── ContactDao.kt
│       │   │   │   ├── ConversationDao.kt
│       │   │   │   ├── MessageLocalDao.kt
│       │   │   │   ├── OutboxDao.kt          # Messages en attente + `getExhaustedMessages()`
│       │   │   │   └── RatchetStateDao.kt
│       │   │   │
│       │   │   ├── model/
│       │   │   │   ├── UserLocal.kt          # Identité locale
│       │   │   │   ├── Contact.kt            # Contact (pseudo + pubkey)
│       │   │   ├── Conversation.kt       # Conversation (ephemeral, fingerprint, lastDeliveredAt)
│       │   │   │   ├── MessageLocal.kt       # Message (plaintext, ephemeral)
│       │   │   ├── EncryptedMessage.kt   # Message chiffré (format réseau)
│       │   │   └── RatchetState.kt       # État du ratchet par conversation
│       │   │   │
│       │   │   ├── remote/
│       │   │   └── TorTransport.kt       # Transport Tor Hidden Services P2P + Mailbox
│       │   │   │
│       │   │   └── repository/
│       │   │       └── ChatRepository.kt     # Source de vérité unique (Mutex)
│       │   │
│       │   ├── util/
│       │   │   ├── QrCodeGenerator.kt        # Génération QR codes (ZXing)
│       │   │   ├── SecureFileManager.kt      # Suppression sécurisée de fichiers (écrasement 2 passes : aléatoire + zéros)
│       │   │   ├── FialkaSecurePrefs.kt      # Stockage sécurisé direct Android Keystore AES-256-GCM (remplace security-crypto)
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
│       │       │   ├── RestoreFragment.kt
│       │       │   └── SeedVerificationFragment.kt  # Confirmation 3 mots après backup
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
│       │           ├── SettingsAdapter.kt            # RecyclerView settings avec recherche + filtres catégorie
│       │           ├── SettingsViewModel.kt          # StateFlow settings items
│       │           ├── AppearanceFragment.kt
│       │           ├── NotificationsFragment.kt
│       │           ├── SecurityFragment.kt
│       │           ├── PrivacyFragment.kt        # Sous-écran Confidentialité (dummy traffic, éphémère)
│       │           ├── EphemeralSettingsFragment.kt
│       │           ├── PinSetupDialogFragment.kt
│       │           ├── ThemeSelectorBottomSheet.kt   # Sélecteur visuel 5 thèmes
│       │           └── DurationSelectorBottomSheet.kt # Sélecteur durée messages éphémères
│       │
│       └── res/
│           ├── anim/                         # 14 animations (slide, fade, bubble, cascade, bottom sheet)
│           ├── drawable/                     # Bulles, badges, icônes, backgrounds, brand orbs, panels
│           ├── layout/                       # 43 layouts XML (fragments + items + bottom sheets)
│           ├── menu/                         # Menu conversations
│           ├── navigation/nav_graph.xml      # 21 destinations, transitions animées
│           ├── raw/bip39_english.txt         # Wordlist BIP-39 (2048 mots)
│           ├── xml/file_paths.xml            # FileProvider paths (partage fichiers)
│           ├── values/                       # Couleurs, strings, thèmes, 22 attrs custom
│           └── values-night/                 # Couleurs dark mode
```

---

<div align="center">

[← Retour au README](../../README.md)

</div>
