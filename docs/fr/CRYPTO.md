<div align="right">
  🇫🇷 Français | <a href="../en/CRYPTO.md">🇬🇧 English</a>
</div>

<div align="center">

# 🔐 Protocole Cryptographique

<img src="https://img.shields.io/badge/Key_Exchange-X25519_ECDH-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Encryption-AES--256--GCM-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/PFS-Double_Ratchet-6A1B9A?style=for-the-badge" />

</div>

---

## Vue d'ensemble

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
2. Bob scanne le QR → entre le pseudo d'Alice → crée le contact
3. Chaque côté calcule : `shared_secret = X25519(ma_clé_privée, clé_publique_contact)`
4. Le rôle (initiator/responder) est déterminé par l'**ordre lexicographique** des clés publiques

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
- ✅ Badge dans le chat : vert (vérifié ✓) ou orange (non vérifié ➤)
- ✅ Persisté en Room, état de vérification par conversation

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

  plaintext → AES-256-GCM(message_key[N], random_iv_12B) → ciphertext
```

### Propriétés

- ✅ Chaque message a sa propre clé de chiffrement (KDF chain)
- ✅ L'avancement de la chaîne est **irréversible** (one-way function)
- ✅ **Healing** : compromission d'une chain key → DH ratchet guérit au prochain échange
- ✅ Compromettre la clé actuelle **ne révèle pas** les clés passées
- ✅ Les clés intermédiaires sont **zérorisées** de la mémoire après usage
- ✅ Clés éphémères X25519 renouvelées à chaque changement de direction

---

## Ce qui transite sur Firebase

### Messages (chiffrés)

```json
{
  "ciphertext": "a3F4bWx...",
  "iv": "dG9rZW4...",
  "createdAt": 1700000000000,
  "senderUid": "firebase-anonymous-uid"
}
```

### Paramètres éphémères

```
/conversations/{id}/settings/ephemeralDuration = 3600000
```

### Supprimé du wire format (V1.1 metadata hardening)

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

---

## Modèle de menace

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

> Voir aussi [`SECURITY.md`](../../SECURITY.md) pour l'analyse complète des mesures de sécurité.

---

<div align="center">

[← Retour au README](../../README.md)

</div>
