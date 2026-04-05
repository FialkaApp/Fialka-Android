<div align="right">
  🇫🇷 Français | <a href="../en/ARCHITECTURE.md">🇬🇧 English</a>
</div>

<div align="center">

# 🏗 Architecture

<img src="https://img.shields.io/badge/Pattern-Single_Activity-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Layer-Repository-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/DB-Room_+_SQLCipher-6A1B9A?style=for-the-badge" />

</div>

---

## Vue d'ensemble

```
┌──────────────────────────────────────────────────┐
│                    UI Layer                       │
│         Fragments · ViewModels · Adapters         │
│      5 Thèmes Material 3 · Animations · Navigation  │
├──────────────────────────────────────────────────┤
│               Repository Layer                    │
│      ChatRepository — source de vérité unique     │
├────────────────┬────────────────┬────────────────┤
│    Room DB     │     Crypto     │   Transport     │
│   (SQLCipher)  │  CryptoMgr +   │  Tor Hidden     │
│                │  Ratchet +     │  Services P2P   │
│                │  PQXDH(ML-KEM) │  + Mailbox      │
│                │  + ML-DSA-44   │  + UnifiedPush  │
├────────────────┴────────────────┴────────────────┤
│           JNI Bridge (FialkaNative.kt)            │
├──────────────────────────────────────────────────┤
│         Fialka-Core (Rust) — libfialka_core.so   │
│  AES-GCM · ChaCha20 · Ed25519 · X25519 · ML-KEM  │
│  ML-DSA · HKDF · Double Ratchet · Onion          │
│       arm64-v8a + x86_64 (cross-platform)        │
└──────────────────────────────────────────────────┘
```

---

## Principes fondamentaux

| Principe | Détail |
|----------|--------|
| **Single Activity** | Navigation Component avec 15 fragments |
| **Repository Pattern** | `ChatRepository` coordonne Room, Crypto et Transport |
| **Aucun accès transport depuis l'UI** | Tout passe par Repository → TorTransport |
| **Mutex par conversation** | Les opérations ratchet sont sérialisées (thread-safe) |
| **Zéro serveur central** | Toute communication est P2P via Tor Hidden Services |
| **1 Seed → Tout** | Le seed Ed25519 maître dérive identité, .onion, X25519, ML-KEM, empreinte |
| **Handshake ML-DSA-44** | Signature post-quantique à l'établissement de session |
| **Fialka Mailbox** | Livraison hors-ligne via 4 modes (Direct, Personnel, Nœud privé, Nœud public) |
| **PQXDH différé** | Upgrade post-quantique du root_key au premier message (pas de bootstrap) |
| **Vérification indépendante** | Chaque utilisateur vérifie l'empreinte de son côté (état local Room uniquement) |
| **Système d'invitation** | Scan QR → demande contact via Tor → acceptation → chat P2P actif |

---

## Modes d'opération : User vs Mailbox

Fialka supporte **deux modes distincts et mutuellement exclusifs** sur un même appareil :

### Mode User (par défaut)

**Description** : Le téléphone fonctionne comme un client de messagerie classique.

**Fonctionnalités** :
- Création d'un compte Fialka (dérive identité depuis BIP-39 seed)
- Génération d'une adresse .onion déterministe (Tor Hidden Service)
- Envoi et réception de messages P2P chiffré E2E
- Gestion des contacts et conversations
- Possibilité d'accepter des messages hors-ligne via un Mailbox externe (Personnel, Privé, ou Public)

**⚠️ Contrainte majeure** : **Un SEUL Mode User par téléphone** — impossible d'avoir Mode User et Mode Mailbox activés simultanément sur le même appareil. Besoin de 2 téléphones.

---

### Mode Mailbox (serveur P2P chiffré)

**Description** : Le téléphone devient un service de stockage et de transport de messages chiffrés pour d'autres utilisateurs.

**Fonctionnement technique** :
1. Le Mailbox expose une adresse .onion permanente (Tor Hidden Service)
2. Les utilisateurs Mode User envoient des blobs chiffrés au Mailbox (via TorTransport)
3. Le Mailbox les stocke sur disque (aucun déchiffrage, blob opaque)
4. À l'expiration TTL (7 jours) ou après récupération, les blobs sont purgés automatiquement
5. Le propriétaire du Mailbox récupère les messages lors du réveil de son téléphone principal

**Trois modèles de partage** :

#### 1️⃣ Mailbox Personnel
- Utilisé **uniquement** par le propriétaire du téléphone Mode User
- Chaque compte User dispose d'UN Mailbox Personnel
- Ex: "Mon Mailbox pour quand je suis offline"
- Accessible uniquement par le propriétaire

