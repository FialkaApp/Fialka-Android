<div align="right">
  🇫🇷 Français | <a href="../en/CHANGELOG.md">🇬🇧 English</a>
</div>

<div align="center">

# 🗺 Changelog & Roadmap

<img src="https://img.shields.io/badge/Current-V3.0-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Next-V3.1-9C4DCC?style=for-the-badge" />

</div>

---

## ✅ V1 — Core

> Fondations : chiffrement E2E, contacts via QR, conversations persistantes.

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
- [x] 5 thèmes UI — Midnight, Hacker, Phantom (défaut), Aurora, Daylight + sélecteur visuel
- [x] Animations complètes — Transitions navigation, bulles animées, liste en cascade, toolbar scrollable

---

## ✅ V2 — Crypto Upgrade

> Full Double Ratchet X25519, remplacement de P-256 par Curve25519.

- [x] **Full Double Ratchet X25519** — DH ratchet + KDF chains + healing automatique
- [x] **X25519 natif** — Courbe Curve25519 (API 33+), remplace P-256
- [x] **Initial chains** — Les deux côtés peuvent envoyer immédiatement après acceptation
- [x] **Échange d'éphémères naturel** — Via les vrais messages, pas de message bootstrap

---

## ✅ V2.1 — Account Lifecycle

> Backup BIP-39, restauration, suppression complète, détection de comptes morts.

- [x] **Phrase mnémonique BIP-39** — Backup de la clé privée X25519 en 24 mots (256 bits + 8-bit checksum SHA-256)
- [x] **Backup après création** — Écran dédié affiche les 24 mots en grille 3 colonnes (confirmation checkbox)
- [x] **Restauration de compte** — Saisie de 24 mots + pseudo → restaure clé privée → dérive clé publique (DH base point u=9)
- [x] **Suppression compte complète** — Nettoie Firebase : profil `/users/{uid}`, `/inbox/{hash}`, `/conversations/{id}`
- [x] **Nettoyage ancien profil** — `removeOldUserByPublicKey()` supprime l'ancien nœud `/users/` orphelin
- [x] **Détection conversation morte** — AlertDialog clair ("Conversation supprimée") avec option supprimer
- [x] **Re-invitation contact** — Contact local stale nettoyé pour permettre re-invitation
- [x] **Auto-détection à la réception** — Inbox listener vérifie conversations stale → nettoyage auto
- [x] **Firebase rules conversation** — `.read` et `.write` restreints au niveau `$conversationId`

---

## ✅ V2.2 — UI Modernization

> 5 thèmes, animations complètes, CoordinatorLayout, zéro couleur hardcodée.

- [x] **5 thèmes** — Midnight (teal/cyan), Hacker (AMOLED Matrix green), Phantom (anthracite purple, défaut), Aurora (amber/orange), Daylight (clean light blue)
- [x] **22 attributs de couleur** — `attrs.xml` complet : toolbar, bulles, avatars, badges, input bar, surfaces, dividers
- [x] **Sélecteur de thèmes** — Grille MaterialCardView avec prévisualisation des couleurs et indicateur de sélection
- [x] **Bulles dynamiques** — Couleurs de bulles sent/received par thème via `backgroundTint` (base blanche + tint)
- [x] **Avatars/badges thématiques** — Couleurs d'avatars, badges non lus, FAB, send button adaptées au thème
- [x] **Toolbar thématique** — Toutes les toolbars (10+) utilisent `?attr/colorToolbarBackground`, elevation 0dp
- [x] **Transitions de navigation** — Slide droite/gauche (forward/back), slide haut/bas (modales), fade (onboarding)
- [x] **Animations des bulles** — Entrée depuis la droite (sent) / gauche (received), nouveaux messages uniquement
- [x] **Liste animée** — Cascade fall-in sur la liste des conversations (8% de décalage)
- [x] **CoordinatorLayout** — Toolbar se replie au scroll + réapparaît (scroll|enterAlways|snap)
- [x] **FAB auto-hide** — `HideBottomViewOnScrollBehavior` masque le FAB au scroll
- [x] **Zéro couleur hardcodée** — Toutes les couleurs UI → `?attr/` (theme-aware)

---

## ✅ V3.0 — Security Hardening

> Durcissement sécuritaire complet : chiffrement renforcé, anti-analyse de trafic, partage de fichiers E2E.

### 🛡️ Build & Obfuscation
- [x] **R8/ProGuard** — `isMinifyEnabled=true`, `isShrinkResources=true`, repackaging en release
- [x] **Log stripping** — `Log.d()`, `Log.v()`, `Log.i()` supprimés par ProGuard (`assumenosideeffects`)

### 🔐 Crypto & Métadonnées
- [x] **Delete-after-delivery** — Ciphertext supprimé de Firebase RTDB immédiatement après déchiffrement réussi
- [x] **Message padding** — Plaintext paddé à taille fixe (256/1K/4K/16K octets) avec header 2 octets + remplissage SecureRandom
- [x] **senderUid HMAC** — `senderUid` = HMAC-SHA256(conversationId, UID) tronqué 128 bits — Firebase ne peut plus corréler le même utilisateur entre conversations
- [x] **PBKDF2 PIN** — SHA-256 remplacé par PBKDF2-HMAC-SHA256 (600K itérations, salt 16 octets) ; migration auto des anciens hashes

### 👻 Anti-analyse de trafic
- [x] **Dummy traffic** — Messages factices périodiques (45–120s aléatoire) via le vrai Double Ratchet — indistinguables des vrais messages sur le réseau
- [x] **Toggle configurable** — Activation/désactivation dans Paramètres → Sécurité → Trafic factice
- [x] **Prefix opaque** — Marqueur dummy en octets de contrôle non-imprimables (`\u0007\u001B\u0003`)

### 📎 Partage de fichiers E2E
- [x] **Chiffrement par fichier** — Clé AES-256-GCM aléatoire par fichier, chiffré côté client
- [x] **Firebase Storage** — Upload chiffré, métadonnées (URL + clé + IV + nom + taille) envoyées via le ratchet
- [x] **Réception auto** — Download + déchiffrement local + stockage app-private ; fichier Storage supprimé après livraison
- [x] **UI attach** — Bouton 📎 dans le chat, file picker, limite 25 Mo, clic pour ouvrir
- [x] **Storage rules** — Accès authentifié uniquement, 50 Mo max, chemin `/encrypted_files/`

### 🗄️ Base de données
- [x] **Index Room** — Index composites : messages(conversationId, timestamp), messages(expiresAt), conversations(accepted), contacts(publicKey)
- [x] **Double-listener guard** — `processedFirebaseKeys` empêche la désynchronisation ratchet quand 2 listeners traitent le même message

---

## 🔜 V3.1 — Planned

- [ ] **Signature ECDSA** — Clé de signature dédiée (PURPOSE_SIGN) pour authentifier chaque message
- [ ] **Groupes** — Conversations à 3+ participants
- [ ] **Suppression pour tous** — Supprimer un message côté local + Firebase
- [ ] **Typing indicators** — "En train d'écrire..."
- [ ] **Relay privé** — Serveur relay dédié pour réduire la dépendance Firebase

---

<div align="center">

[← Retour au README](../../README.md)

</div>
