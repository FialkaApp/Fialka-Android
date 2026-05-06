<div align="right">
  🇫🇷 Français | <a href="../en/CHANGELOG.md">🇬🇧 English</a>
</div>

<div align="center">

# 🗺 Changelog & Roadmap

<img src="https://img.shields.io/badge/Current-V4.3.5--alpha-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/versionCode-14-9C4DCC?style=for-the-badge" />

</div>

---

<details>
<summary><h2>✅ V1 — Core</h2></summary>


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
- [x] App Lock — Code PIN 6 chiffres + déverrouillage biométrique opt-in
- [x] Profil amélioré — Cards, en-tête avatar, zone danger, UX modernisée
- [x] Paramètres améliorés — Sections verrouillage / notifications / sécurité
- [x] Messages éphémères — Timer côté envoi + côté lecture, durée synchro Firebase
- [x] Dark mode — Thème DayNight complet, couleurs adaptatives
- [x] Auto-lock timeout — Configurable (5s → 5min), défaut 5 secondes
- [x] Sous-écran Fingerprint — Visualisation + vérification dédiée
- [x] Profil contact redesign — Hub conversation (éphémère, fingerprint, danger zone)
- [x] 5 thèmes UI — Midnight, Hacker, Phantom (défaut), Aurora, Daylight + sélecteur visuel
- [x] Animations complètes — Transitions navigation, bulles animées, liste en cascade, toolbar scrollable

</details>

---

<details>
<summary><h2>✅ V2 — Crypto Upgrade</h2></summary>


> Full Double Ratchet X25519, remplacement de P-256 par Curve25519.

- [x] **Full Double Ratchet X25519** — DH ratchet + KDF chains + healing automatique
- [x] **X25519 natif** — Courbe Curve25519 (API 33+), remplace P-256
- [x] **Initial chains** — Les deux côtés peuvent envoyer immédiatement après acceptation
- [x] **Échange d'éphémères naturel** — Via les vrais messages, pas de message bootstrap

</details>

---

<details>
<summary><h2>✅ V2.1 — Account Lifecycle</h2></summary>


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

</details>

---

<details>
<summary><h2>✅ V2.2 — UI Modernization</h2></summary>


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

</details>

---

<details>
<summary><h2>✅ V3.0 — Security Hardening</h2></summary>


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

</details>

---

<details>
<summary><h2>✅ V3.1 — Settings Redesign & PIN Upgrade</h2></summary>


> Paramètres repensés Signal/Telegram, PIN 6 chiffres, sous-écran Confidentialité, performance PIN.

### ⚙️ Paramètres
- [x] **Redesign complet** — Hiérarchie Signal-like : Général (Apparence, Notifications), Confidentialité, Sécurité, À propos
- [x] **Sous-écran Confidentialité** — Messages éphémères, delete-after-delivery, dummy traffic regroupés
- [x] **PrivacyFragment** — Nouveau fragment dédié avec navigation intégrée
- [x] **Section À propos** — Version dynamique, info chiffrement, licence GPLv3

### 🔐 Sécurité PIN
- [x] **PIN 6 chiffres** — Remplacement du code à 4 chiffres, 6 dots sur l’écran de verrouillage
- [x] **Suppression legacy** — Retrait du support SHA-256 et backward compat 4 chiffres
- [x] **Coroutines PIN** — Vérification PBKDF2 (600K itérations) sur `Dispatchers.Default`, zéro freeze UI
- [x] **Cache EncryptedSharedPreferences** — Double-checked locking, plus d’init Keystore répétée
- [x] **Vérification unique** — Check uniquement au 6ème chiffre (plus de check intermédiaire)

</details>

---

<details>
<summary><h2>✅ V3.2 — Ed25519 Message Signing</h2></summary>


> Signature par message Ed25519, badge ✅/⚠️, durcissement Firebase rules, nettoyage clés de signature.

### ✍️ Signature de messages
- [x] **Ed25519 (BouncyCastle 1.78.1)** — Paire de clés de signature dédiée (séparée de X25519)
- [x] **Données signées** — `ciphertext_UTF8 || conversationId_UTF8 || createdAt_bigEndian8` — anti-falsification + anti-replay
- [x] **Provider JCA** — `Security.removeProvider("BC")` + `insertProviderAt(BouncyCastleProvider(), 1)` dans Application.onCreate()
- [x] **Stockage clé** — Clé privée dans EncryptedSharedPreferences ; clé publique sur `/signing_keys/{SHA256_hash}` et `/users/{uid}/signingPublicKey`
- [x] **Vérification à la réception** — Récupération clé publique Ed25519 par hash d'identité, badge ✅ (valide) ou ⚠️ (invalide/absent)
- [x] **Timestamp client** — `createdAt` = `System.currentTimeMillis()` (pas `ServerValue.TIMESTAMP`) pour cohérence signature

### 🛡️ Durcissement Firebase
- [x] **Participants scopés** — `/conversations/$id/participants` lisible uniquement par les membres (plus par tous les authentifiés)
- [x] **Nettoyage clés de signature** — `/signing_keys/{hash}` supprimé à la suppression de compte

</details>

---

<details>
<summary><h2>✅ V3.3 — Material 3, Tor Integration, Attachment UX & Log Hardening</h2></summary>


> Migration complète Material Design 3, intégration Tor (SOCKS5 + VPN TUN), icônes d'attachement inline style Session, permissions Android 13+, durcissement Firebase et logs.

### 🎨 Material Design 3
- [x] **Migration M2 → M3** — Tous les 5 thèmes migrés de `Theme.MaterialComponents` vers `Theme.Material3.Dark.NoActionBar` / `Theme.Material3.Light.NoActionBar`
- [x] **Rôles de couleur M3 complets** — Ajout de `colorPrimaryContainer`, `colorOnPrimary`, `colorSecondary`, `colorSurfaceVariant`, `colorOutline`, `colorSurfaceContainerHigh/Medium/Low`, `colorError`, etc. sur les 5 thèmes
- [x] **TextInputLayout M3** — Migration vers `Widget.Material3.TextInputLayout.OutlinedBox` (Onboarding, Restore, AddContact)
- [x] **Boutons M3** — Migration vers `Widget.Material3.Button.TextButton` / `OutlinedButton` (TorBootstrap, Onboarding, Profile)
- [x] **Geste prédictif Android 13+** — `enableOnBackInvokedCallback="true"` dans le manifest

