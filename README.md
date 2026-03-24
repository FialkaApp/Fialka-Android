<div align="right">
  🇫🇷 Français | <a href="README-en.md">🇬🇧 English</a>
</div>

<br/>

<div align="center">

# 🔐 Fialka

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
  Personne ne peut les lire. Pas de serveur central.

  Pas de numéro. Pas d'email. Pas de Google.
  Juste une clé et Tor.
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

- **1 seed Ed25519 → tout** : identité, .onion, X25519, ML-KEM, fingerprint
- **PQXDH** : X25519 + **ML-KEM-1024** (post-quantique hybride)
- **ML-DSA-44** : signature PQ sur le handshake
- **SPQR** : ré-encapsulation PQ périodique (toutes les 10 msgs)
- **AES-256-GCM** / **ChaCha20-Poly1305** (auto) + **Double Ratchet** avec PFS + healing
- **Fingerprint emojis** 96-bit anti-MITM + **QR code scanner**
- **BIP-39** backup (24 mots) → restaure toute l'identité
- **Photos one-shot** — vue unique, suppression sécurisée 2 phases
- Seed dans **Android Keystore** (StrongBox si dispo)
- Base locale chiffrée **SQLCipher**
- **Message padding** taille fixe (256/1K/4K/16K)
- **Dummy traffic** (trafic factice par conversation)
- **Fichiers E2E** chiffrés AES-256-GCM (transfert P2P via Tor)
- **Signature Ed25519** anti-falsification par message
- **Zéro Google, zéro Firebase** — full P2P via **Tor Hidden Services**

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
| 🆔 | **1 seed → tout** | Ed25519 seed → Account ID, .onion, X25519, ML-KEM, fingerprint |
| 🔄 | **Perfect Forward Secrecy** | Double Ratchet (DH + KDF chains) |
| 🔏 | **Fingerprint emojis + QR** | 96-bit, 16 emojis + QR code SHA-256, scanner intégré |
| 🛡️ | **ML-DSA-44** | Signature post-quantique hybride sur le handshake |
| 🌐 | **Tor P2P** | Zéro serveur central — .onion directe, IP jamais visible |
| 📬 | **Fialka Mailbox** | Livraison offline (4 modes : P2P, perso, privé, public) |
| 🔑 | **Keystore-backed** | Seed dans Android Keystore (StrongBox si dispo) |
| 🗄️ | **SQLCipher** | Base Room chiffrée AES-256 |
| 🧹 | **Zeroing mémoire** | Clés intermédiaires remplies de zéros |
| 📏 | **Message padding** | Taille fixe (256/1K/4K/16K) anti-analyse de trafic |
| 👻 | **Dummy traffic** | Messages factices périodiques (configurable) |
| 📎 | **Fichiers E2E** | Chiffrement AES-256-GCM, transfert P2P via Tor |
| 🔒 | **PBKDF2 PIN** | 600K itérations + salt |
| ✍️ | **Signature Ed25519** | Chaque message signé, badge ✅/⚠️ anti-falsification |
| 📸 | **Photos one-shot** | Vue unique (sender + receiver), suppression sécurisée 2 phases |

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
| 🔔 | **Push notifications** | UnifiedPush + ntfy.sh, zéro contenu (self-hostable) |
| ⏱️ | **Messages éphémères** | 10 durées (30s → 1 mois) |
| 📁 | **Partage de fichiers E2E** | Chiffrés AES-256-GCM, transfert P2P via Tor |
| 👻 | **Trafic factice** | Messages indistinguables pour masquer l'activité |
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
| 🗑️ | **Suppression complète** | Supprime toutes les données locales |
| 📵 | **Anonyme** | Zéro numéro, zéro email, zéro Google, zéro tracking |

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
│    Room DB     │     Crypto     │   Transport     │
│   (SQLCipher)  │  PQXDH + DR    │  Tor P2P .onion │
│                │  + ML-DSA-44   │  + Mailbox      │
└────────────────┴────────────────┴────────────────┘
```

> 📖 **Détails** — [Architecture complète](docs/fr/ARCHITECTURE.md) · [Protocole crypto](docs/fr/CRYPTO.md) · [Structure du projet](docs/fr/STRUCTURE.md)

---

<div align="center">

## 🛠 Quick Start

</div>

```bash
# 1. Clone
git clone https://github.com/FialkaApp/Fialka-Android.git
cd Fialka-Android