#### 2️⃣ Mailbox Privé (liste blanche)
- Partagé avec une liste restreinte (famille, amis, collaborateurs)
- **OWNER seul** manage la liste blanche et autorisations
- Seuls les utilisateurs autorisés peuvent déposer des messages
- Contrôle granulaire : peut ajouter/retirer des accès à tout moment

#### 3️⃣ Mailbox Public
- Accepte les messages de **TOUS** les utilisateurs Fialka
- Aucune liste blanche, aucune authentication
- Utile pour : bots, services ouverts, relais communautaires

**Garanties de sécurité** :
- ✅ **ZÉRO déchiffrage côté serveur** — blob totalement opaque et incompréhensible au Mailbox
- ✅ **Blob brut** — pas d'extraction de métadonnées (destinataire, timing pattern, taille réelle, etc.)
- ✅ **TTL 7 jours max** — expiration automatique
- ✅ **Statstics only** — seuls compteurs cumulatifs (totalDeposited, totalFetched)
- ✅ **Polling adaptatif** — 10 s de base, 5 s après réception de messages, backoff 60 s sur erreur réseau
- ✅ **Configuration UI complète** — paramètres pour mode, TTL, whitelist (accès dans Settings)

**⚠️ Contrainte majeure** : **Nécessite 2 appareils** pour fonctionner (ou emprunter à un ami)
- Appareil A = Téléphone Mode User (usage normal, chattage)
- Appareil B = Téléphone Mode Mailbox (serveur, reste branché H24 sur secteur + WiFi)

**Cas d'usage réel** :
- Vous avez un téléphone principal Mode User (Fialka, messagerie)
- Vous avez une vieille tablette ou second téléphone branché en permanence à la maison
- Vous activez Mode Mailbox sur la tablette
- Vous configurez "Mailbox Personnel" sur votre téléphone principal (Settings → Mailbox)
- Quand vous êtes offline, les gens vous envoient des messages → stockés dans votre tablette Mailbox
- À votre retour online : tablette Mailbox vous transmet tous les messages, récup efficace

---

### Tableau comparatif

| Aspect | Mode User | Mode Mailbox |
|--------|-----------|--------------|
| **Rôle** | Client messaging | Serveur de transport P2P |
| **Peut chatter** | ✅ Oui | ❌ Non |
| **Reçoit messages** | ✅ Oui (P2P ou via Mailbox) | ✅ Oui (stockage uniquement) |
| **Crée/gère contacts** | ✅ Oui | ❌ Non |
| **Accès à DB locale** | ✅ Oui (historique Room) | ❌ Non |
| **Confidentiel contenu** | ✅ Room DB SQLCipher local | ✅ Blob opaque, aucun déchiffrage |
| **Besoins réseau** | Internet + Tor (peut être offline) | **Internet H24 + Secteur** |
| **Adresse .onion** | ✅ Oui (uniquement si configuré) | ✅ Oui (mandatory) |
| **TTL messages** | Illimité (Room DB) | **7 jours max** |
| **Partage possible** | ❌ Non (1 compte par phone) | ✅ Oui (Personnel, Privé, Public) |
| **Max par téléphone** | **1 SEUL** | **1 SEUL** |
| **Compatibilité** | ❌ Exclusif (pas Mailbox simultanément) | ❌ Exclusif (pas User simultanément) |

---

## Couches

| Couche | Rôle | Fichiers clés |
|--------|------|---------------|
| **UI** | Écrans, navigation, interactions | `ui/` — Fragments, ViewModels, Adapters (Material 3) |
| **Repository** | Coordination local/crypto/remote | `data/repository/ChatRepository.kt` |
| **Crypto** | X25519, ECDH, AES-GCM, Double Ratchet, BIP-39, Ed25519, PQXDH (ML-KEM-1024) | `crypto/CryptoManager.kt`, `DoubleRatchet.kt`, `MnemonicManager.kt` |
| **Local DB** | Room v24 — users, contacts, messages, ratchet (indexes composites) | `data/local/` — DAOs, Database (SQLCipher) |
| **Remote** | Transport Tor Hidden Services P2P (.onion, ciphertext only) | `data/remote/TorTransport.kt` |
| **Util** | QR, 5 thèmes, app lock, éphémère, dummy traffic, DeviceSecurityManager | `util/ThemeManager.kt`, `AppLockManager.kt`, `DummyTrafficManager.kt`, `DeviceSecurityManager.kt` |

---

## Push Notifications (opt-in)

```
Phone A → sendMessage() → Tor Hidden Service → Phone B
                                                  ↓ (si hors-ligne)
                                          Fialka Mailbox stocke le ciphertext
                                                  ↓
                                          UnifiedPush + ntfy.sh
                                                  ↓
                                          Phone B se réveille → connecte au Mailbox
                                          → récupère + déchiffre les messages
                                          "Nouveau message reçu"
                                          (ZÉRO contenu, ZÉRO metadata)
```

