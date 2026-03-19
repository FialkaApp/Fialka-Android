<div align="right">
  🇫🇷 Français | <a href="../en/CRYPTO.md">🇬🇧 English</a>
</div>

<div align="center">

# 🔐 Protocole Cryptographique

<img src="https://img.shields.io/badge/Key_Exchange-X25519_ECDH-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Post--Quantum-PQXDH_(ML--KEM--768)-4A148C?style=for-the-badge" />
<img src="https://img.shields.io/badge/Encryption-AES--256--GCM-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/PFS-Double_Ratchet-6A1B9A?style=for-the-badge" />

</div>

---

## Vue d'ensemble

```
Alice                                         Bob
  │                                             │
  │◄──────── Échange clés publiques ──────────►│
  │           (QR code v2 : X25519 + ML-KEM)    │
  │                                             │
  │  shared_secret = X25519(sk_A, pk_B)         │  shared_secret = X25519(sk_B, pk_A)
  │  root_key = HKDF(shared_secret)            │  root_key = HKDF(shared_secret)
  │  send_chain = HKDF(root, "init-send")      │  recv_chain = HKDF(root, "init-send")
  │  recv_chain = HKDF(root, "init-recv")      │  send_chain = HKDF(root, "init-recv")
  │                                             │
  │  ┌─ PQXDH (premier message) ────────────┐  │
  │  │ kem_ct = ML-KEM-768.Encaps(pk_kem_B)   │  │
  │  │ kem_ss = shared secret ML-KEM          │  │
  │  │ root_key' = HKDF(root_key || kem_ss)   │  │
  │  └──────────────────────────────────────┘  │
  │                                             │
  │  msg_key = HMAC(send_chain, 0x01)          │
  │  send_chain = HMAC(send_chain, 0x02)       │
  │  ct = AES-GCM(msg_key, iv, plaintext)      │
  │                                             │
  │  ──── {ct, iv, ephKey, kemCiphertext} ────►│
  │           (Firebase relay)                  │
  │                                             │
  │                                             │  kem_ss = ML-KEM-768.Decaps(sk_kem_B, kem_ct)
  │                                             │  root_key' = HKDF(root_key || kem_ss)
  │                                             │  msg_key = HMAC(recv_chain, 0x01)
  │                                             │  recv_chain = HMAC(recv_chain, 0x02)
  │                                             │  plaintext = AES-GCM-dec(msg_key, iv, ct)
```

---

## Identité

1. Génération d'une paire **X25519** au premier lancement
2. Clé privée → **EncryptedSharedPreferences** (AES-256-GCM, Android Keystore-backed)
3. Clé publique → Base64 + QR code pour partage
4. Backup : clé privée → **24 mots BIP-39** (256 bits + 8-bit checksum SHA-256)
5. Restauration : 24 mots → clé privée → dérivation clé publique (DH avec point de base X25519 u=9)

---

## Échange de clés

1. Alice montre son **QR code** (ou partage sa clé publique)
2. Bob scanne le QR → le pseudo d'Alice est pré-rempli automatiquement → crée le contact
3. Chaque côté calcule : `shared_secret = X25519(ma_clé_privée, clé_publique_contact)`
4. Le rôle (initiator/responder) est déterminé par l'**ordre lexicographique** des clés publiques
5. Le QR v2 encode aussi la clé publique **ML-KEM-768** pour l'upgrade PQXDH

> **Format QR v2 :** `securechat://contact?key=<X25519_base64>&kem=<ML-KEM-768_base64>&name=<displayName>`

---

## Fingerprint Emojis (96-bit, anti-MITM)

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
- ✅ Badge dans le chat : ✅ Vérifié / ⚠️ Non vérifié
- ✅ Vérification **indépendante** par utilisateur (état local Room uniquement)
- ✅ Messages système dans le chat lors de la vérification/retrait (avec lien cliquable "Voir l'empreinte")
- ✅ Notification événementielle Firebase (`fingerprintEvent: "verified:<timestamp>"`) — notifie le pair, ne synchronise pas l'état

---

## Double Ratchet (PFS + Healing)

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

  plaintext → pad(plaintext) → AES-256-GCM(message_key[N], random_iv_12B) → ciphertext
