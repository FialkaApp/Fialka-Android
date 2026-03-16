<div align="center">

# 🔐 SecureChat

**Chat chiffré de bout en bout pour Android — gratuit, anonyme, sans serveur**

[![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple?logo=kotlin)](https://kotlinlang.org/)
[![Firebase](https://img.shields.io/badge/Relay-Firebase-orange?logo=firebase)](https://firebase.google.com/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![API](https://img.shields.io/badge/API-33%2B-brightgreen)](https://developer.android.com/about/versions/13)

<br/>

> Messages chiffrés **avant** d'être envoyés. Firebase ne voit que du bruit.
> Pas de numéro de téléphone. Pas d'email. Juste un pseudo et une clé.

</div>

---

## 📋 Table des matières

- [Fonctionnalités](#-fonctionnalités)
- [Screenshots](#-screenshots)
- [Architecture](#-architecture)
- [Protocole cryptographique](#-protocole-cryptographique)
- [Installation](#-installation)
- [Configuration Firebase](#-configuration-firebase)
- [Structure du projet](#-structure-du-projet)
- [Dépendances](#-dépendances)
- [Sécurité](#-sécurité)
- [Roadmap](#-roadmap)
- [Contribuer](#-contribuer)
- [Licence](#-licence)

---

## ✨ Fonctionnalités

| Fonctionnalité | Description |
|----------------|-------------|
| 🔐 **Chiffrement E2E** | X25519 ECDH + AES-256-GCM — personne ne lit vos messages |
| 🔄 **Perfect Forward Secrecy** | Double Ratchet (X25519 DH + KDF chains) — healing automatique |
| 📷 **QR Code** | Ajoutez un contact en scannant son QR code |
| ✏️ **Saisie manuelle** | Ajoutez un contact en collant sa clé publique |
| 📨 **Demandes de contact** | Système d'invitation : envoi → notification → accepter/refuser |
| ⏳ **Conversations en attente** | Les messages ne sont envoyés qu'après acceptation mutuelle |
| 📵 **Anonyme** | Pas de numéro, pas d'email — juste un pseudo + clé publique |
| 💰 **Gratuit** | Firebase Blaze (gratuit jusqu'à 2M invocations/mois) |
| 🔑 **Clé privée protégée** | Stockée dans EncryptedSharedPreferences (AES-256-GCM, Keystore-backed) |
| 🧹 **Auto-nettoyage** | Messages Firebase auto-supprimés après 7 jours |
| 🛡️ **Zéro fuite mémoire** | Toutes les clés intermédiaires sont zérorisées après usage |
| 📱 **Android 15 ready** | Support edge-to-edge natif (targetSdk 35) |
| 🔴 **Messages non lus** | Badge compteur sur la liste des conversations |
| 📍 **Marqueur "Nouveaux messages"** | Séparateur dans le chat pour repérer les messages non lus |
| 🔄 **Réception en temps réel** | Les messages arrivent même quand le chat n'est pas ouvert |
| 🔔 **Push notifications** | Opt-in — notifications FCM quand l'app est fermée (aucun contenu message) |
| 🔏 **Fingerprint emojis** | Empreinte partagée de 96 bits (16 emojis / 64 palette) — vérification anti-MITM |
| 👤 **Profil du contact** | Hub conversation : empreinte, éphémère, recherche, supprimer |
| 🕵️ **Metadata hardening** | senderPublicKey + messageIndex supprimés de Firebase — graphe social protégé |
| 🔒 **App Lock** | Code PIN 4 chiffres + déverrouillage biométrique (empreinte/visage) — opt-in |
| ⏱️ **Messages éphémères** | 10 durées (30s → 1 mois) — timer synchro Firebase entre les 2 utilisateurs |
| 🌙 **Dark mode** | Thème DayNight complet — couleurs adaptatives pour toutes les vues |
| ⚙️ **Paramètres avancés** | Hub avec 4 sous-écrans : Apparence, Notifications, Sécurité, Éphémère |
| 🎨 **Thème personnalisable** | Choix du mode : Système / Clair / Sombre |
| 🔐 **Auto-lock timeout** | Verrouillage automatique configurable (5s, 15s, 30s, 1min, 5min) |
| 🔑 **Mnemonic backup** | 24 mots BIP-39 pour sauvegarder/restaurer la clé d'identité X25519 |
| ♻️ **Restauration de compte** | Restaurer son identité sur un nouvel appareil via phrase mnémonique |
| 🗑️ **Suppression complète** | Suppression de compte nettoie Firebase (profil, conversations, inbox) |
| 💀 **Détection convo morte** | Détecte automatiquement les conversations supprimées par l'autre contact |
| 🔄 **Re-invitation contacts** | Possibilité de ré-inviter un contact qui a supprimé/restauré son compte |

---

## 📱 Screenshots

> 📸 *Les captures d'écran seront ajoutées dans une prochaine mise à jour.*

---

## 🏗 Architecture

```
┌─────────────────────────────────────────────┐
│                  UI Layer                    │
│       Fragments · ViewModels · Adapters      │
├─────────────────────────────────────────────┤
│              Repository Layer                │
│     ChatRepository — source de vérité unique │
├──────────────┬───────────────┬──────────────┤
│   Room DB    │    Crypto     │   Firebase   │
│   (local)    │  CryptoMgr +  │   Relay +    │
│              │   Ratchet     │    FCM       │
└──────────────┴───────────────┴──────────────┘
         Cloud Function (push triggers)
```

### Push Notifications (opt-in)

```
Phone A → sendMessage() → Firebase RTDB
                              ↓
                   Cloud Function (onCreate)
                              ↓
                   /users/{uid}/fcm_token ?
                              ↓ (si token existe)
                   FCM → Phone B notification
                   "Nouveau message de Alice"
                   (ZÉRO contenu de message)
```

### Principes

- **Single Activity** — Navigation Component avec fragments
- **Repository Pattern** — `ChatRepository` coordonne Room, Crypto et Firebase
- **Aucun accès Firebase depuis l'UI** — tout passe par Repository → FirebaseRelay
- **Mutex par conversation** — les opérations ratchet sont sérialisées (thread-safe)
- **Envoi atomique** — le ratchet n'avance que si Firebase confirme l'envoi
- **Système d'invitation** — demande de contact → notification inbox → acceptation → conversation active

| Couche | Rôle | Fichiers clés |
|--------|------|---------------|
| **UI** | Écrans, navigation, interactions | `ui/` — Fragments, ViewModels, Adapters |
| **Repository** | Coordination local/crypto/remote | `data/repository/ChatRepository.kt` |
| **Crypto** | X25519, ECDH, AES-GCM, Double Ratchet, BIP-39 | `crypto/CryptoManager.kt`, `crypto/DoubleRatchet.kt`, `crypto/MnemonicManager.kt` |
| **Local DB** | Room v10 — users, contacts, messages, ratchet | `data/local/` — DAOs, Database (SQLCipher) |
| **Remote** | Relay Firebase (ciphertext only) | `data/remote/FirebaseRelay.kt` |
| **Util** | QR codes, thèmes, app lock, éphémère | `util/QrCodeGenerator.kt`, `ThemeManager.kt`, `AppLockManager.kt`, `EphemeralManager.kt` |

### Flux des demandes de contact

```
Alice                              Firebase                              Bob
  │                                   │                                    │
  │  1. Scan QR / colle clé pub       │                                    │
  │  2. createConversation(pending)   │                                    │
  │  3. sendContactRequest ──────────►│ inbox/{bob_hash}/{convId}          │
  │                                   │                                    │
  │                                   │   listenForContactRequests() ◄─────│
  │                                   │                                    │
  │                                   │         4. "Nouvelle demande de    │
  │                                   │             Alice" s'affiche       │
  │                                   │                                    │
  │                                   │◄── 5. acceptContactRequest() ──────│
  │                                   │    notifyRequestAccepted()         │
  │                                   │                                    │
  │   listenForAcceptances() ◄────────│ accepted/{convId}                  │
  │   6. markConversationAccepted()   │                                    │
  │                                   │                                    │
  │◄═══════════ Chat E2E actif ══════►│◄══════════════════════════════════►│
```

### Cycle de vie d'un compte

```
Création :
  Onboarding → generateIdentityKeyPair() → registerPublicKey() → BackupPhrase (24 mots)

Backup (BIP-39, 24 mots) :
  privateKey (32 bytes) → SHA-256 → 1er octet = checksum → 33 bytes → 24 × 11 bits → 24 mots

Restauration :
  24 mots → mnemonicToPrivateKey() → restoreIdentityKey() → DH(privKey, basePoint u=9) → pubKey
  → removeOldUserByPublicKey() → registerPublicKey() → prêt

Suppression de compte (A supprime) :
  A: deleteUserProfile(/users/{uid}) + deleteInbox(/inbox/{hash}) + deleteConversation(toutes)
  B: envoie message → Permission Denied → isConversationAliveOnFirebase()=false → AlertDialog
  B: re-invite A → dead convo détectée → deleteStaleConversation() → nouvelle invitation

Réception d'invitation (convo locale stale) :
  Inbox listener → conversation locale existe? → isConversationAliveOnFirebase()
  → si morte: deleteStaleConversation() → affiche comme nouvelle demande
```

---

## 🔐 Protocole cryptographique

### Vue d'ensemble

```
Alice                                         Bob
  │                                             │
  │◄──────── Échange clés publiques ──────────►│
  │           (QR code ou copier/coller)        │
  │                                             │
  │  shared_secret = X25519(sk_A, pk_B)         │  shared_secret = X25519(sk_B, pk_A)
  │  root_key = HKDF(shared_secret)            │  root_key = HKDF(shared_secret)
  │  send_chain = HKDF(root, "init-send")      │  recv_chain = HKDF(root, "init-send")
  │  recv_chain = HKDF(root, "init-recv")      │  send_chain = HKDF(root, "init-recv")
  │                                             │
  │  msg_key = HMAC(send_chain, 0x01)          │
  │  send_chain = HMAC(send_chain, 0x02)       │
  │  ct = AES-GCM(msg_key, iv, plaintext)      │
  │                                             │
  │  ──── {ct, iv, ephemeralKey} ──────────────►│
  │           (Firebase relay)                  │
  │                                             │  msg_key = HMAC(recv_chain, 0x01)
  │                                             │  recv_chain = HMAC(recv_chain, 0x02)
  │                                             │  plaintext = AES-GCM-dec(msg_key, iv, ct)
```

### Identité

1. Génération d'une paire **X25519** au premier lancement
2. Clé privée → **EncryptedSharedPreferences** (AES-256-GCM, Android Keystore-backed)
3. Clé publique → Base64 + QR code pour partage
4. Backup : clé privée → **24 mots BIP-39** (256 bits + 8-bit checksum SHA-256)
5. Restauration : 24 mots → clé privée → dérivation clé publique (DH avec point de base X25519 u=9)

### Échange de clés

1. Alice montre son **QR code** (ou partage sa clé publique)
2. Bob scanne le QR → entre le pseudo d'Alice → crée le contact
3. Chaque côté calcule : `shared_secret = X25519(ma_clé_privée, clé_publique_contact)`
4. Le rôle (initiator/responder) est déterminé par l'**ordre lexicographique** des clés publiques

### Fingerprint Emojis (96-bit, anti-MITM)

Chaque conversation a une **empreinte partagée** calculée à partir des deux clés publiques :

```
sorted_keys = sort_lexicographic(pubKeyA, pubKeyB)
hash = SHA-256(sorted_keys[0] + sorted_keys[1])
fingerprint = 16 emojis choisis dans une palette de 64
            = 16 × log2(64) = 96 bits d'entropie
```

**Format :** `🔥🐱🦄🍕 🌟🚀💎⚡ 🎸📱🔔🎉 🌈🐶🎯🍀` (4 × 4 emojis)

Les deux téléphones calculent la **même** empreinte. L'utilisateur compare visuellement (en personne ou par appel vidéo) pour détecter une attaque MITM.

- ✅ Palette de 64 emojis (puissance de 2 → zéro biais modulo)
- ✅ 96 bits d'entropie (7.9 × 10²⁸ combinaisons)
- ✅ Badge dans le chat : vert (vérifié ✓) ou orange (non vérifié ➤)
- ✅ Persisté en Room, état de vérification par conversation

### Double Ratchet (PFS + Healing)

```
Initialisation (à l'acceptation du contact) :
  root_key     = HKDF(shared_secret, "SecureChat-DR-root")
  send_chain   = HKDF(root_key, "SecureChat-DR-chain-init-send")
  recv_chain   = HKDF(root_key, "SecureChat-DR-chain-init-recv")  (swapped pour responder)
  ephemeral    = X25519.generateKeyPair()

Pour chaque message N (KDF chain) :
  message_key[N]  = HMAC-SHA256(chain_key[N], 0x01)   ← clé unique
  chain_key[N+1]  = HMAC-SHA256(chain_key[N], 0x02)   ← avancement irréversible

DH Ratchet (healing) — quand l'éphémère remote change :
  dh_secret    = X25519(local_ephemeral_priv, remote_ephemeral_pub)
  new_root_key = HKDF(root_key || dh_secret, "root-ratchet")
  new_chain    = HKDF(root_key || dh_secret, "chain-ratchet")
  → Nouvelle clé éphémère locale générée

  plaintext → AES-256-GCM(message_key[N], random_iv_12B) → ciphertext
```

**Propriétés :**
- ✅ Chaque message a sa propre clé de chiffrement (KDF chain)
- ✅ L'avancement de la chaîne est **irréversible** (one-way function)
- ✅ **Healing** : compromission d'une chain key → DH ratchet guérit au prochain échange
- ✅ Compromettre la clé actuelle **ne révèle pas** les clés passées
- ✅ Les clés intermédiaires sont **zérorisées** de la mémoire après usage
- ✅ Clés éphémères X25519 renouvelées à chaque changement de direction

### Ce qui transite sur Firebase

**Messages (chiffrés) :**
```json
{
  "ciphertext": "a3F4bWx...",
  "iv": "dG9rZW4...",
  "createdAt": 1700000000000,
  "senderUid": "firebase-anonymous-uid"
}
```

**Paramètres éphémères (synchro entre les 2 utilisateurs) :**
```
/conversations/{id}/settings/ephemeralDuration = 3600000
```

**Supprimé du wire format (V1.1 metadata hardening) :**
- `senderPublicKey` — inutile en 1-to-1 (le destinataire connaît déjà la clé du contact)
- `messageIndex` — chiffré dans le payload AES-GCM (trial decryption côté réception)

**Jamais envoyé :** texte en clair, clés privées, clés de chaîne, position du ratchet.

### Demande de contact (inbox Firebase)

```json
{
  "senderPublicKey": "MFkwEwYHKoZ...",
  "senderDisplayName": "Alice",
  "conversationId": "conv_abc123",
  "createdAt": 1700000000000
}
```

**Flux :** Alice envoie une demande → apparaît dans l'inbox de Bob → Bob accepte ou refuse → si accepté, les deux côtés peuvent chatter.

### Modèle de menace

| Menace | Protégé ? | Détail |
|--------|-----------|--------|
| Firebase lit les messages | ✅ | Chiffré E2E, Firebase ne voit que du ciphertext |
| Compromission d'une clé message | ✅ | PFS — chaque message a sa propre clé |
| Replay d'anciens messages | ✅ | sinceTimestamp + messageIndex (embedded dans ciphertext) |
| Race conditions ratchet | ✅ | Mutex par conversation |
| Attaque MITM | ✅ | Fingerprint emojis 96-bit (vérification visuelle) |
| Vol du téléphone déverrouillé | ✅ | Keystore, SQLCipher, App Lock PIN + biométrie, auto-lock |
| Messages sensibles oubliés | ✅ | Messages éphémères (timer sur envoi / lecture) |
| Métadonnées (qui/quand) | ⚠️ | senderPublicKey + messageIndex supprimés ; senderUid + timestamps restent |
| Perte du téléphone | ✅ | Phrase mnémonique 24 mots (BIP-39) pour restaurer l'identité |
| Contact supprime son compte | ✅ | Détection automatique conversation morte + nettoyage + re-invitation |

> Voir [`SECURITY.md`](SECURITY.md) pour l'analyse complète.

---

## 🛠 Installation

### Prérequis

- **Android Studio** Hedgehog (2023.1.1) ou plus récent
- **JDK 17**
- Un projet **Firebase** (plan Blaze — gratuit jusqu'à 2M invocations/mois)
- **Node.js 18+** (pour déployer la Cloud Function)

### Cloner le repo

```bash
git clone https://github.com/DevBot667/SecureChat.git
cd SecureChat
```

### Configurer Firebase

> Voir la section [Configuration Firebase](#-configuration-firebase) ci-dessous.

### Compiler

```bash
./gradlew assembleDebug
```

Ou ouvrir dans Android Studio → **Run** sur un émulateur ou device physique.

---

## 🔥 Configuration Firebase

### Étape 1 — Créer le projet

1. Aller sur [Firebase Console](https://console.firebase.google.com/)
2. **Créer un projet** (désactiver Google Analytics si souhaité)
3. **Ajouter une app Android** :
   - Package : `com.securechat`
   - Télécharger `google-services.json`

### Étape 2 — Configurer

1. **Copier** `google-services.json` dans `app/`
2. Firebase Console → **Authentication** → Méthode de connexion → **Anonyme** → Activer
3. Firebase Console → **Realtime Database** → Créer (région la plus proche)
4. Onglet **Règles** → coller le contenu de [`firebase-rules.json`](firebase-rules.json)

### Étape 3 — Déployer la Cloud Function (push notifications)

```bash
# Installer Firebase CLI
npm install -g firebase-tools

# Se connecter
firebase login

# Depuis la racine du projet
cd functions
npm install
cd ..
firebase deploy --only functions
```

La Cloud Function se déclenche automatiquement à chaque nouveau message et envoie un push aux destinataires qui ont activé les notifications.

> ⚠️ **`google-services.json` est dans le `.gitignore`** — il ne sera jamais poussé sur GitHub.
> Le fichier `app/google-services.json.template` montre la structure attendue.

---

## 📂 Structure du projet

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
│       │   │   ├── CryptoManager.kt          # X25519, ECDH, AES-256-GCM, HKDF, backup/restore
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
│       │   │   │   ├── Conversation.kt       # Conversation (ephemeral, fingerprint, etc.)
│       │   │   │   ├── MessageLocal.kt       # Message (plaintext, ephemeral)
│       │   │   │   ├── FirebaseMessage.kt    # Message chiffré (Firebase)
│       │   │   │   └── RatchetState.kt       # État du ratchet par conversation
│       │   │   │
│       │   │   ├── remote/
│       │   │   │   └── FirebaseRelay.kt      # Auth anonyme + relay + ephemeral sync
│       │   │   │
│       │   │   └── repository/
│       │   │       └── ChatRepository.kt     # Source de vérité unique (Mutex-protected)
│       │   │
│       │   ├── util/
│       │   │   ├── QrCodeGenerator.kt        # Génération QR codes (ZXing)
│       │   │   ├── ThemeManager.kt           # Gestion DayNight (Système/Clair/Sombre)
│       │   │   ├── AppLockManager.kt         # PIN, biométrie, auto-lock timeout
│       │   │   └── EphemeralManager.kt       # Durées éphémères (30s → 1 mois)
│       │   │
│       │   └── ui/
│       │       ├── onboarding/               # Création d'identité + backup + restauration
│       │       │   ├── OnboardingFragment.kt
│       │       │   ├── OnboardingViewModel.kt
│       │       │   ├── BackupPhraseFragment.kt   # Affiche les 24 mots après création
│       │       │   └── RestoreFragment.kt        # Restauration via phrase mnémonique
│       │       ├── conversations/            # Liste des chats + demandes de contact
│       │       │   ├── ConversationsFragment.kt
│       │       │   ├── ConversationsViewModel.kt
│       │       │   ├── ConversationsAdapter.kt
│       │       │   └── ContactRequestsAdapter.kt
│       │       ├── addcontact/               # Scanner QR + saisie manuelle
│       │       ├── chat/                     # Messages E2E + bulles
│       │       │   ├── ChatFragment.kt
│       │       │   ├── ChatViewModel.kt
│       │       │   ├── MessagesAdapter.kt    # Bulles sent/received + divider
│       │       │   ├── ConversationProfileFragment.kt  # Hub profil conversation
│       │       │   └── FingerprintFragment.kt          # Vérification empreinte emojis
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
│           ├── anim/                         # Animations de transition
│           ├── drawable/                     # Bulles, badges, icônes, backgrounds
│           ├── layout/                       # 22 layouts XML (fragments + items)
│           ├── menu/                         # Menu conversations (profil, settings, reset)
│           ├── navigation/nav_graph.xml      # 15 destinations
│           ├── raw/bip39_english.txt         # Wordlist BIP-39 (2048 mots)
│           ├── values/                       # Couleurs, strings, thèmes (mode clair)
│           └── values-night/                 # Couleurs dark mode
│
├── functions/                                # Firebase Cloud Function (push)
│   ├── index.js
│   ├── package.json
│   └── .gitignore
```

---

## 📦 Dépendances

| Dépendance | Version | Usage |
|------------|---------|-------|
| Kotlin | 2.1.0 | Langage |
| AndroidX Core / AppCompat / Material | Latest | UI Material Design |
| AndroidX Navigation | 2.8.9 | Navigation single-activity |
| AndroidX Lifecycle | 2.8.7 | ViewModels, LiveData, coroutines |
| Room + KSP | 2.7.1 | Base de données locale SQLite |
| SQLCipher | 4.5.4 | Chiffrement AES-256 de la base Room |
| Firebase BOM | 34.10.0 | Auth anonyme + Realtime Database + Cloud Messaging |
| firebase-functions (Node.js) | 7.0.0 | Cloud Function trigger (push notifications) |
| firebase-admin (Node.js) | 13.6.0 | Admin SDK pour RTDB + FCM côté serveur |
| AndroidX Security Crypto | 1.1.0-alpha06 | Stockage sécurisé |
| AndroidX Biometric | 1.1.0 | BiometricPrompt (empreinte, visage) |
| Kotlinx Coroutines | 1.9.0 | Async + Flow |
| ZXing Android Embedded | 4.3.0 | Génération et scan QR codes |

---

## 🔒 Sécurité

### Mesures implémentées

- ✅ **Zeroing mémoire** — Toutes les clés intermédiaires (`sharedSecret`, `chainKey`, `messageKey`, `plaintext bytes`, `IV`) sont remplies de zéros après usage
- ✅ **SecureRandom singleton** — IV de 12 octets, jamais réutilisé
- ✅ **Envoi atomique** — Le ratchet state n'est persisté qu'après confirmation Firebase
- ✅ **Mutex par conversation** — Pas de race condition sur le ratchet (mutex partagés entre instances)
- ✅ **Push opt-in** — Notifications désactivées par défaut, aucun token FCM stocké tant que l'utilisateur n'active pas
- ✅ **Zéro contenu dans les push** — Seul le nom de l'expéditeur est inclus, jamais le contenu du message
- ✅ **Token FCM supprimable** — Désactiver les push supprime immédiatement le token de Firebase
- ✅ **Re-auth Firebase** — Session anonyme restaurée après app kill
- ✅ **TTL 7 jours** — Les vieux messages sont auto-supprimés de Firebase
- ✅ **Anti-replay** — Filtrage par `sinceTimestamp` + messageIndex (embedded dans ciphertext)
- ✅ **Metadata hardening** — `senderPublicKey` et `messageIndex` supprimés du wire format Firebase
- ✅ **Trial decryption** — messageIndex chiffré dans le payload AES-GCM (MAX_SKIP=100)
- ✅ **Fingerprint emojis** — Empreinte partagée 96-bit (64 emojis, 16 positions, zéro biais modulo)
- ✅ **Anti-MITM** — Vérification visuelle badge vert/orange, état persisté par conversation
- ✅ **SQLCipher** — Base Room chiffrée AES-256 (passphrase 256-bit stockée dans EncryptedSharedPreferences / Keystore)
- ✅ **Pas de logs sensibles** — Clés et plaintexts retirés de Logcat
- ✅ **Keystore delete on reset** — Clé privée supprimée quand on supprime le compte
- ✅ **`allowBackup=false`** — Pas de backup automatique des données
- ✅ **App Lock** — Code PIN 4 chiffres (SHA-256) + biométrie opt-in (BiometricPrompt STRONG|WEAK)
- ✅ **PIN hash sécurisé** — SHA-256, stocké dans EncryptedSharedPreferences (Keystore-backed)
- ✅ **Auto-lock timeout** — Configurable (5s, 15s, 30s, 1min, 5min), défaut 5 secondes
- ✅ **Messages éphémères** — Timer côté envoi (immédiat) + côté lecture (activé quand le chat s'ouvre)
- ✅ **Ephemeral sync** — Durée éphémère synchro Firebase entre les 2 participants
- ✅ **Firebase security rules** — Lecture/écriture restreinte aux participants (messages, settings, delete conversation)
- ✅ **Dark mode** — Thème DayNight avec couleurs adaptatives (bg, bulles, badges)
- ✅ **BIP-39 mnemonic** — 24 mots pour backup/restauration de la clé privée X25519 (256 bits + checksum SHA-256)
- ✅ **Account lifecycle** — Suppression nettoie Firebase (profil, inbox, conversations), détection convo morte, re-invitation contact

### Limites connues

> Voir [`SECURITY.md`](SECURITY.md) pour l'analyse détaillée.

---

## 🗺 Roadmap

### ✅ V1 — Done

- [x] Chiffrement E2E (X25519 ECDH + AES-256-GCM)
- [x] Perfect Forward Secrecy (Double Ratchet X25519)
- [x] QR Code (génération + scan)
- [x] Saisie manuelle de clé publique
- [x] Demandes de contact (envoi, notification inbox, accepter/refuser)
- [x] Conversations en attente (pending → accepted)
- [x] Notification d'acceptation en temps réel
- [x] Profil (pseudo modifiable, copier/partager clé)
- [x] Suppression de compte complète
- [x] Design WhatsApp-like
- [x] Anti-doublons + anti-replay
- [x] TTL Firebase (7 jours)
- [x] Hardening crypto (zeroing, mutex, atomic send)
- [x] Support Android 15 edge-to-edge (targetSdk 35)
- [x] Re-authentification Firebase automatique après app kill
- [x] Badge messages non lus sur la liste des conversations
- [x] Marqueur "Nouveaux messages" dans le chat (disparaît après lecture)
- [x] Réception des messages en temps réel sur la liste des conversations
- [x] Push notifications FCM opt-in (Cloud Function + zéro contenu message)
- [x] Écran Paramètres (push ON/OFF, token supprimable)
- [x] Fingerprint emojis 96-bit (64 palette × 16 positions, anti-MITM)
- [x] Profil du contact (empreinte, vérification manuelle, badge chat)
- [x] SQLCipher — Chiffrement de la base Room locale (256-bit, EncryptedSharedPreferences)
- [x] Metadata hardening — senderPublicKey + messageIndex supprimés de Firebase (trial decryption)
- [x] App Lock — Code PIN 4 chiffres + déverrouillage biométrique opt-in
- [x] Profil amélioré — Cards, en-tête avatar, zone danger, UX modernisée
- [x] Paramètres améliorés — Sections verrouillage / notifications / sécurité
- [x] Messages éphémères — Timer côté envoi + côté lecture, durée synchro Firebase
- [x] Dark mode — Thème DayNight complet, couleurs adaptatives
- [x] Auto-lock timeout — Configurable (5s → 5min), défaut 5 secondes
- [x] Sous-écran Fingerprint — Visualisation + vérification dédiée
- [x] Profil contact redesign — Hub conversation (éphémère, fingerprint, danger zone)

### ✅ V2 — Done (Crypto Upgrade)

- [x] **Full Double Ratchet X25519** — DH ratchet + KDF chains + healing automatique
- [x] **X25519 natif** — Courbe Curve25519 (API 33+), remplace P-256
- [x] **Initial chains** — Les deux côtés peuvent envoyer immédiatement après acceptation
- [x] **Échange d'éphémères naturel** — Via les vrais messages, pas de message bootstrap

### ✅ V2.1 — Done (Account Lifecycle)

- [x] **Phrase mnémonique BIP-39** — Backup de la clé privée X25519 en 24 mots (256 bits + 8-bit checksum SHA-256)
- [x] **Backup après création** — Écran dédié affiche les 24 mots en grille 3 colonnes (confirmation checkbox)
- [x] **Restauration de compte** — Saisie de 24 mots + pseudo → restaure clé privée → dérive clé publique (DH base point u=9)
- [x] **Suppression compte complète** — Nettoie Firebase : profil `/users/{uid}`, `/inbox/{hash}`, `/conversations/{id}` (toutes, pas seulement acceptées)
- [x] **Nettoyage ancien profil** — `removeOldUserByPublicKey()` supprime l'ancien nœud `/users/` orphelin lors d'une restauration
- [x] **Détection conversation morte** — Quand B envoie un message dans une conversation supprimée → AlertDialog clair ("Conversation supprimée") avec option supprimer
- [x] **Re-invitation contact** — Si la conversation Firebase est morte, le contact local stale est nettoyé (messages + ratchet + contact) pour permettre re-invitation
- [x] **Auto-détection à la réception** — L'inbox listener vérifie si une invitation concerne une conversation locale stale → nettoyage automatique → affiche la nouvelle demande
- [x] **Firebase rules conversation** — `.read` et `.write` (delete only: `!newData.exists()`) au niveau `$conversationId` pour les participants

### 🔜 V3 — Planned

- [ ] **Signature ECDSA** — Clé de signature dédiée (PURPOSE_SIGN) pour authentifier chaque message
- [ ] **Groupes** — Conversations à 3+ participants
- [ ] **Suppression pour tous** — Supprimer un message côté local + Firebase
- [ ] **Typing indicators** — "En train d'écrire..."
- [ ] **Relay privé** — Serveur relay dédié pour réduire la dépendance Firebase

---

## 🤝 Contribuer

1. Fork le repo
2. Crée ta branche (`git checkout -b feature/ma-feature`)
3. Commit (`git commit -m 'Ajout de ma feature'`)
4. Push (`git push origin feature/ma-feature`)
5. Ouvre une **Pull Request**

> ⚠️ Pour toute modification crypto, ouvrir une **issue** d'abord pour discussion.

---

## 📄 Licence

Ce projet est sous licence [MIT](LICENSE).

Fourni à des fins **éducatives**. Utilisez-le comme base pour comprendre le chiffrement E2E sur mobile.

---

<div align="center">

**Fait avec 🔐 et ☕ — SecureChat V2.1**

*"Vos messages, vos clés, votre vie privée."*

</div>
