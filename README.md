<div align="center">

# 🔐 SecureChat

**Chat chiffré de bout en bout pour Android — gratuit, anonyme, sans serveur**

[![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple?logo=kotlin)](https://kotlinlang.org/)
[![Firebase](https://img.shields.io/badge/Relay-Firebase-orange?logo=firebase)](https://firebase.google.com/)
[![License](https://img.shields.io/badge/License-MIT-blue)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen)](https://developer.android.com/about/versions/oreo)

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
| 🔐 **Chiffrement E2E** | ECDH P-256 + AES-256-GCM — personne ne lit vos messages |
| 🔄 **Perfect Forward Secrecy** | Symmetric Ratchet — chaque message = clé unique et irréversible |
| 📷 **QR Code** | Ajoutez un contact en scannant son QR code |
| 📵 **Anonyme** | Pas de numéro, pas d'email — juste un pseudo + clé publique |
| 💰 **Gratuit** | Firebase Spark (plan gratuit) comme simple relay |
| 🔑 **Keystore Android** | Clé privée en hardware (TEE/StrongBox quand disponible) |
| 🧹 **Auto-nettoyage** | Messages Firebase auto-supprimés après 7 jours |
| 🛡️ **Zéro fuite mémoire** | Toutes les clés intermédiaires sont zérorisées après usage |

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
│   (local)    │  CryptoMgr +  │    Relay     │
│              │   Ratchet     │   (remote)   │
└──────────────┴───────────────┴──────────────┘
```

### Principes

- **Single Activity** — Navigation Component avec fragments
- **Repository Pattern** — `ChatRepository` coordonne Room, Crypto et Firebase
- **Aucun accès Firebase depuis l'UI** — tout passe par Repository → FirebaseRelay
- **Mutex par conversation** — les opérations ratchet sont sérialisées (thread-safe)
- **Envoi atomique** — le ratchet n'avance que si Firebase confirme l'envoi

| Couche | Rôle | Fichiers clés |
|--------|------|---------------|
| **UI** | Écrans, navigation, interactions | `ui/` — Fragments, ViewModels, Adapters |
| **Repository** | Coordination local/crypto/remote | `data/repository/ChatRepository.kt` |
| **Crypto** | Clés, ECDH, AES-GCM, Ratchet | `crypto/CryptoManager.kt`, `crypto/SymmetricRatchet.kt` |
| **Local DB** | Room — users, contacts, messages, ratchet | `data/local/` — DAOs, Database |
| **Remote** | Relay Firebase (ciphertext only) | `data/remote/FirebaseRelay.kt` |
| **Util** | QR codes | `util/QrCodeGenerator.kt` |

---

## 🔐 Protocole cryptographique

### Vue d'ensemble

```
Alice                                         Bob
  │                                             │
  │◄──────── Échange clés publiques ──────────►│
  │           (QR code ou copier/coller)        │
  │                                             │
  │  shared_secret = ECDH(sk_A, pk_B)          │  shared_secret = ECDH(sk_B, pk_A)
  │  root_key = HKDF(shared_secret)            │  root_key = HKDF(shared_secret)
  │  send_chain = HKDF(root, "chain-A")        │  recv_chain = HKDF(root, "chain-A")
  │  recv_chain = HKDF(root, "chain-B")        │  send_chain = HKDF(root, "chain-B")
  │                                             │
  │  msg_key = HMAC(send_chain, 0x01)          │
  │  send_chain = HMAC(send_chain, 0x02)       │
  │  ct = AES-GCM(msg_key, iv, plaintext)      │
  │                                             │
  │  ──── {ct, iv, index, pubkey} ────────────►│
  │           (Firebase relay)                  │
  │                                             │  msg_key = HMAC(recv_chain, 0x01)
  │                                             │  recv_chain = HMAC(recv_chain, 0x02)
  │                                             │  plaintext = AES-GCM-dec(msg_key, iv, ct)
```

### Identité

1. Génération d'une paire **EC P-256** (secp256r1) au premier lancement
2. Clé privée → **Android Keystore** (hardware-backed TEE/StrongBox)
3. Clé publique → Base64 + QR code pour partage

### Échange de clés

1. Alice montre son **QR code** (ou partage sa clé publique)
2. Bob scanne le QR → entre le pseudo d'Alice → crée le contact
3. Chaque côté calcule : `shared_secret = ECDH(ma_clé_privée, clé_publique_contact)`
4. Le rôle (initiator/responder) est déterminé par l'**ordre lexicographique** des clés publiques

### Symmetric Ratchet (PFS)

```
Pour chaque message N :
  message_key[N]  = HMAC-SHA256(chain_key[N], 0x01)   ← clé unique
  chain_key[N+1]  = HMAC-SHA256(chain_key[N], 0x02)   ← avancement irréversible

  plaintext → AES-256-GCM(message_key[N], random_iv_12B) → ciphertext
```

**Propriétés :**
- ✅ Chaque message a sa propre clé de chiffrement
- ✅ L'avancement de la chaîne est **irréversible** (one-way function)
- ✅ Compromettre la clé actuelle **ne révèle pas** les clés passées
- ✅ Les clés intermédiaires sont **zérorisées** de la mémoire après usage

### Ce qui transite sur Firebase

```json
{
  "senderPublicKey": "MFkwEwYHKoZ...",
  "ciphertext": "a3F4bWx...",
  "iv": "dG9rZW4...",
  "messageIndex": 42,
  "createdAt": 1700000000000
}
```

**Jamais envoyé :** texte en clair, clés privées, clés de chaîne.

### Modèle de menace

| Menace | Protégé ? | Détail |
|--------|-----------|--------|
| Firebase lit les messages | ✅ | Chiffré E2E, Firebase ne voit que du ciphertext |
| Compromission d'une clé message | ✅ | PFS — chaque message a sa propre clé |
| Replay d'anciens messages | ✅ | sinceTimestamp + messageIndex |
| Race conditions ratchet | ✅ | Mutex par conversation |
| Métadonnées (qui/quand) | ❌ | Visible dans Firebase |
| Vol du téléphone déverrouillé | ⚠️ | Clés dans Keystore, mais messages en clair dans Room |

> Voir [`SECURITY.md`](SECURITY.md) pour l'analyse complète.

---

## 🛠 Installation

### Prérequis

- **Android Studio** Hedgehog (2023.1.1) ou plus récent
- **JDK 17**
- Un projet **Firebase** (plan gratuit Spark)

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
4. Onglet **Règles** → coller :

```json
{
  "rules": {
    "conversations": {
      "$conversationId": {
        "messages": {
          ".read": "auth != null",
          ".write": "auth != null",
          ".indexOn": ["createdAt"],
          "$messageId": {
            ".validate": "newData.hasChildren(['senderPublicKey', 'ciphertext', 'iv', 'messageIndex', 'createdAt'])"
          }
        }
      }
    },
    "users": {
      "$uid": {
        ".read": "auth != null",
        ".write": "auth.uid === $uid"
      }
    }
  }
}
```

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
│       │   │
│       │   ├── crypto/
│       │   │   ├── CryptoManager.kt          # EC P-256, ECDH, AES-256-GCM, HKDF
│       │   │   └── SymmetricRatchet.kt       # KDF chain ratchet (PFS)
│       │   │
│       │   ├── data/
│       │   │   ├── local/
│       │   │   │   ├── SecureChatDatabase.kt # Room DB v2
│       │   │   │   ├── UserLocalDao.kt
│       │   │   │   ├── ContactDao.kt
│       │   │   │   ├── ConversationDao.kt
│       │   │   │   ├── MessageLocalDao.kt
│       │   │   │   └── RatchetStateDao.kt
│       │   │   │
│       │   │   ├── model/
│       │   │   │   ├── UserLocal.kt          # Identité locale
│       │   │   │   ├── Contact.kt            # Contact (pseudo + pubkey)
│       │   │   │   ├── Conversation.kt       # Conversation (participant + ratchet)
│       │   │   │   ├── MessageLocal.kt       # Message déchiffré (local only)
│       │   │   │   ├── FirebaseMessage.kt    # Message chiffré (Firebase)
│       │   │   │   └── RatchetState.kt       # État du ratchet par conversation
│       │   │   │
│       │   │   ├── remote/
│       │   │   │   └── FirebaseRelay.kt      # Auth anonyme + relay chiffré + TTL cleanup
│       │   │   │
│       │   │   └── repository/
│       │   │       └── ChatRepository.kt     # Source de vérité unique (Mutex-protected)
│       │   │
│       │   ├── util/
│       │   │   └── QrCodeGenerator.kt        # Génération QR codes (ZXing)
│       │   │
│       │   └── ui/
│       │       ├── MainActivity.kt           # Single-activity (NavHost)
│       │       ├── onboarding/               # Création d'identité
│       │       ├── conversations/            # Liste des chats
│       │       ├── addcontact/               # Scanner QR + saisie manuelle
│       │       ├── chat/                     # Messages E2E + bulles WhatsApp-like
│       │       └── profile/                  # QR code, copier/partager, supprimer
│       │
│       └── res/
│           ├── drawable/                     # Bulles, cercles, icônes
│           ├── layout/                       # Tous les layouts XML
│           ├── menu/                         # Menu conversations (profil, reset)
│           ├── navigation/nav_graph.xml      # Graph de navigation
│           └── values/                       # Couleurs, strings, thèmes
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
| Firebase BOM | 34.10.0 | Auth anonyme + Realtime Database |
| AndroidX Security Crypto | 1.1.0-alpha06 | Stockage sécurisé |
| Kotlinx Coroutines | 1.9.0 | Async + Flow |
| ZXing Android Embedded | 4.3.0 | Génération et scan QR codes |

---

## 🔒 Sécurité

### Mesures implémentées

- ✅ **Zeroing mémoire** — Toutes les clés intermédiaires (`sharedSecret`, `chainKey`, `messageKey`, `plaintext bytes`, `IV`) sont remplies de zéros après usage
- ✅ **SecureRandom singleton** — IV de 12 octets, jamais réutilisé
- ✅ **Envoi atomique** — Le ratchet state n'est persisté qu'après confirmation Firebase
- ✅ **Mutex par conversation** — Pas de race condition sur le ratchet
- ✅ **Re-auth Firebase** — Session anonyme restaurée après app kill
- ✅ **TTL 7 jours** — Les vieux messages sont auto-supprimés de Firebase
- ✅ **Anti-replay** — Filtrage par `sinceTimestamp` + `messageIndex`
- ✅ **Pas de logs sensibles** — Clés et plaintexts retirés de Logcat
- ✅ **Keystore delete on reset** — Clé privée supprimée quand on supprime le compte
- ✅ **`allowBackup=false`** — Pas de backup automatique des données

### Limites connues

> Voir [`SECURITY.md`](SECURITY.md) pour l'analyse détaillée.

---

## 🗺 Roadmap

### ✅ V1 — Done

- [x] Chiffrement E2E (ECDH P-256 + AES-256-GCM)
- [x] Perfect Forward Secrecy (Symmetric Ratchet)
- [x] QR Code (génération + scan)
- [x] Profil (pseudo modifiable, copier/partager clé)
- [x] Suppression de compte complète
- [x] Design WhatsApp-like
- [x] Anti-doublons + anti-replay
- [x] TTL Firebase (7 jours)
- [x] Hardening crypto (zeroing, mutex, atomic send)

### 🔜 V2 — Planned

- [ ] **Full Double Ratchet** — Rotation DH asymétrique (comme Signal)
- [ ] **SQLCipher** — Chiffrement de la base Room locale
- [ ] **Notifications push** — Firebase Cloud Messaging
- [ ] **Vérification de clé** — Fingerprint emoji (comme Signal/WhatsApp)
- [ ] **Groupes** — Conversations à 3+ participants
- [ ] **Suppression pour tous** — Supprimer un message côté local + Firebase
- [ ] **Export/Import de clés** — Backup chiffré de l'identité
- [ ] **Typing indicators** — "En train d'écrire..."

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

**Fait avec 🔐 et ☕ — SecureChat V1**

*"Vos messages, vos clés, votre vie privée."*

</div>
