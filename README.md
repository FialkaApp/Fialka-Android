<div align="right">
  🇫🇷 Français | <a href="README-en.md">🇬🇧 English</a>
</div>

<br/>

<div align="center">

# 🔐 SecureChat

### Chat chiffré de bout en bout pour Android — gratuit, anonyme, sans serveur

<br/>

[![Android](https://img.shields.io/badge/Android-33%2B-a855f7?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7c3aed?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![E2E](https://img.shields.io/badge/PQXDH-X25519%20%2B%20ML--KEM--1024-6d28d9?style=for-the-badge&logo=letsencrypt&logoColor=white)](docs/fr/CRYPTO.md)
[![License](https://img.shields.io/badge/GPLv3-License-8b5cf6?style=for-the-badge)](LICENSE)
[![Terms](https://img.shields.io/badge/Terms-Conditions-8b5cf6?style=for-the-badge)](TERMS.md)
[![Privacy](https://img.shields.io/badge/Privacy-Policy-8b5cf6?style=for-the-badge)](PRIVACY.md)

<br/>

<table>
<tr>
<td>

```
  Vos messages sont chiffrés AVANT d'être envoyés.
  Firebase ne voit que du bruit.

  Pas de numéro. Pas d'email.
  Juste un pseudo et une clé.
```

</td>
</tr>
</table>

<br/>

</div>

---

<div align="center">

## ⚡ En bref

</div>

<table>
<tr>
<td width="50%">

### 🔐 Crypto

- **PQXDH** : X25519 + **ML-KEM-1024** (post-quantique)
- **SPQR** : ré-encapsulation PQ périodique (toutes les 10 msgs)
- **AES-256-GCM** / **ChaCha20-Poly1305** (auto) + **Double Ratchet** avec PFS + healing
- **Fingerprint emojis** 96-bit anti-MITM + **QR code scanner**
- **Vérification indépendante** par utilisateur + messages système
- **BIP-39** backup (24 mots) + grille autocomplete
- **Photos one-shot** — vue unique, suppression sécurisée 2 phases
- Clé privée dans **Android Keystore** (StrongBox si dispo)
- Base locale chiffrée **SQLCipher**
- **Message padding** taille fixe (256/1K/4K/16K)
- **PBKDF2** PIN (600K itérations)
- **Dummy traffic** (trafic factice par conversation)
- **Fichiers E2E** chiffrés AES-256-GCM
- **Signature Ed25519** anti-falsification par message
- **Tor intégré** — SOCKS5, VPN TUN, IP masquée

</td>
<td width="50%">

### 🎨 UI/UX

- **Material Design 3** — Migration complète des 5 thèmes
- **5 thèmes** : Midnight · Hacker · **Phantom** · Aurora · Daylight
- **Animations fluides** — transitions, bulles, cascade
- **Icônes d'attachement inline** — Style Session, animation slide-up
- **Écran Tor Bootstrap** — Choix Tor/Normal, progress animé, 5 thèmes
- **Toolbar scrollable** + FAB auto-hide
- **Bulles dynamiques** colorées par thème
- **App Lock** PIN + biométrie
- **Messages éphémères** (30s → 1 mois)
- **Photos one-shot** vue unique 🔥

</td>
</tr>
</table>

---

<div align="center">

## ✨ Fonctionnalités

</div>

<table>
<tr><td>

<details open>
<summary><b>🔒 Sécurité & Crypto</b></summary>
<br/>

| | Feature | Détail |
|---|---------|--------|
| 🔐 | **Chiffrement E2E** | PQXDH : X25519 + ML-KEM-1024 + AES-256-GCM / ChaCha20-Poly1305 + SPQR |
| 🔄 | **Perfect Forward Secrecy** | Double Ratchet (DH + KDF chains) |
| 🔏 | **Fingerprint emojis + QR** | 96-bit, 16 emojis + QR code SHA-256, scanner intégré |
| ✅ | **Vérification indépendante** | Chacun vérifie de son côté, message système + lien cliquable |
| 🛡️ | **DeviceSecurityManager** | Détection StrongBox, niveau MAXIMUM/STANDARD |
| 🕵️ | **Metadata hardening** | senderUid hashé HMAC + messageIndex chiffré |
| 🛡️ | **Zero-knowledge relay** | Firebase ne voit que du ciphertext |
| 🔑 | **Keystore-backed** | Clé privée dans EncryptedSharedPreferences |
| 🗄️ | **SQLCipher** | Base Room chiffrée AES-256 |
| 🧹 | **Zeroing mémoire** | Clés intermédiaires remplies de zéros |
| 📏 | **Message padding** | Taille fixe (256/1K/4K/16K) anti-analyse de trafic |
| 🗑️ | **Delete-after-delivery** | Messages supprimés de Firebase après déchiffrement |
| 👻 | **Dummy traffic** | Messages factices périodiques (configurable) |
| 📎 | **Fichiers E2E** | Chiffrement AES-256-GCM par fichier via Firebase Storage |
| 🔒 | **PBKDF2 PIN** | 600K itérations + salt (remplace SHA-256) |
| ✍️ | **Signature Ed25519** | Chaque message signé, badge ✅/⚠️ anti-falsification |
| 📸 | **Photos one-shot** | Vue unique (sender + receiver), suppression sécurisée 2 phases |

</details>

<details>
<summary><b>💬 Messagerie</b></summary>
<br/>

| | Feature | Détail |
|---|---------|--------|
| 📷 | **QR Code** | Scan → clé publique + pseudo auto-remplis (deep link v2) |
| 📨 | **Demandes de contact** | Invitation → notification → accepter/refuser |
| 🔴 | **Messages non lus** | Badge compteur + séparateur dans le chat |
| 🔄 | **Temps réel** | Messages reçus même app en arrière-plan |
| 🔔 | **Push notifications** | Opt-in, zéro contenu message |
| ⏱️ | **Messages éphémères** | 10 durées (30s → 1 mois), synchro Firebase |
| � | **Partage de fichiers E2E** | Chiffrés AES-256-GCM, 25 Mo max |
| 👻 | **Trafic factice** | Messages indistinguables pour masquer l'activité |
| 🗑️ | **Delete-after-delivery** | Ciphertext supprimé de Firebase après réception |
| �💀 | **Détection convo morte** | Auto-détection + nettoyage + re-invitation |

</details>

<details>
<summary><b>🎨 Interface</b></summary>
<br/>

| | Feature | Détail |
|---|---------|--------|
| 🌙 | **5 thèmes** | Midnight · Hacker · Phantom · Aurora · Daylight |
| ✨ | **Animations** | Transitions slide/fade, bulles animées, cascade |
| 📜 | **Toolbar scrollable** | Se replie au scroll, réapparaît (snap) |
| 🔽 | **FAB auto-hide** | Disparaît au scroll vers le bas |
| 🫧 | **Bulles dynamiques** | Couleurs adaptées au thème via backgroundTint |
| 🎭 | **Sélecteur visuel** | Grille MaterialCardView avec prévisualisation |
| 📎 | **Icônes inline** | Attachement style Session (fichier/photo/caméra) animé |

</details>

<details>
<summary><b>🔒 Protection</b></summary>
<br/>

| | Feature | Détail |
|---|---------|--------|
| 🔒 | **App Lock** | PIN 6 chiffres + biométrie opt-in |
| ⏰ | **Auto-lock** | Timeout configurable (5s → 5min) |
| 🔑 | **Backup BIP-39** | 24 mots pour sauvegarder la clé d'identité |
| ♻️ | **Restauration** | Grille autocomplete 24 mots + restaurer sur nouvel appareil |
| 🗑️ | **Suppression complète** | Nettoie Firebase (profil, inbox, convos, clés de signature) |
| 📵 | **Anonyme** | Zéro numéro, zéro email, zéro tracking |

</details>

</td></tr>
</table>

---

<div align="center">

## 🏗 Architecture

</div>

```
┌──────────────────────────────────────────────────┐
│                    UI Layer                       │
│         Fragments · ViewModels · Adapters         │
├──────────────────────────────────────────────────┤
│               Repository Layer                    │
│      ChatRepository — source de vérité unique     │
├────────────────┬────────────────┬────────────────┤
│    Room DB     │     Crypto     │    Firebase     │
│   (SQLCipher)  │  PQXDH + DR    │  Relay + FCM    │
└────────────────┴────────────────┴────────────────┘
```

> 📖 **Détails** — [Architecture complète](docs/fr/ARCHITECTURE.md) · [Protocole crypto](docs/fr/CRYPTO.md) · [Structure du projet](docs/fr/STRUCTURE.md)

---

<div align="center">

## 🛠 Quick Start

</div>

```bash
# 1. Clone
git clone https://github.com/DevBot667/SecureChat.git
cd SecureChat

# 2. Ajouter google-services.json dans app/ (voir docs/SETUP.md)

# 3. Build
./gradlew assembleDebug
```

> 📖 **Guide complet** — [Installation & Configuration Firebase](docs/fr/SETUP.md)

---

<div align="center">

## 🔐 Sécurité

</div>

| Mesure | Statut |
|--------|--------|
| Chiffrement E2E (PQXDH : X25519 + ML-KEM-1024 + AES-256-GCM / ChaCha20) | ✅ |
| Double Ratchet avec PFS + healing | ✅ |
| Zeroing mémoire (clés intermédiaires) | ✅ |
| Envoi atomique (ratchet + Firebase) | ✅ |
| Mutex par conversation (thread-safe) | ✅ |
| SQLCipher (base locale chiffrée AES-256) | ✅ |
| Metadata hardening (trial decryption) | ✅ |
| senderUid hashé HMAC-SHA256 par conversation | ✅ |
| Message padding taille fixe (anti traffic analysis) | ✅ |
| Delete-after-delivery (Firebase auto-cleanup) | ✅ |
| Dummy traffic configurable (trafic factice) | ✅ |
| Fichiers E2E (AES-256-GCM + Firebase Storage) | ✅ |
| PBKDF2 PIN (600K itérations + salt) | ✅ |
| R8/ProGuard obfuscation + log stripping complet (d/v/i/w/e/wtf) | ✅ |
| Fingerprint emojis 96-bit anti-MITM + QR code SHA-256 scanner | ✅ |
| App Lock (PIN + biométrie) | ✅ |
| Firebase security rules restrictives | ✅ |
| BIP-39 backup/restore (24 mots) | ✅ |
| `allowBackup=false`, zéro logs sensibles | ✅ |
| Material Design 3 — migration complète des 5 thèmes | ✅ |
| Icônes d'attachement inline avec animation (style Session) | ✅ |
| Permissions Android 13+ (READ_MEDIA_IMAGES/AUDIO) | ✅ |
| Geste prédictif (enableOnBackInvokedCallback) | ✅ |
| Routage Tor intégré (SOCKS5 + VPN TUN + libtor.so) | ✅ |
| Écran bootstrap Tor (choix + progress + 5 thèmes) | ✅ |
| Toggle Tor dans Paramètres Sécurité + reconnexion | ✅ |
| Dummy traffic par conversation | ✅ |
| Signature Ed25519 par message (anti-falsification) | ✅ |
| PQXDH : X25519 + ML-KEM-1024 (résistance post-quantique) | ✅ |
| SPQR : ré-encapsulation ML-KEM toutes les 10 messages | ✅ |
| ChaCha20-Poly1305 alternatif (auto-détection hardware AES) | ✅ |
| Upgrade PQXDH différé (rootKey-only, zéro désync) | ✅ |
| StrongBox hardware key storage (si disponible) | ✅ |
| DeviceSecurityManager (probe StrongBox + profil utilisateur) | ✅ |
| QR deep link v2 (X25519 + ML-KEM + nom, auto-fill) | ✅ |
| displayName masqué de Firebase (zéro PII serveur) | ✅ |
| Vérification d'empreinte indépendante par utilisateur | ✅ |
| Messages système de vérification + lien cliquable | ✅ |
| lastDeliveredAt (skip messages déjà traités au redémarrage) | ✅ |
| Delete-after-failure (nettoyage messages en échec Firebase) | ✅ |
| Déduplication atomique dual-listener (ConcurrentHashMap) | ✅ |
| Nettoyage clés de signature à la suppression de compte | ✅ |
| **Audit sécurité V3.4.1 — 42+ vulnérabilités corrigées** | ✅ |
| Firebase rules : write-once (signing_keys, mlkem_keys, inbox) | ✅ |
| Validation senderUid/ciphertext/iv/createdAt dans Firebase rules | ✅ |
| Zeroing mémoire HKDF (IKM, PRK, expandInput) | ✅ |
| Zeroing mémoire MnemonicManager (encode + decode) | ✅ |
| FLAG_SECURE (MainActivity, LockScreen, RestoreFragment, dialogs) | ✅ |
| Protection anti-tapjacking (filterTouchesWhenObscured) | ✅ |
| usesCleartextTraffic=false (zéro trafic HTTP) | ✅ |
| Deep link hardening (whitelist, limites, anti-injection) | ✅ |
| Clipboard EXTRA_IS_SENSITIVE + auto-clear 30s | ✅ |
| SecureFileManager (suppression 2 passes : aléatoire + zéros) | ✅ |
| FCM payload opaque (zéro metadata dans les notifications push) | ✅ |
| Firebase Storage : delete restreint au uploadeur uniquement | ✅ |
| Validation ML-KEM taille + Base64 côté client | ✅ |
| Input validation FirebaseRelay.sendMessage (require guards) | ✅ |
| Cloud Function : regex validation senderUid + conversationId | ✅ |

> 📖 **Analyse complète** — [`SECURITY.md`](SECURITY.md) · [Protocole crypto](docs/fr/CRYPTO.md)

---

<div align="center">

## 🗺 Roadmap

</div>

| Version | Thème | Statut |
|---------|-------|--------|
| **V1** | Core — E2E, contacts, conversations, push, fingerprint, SQLCipher, App Lock, éphémère | ✅ Done |
| **V2** | Crypto Upgrade — Full Double Ratchet X25519, Curve25519 natif | ✅ Done |
| **V2.1** | Account Lifecycle — BIP-39 backup, restauration, suppression, dead convo | ✅ Done |
| **V2.2** | UI Modernization — 5 thèmes, animations, CoordinatorLayout, zero hardcoded colors | ✅ Done |
| **V3** | Security Hardening — R8, delete-after-delivery, padding, HMAC UID, PBKDF2, dummy traffic, fichiers E2E | ✅ Done |
| **V3.1** | Settings Redesign — Paramètres Signal-like, PIN 6 chiffres, sous-écran Confidentialité, coroutines PIN | ✅ Done |
| **V3.2** | Ed25519 Signing — Signature par message, badge ✅/⚠️, durcissement Firebase rules, nettoyage clés | ✅ Done |
| **V3.3** | Material 3 + Tor + Attachment UX — Migration M3, intégration Tor complète, icônes inline Session, permissions Android 13+, durcissement logs | ✅ Done |
| **V3.4** | PQXDH + Security — ML-KEM-1024 post-quantique, deep link v2, QR auto-fill nom, displayName masqué Firebase, DeviceSecurityManager StrongBox, vérification empreinte indépendante, messages système, fix désync PQXDH, fix dual-listener, lastDeliveredAt | ✅ Done |
| **V3.4.1** | One-Shot + UX + Security Audit — Photos éphémères, grille BIP-39, QR fingerprint, audit 29 layouts, PIN oublié, **audit sécurité complet (42+ fixes)** : Firebase rules write-once, zeroing mémoire HKDF/mnemonic, FLAG_SECURE, deep link hardening, SecureFileManager, FCM opaque, Storage owner-only delete, input validation | ✅ Done |
| **V3.5** | SPQR + ChaCha20 + Threat Model — Triple Ratchet PQ (ré-encapsulation ML-KEM toutes les 10 msgs), ChaCha20-Poly1305 alternatif (auto-détection hardware), modèle de menace documenté dans SECURITY.md | ✅ Done |
| **V3.6** | Planned — Camouflage app + faux écran, Dual PIN, panic button, messages vocaux E2E, sealed sender, reply/quote | 🔜 |

> 📖 **Détails** — [Changelog complet](docs/fr/CHANGELOG.md)

---

<div align="center">

## 🤝 Contribuer

</div>

1. Fork le repo
2. Crée ta branche (`git checkout -b feature/ma-feature`)
3. Commit (`git commit -m 'Ajout de ma feature'`)
4. Push (`git push origin feature/ma-feature`)
5. Ouvre une **Pull Request**

> ⚠️ Pour toute modification crypto, ouvrir une **issue** d'abord pour discussion.

---

<div align="center">

## 📖 Documentation

| Document | Contenu |
|----------|---------|
| [**Architecture**](docs/fr/ARCHITECTURE.md) | Patterns, layers, flux des demandes, cycle de vie |
| [**Protocole Crypto**](docs/fr/CRYPTO.md) | X25519, Double Ratchet, fingerprint, modèle de menace |
| [**Installation**](docs/fr/SETUP.md) | Prérequis, Firebase, build, dépendances |
| [**Structure**](docs/fr/STRUCTURE.md) | Arbre complet du projet |
| [**Changelog**](docs/fr/CHANGELOG.md) | Historique V1 → V3.5 |
| [**Sécurité**](SECURITY.md) | Audit complet, limites connues |

</div>

---

<div align="center">

Ce projet est sous licence [GPLv3](LICENSE). Consultez les [Terms of Service](TERMS.md) avant toute utilisation.

Fourni à des fins **éducatives**. Utilisez-le comme base pour comprendre le chiffrement E2E sur mobile.

<br/>

> **⚠️ Avertissement** : Ce logiciel est un projet personnel et éducatif. L'implémentation cryptographique **n'a pas été auditée** par un cabinet de sécurité tiers. Aucune garantie de sécurité absolue n'est fournie. Ne l'utilisez pas comme seul moyen de communication sécurisée dans des situations critiques. L'utilisation de ce logiciel est **à vos propres risques**. Voir [TERMS.md](TERMS.md).

<br/>

<img src="https://img.shields.io/badge/SecureChat-V3.5-7c3aed?style=for-the-badge&logo=android&logoColor=white" />

<br/><br/>

*"Vos messages, vos clés, votre vie privée."*

<br/>

</div>