---

## Flux des demandes de contact

```
Alice                           Réseau Tor                            Bob
  │                                   │                                    │
  │  1. Scan QR / colle clé pub       │                                    │
  │  2. createConversation(pending)   │                                    │
  │  3. sendContactRequest ──────────►│───► Service .onion de Bob            │
  │     (via Tor Hidden Service)      │    (ou Mailbox si hors-ligne)      │
  │                                   │                                    │
  │                                   │         4. "Nouvelle demande de    │
  │                                   │             Alice" s'affiche       │
  │                                   │                                    │
  │                                   │◄── 5. acceptContactRequest() ──────│
  │                                   │    (handshake signé ML-DSA-44)     │
  │                                   │                                    │
  │   Session PQXDH établie ◄─────────│                                    │
  │   6. markConversationAccepted()   │                                    │
  │                                   │                                    │
  │◄═════════ Chat E2E P2P actif ═══►│◄══════════════════════════════════►│
```

---

## Cycle de vie d'un compte

```
Création :
  Onboarding → generateSeed(32 bytes) → dérive Ed25519 keypair
  → dérive Account ID (SHA3-256 → Base58)
  → dérive adresse .onion (Tor v3)
  → dérive X25519 (birational map) + ML-KEM-1024 (HKDF)
  → BackupPhrase (BIP-39, 24 mots)

Backup (BIP-39, 24 mots) :
  seed (32 bytes) → SHA-256 → 1er octet = checksum → 33 bytes → 24 × 11 bits → 24 mots

Restauration :
  24 mots → mnemonicToSeed() → re-dérive toutes les clés (Ed25519, X25519, ML-KEM, .onion)
  → identité entièrement restaurée sur nouvel appareil

Suppression de compte :
  A: efface données locales + contenu Mailbox + publie révocation de clé
  B: détecte clé révoquée → AlertDialog → re-invite si besoin
```

---

## Dummy Traffic (anti analyse de trafic)

```
DummyTrafficManager.start(context):
  → isEnabled(context)? → Non: return
  → Oui: boucle CoroutineScope(Dispatchers.IO)
    → délai aléatoire 30–90 s
    → pour chaque conversation active (Room)
      → generateDummyMessage() : préfixe opaque + random bytes
      → chiffre avec Double Ratchet (même pipeline)
      → envoie via Tor Hidden Service (même route .onion)
      → le récepteur détecte le préfixe → drop silencieux (pas d'insertion Room)

Toggle: SecurityFragment → SharedPreferences("fialka_settings") → "dummy_traffic_enabled"
```

---

## Partage de fichier E2E

```
Envoi (ChatViewModel.sendFile):
  fichier → generateFileKey() (AES-256-GCM, clé aléatoire)
  → encryptFile(fileKey, plainBytes) → cipherBytes
  → envoie cipherBytes P2P via Tor Hidden Service
  → message texte = "FILE|" + fileId + "|" + Base64(fileKey) + "|" + fileName
  → chiffre avec Double Ratchet → envoie via Tor

Réception (ChatRepository):
  → déchiffre message → détecte préfixe "FILE|"
  → parse: fileId, fileKey (Base64), fileName
  → reçoit cipherBytes via Tor P2P
  → decryptFile(fileKey, cipherBytes) → plainBytes
  → sauvegarde dans stockage interne app
  → affiche lien cliquable dans le chat
```

---

## Photo One-Shot (vue unique)

```
Envoi :
  fichier photo → chiffrement AES-256-GCM → envoie via Tor P2P
  → message = "FILE|fileId|key|iv|fileName|fileSize|1" (flag one-shot = "1")
  → chiffre avec Double Ratchet → envoie via Tor Hidden Service

Réception :
  → déchiffre message → détecte flag one-shot (7ème champ = "1")
  → reçoit + déchiffre fichier → stockage local
  → affiche bulle avec icône 🔥 "Ouvrir (1 fois)"

Ouverture (2 phases) :
  Phase 1 (immédiate) : flagOneShotOpened() → UPDATE oneShotOpened=1 dans Room
                         → la bulle passe en état "Vérouillée" immédiatement
  Phase 2 (5s delay)  : coroutine delay(5000)
                         → supprime fichier physique (File.delete())
                         → markOneShotOpened() → UPDATE localFilePath=NULL

Anti-contournement :
  • Le flag DB est posé AVANT l'ouverture de l'Intent viewer
  • Même si l'utilisateur quitte la conversation, le flag est déjà en base
  • Au retour, la bulle affiche "Éphémère déjà vue / Expirée"
```

---

<div align="center">

[← Retour au README](../../README.md)

</div>