# 2. Build
./gradlew assembleDebug
```

> 📖 **Guide complet** — [Installation & Configuration](docs/fr/SETUP.md)

---

<div align="center">

## 🔐 Sécurité

</div>

| Mesure | Statut |
|--------|--------|
| **Identité : 1 seed Ed25519 → tout** (Account ID, .onion, X25519, ML-KEM, fingerprint) | ✅ |
| **Zéro serveur central** — P2P intégral via Tor Hidden Services (.onion) | ✅ |
| **Zéro Google, zéro Firebase** — aucune dépendance corporate | ✅ |
| Chiffrement E2E (PQXDH : X25519 + ML-KEM-1024 + AES-256-GCM / ChaCha20) | ✅ |
| Double Ratchet avec PFS + healing | ✅ |
| SPQR : ré-encapsulation ML-KEM-1024 toutes les 10 messages | ✅ |
| ML-DSA-44 : signature PQ hybride sur le handshake | ✅ |
| Ed25519 : signature classique par message (badge ✅/⚠️) | ✅ |
| Fialka Mailbox : livraison offline (4 modes : P2P, perso, privé, public) | ✅ |
| UnifiedPush + ntfy.sh (notifications sans Google, self-hostable) | ✅ |
| Sealed Sender via Tor (supérieur à Signal — IP jamais visible) | ✅ |
| Zeroing mémoire (clés intermédiaires, HKDF, MnemonicManager) | ✅ |
| Mutex par conversation (thread-safe) | ✅ |
| SQLCipher (base locale chiffrée AES-256) | ✅ |
| Message padding taille fixe (256/1K/4K/16K — anti traffic analysis) | ✅ |
| Dummy traffic configurable (trafic factice par conversation) | ✅ |
| Fichiers E2E (AES-256-GCM, transfert P2P via Tor) | ✅ |
| PBKDF2 PIN (600K itérations + salt) | ✅ |
| R8/ProGuard obfuscation + log stripping complet | ✅ |
| Fingerprint emojis 96-bit anti-MITM + QR code SHA-256 scanner | ✅ |
| App Lock (PIN + biométrie) | ✅ |
| BIP-39 backup/restore (24 mots) → restaure toute l'identité | ✅ |
| `allowBackup=false`, zéro logs sensibles | ✅ |
| StrongBox hardware key storage (si disponible) | ✅ |
| DeviceSecurityManager (probe StrongBox + profil sécurité) | ✅ |
| FLAG_SECURE (toutes activités sensibles + dialogs) | ✅ |
| Protection anti-tapjacking (filterTouchesWhenObscured) | ✅ |
| usesCleartextTraffic=false (zéro trafic HTTP) | ✅ |
| Deep link hardening (whitelist, limites, anti-injection) | ✅ |
| Clipboard EXTRA_IS_SENSITIVE + auto-clear 30s | ✅ |
| SecureFileManager (suppression 2 passes : aléatoire + zéros) | ✅ |
| Photos one-shot (vue unique, suppression sécurisée 2 phases) | ✅ |
| Messages éphémères (30s → 1 mois) | ✅ |
| **Audit sécurité V3.4.1 — 42+ vulnérabilités corrigées** | ✅ |

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
| **V3.1** | Settings Redesign — Paramètres Signal-like, PIN 6 chiffres, sous-écran Confidentialité | ✅ Done |
| **V3.2** | Ed25519 Signing — Signature par message, badge ✅/⚠️, nettoyage clés | ✅ Done |
| **V3.3** | Material 3 + Tor + Attachment UX — Migration M3, intégration Tor complète, icônes inline | ✅ Done |
| **V3.4** | PQXDH + Security — ML-KEM-1024 post-quantique, deep link v2, QR auto-fill, DeviceSecurityManager StrongBox | ✅ Done |
| **V3.4.1** | One-Shot + Security Audit — Photos éphémères, grille BIP-39, QR fingerprint, **audit sécurité (42+ fixes)** | ✅ Done |
| **V3.5** | SPQR + ChaCha20 — Triple Ratchet PQ (ML-KEM toutes les 10 msgs), ChaCha20-Poly1305 alternatif | ✅ Done |
| **V4.0** | Kill Firebase — P2P .onion + Mailbox store-and-forward, invite QR, deep links | ✅ Done |
| **V3.6a** | UX — Reply/Quote, messages vocaux E2E (Opus), Dual PIN + Panic Button | 🔜 |
| **V3.6b** | Crypto — **ML-DSA-44 handshake hybride**, app disguise + faux écran | 🔜 |
| **V4.0** | **Zéro Google** — Account ID Ed25519, Tor Hidden Services P2P, **suppression totale de Firebase**, Fialka Mailbox, UnifiedPush + ntfy.sh | 🔜 |
| **V4.1** | Polish — Sealed Sender réel (VXEdDSA), multi-device Sesame, **audit externe** (Cure53 / Trail of Bits) | 🔜 |
| **V5.0** | Long terme — **FN-DSA Falcon-512** signatures PQ par message, réseau Mailbox décentralisé, Bluetooth/WiFi fallback | 🔮 |

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
| [**Installation**](docs/fr/SETUP.md) | Prérequis, build, dépendances |
| [**Structure**](docs/fr/STRUCTURE.md) | Arbre complet du projet |
| [**Changelog**](docs/fr/CHANGELOG.md) | Historique V1 → V4.0 |
| [**Sécurité**](SECURITY.md) | Audit complet, limites connues |

</div>

---

<div align="center">

Ce projet est sous licence [GPLv3](LICENSE). Consultez les [Conditions d'utilisation](TERMS.md) et la [Politique de confidentialité](PRIVACY.md) avant toute utilisation.

<br/>

> **⚠️ Avertissement** : **Fialka est un outil.** Les développeurs n'opèrent aucune infrastructure, ne stockent aucune donnée utilisateur, et ne peuvent accéder à aucun message. L'implémentation cryptographique **n'a pas été auditée** par un cabinet de sécurité tiers. Aucune garantie de sécurité absolue n'est fournie. L'utilisation de ce logiciel est **à vos propres risques** et **sous votre entière responsabilité**. Voir [TERMS.md](TERMS.md).

<br/>

© 2024-2026 FialkaApp Contributors. Licensed under [GPLv3](LICENSE).

<br/>

<img src="https://img.shields.io/badge/Fialka-V4.0-7c3aed?style=for-the-badge&logo=android&logoColor=white" />

<br/><br/>

*"Vos messages, vos clés, votre vie privée."*

<br/>

</div>
