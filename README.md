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
[![E2E](https://img.shields.io/badge/X25519-AES--256--GCM-6d28d9?style=for-the-badge&logo=letsencrypt&logoColor=white)](docs/CRYPTO.md)
[![License](https://img.shields.io/badge/GPLv3-License-8b5cf6?style=for-the-badge)](LICENSE)

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

- **X25519 ECDH** + **AES-256-GCM**
- **Double Ratchet** avec PFS + healing
- **Fingerprint emojis** 96-bit anti-MITM
- **BIP-39** backup (24 mots)
- Clé privée dans **Android Keystore**
- Base locale chiffrée **SQLCipher**

</td>
<td width="50%">

### 🎨 UI/UX

- **5 thèmes** : Midnight · Hacker · **Phantom** · Aurora · Daylight
- **Animations fluides** — transitions, bulles, cascade
- **Toolbar scrollable** + FAB auto-hide
- **Bulles dynamiques** colorées par thème
- **App Lock** PIN + biométrie
- **Messages éphémères** (30s → 1 mois)

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
| 🔐 | **Chiffrement E2E** | X25519 ECDH + AES-256-GCM |
| 🔄 | **Perfect Forward Secrecy** | Double Ratchet (DH + KDF chains) |
| 🔏 | **Fingerprint emojis** | 96-bit, 16 emojis, anti-MITM |
| 🕵️ | **Metadata hardening** | senderPublicKey + messageIndex supprimés |
| 🛡️ | **Zero-knowledge relay** | Firebase ne voit que du ciphertext |
| 🔑 | **Keystore-backed** | Clé privée dans EncryptedSharedPreferences |
| 🗄️ | **SQLCipher** | Base Room chiffrée AES-256 |
| 🧹 | **Zeroing mémoire** | Clés intermédiaires remplies de zéros |

</details>

<details>
<summary><b>💬 Messagerie</b></summary>
<br/>

| | Feature | Détail |
|---|---------|--------|
| 📷 | **QR Code** | Scan → clé publique + pseudo auto-remplis |
| 📨 | **Demandes de contact** | Invitation → notification → accepter/refuser |
| 🔴 | **Messages non lus** | Badge compteur + séparateur dans le chat |
| 🔄 | **Temps réel** | Messages reçus même app en arrière-plan |
| 🔔 | **Push notifications** | Opt-in, zéro contenu message |
| ⏱️ | **Messages éphémères** | 10 durées (30s → 1 mois), synchro Firebase |
| 💀 | **Détection convo morte** | Auto-détection + nettoyage + re-invitation |

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

</details>

<details>
<summary><b>🔒 Protection</b></summary>
<br/>

| | Feature | Détail |
|---|---------|--------|
| 🔒 | **App Lock** | PIN 4 chiffres + biométrie opt-in |
| ⏰ | **Auto-lock** | Timeout configurable (5s → 5min) |
| 🔑 | **Backup BIP-39** | 24 mots pour sauvegarder la clé d'identité |
| ♻️ | **Restauration** | Restaurer sur un nouvel appareil via phrase |
| 🗑️ | **Suppression complète** | Nettoie Firebase (profil, inbox, convos) |
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
│   (SQLCipher)  │  X25519 + DR   │  Relay + FCM    │
└────────────────┴────────────────┴────────────────┘
```

> 📖 **Détails** — [Architecture complète](docs/ARCHITECTURE.md) · [Protocole crypto](docs/CRYPTO.md) · [Structure du projet](docs/STRUCTURE.md)

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

> 📖 **Guide complet** — [Installation & Configuration Firebase](docs/SETUP.md)

---

<div align="center">

## 🔐 Sécurité

</div>

| Mesure | Statut |
|--------|--------|
| Chiffrement E2E (X25519 + AES-256-GCM) | ✅ |
| Double Ratchet avec PFS + healing | ✅ |
| Zeroing mémoire (clés intermédiaires) | ✅ |
| Envoi atomique (ratchet + Firebase) | ✅ |
| Mutex par conversation (thread-safe) | ✅ |
| SQLCipher (base locale chiffrée AES-256) | ✅ |
| Metadata hardening (trial decryption) | ✅ |
| Fingerprint emojis 96-bit anti-MITM | ✅ |
| App Lock (PIN + biométrie) | ✅ |
| Firebase security rules restrictives | ✅ |
| BIP-39 backup/restore (24 mots) | ✅ |
| `allowBackup=false`, zéro logs sensibles | ✅ |

> 📖 **Analyse complète** — [`SECURITY.md`](SECURITY.md) · [Protocole crypto](docs/CRYPTO.md)

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
| **V3** | Planned — Signature ECDSA, groupes, suppression pour tous, typing indicators | 🔜 |

> 📖 **Détails** — [Changelog complet](docs/CHANGELOG.md)

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
| [**Architecture**](docs/ARCHITECTURE.md) | Patterns, layers, flux des demandes, cycle de vie |
| [**Protocole Crypto**](docs/CRYPTO.md) | X25519, Double Ratchet, fingerprint, modèle de menace |
| [**Installation**](docs/SETUP.md) | Prérequis, Firebase, build, dépendances |
| [**Structure**](docs/STRUCTURE.md) | Arbre complet du projet |
| [**Changelog**](docs/CHANGELOG.md) | Historique V1 → V2.2 + roadmap V3 |
| [**Sécurité**](SECURITY.md) | Audit complet, limites connues |

</div>

---

<div align="center">

Ce projet est sous licence [GPLv3](LICENSE).

Fourni à des fins **éducatives**. Utilisez-le comme base pour comprendre le chiffrement E2E sur mobile.

<br/>

<img src="https://img.shields.io/badge/SecureChat-V2.2-7c3aed?style=for-the-badge&logo=android&logoColor=white" />

<br/><br/>

*"Vos messages, vos clés, votre vie privée."*

<br/>

</div>