```

### Padding (anti analyse de taille)

Avant chiffrement, chaque message est paddé vers le bucket supérieur :

| Taille message | Bucket |
|----------------|--------|
| ≤ 256 B        | 256 B  |
| ≤ 1 KB         | 1 KB   |
| ≤ 4 KB         | 4 KB   |
| > 4 KB         | 16 KB  |

- Header : 2 octets (Big-Endian) = longueur réelle du plaintext
- Remplissage : `SecureRandom` bytes jusqu'à la taille du bucket
- Suppression du padding à la réception via le header 2 octets

### Propriétés

- ✅ Chaque message a sa propre clé de chiffrement (KDF chain)
- ✅ L'avancement de la chaîne est **irréversible** (one-way function)
- ✅ **Healing** : compromission d'une chain key → DH ratchet guérit au prochain échange
- ✅ Compromettre la clé actuelle **ne révèle pas** les clés passées
- ✅ Les clés intermédiaires sont **zérorisées** de la mémoire après usage
- ✅ Clés éphémères X25519 renouvelées à chaque changement de direction

---

## Signature de message (Ed25519, V3.2)

Chaque message est signé avec une clé **Ed25519** dédiée (séparée de la clé d'identité X25519) via BouncyCastle 1.80.

```
Envoi :
  signingKeyPair = getOrDeriveSigningKeyPair()   (EncryptedSharedPreferences)
  dataToSign = ciphertext.UTF8 || conversationId.UTF8 || createdAt.bigEndian8bytes
  signature  = Ed25519.sign(signingKeyPair.private, dataToSign)
  → envoyé dans le message Firebase : { ..., "signature": Base64(signature) }

Réception :
  signingPublicKey = fetchSigningPublicKeyByIdentity(contact.publicKey)
  dataToVerify = ciphertext.UTF8 || conversationId.UTF8 || createdAt.bigEndian8bytes
  valid = Ed25519.verify(signingPublicKey, dataToVerify, signature)
  → Badge : ✅ (valid=true) ou ⚠️ (valid=false ou clé absente)
```

### Propriétés

- ✅ **Anti-falsification** : seul le détenteur de la clé privée Ed25519 peut signer
- ✅ **Anti-replay inter-conversation** : `conversationId` inclus dans les données signées
- ✅ **Anti-manipulation temporelle** : `createdAt` (timestamp client) inclus dans les données signées
- ✅ **Clé de signature séparée** de la clé d'identité X25519 (pas de mélange des usages)
- ✅ **Nettoyage** : clé de signature supprimée de Firebase (`/signing_keys/{hash}`) à la suppression du compte

---

## PQXDH — Upgrade Post-Quantique (V3.4)

SecureChat implémente un échange de clés **hybride** combinant X25519 (classique) et ML-KEM-768 (post-quantique) via le protocole PQXDH.

### Principe

```
À l'ajout du contact (QR scan) :
  Les clés publiques X25519 ET ML-KEM-768 sont échangées via le QR code v2.
  La conversation démarre en mode classique X25519 uniquement (root_key classique).

Premier message (initiator) :
  kem_ct, kem_ss = ML-KEM-768.Encaps(contact_kem_publicKey)
  root_key' = HKDF(root_key || kem_ss, "pqxdh-upgrade")
  → message Firebase inclut { ..., "kemCiphertext": Base64(kem_ct) }
  → root_key upgradé localement (chains recalculées)

Réception du premier message (responder) :
  kem_ss = ML-KEM-768.Decaps(my_kem_privateKey, kemCiphertext)
  root_key' = HKDF(root_key || kem_ss, "pqxdh-upgrade")
  → root_key upgradé localement (chains recalculées)

Messages suivants :
  kemCiphertext n'est plus envoyé (upgrade unique)
  Double Ratchet continue avec root_key' (hybride)