### 📎 Icônes d'attachement inline (style Session)
- [x] **Remplacement du BottomSheet** — Les 3 options (Fichier 📁, Photo 🖼, Caméra 📷) apparaissent comme des icônes verticales animées au-dessus du bouton +
- [x] **Animation slide-up + fade-in** — Les icônes glissent vers le haut avec fondu, le bouton + tourne en × (rotation 45°)
- [x] **Overlay de fermeture** — Vue transparente plein écran pour fermer les icônes au tap n'importe où
- [x] **ic_add.xml** — Nouvelle icône vectorielle + pour le bouton d'attachement

### 📱 Permissions Android 13+
- [x] **READ_MEDIA_IMAGES** — Permission Android 13+ pour l'accès aux photos
- [x] **READ_MEDIA_AUDIO** — Permission Android 13+ pour l'accès aux fichiers audio
- [x] **READ_EXTERNAL_STORAGE** — Fallback avec `maxSdkVersion="32"` pour Android 12 et inférieur
- [x] **Permission launchers** — Logique complète de demande de permission avec dialogue de refus

### 🔥 Corrections Firebase
- [x] **Sign-out Firebase** — Suppression de `database.goOnline()` après `auth.signOut()` (corrige l'erreur de permission Firebase)
- [x] **Firebase locale** — Remplacement de `useAppLanguage()` par `setLanguageCode(Locale.getDefault().language)` explicite (corrige X-Firebase-Locale null)
- [x] **Double publication de clé de signature** — Flag `signingKeyPublished` + `markSigningKeyPublished()` élimine la publication redondante entre OnboardingViewModel et ConversationsViewModel

### 🛡️ Durcissement des logs
- [x] **ProGuard complet** — Ajout de `Log.w()`, `Log.e()`, `Log.wtf()` dans `assumenosideeffects` (en plus de d/v/i) — suppression totale des logs en release
- [x] **Sanitisation des logs** — Suppression des UIDs Firebase, hash de clés et préfixes de clés des messages de log de debug
- [x] **Zéro donnée sensible** — `FirebaseRelay.kt` et `ChatRepository.kt` n'affichent plus de chemins Firebase ou d'identifiants dans les logs

### 🧅 Intégration Tor
- [x] **TorManager.kt** — Singleton avec `StateFlow<TorState>` (`IDLE`, `STARTING`, `BOOTSTRAPPING(%)`, `CONNECTED`, `ERROR`, `DISCONNECTED`)
- [x] **TorVpnService.kt** — Service VPN TUN → hev-socks5-tunnel → SOCKS5 :9050 → Tor → Internet
- [x] **libtor.so + libhev-socks5-tunnel.so** — Binaires natifs arm64-v8a embarqués
- [x] **ProxySelector global** — Tout le trafic HTTP routé via SOCKS5 `127.0.0.1:9050` quand Tor activé
- [x] **Démarrage conditionnel** — `FialkaApplication.onCreate()` démarre Tor si activé
- [x] **TorBootstrapFragment** — `startDestination` du nav graph, choix Tor/Normal au premier lancement
- [x] **Progress circulaire animé** — Pourcentage en temps réel, texte de statut dynamique, pulse animation
- [x] **Respecte les 5 thèmes** — Couleurs via `?attr/` du thème actif
- [x] **Toggle Tor** — ON/OFF dans Paramètres → Sécurité avec reconnexion manuelle
- [x] **Statut temps réel** — "Connecté via Tor" / "Reconnexion..." / "Déconnecté"
- [x] **Dummy traffic par conversation** — Trafic factice individuel par conversation active

</details>

---

<details>
<summary><h2>✅ V3.4 — Post-Quantum & Device Security</h2></summary>


> PQXDH hybride (ML-KEM-1024 + X25519), DeviceSecurityManager StrongBox, QR deep link v2, vérification d'empreinte indépendante, corrections de désynchronisation ratchet.

### 🔐 Cryptographie Post-Quantique (PQXDH)
- [x] **ML-KEM-1024 (Kyber)** — Encapsulation post-quantique via BouncyCastle 1.80, paire clé encaps/decaps dédiée
- [x] **PQXDH hybride** — Échange de clés X25519 classique + ML-KEM-1024 encapsulation en parallèle
- [x] **Upgrade différée du rootKey** — La première conversation démarre en classique X25519 ; le rootKey est upgradé avec le secret ML-KEM au premier message (pas de message bootstrap)
- [x] **kemCiphertext dans le premier message** — Le ciphertext ML-KEM est envoyé une seule fois, dans le premier message Firebase de la conversation
- [x] **QR deep link v2** — Format `fialka://contact?key=<X25519>&kem=<ML-KEM-1024-pubKey>&name=<displayName>` — clé ML-KEM encodée dans le QR
- [x] **Auto-fill nom depuis QR** — Le pseudo du contact est pré-rempli automatiquement depuis le scan QR

### 🛡️ Sécurité Appareil
- [x] **DeviceSecurityManager** — Sonde StrongBox hardware, niveaux de sécurité MAXIMUM/STANDARD
- [x] **Bannière StrongBox** — Indicateur visuel dans Paramètres → À propos selon le niveau de sécurité détecté
- [x] **displayName masqué** — Le pseudo n'est plus stocké sur Firebase (`storeDisplayName` → no-op), supprimé des Firebase rules
- [x] **Paramètres réorganisés** — Carte sécurité déplacée dans la section À propos, texte chiffrement mis à jour

### 🔧 Corrections Critiques
- [x] **Fix désynchronisation PQXDH** — `syncExistingMessages()` à l'acceptation d'un contact pour déclencher correctement l'init PQXDH
- [x] **Delete-after-failure** — Les messages échoués au déchiffrement sont nettoyés de Firebase (évite boucle d'erreur infinie)
- [x] **lastDeliveredAt** — Nouveau champ sur l'entité Conversation pour filtrage lower-bound des messages Firebase (évite re-traitement)
- [x] **Fix dual-listener** — `ConcurrentHashMap.putIfAbsent()` + éviction LRU pour empêcher les race conditions sur les listeners Firebase
- [x] **Fix déchiffrement à l'acceptation** — Le responder déclenche maintenant le sync des messages existants dès l'acceptation

### 🔏 Vérification d'empreinte
- [x] **Vérification indépendante** — Chaque utilisateur vérifie de son côté (état local Room uniquement, pas de sync d'état)
- [x] **Événements Firebase** — Notification événementielle `fingerprintEvent: "verified:<timestamp>"` (push seulement, pas de sync d'état)
- [x] **Messages système** — Info message dans le chat quand un participant vérifie/retire la vérification
- [x] **Lien cliquable** — "Voir l'empreinte" dans les messages système redirige vers l'écran fingerprint
- [x] **Toggle vérifier/retirer** — Bouton dans FingerprintFragment pour marquer vérifié ou retirer la vérification
- [x] **Badges mis à jour** — ✅ Vérifié / ⚠️ Non vérifié (remplace l'ancien format vert/orange)

### 🗄️ Base de données
- [x] **Room v16** — Migration v15→v16 : ajout colonne `lastDeliveredAt` sur Conversation
- [x] **Version 3.4.0** — `versionCode 5`, `versionName "3.4.0"`

</details>

---

<details>
<summary><h2>✅ V3.4.1 — One-Shot Photos, Restore Redesign & QR Fingerprint</h2></summary>


> Photos éphémères one-shot, écran de restauration repensé avec grille BIP-39, vérification d'empreinte par QR code, améliorations UI.

### 📸 Photos éphémères One-Shot
- [x] **Envoi one-shot** — Option "photo éphémère" : la photo ne peut être vue qu'une seule fois par le destinataire ET l'expéditeur
- [x] **Suppression sécurisée 2 phases** — Phase 1 : flag `oneShotOpened=1` immédiat dans Room (empêche la re-visualisation) ; Phase 2 : suppression physique du fichier après 5 secondes (délai pour l'app de visualisation)
- [x] **Protection anti-navigation** — Le flag DB est posé immédiatement au clic (pas dans un `Handler.postDelayed`), empêchant le contournement par retour arrière
- [x] **UI expéditeur** — 4 états : one-shot expiré (🔥 verrouillé, grisé), one-shot prêt (🔥 "Ouvrir 1 fois"), fichier normal, message texte
- [x] **UI destinataire** — 6 états avec gestion one-shot intégrée dans les bulles reçues
- [x] **Indicateur d'envoi** — Icône ✓ de confirmation d'envoi dans les bulles

### 🔑 Écran de restauration repensé
- [x] **Grille BIP-39 professionnelle** — 24 cellules `AutoCompleteTextView` en grille 3×8 avec numérotation
- [x] **Autocomplete BIP-39** — Chaque cellule propose les 2048 mots BIP-39 avec seuil de 1 caractère
- [x] **Auto-avancement** — Sélection d'un mot ou Entrée passe automatiquement à la cellule suivante
- [x] **Coloration de focus** — Vert = mot valide BIP-39, rouge = mot invalide
- [x] **Compteur de mots** — Affichage "X / 24 mots" en temps réel
- [x] **Validation visuelle** — Mots invalides surlignés en rouge lors de la tentative de restauration

### 🔏 QR Code Fingerprint
- [x] **Toggle emoji/QR** — Bascule animée (rotation 180° + fade) entre emojis 16 caractères et QR code
- [x] **QR SHA-256 hex** — Le QR encode le fingerprint en hexadécimal SHA-256 (64 caractères ASCII, pas les emojis) pour éviter les problèmes d'encodage Unicode
- [x] **Scanner QR fingerprint** — Utilise le même `CustomScannerActivity` que l'invitation de contact (torche, orientation libre)
- [x] **Vérification automatique** — Scan QR → comparaison hex `ignoreCase` → dialogue ✅ match ou ❌ MITM warning
- [x] **Méthode `getSharedFingerprintHex()`** — Nouvelle méthode dans CryptoManager retournant le SHA-256 hex brut des clés publiques triées

### 🎨 Améliorations UI
- [x] **Dialogue de confirmation d'envoi** — Confirmation avant envoi de fichiers
- [x] **Barre de progression** — Indicateur d'upload/download de fichiers
- [x] **Bouton retry** — Réessayer l'envoi en cas d'échec
- [x] **Protocole affiché** — "PQXDH · X25519 + ML-KEM-1024 · AES-256-GCM · Double Ratchet" dans le profil du contact
- [x] **Fix timestamps** — Correction de l'affichage des horodatages dans les bulles
- [x] **Fix maxWidth** — Largeur maximale des bulles corrigée
- [x] **Audit 29 layouts** — Revue complète et corrections des 29 fichiers de layout
- [x] **PIN oublié** — Flux de récupération "PIN oublié" avec phrase mnémonique

### 🗄️ Base de données
- [x] **Room v17** — Migration v16→v17 : ajout colonne `oneShotOpened` sur MessageLocal
- [x] **`flagOneShotOpened()`** — Nouvelle requête DAO : `UPDATE messages SET oneShotOpened = 1 WHERE localId = :messageId`
- [x] **Version 3.4.1** — `versionCode 6`, `versionName "3.4.1"`

### 🛡️ Audit de sécurité (42+ vulnérabilités corrigées)
- [x] **Firebase rules write-once** — `/signing_keys/{hash}`, `/mlkem_keys/{hash}`, `/inbox/{hash}/{convId}` imposent `!data.exists()` — empêche l'écrasement de clés et le replay de demandes
- [x] **Firebase rules validation** — `senderUid.length === 32`, ciphertext non-vide + max 65536, iv non-vide + max 100, `createdAt <= now + 60000`
- [x] **Zeroing mémoire HKDF** — `hkdfExtractExpand()` efface IKM, `hkdfExpand()` efface PRK + expandInput après usage
- [x] **Zeroing mémoire Mnemonic** — `privateKeyToMnemonic()` et `mnemonicToPrivateKey()` effacent tous les tableaux d'octets intermédiaires et nettoient le StringBuilder
- [x] **Validation entrée PQXDH** — `deriveRootKeyPQXDH()` exige les deux entrées de 32 octets exactement
- [x] **Séparateur ConversationId** — `deriveConversationId()` utilise `"|"` pour éviter les collisions de concaténation de clés
- [x] **FLAG_SECURE** — Appliqué sur `MainActivity`, `LockScreenActivity`, `RestoreFragment` et le dialog mnemonic — bloque screenshots, enregistrement d'écran, aperçu tâches
- [x] **Masquage mnemonic** — Le champ mnemonic du PIN oublié utilise `TYPE_TEXT_VARIATION_PASSWORD`
- [x] **Seuil autocomplete** — Seuil autocomplete BIP-39 augmenté de 1 → 3 caractères
- [x] **Nettoyage RestoreFragment** — Les 24 champs de mots sont effacés dans `onDestroyView()`
- [x] **Durcissement deep links** — Réécriture complète de `parseInvite()` : whitelist paramètres, limites de taille, rejet doublons, rejet caractères de contrôle, validation Base64, max 4000 chars
- [x] **Validation ML-KEM** — Validation côté client de la clé publique ML-KEM (longueur < 2500, décodage Base64, taille décodée 1500–1650 octets)
- [x] **Sécurité presse-papiers** — Flag `EXTRA_IS_SENSITIVE` + auto-effacement 30 secondes via `Handler.postDelayed`
- [x] **SecureFileManager** — Nouvel utilitaire : écrasement 2 passes (données aléatoires + zéros, `fd.sync()`) avant `File.delete()`
- [x] **Zeroing fileBytes** — `saveFileLocally()` appelle `fileBytes.fill(0)` après écriture
- [x] **Suppression sécurisée one-shot** — Les fichiers one-shot utilisent `SecureFileManager.secureDelete()`
- [x] **Nettoyage conversations mortes** — `deleteStaleConversation()` écrase le répertoire de fichiers de la conversation
- [x] **Nettoyage messages expirés** — `deleteExpiredMessages()` supprime les fichiers associés en premier
- [x] **Guards FirebaseRelay** — `sendMessage()` avec `require()` sur tous les champs (conversationId, ciphertext, iv, taille senderUid, createdAt)
- [x] **Validation Cloud Function** — Validation regex pour senderUid (`/^[0-9a-f]{32}$/`) et format conversationId
- [x] **Payload FCM opaque** — Données push réduites à `{type: "new_message", sync: "1"}` — zéro fuite de metadata
- [x] **Notification générique** — `MyFirebaseMessagingService` affiche « Nouveau message reçu » (pas de nom, pas d'ID conversation)
- [x] **usesCleartextTraffic=false** — Imposé sur `<application>` — bloque tout trafic HTTP non chiffré
- [x] **filterTouchesWhenObscured** — Activé sur `MainActivity` et `LockScreenActivity` — protection tapjacking
- [x] **Storage rules suppression par propriétaire** — `resource.metadata['uploaderUid'] == request.auth.uid` requis pour supprimer
- [x] **Metadata upload** — `uploadEncryptedFile()` attache `uploaderUid` dans les StorageMetadata

</details>

---

<details>
<summary><h2>✅ V3.5 — SPQR, ChaCha20-Poly1305 & Threat Model</h2></summary>


> Triple Ratchet post-quantique (SPQR), chiffrement alternatif ChaCha20-Poly1305, modèle de menace documenté.

### 🔐 SPQR — Ré-encapsulation PQ périodique (Triple Ratchet)
- [x] **PQ Ratchet Step** — Nouvelle fonction `DoubleRatchet.pqRatchetStep()` : mixe un secret ML-KEM frais dans le rootKey via HKDF (info: `Fialka-SPQR-pq-ratchet`)
- [x] **Intervalle de ré-encapsulation** — `PQ_RATCHET_INTERVAL = 10` messages : toutes les 10 messages, le sender effectue un ML-KEM encaps et upgrape le rootKey
- [x] **Sender-side** — Dans `sendMessage()`, quand le compteur atteint 10 et que PQXDH est initialisé : `mlkemEncaps(remoteMlkemPublicKey)` → `pqRatchetStep(rootKey, ssPQ)` → nouveau rootKey + `kemCiphertext` attaché au message
- [x] **Receiver-side** — Dans `receiveMessage()`, détection du `kemCiphertext` sur une session déjà PQ-initialisée : `mlkemDecaps()` → `pqRatchetStep()` → rootKey upgradé, compteur réinitialisé
- [x] **Compteur persistant** — Nouveau champ `pqRatchetCounter` dans `RatchetState` (Room entity), incrémenté à chaque message envoyé
- [x] **Compatibilité** — Le mécanisme est transparent : pas de champ supplémentaire sur le wire (réutilise `kemCiphertext`), distingué du PQXDH initial par `pqxdhInitialized`

### 🔒 ChaCha20-Poly1305 — Chiffrement alternatif
- [x] **`encryptChaCha()` / `decryptChaCha()`** — Implémentation complète via BouncyCastle `ChaCha20Poly1305` AEAD (nonce 12 octets, tag 16 octets)
- [x] **Détection hardware AES** — `hasHardwareAes()` détecte la présence de l'extension ARMv8 Crypto ; ChaCha20 est sélectionné automatiquement sur les appareils sans accélération matérielle AES
- [x] **Sélection dynamique** — Le chiffrement de chaque message utilise AES-256-GCM (défaut) ou ChaCha20-Poly1305 selon le hardware du sender
- [x] **Champ `cipherSuite`** — Nouveau champ dans `FirebaseMessage` (0 = AES-GCM, 1 = ChaCha20) ; le receiver déchiffre avec le bon algorithme automatiquement
- [x] **Rétrocompatibilité** — Les anciens messages sans `cipherSuite` (= 0) sont déchiffrés en AES-GCM comme avant

### 📋 Modèle de menace documenté
- [x] **SECURITY.md** — Ajout d'une section Threat Model complète avec 6 tiers d'adversaires (T1 curious → T6 quantum)
- [x] **Matrice protection/résiduel** — Tableau détaillé des protections et risques résiduels par tier
- [x] **Limites documentées** — Section explicite « Ce que Fialka ne protège PAS »
- [x] **Principes de design** — 7 principes : defense in depth, hybrid PQ, forward secrecy, post-compromise healing, zero trust transport, minimal metadata, fail-safe defaults

### 🗄️ Base de données
- [x] **Room v18** — Migration v17→v18 : ajout colonne `pqRatchetCounter` sur RatchetState
- [x] **Version 3.5** — `versionCode 7`, `versionName "3.5"`

</details>

---

<details open>
<summary><h2>✅ V4.0 — Kill Firebase & P2P Pur via Tor</h2></summary>

> Suppression totale de Firebase, infrastructure P2P pure via Tor Hidden Services, système Mailbox offline 4 modes, identité « 1 Seed → Tout », ML-DSA-44, pipeline de livraison avec statuts, refresh UI 2026.
> `versionCode 8` · `versionName "4.0"` · Room v24

### 🔥 Kill Firebase — P2P Pur
- [x] **Suppression totale de Firebase** — Firebase BoM, Auth, RTDB, Storage, FCM, Cloud Functions entièrement retirés. Zéro dépendance serveur central.
- [x] **TorTransport** — Protocole binaire de trames (magic `0xF1 0xA1`), 13 types de trames (P2P + commandes Mailbox), écriture/lecture avec timeout
- [x] **P2PServer** — Listener de trames entrantes, dispatch `TYPE_MESSAGE`, `TYPE_CONTACT_REQ`, `TYPE_KEY_BUNDLE`, `TYPE_CONTACT_REQ_RESPONSE`, etc.
- [x] **OutboxManager** — Boucle de retry avec backoff exponentiel (max 50 tentatives, plafond 30 min), `DeliveryResult` (DIRECT / MAILBOX / QUEUED)
- [x] **UnifiedPush** — Notifications sans Firebase

### 🆔 Identité « 1 Seed → Tout »
- [x] **Seed Ed25519 unique** — Dérive : Account ID, adresse .onion, X25519, ML-KEM-1024, ML-DSA-44, fingerprint émoji
- [x] **ML-DSA-44** — Signature post-quantique hybride au handshake de chaque session (Ed25519 + ML-DSA-44 simultanés)
- [x] **AccountID** — `SHA3-256(pubkey Ed25519)` → Base58 (ex : `Fa3x...9Z`)
- [x] **Adresse .onion déterministe** — Dérivée du seed, stable entre réinstallations
- [x] **SeedVerificationFragment** — Confirmation de 3 mots après la sauvegarde du seed

### 🌐 Tor V4
- [x] **Guardian Project Tor** — `libtor.so` pour Tor v3 Hidden Services réels
- [x] **Multi-circuit** — Circuits actifs affichés en temps réel dans l'interface
- [x] **`killOrphanedTor()`** — Lit le cookie d'auth AVANT suppression (`AUTHENTICATE <hex>`), empêche les démons orphelins
- [x] **Anti-orphan messages** — `getPendingMessages()` inclut les messages bloqués en `STATUS_SENDING`

### 📬 Système Mailbox (4 modes)
- [x] **Direct P2P** — Communication directe, pas de relais
- [x] **Personal** — `.onion` personnel comme boîte aux lettres asynchrone
- [x] **Private Node** — Nœud privé avec liste blanche membres
- [x] **Public Node** — Nœud public ouvert
- [x] **MailboxServer** — Stockage opaque de blobs chiffrés, jamais de déchiffrement serveur, contrôle OWNER, TTL 7 jours
- [x] **MailboxClientManager** — Polling 60s, `_fetching: StateFlow<Boolean>` pour bloquer l'UI pendant le fetch
- [x] **Propagation `mailboxOnion`** — `senderMailboxOnion` inclus dans tous les messages ; P2PServer met à jour `participantMailboxOnion` à la réception
- [x] **Stats cumulatives** — `totalDeposited`, `totalFetched`, `totalDataProcessed` persistés en SharedPreferences (blobs supprimés après livraison)
- [x] **Dashboard auto-refresh** — Toutes les 30s

### 📦 Pipeline de livraison avec statuts
- [x] **`MessageLocal.deliveryStatus`** — `SENT(0)` / `MAILBOX(1)` / `FAILED(2)` / `PENDING(3)`
- [x] **`OutboxMessage.messageLocalId`** — Liaison retour vers `MessageLocal` pour mise à jour du statut
- [x] **Badges de livraison** — ✓ Envoyé · 📬 Mailbox · ⏳ En attente · ❌ Échec
- [x] **Bouton Renvoyer** — Messages `FAILED` → `OutboxDao.resetRetryForMessage()` relance la tentative
- [x] **Bannière fetch** — `fetchBanner` + `ProgressBar` dans le chat, input désactivé pendant le fetch Mailbox

### 🎨 Refresh UI 2026
- [x] **Settings refactorisé** — `SettingsAdapter` + `SettingsViewModel`, recherche + filtres catégorie (Apparence, Notifications, Confidentialité, Sécurité, Réseau, À propos)
- [x] **ThemeSelectorBottomSheet** — Sélecteur visuel 5 thèmes avec prévisualisation en temps réel
- [x] **DurationSelectorBottomSheet** — Sélecteur de durée messages éphémères
- [x] **Écran conversations** — Eyebrow « MESSAGERIE CHIFFRÉE » (monospace 9sp) + titre 24sp, bande accent gauche 3dp sur chaque item
- [x] **Ajouter un contact** — Section hero « CONNEXION SÉCURISÉE », diviseur OR visuel, `ConstraintLayout` → `LinearLayout`
- [x] **Profil** — Avatar 108dp, eyebrow « GHOST IDENTITY », caption `Ed25519 · ML-DSA-44 · ML-KEM-1024` en monospace
- [x] **Profil contact** — Eyebrow « NODE INFO », badge E2E pill avec fond `bg_key_box`

### 🔧 Corrections critiques pipeline P2P
- [x] **Fix messages orphelins** — `getPendingMessages()` inclut `STATUS_SENDING` bloqués (récupération après crash/reboot)
- [x] **Fix `resolveRecipientEd25519`** — Fallback sur `publicKey` si `signingPublicKey` absent
- [x] **Fix `sendContactRequest`** — Retourne `Boolean`, logs complets
- [x] **Fix `killOrphanedTor`** — Lecture du cookie auth AVANT suppression du fichier

### 🗄️ Base de données
- [x] **Room v24** — `deliveryStatus` sur `MessageLocal`, `messageLocalId` + `fallbackOnion` sur `OutboxMessage`, migrations v18→v24
- [x] **MailboxDatabase v1** — Entités `MailboxBlob`, `MailboxMember`, `MailboxInvite` (mode MAILBOX uniquement)
- [x] **Version 4.0** — `versionCode 8`, `versionName "4.0"`

</details>

---

<details open>
<summary><h2>✅ V4.0.1 — Sécurité Keystore, Corrections SQLCipher & Fiabilité transport</h2></summary>


> Remplacement de `security-crypto` par `FialkaSecurePrefs` (Keystore direct), correction crash SQLCipher 4.14.1, correction NPE `DurationSelectorBottomSheet`, fiabilité complète du transport (5 corrections).

### 🔐 Sécurité — FialkaSecurePrefs (Android Keystore direct)
- [x] **Suppression de `security-crypto`** — `androidx.security:security-crypto:1.1.0-alpha06` et `MasterKey.Builder` / `EncryptedSharedPreferences` entièrement retirés
- [x] **`FialkaSecurePrefs`** — Nouvelle implémentation `object` utilisant l’Android Keystore directement : AES-256-GCM, alias clé `fialka_ks_{name}`, fichier préférences `{name}_v2`
- [x] **StrongBox avec fallback TEE** — Tentative silencieuse sur StrongBox, retrait automatique vers TEE standard en cas d’absence
- [x] **Migration des 6 fichiers** — `CryptoManager`, `FialkaDatabase`, `MailboxDatabase`, `AppMode`, `AppLockManager`, `MailboxClientManager` migrés vers `FialkaSecurePrefs.open()`
- [x] **AGP 8.9.1 + compileSdk 36 + Gradle 8.13** — Mise à niveau de la chaîne de build ; zéro avertissement en release

### 🐛 Correction — Crash SQLCipher à l’initialisation
- [x] **`System.loadLibrary("sqlcipher")`** — `sqlcipher-android:4.14.1` supprime l’initialiseur statique ; `FialkaApplication.onCreate()` charge la bibliothèque native en premier
- [x] **Appels défensifs** — `FialkaDatabase.getInstance()` et `MailboxDatabase.buildDatabase()` appellent également `loadLibrary` en garde défensive

### 🐛 Correction — NPE DurationSelectorBottomSheet
- [x] **Paramètre `context` supprimé** — `DurationSelectorBottomSheet` n’accepte plus de `Context` externe ; `BottomSheetDialogFragment` dispose de son propre contexte
- [x] **`show()` simplifié** — Suppression de `fragmentManager.findFragmentById(R.id.nav_host_fragment)?.requireContext()!!` qui retournait `null` depuis `childFragmentManager`

### ⚡ Fiabilité du transport
- [x] **Réduction du délai de retry** — `RETRY_INTERVAL_MS` 60 s → 15 s ; plafond `MAX_RETRY_DELAY_MS` 30 min → 3 min (backoff : 15 s / 30 s / 1 min / 2 min / 3 min)
- [x] **Statut `DELIVERY_FAILED` sur messages épuisés** — Les messages atteignant 50 tentatives reçoivent le statut `DELIVERY_FAILED` AVANT suppression de la file (corrige le sablier ⏳ permanent)
- [x] **Fallback Mailbox dans `processOutboxForContact()`** — Si le dépôt P2P échoue, dépôt automatique via Mailbox avec mise à jour vers `DELIVERY_MAILBOX`
- [x] **Sweep Mailbox dans `broadcastPresence()`** — Après la boucle des contacts hors-ligne, `processOutbox()` est appelé pour traiter les messages en attente
- [x] **Fetch adaptatif `MailboxClientManager`** — Base 10 s, 5 s si messages reçus (+`OutboxManager.flushNow()`), backoff jusqu’à 60 s sur erreur

### 📦 Dépendances mises à jour
- [x] **BouncyCastle** 1.80 → **1.83** (ML-KEM-1024, Ed25519, ML-DSA-44)
- [x] **SQLCipher** 4.5.4 → **4.14.1**
- [x] **Room** 2.7.1 → **2.8.4**
- [x] **Coroutines** 1.9.0 → **1.10.2**
- [x] **Navigation** 2.8.9 → **2.9.7** | **Lifecycle** 2.8.7 → **2.10.0**

</details>

---

<details open>
<summary><h2>✅ V4.0.2 — Pont JNI Rust Fialka-Core</h2></summary>


> Migration de la cryptographie native vers Rust — toute la crypto est désormais exécutée dans la bibliothèque Rust ` fialka-core `, exposée via un pont JNI. Suppression complète de BouncyCastle.

### 🦀 Pont JNI Rust (Fialka-Core)
- [x] **30 fonctions JNI** implémentées dans `Fialka-Core/src/ffi/mod.rs` via the `jni = 0.21` crate
- [x] **`FialkaNative.kt`** — Kotlin bridge object, `System.loadLibrary("fialka_core")`, 30 `external fun`
- [x] **`libfialka_core.so`** — compilée avec `cargo-ndk` for `arm64-v8a` (906 KB) and `x86_64` (984 KB)
- [x] **git submodule** — `Fialka-Core/` intégré comme submodule dans Fialka-Android

### 🔐 Migration cryptographique
- [x] **`CryptoManager.kt`** — 100% migré : tous les appels BouncyCastle remplacés par `FialkaNative`
  - `identityDerive` → 8704-octets bundle (Ed25519, X25519, ML-KEM-1024, ML-DSA-44)
  - `encryptAes` / `decryptAes` → FialkaNative.encryptAes / decryptAes
  - `encryptChaCha` / `decryptChaCha` → FialkaNative (nonce 12 octets, tag 16 octets)
  - `encryptFile` / `decryptFile` → FialkaNative (format : key[32] || iv[12] || CT)
  - `hmacSha256` → FialkaNative.hmacSha256
  - `hkdfZeroSalt` / dérivation de clés → FialkaNative.hkdfZeroSalt
  - `ed25519Sign` / `ed25519Verify` → FialkaNative
  - `x25519Dh` → FialkaNative (suppression préfixes ASN.1 JCA : X509 12 octets, PKCS8 16 octets)
  - `mlkemEncaps` / `mlkemDecaps` → FialkaNative
  - `mldsaSign` / `mldsaVerify` → FialkaNative
  - `deriveRootKeyPqxdh` → FialkaNative
  - `computeOnion` / `ed25519ToX25519Raw` → FialkaNative
- [x] **`TorTransport.kt`** — `signEd25519` / `verifyEd25519` migrés vers FialkaNative
- [x] **`build.gradle.kts`** — `org.bouncycastle:bcprov-jdk18on:1.83` dépendance supprimée
- [x] **Tests Robolectric + JVM** supprimés (incompatibles avec JNI) ; tests migrés vers `androidTest`

### 🧩 Cross-platform
- [x] Même bibliothèque Rust utilisée sur Android (JNI) et toute autre cible (Windows/Linux/iOS via FFI)
- [x] **Version V4.0.2** — `versionCode 10`

</details>

---

<details open>
<summary><h2>✅ V4.1.0-alpha — Auth Ed25519, Tests Unitaires & Sécurité Migration</h2></summary>

> `versionCode 11` · `versionName "4.1.0-alpha"` · Correction sécurité + couverture tests + protection UX

### 🔐 Sécurité — Vérification signature Ed25519 sur les demandes de contact
- [x] **`P2PServer.handleContactRequest()`** — Vérifie maintenant la signature Ed25519 sur les demandes de contact entrantes. Auparavant, `senderSigningPublicKey` était reçu mais jamais vérifié — n'importe qui pouvait usurper une identité.
- [x] **Données signées canoniques** — `senderPubKey(UTF-8) || 0x00 || conversationId(UTF-8) || createdAt(big-endian 8 octets)` — séparé par domaine, déterministe
- [x] **`sendContactRequest()`** — Signe maintenant le payload avec `FialkaNative.ed25519Sign()` et inclut le champ `requestSignature`
- [x] **Rétrocompatible** — Les anciens clients sans `requestSignature` sont encore acceptés (fenêtre de migration progressive)

### 🧪 Tests Unitaires — Couverture Crypto & Ratchet
- [x] **Robolectric 4.13** ajouté comme dépendance de test
- [x] **`CryptoManagerPureTest`** — 20 tests : déterminisme/commutativité/unicité de `deriveConversationId`, encodage timestamp `buildSignedData`, pseudonymat cross-conversation `hashSenderUid`
- [x] **`RatchetSimulationTest`** — 16 tests : ratchet bidirectionnel, unicité 500 clés, intervalle SPQR=10, correction `pqRatchetStep`, symétrie initiator/responder
- [x] **`DoubleRatchetTest`** — 2 tests JNI marqués `@Ignore` (nécessitent `libfialka_core.so` — à migrer en tests instrumentés)
- [x] **45 tests unitaires au total, 0 échec**

### 🛡️ Protection UX — Avertissement migration destructive
- [x] **`FialkaDatabase.needsDestructiveMigration()`** — Détecte quand une mise à jour de schéma Room déclencherait `fallbackToDestructiveMigration` (DROP ALL TABLES)
- [x] **`MainActivity`** — Affiche un `AlertDialog` bloquant avant tout accès DB lors d'une mise à jour : l'utilisateur doit confirmer explicitement la perte de données ou quitter
- [x] **`FialkaDatabase.recordCurrentVersion()`** — Persiste la version du schéma dans des `SharedPreferences` simples après ouverture confirmée

### 🛠 Infrastructure Dev
- [x] **`version.properties`** — Source unique de vérité pour `VERSION_CODE` + `VERSION_NAME` ; `build.gradle.kts` le lit automatiquement — modifier uniquement `version.properties` pour bumper la version
- [x] **Version V4.1.0-alpha** — `versionCode 11`

</details>

---

<details open>
<summary><h2>✅ V4.2.0-alpha — Wallet Monero XMR, Paramètres Tor & Seed Backup</h2></summary>

> `versionCode 12` · `versionName "4.2.0-alpha"` · Wallet Monero non-custodial local, paiements XMR in-chat, améliorations Tor

### 💰 Wallet Monero (XMR) — Non-custodial
- [x] **Wallet XMR local** — clés privées générées et stockées exclusivement sur l'appareil (SQLCipher)
- [x] **Dérivé depuis le seed** — 1 seed Ed25519 → wallet XMR (déterministe)
- [x] **Paiements XMR in-chat** — envoi de montants XMR directement depuis une conversation
- [x] **Adresse de donation** — sous-adresse XMR pour soutenir le développement
- [x] **Zéro custody** — les développeurs n'ont aucune visibilité sur les fonds
- [x] **Conformité légale** — wallet non-custodial hors champ PSAN (AMF) et MiCA (Art. 2, 2023/1114)

### 🧅 Améliorations Tor & Réseau
- [x] **Paramètres Tor avancés** — bridges, pays exclus, timeouts configurables
- [x] **Indicateur de bande passante Tor** — affichage du débit en temps réel
- [x] **Version V4.2.0-alpha** — `versionCode 12`

</details>

---

<details open>
<summary><h2>✅ V4.3.0-alpha — Backup .fialka E2E, Réseau Monero Stagenet/Mainnet, Gestion du stockage & Mise à jour légale</h2></summary>

> `versionCode 13` · `versionName "4.3.0-alpha"` · Backup chiffré complet, sélection réseau Monero (Stagenet/Mainnet), écran de gestion du stockage, CATEGORY_DATA dans les paramètres, mise à jour légale V5

### 🌐 Réseau Monero — Sélection Stagenet/Mainnet
- [x] **`WalletPreferences`** — constantes `MAINNET = 0` / `STAGENET = 2`, `getNetworkType()`, `setNetworkType()`, `getNetworkLabel()`, `isStagenet()`
- [x] **Defaults intelligents par réseau** — node `127.0.0.1:38081` (stagenet) vs `127.0.0.1:18081` (mainnet), restore height adaptée
- [x] **`WalletRepository`** — suppression du `NETWORK_TYPE = 2` codé en dur, tout passe par `WalletPreferences.getNetworkType(context)` (4 usages)
- [x] **Dialog "Supprimer pour changer"** — si un wallet existe, changer de réseau déclenche une confirmation avec suppression du wallet existant (pas de griser les boutons)
- [x] **Badges réseau** — badge rouge STAGENET / vert MAINNET dans `WalletHomeFragment`, `WalletSettingsFragment`, `WalletSeedBackupFragment`
- [x] **Banner STAGENET** — bannière pleine largeur dans les paramètres wallet pour avertir des fonds fictifs
- [x] **Fix critique : `setWalletCreated(true)`** — maintenant appelé après création (seed backup) ET restauration (import seed), lock déclenchable correctement

### 🐛 Corrections
- [x] **Fix label "Données" dans les Paramètres** — `SettingsAdapter.getCategoryTitle()` manquait le case `CATEGORY_DATA`, affichait la clé brute `"data"` au lieu de `"💾 Données"`
- [x] **Fix `validateAddress(context, address)`** — signature mise à jour dans `ChatViewModel` après refactoring `WalletRepository`

### 💾 Backup & Restauration — Format .fialka
- [x] **Format `.fialka`** — Magic bytes `0xF1A15A5E` + PBKDF2-HMAC-SHA256 (600K itérations + salt 16 bytes) + AES-256-GCM (nonce 12 bytes + tag 16 bytes)
- [x] **Contenu du backup** — identité (clés Ed25519), contacts, wallet XMR (optionnel) — **PAS les messages** (confidentialité par conception)
- [x] **Export chiffré** — phrase de passe connue de l'utilisateur uniquement, les développeurs n'ont aucun accès
- [x] **Import avec validation** — vérification magic bytes + déchiffrement + restauration complète de l'identité
- [x] **Accès via Paramètres → Données** — export et import accessibles depuis la nouvelle catégorie CATEGORY_DATA

### 🗄️ Gestion du stockage — StorageFragment
- [x] **`StorageFragment.kt`** — écran de gestion du stockage en temps réel
- [x] **Stats en temps réel** — nombre de messages, nombre de fichiers, taille DB (SQLCipher WAL+SHM), taille cache
- [x] **Actions de nettoyage** — vider le cache, supprimer les messages fichiers (avec confirmation count), purger les messages expirés
- [x] **Zone sensible** — supprimer tous les messages (avec double confirmation)
- [x] **`refreshStats()`** appelé après chaque action
- [x] **Nouvelles requêtes DAO** — `getTotalMessageCount`, `getFileMessageCount`, `deleteFileMessages`, `deleteAllMessages`, `getTotalConversationCount`, `deleteAllConversations`

### ⚙️ Paramètres — CATEGORY_DATA
- [x] **Nouvelle catégorie "Données"** (`chipData`) dans les Paramètres — regroupe stockage, export backup, import backup
- [x] **Stockage** migré de type `ACTION` → `NAVIGATE` vers `storageFragment`
- [x] **Navigation** — `action_settings_to_storage` + node `storageFragment` dans `nav_graph.xml`

### 📚 Mise à jour légale — TermsManager V5
- [x] **`CURRENT_TERMS_VERSION = 5`** — force le re-consentement pour tous les utilisateurs existants
- [x] **TERMS.md V5** — Section 6 complète : légalité wallet XMR (France/AMF/PSAN, UE/MiCA 2023/1114, US/FinCEN/OFAC/IRS, autres juri)
- [x] **Section 13** — notice légale backup .fialka
- [x] **PRIVACY.md** — Section 4.1 wallet XMR (données locales uniquement), Section 5.1 backup
- [x] **strings.xml** — `terms_section_wallet_title/body`, `terms_section_backup_title/body` ajoutés
- [x] **Version V4.3.0-alpha** — `versionCode 13`

### 🎭 App Disguise — Écran de couverture calculatrice
- [x] **`AppDisguiseFragment.kt`** — Paramètre de sélection du déguisement (Calculatrice, Notes, Météo, Horloge)
- [x] **`AppDisguiseManager.kt`** — Gestion des activity-alias, enable/disable dynamique via `PackageManager`
- [x] **`CoverCalculatorActivity.kt`** — Fausse calculatrice fonctionnelle (couverture réaliste)
- [x] **`CoverSecretSetupBottomSheet.kt`** — Configuration du code secret d'accès au vrai chat
- [x] **Icônes de déguisement** — `ic_disguise_calculator`, `ic_disguise_clock`, `ic_disguise_notes`, `ic_disguise_weather`

</details>

---

<details open>
<summary><h2>✅ V4.3.5-alpha — Internationalisation complète (i18n)</h2></summary>


> `versionCode 14` · `versionName "4.3.5-alpha"` · Externalisation complète de toutes les chaînes codées en dur, traduction anglaise intégrale (~1300+ strings), sélecteur de langue in-app, audit XML exhaustif, dépendances bumped.

### 🌍 Internationalisation (i18n)
- [x] **`values-en/strings.xml`** — ~1300+ traductions anglaises, parité complète avec le FR (locale par défaut)
- [x] **`LocaleHelper.kt`** — Utilitaire de changement de locale au runtime, persistance dans SharedPreferences
- [x] **`LanguageSelectionFragment.kt`** — Sélecteur de langue in-app (FR / EN) avec redémarrage de l'Activity
- [x] **`locale_config.xml`** — Déclaration des locales supportées dans AndroidManifest (`fr`, `en`)
- [x] **`ic_language.xml`** — Nouvelle icône vectorielle langue dans le menu des paramètres

### 🔧 Externalisation des chaînes hardcodées
- [x] **50+ fichiers Kotlin** — Audit complet : toutes les chaînes hardcodées remplacées par `getString(R.string.*)`
- [x] **30+ layouts XML** — Tous les `android:text`, `android:hint`, `android:contentDescription` hardcodés remplacés par `@string/*`
- [x] **4 menus XML** — Titres de menus (`menu_search`, `menu_my_profile`, `menu_settings`, `menu_reply`, `menu_wallet_seed_action`, `menu_wallet_settings_action`) externalisés
- [x] **`ConversationsFragment.kt`** — Menu FAB ("💬  Nouvelle conversation", "👥  Nouveau groupe") → `getString(R.string.fab_new_conversation)` / `getString(R.string.fab_new_group)`
- [x] **`ConversationsAdapter.kt`** — Statut "En attente d'acceptation" et placeholder → `getString(R.string.pending_acceptance_short)` / `getString(R.string.conversation_new_placeholder)`
- [x] **`fragment_create_group.xml` + `nav_graph.xml`** — Label "Nouveau groupe" → `@string/create_group_title`
- [x] **35 nouvelles clés i18n** — `menu_*`, `cd_*` (content descriptions), `hint_*`, `fab_*` (`fab_new_conversation`, `fab_new_group`), `pending_acceptance_short`, `conversation_new_placeholder`, `create_group_title`, `xmr_pay_btn`, `settings_autolock_after_3s`

### 📦 Dépendances
- [x] **KSP** 2.3.6 → **2.3.7**
- [x] **swiperefreshlayout** 1.1.0 → **1.2.0**
- [x] **actions/upload-artifact** v4 → **v7** (CI GitHub Actions)

</details>

---

<div align="center">

[← Retour au README](../../README.md)

</div>