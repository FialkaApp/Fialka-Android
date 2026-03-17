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
│         5 Thèmes · Animations · Navigation        │
├──────────────────────────────────────────────────┤
│               Repository Layer                    │
│      ChatRepository — source de vérité unique     │
├────────────────┬────────────────┬────────────────┤
│    Room DB     │     Crypto     │    Firebase     │
│   (SQLCipher)  │  CryptoMgr +   │   Relay +       │
│                │   Ratchet      │    FCM          │
└────────────────┴────────────────┴────────────────┘
                Cloud Function (push triggers)
```

---

## Principes fondamentaux

| Principe | Détail |
|----------|--------|
| **Single Activity** | Navigation Component avec 15 fragments |
| **Repository Pattern** | `ChatRepository` coordonne Room, Crypto et Firebase |
| **Aucun accès Firebase depuis l'UI** | Tout passe par Repository → FirebaseRelay |
| **Mutex par conversation** | Les opérations ratchet sont sérialisées (thread-safe) |
| **Envoi atomique** | Le ratchet n'avance que si Firebase confirme l'envoi |
| **Delete-after-delivery** | Le ciphertext est supprimé de Firebase après déchiffrement |
| **Double-listener guard** | `processedFirebaseKeys` empêche le double-traitement |
| **Système d'invitation** | Demande → notification inbox → acceptation → conversation active |

---

## Couches

| Couche | Rôle | Fichiers clés |
|--------|------|---------------|
| **UI** | Écrans, navigation, interactions | `ui/` — Fragments, ViewModels, Adapters |
| **Repository** | Coordination local/crypto/remote | `data/repository/ChatRepository.kt` |
| **Crypto** | X25519, ECDH, AES-GCM, Double Ratchet, BIP-39 | `crypto/CryptoManager.kt`, `DoubleRatchet.kt`, `MnemonicManager.kt` |
| **Local DB** | Room v12 — users, contacts, messages, ratchet (indexes composites) | `data/local/` — DAOs, Database (SQLCipher) |
| **Remote** | Relay Firebase RTDB + Storage (ciphertext only) | `data/remote/FirebaseRelay.kt` |
| **Util** | QR, 5 thèmes, app lock, éphémère, dummy traffic | `util/ThemeManager.kt`, `AppLockManager.kt`, `DummyTrafficManager.kt` |

---

## Push Notifications (opt-in)

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

---

## Flux des demandes de contact

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

---

## Cycle de vie d'un compte

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

## Dummy Traffic (anti analyse de trafic)

```
DummyTrafficManager.start(context):
  → isEnabled(context)? → Non: return
  → Oui: boucle CoroutineScope(Dispatchers.IO)
    → délai aléatoire 30–90 s
    → pour chaque conversation active (Room)
      → generateDummyMessage() : préfixe opaque + random bytes
      → chiffre avec Double Ratchet (même pipeline)
      → envoie sur Firebase RTDB (/messages/{convId})
      → le récepteur détecte le préfixe → drop silencieux (pas d'insertion Room)

Toggle: SecurityFragment → SharedPreferences("securechat_settings") → "dummy_traffic_enabled"
```

---

## Partage de fichier E2E

```
Envoi (ChatViewModel.sendFile):
  fichier → generateFileKey() (AES-256-GCM, clé aléatoire)
  → encryptFile(fileKey, plainBytes) → cipherBytes
  → uploadToFirebaseStorage(/chat_files/{convId}/{uuid}) → downloadUrl
  → message texte = "FILE|" + downloadUrl + "|" + Base64(fileKey) + "|" + fileName
  → chiffre avec Double Ratchet → envoie sur Firebase RTDB

Réception (ChatRepository):
  → déchiffre message → détecte préfixe "FILE|"
  → parse: url, fileKey (Base64), fileName
  → downloadFromFirebaseStorage(url) → cipherBytes
  → decryptFile(fileKey, cipherBytes) → plainBytes
  → sauvegarde dans stockage interne app
  → affiche lien cliquable dans le chat
```

---

<div align="center">

[← Retour au README](../../README.md)

</div>