```

### Propriétés

- ✅ **Résistance post-quantique** : même si X25519 est cassé par un ordinateur quantique, ML-KEM-768 protège le root_key
- ✅ **Upgrade différée** : pas de message bootstrap — l'upgrade se fait au premier vrai message
- ✅ **Aucune régression** : si ML-KEM échoue, la conversation reste protégée par X25519 classique
- ✅ **BouncyCastle 1.80** : implémentation ML-KEM-768 certifiée (package `org.bouncycastle.pqc.crypto.mlkem`)
- ✅ **StrongBox probe** : `DeviceSecurityManager` détecte le support matériel StrongBox pour la protection des clés

---

## Ce qui transite sur Firebase

### Messages (chiffrés)

```json
{
  "ciphertext": "a3F4bWx...",
  "iv": "dG9rZW4...",
  "createdAt": 1700000000000,
  "senderUid": "HMAC-SHA256(uid, conversationId)",
  "signature": "Ed25519(ciphertext || conversationId || createdAt)"
}
```

- **V3.0** : `senderUid` = `HMAC-SHA256(firebaseUid, conversationId)` → l'UID brut n'est plus visible
- **V3.0** : Les messages sont **supprimés de Firebase** dès réception (`deleteMessageFromFirebase()`)
- **V3.0** : Le message paddé (cf. section Padding) est chiffré → taille uniforme sur le réseau
- **V3.2** : `signature` = Ed25519 sur `ciphertext_UTF8 || conversationId_UTF8 || createdAt_bigEndian8bytes` → anti-falsification + anti-replay
- **V3.2** : `createdAt` = `System.currentTimeMillis()` côté client (pas `ServerValue.TIMESTAMP`) pour cohérence signature

### Paramètres éphémères

```
/conversations/{id}/settings/ephemeralDuration = 3600000
```

### Événements fingerprint (V3.4)

```
/conversations/{id}/settings/fingerprintEvent = "verified:<timestamp>"
```

- Notification événementielle uniquement — **ne synchronise pas** l'état de vérification
- Chaque utilisateur gère son état `fingerprintVerified` localement en Room

### Supprimé du wire format (V1.1 metadata hardening)

- `senderPublicKey` — inutile en 1-to-1 (le destinataire connaît déjà la clé du contact)
- `messageIndex` — chiffré dans le payload AES-GCM (trial decryption côté réception)

**Jamais envoyé :** texte en clair, clés privées, clés de chaîne, position du ratchet.

### Chiffrement de fichiers (V3.0)

```
Envoi :
  fichier → clé AES-256-GCM aléatoire (fileKey)
  → chiffre fichier (encryptFile) → upload Firebase Storage (/chat_files/{convId}/{uuid})
  → message = "FILE|" + downloadUrl + "|" + Base64(fileKey) + "|" + fileName
  → chiffre message avec Double Ratchet → envoie sur RTDB

Réception :
  → déchiffre message → détecte préfixe "FILE|"
  → télécharge fichier chiffré depuis Storage
  → déchiffre avec fileKey → sauvegarde stockage interne
```

### Demande de contact (inbox Firebase)

```json
{
  "senderPublicKey": "MFkwEwYHKoZ...",
  "senderDisplayName": "Alice",
  "conversationId": "conv_abc123",
  "createdAt": 1700000000000
}
```

---

## Modèle de menace

| Menace | Protégé ? | Détail |
|--------|-----------|--------|
| Firebase lit les messages | ✅ | Chiffré E2E, Firebase ne voit que du ciphertext |
| Compromission d'une clé message | ✅ | PFS — chaque message a sa propre clé |
| Replay d'anciens messages | ✅ | sinceTimestamp + lastDeliveredAt + messageIndex (embedded dans ciphertext) |
| Race conditions ratchet | ✅ | Mutex par conversation + ConcurrentHashMap.putIfAbsent() + éviction LRU |
| Attaque MITM | ✅ | Fingerprint emojis 96-bit (vérification visuelle indépendante) |
| Vol du téléphone déverrouillé | ✅ | Keystore, SQLCipher, App Lock PIN + biométrie, auto-lock |
| Messages sensibles oubliés | ✅ | Messages éphémères (timer sur envoi / lecture) |
| Falsification de message | ✅ | Signature Ed25519 par message (V3.2) — badge ✅/⚠️ |
| Métadonnées (qui/quand) | ✅ | senderUid → HMAC-SHA256, padding uniforme, dummy traffic, delete-after-delivery |
| Analyse de trafic | ✅ | Dummy traffic (30-90 s, même pipeline), padding par buckets, suppression après réception |
| Fichiers interceptés | ✅ | Chiffrement AES-256-GCM par fichier, clé transmise dans le canal E2E |
| Perte du téléphone | ✅ | Phrase mnémonique 24 mots (BIP-39) pour restaurer l'identité |
| App Lock brute-force | ✅ | PBKDF2 600 000 itérations + verrouillage biométrique |
| Contact supprime son compte | ✅ | Détection automatique conversation morte + nettoyage + re-invitation |
| Ordinateur quantique (futur) | ✅ | PQXDH hybride ML-KEM-768 — root_key upgradé avec secret post-quantique (V3.4) |
| Désynchronisation ratchet | ✅ | syncExistingMessages à l'acceptation, delete-after-failure, lastDeliveredAt lower-bound |

> Voir aussi [`SECURITY.md`](../../SECURITY.md) pour l'analyse complète des mesures de sécurité.

---

<div align="center">

[← Retour au README](../../README.md)

</div>
