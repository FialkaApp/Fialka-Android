<div align="right">
  🇫🇷 Français | <a href="../en/CHANGELOG.md">🇬🇧 English</a>
</div>

<div align="center">

# 🗺 Changelog & Roadmap

<img src="https://img.shields.io/badge/Current-V3.5-7B2D8E?style=for-the-badge" />
<img src="https://img.shields.io/badge/Next-V3.6-9C4DCC?style=for-the-badge" />

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
- [x] **Démarrage conditionnel** — `SecureChatApplication.onCreate()` démarre Tor si activé
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
- [x] **QR deep link v2** — Format `securechat://contact?key=<X25519>&kem=<ML-KEM-1024-pubKey>&name=<displayName>` — clé ML-KEM encodée dans le QR
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

<details open>
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

<details open>
<summary><h2>✅ V3.5 — SPQR, ChaCha20-Poly1305 & Threat Model</h2></summary>


> Triple Ratchet post-quantique (SPQR), chiffrement alternatif ChaCha20-Poly1305, modèle de menace documenté.

### 🔐 SPQR — Ré-encapsulation PQ périodique (Triple Ratchet)
- [x] **PQ Ratchet Step** — Nouvelle fonction `DoubleRatchet.pqRatchetStep()` : mixe un secret ML-KEM frais dans le rootKey via HKDF (info: `SecureChat-SPQR-pq-ratchet`)
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
- [x] **Limites documentées** — Section explicite « Ce que SecureChat ne protège PAS »
- [x] **Principes de design** — 7 principes : defense in depth, hybrid PQ, forward secrecy, post-compromise healing, zero trust transport, minimal metadata, fail-safe defaults

### 🗄️ Base de données
- [x] **Room v18** — Migration v17→v18 : ajout colonne `pqRatchetCounter` sur RatchetState
- [x] **Version 3.5** — `versionCode 7`, `versionName "3.5"`

</details>

---

<details open>
<summary><h2>🔜 V3.6 — Planned</h2></summary>


> Camouflage avancé, plausible deniability, messages vocaux E2E, sealed sender, améliorations messagerie.

### 🎭 Camouflage de l’app (App Disguise)
- [ ] **Changement d’icône** — L’utilisateur choisit une icône de camouflage parmi des présets : Calculatrice, Notes, Actualités, Météo, Horloge, etc.
- [ ] **Changement du nom affiché** — Le nom de l’app dans le launcher change pour correspondre à l’icône choisie (« Calculatrice », « Notes », « Actualités », etc.)
- [ ] **Thèmes d’icône** — Chaque déguisement a son icône + nom cohérent (style professionnel)
- [ ] **Activity-alias** — Implémentation via `<activity-alias>` dans le manifest (enable/disable dynamique via `PackageManager`)
- [ ] **Confirmation + redémarrage** — Dialog de confirmation avec prévisualisation → « Redémarrer maintenant » → kill + relaunch
- [ ] **Faux écran de couverture** — L’app déguisée ouvre une vraie fausse app fonctionnelle (calculatrice, notes, etc.). Le vrai chat est accessible via un geste secret (long press caché ou code spécial)
- [ ] **Persistance** — Choix sauvegardé dans SharedPreferences, restauré au démarrage

### 🔐 Plausible Deniability & Protection
- [ ] **Dual PIN** — PIN normal ouvre le chat ; PIN de contrainte ouvre un profil vide ou déclenche un wipe silencieux (plausible deniability, niveau journaliste/activiste)
- [ ] **Panic button** — Secouer le téléphone (shake) → suppression instantanée de toutes les conversations + clés + déconnexion (wipe complet)
- [ ] **Screenshot protection** — ~~`FLAG_SECURE` sur toutes les fenêtres~~ ✔️ **Fait dans l'audit de sécurité V3.4.1**
- [ ] **Keyboard incognito** — `flagNoPersonalizedLearning` sur tous les champs de saisie — le clavier ne mémorise/apprend rien

### 🔐 Crypto avancée
- [ ] **Sealed sender** — L’identité de l’expéditeur est cachée côté Firebase — le destinataire déduit le sender uniquement après déchiffrement

### 💬 Messagerie avancée
- [ ] **Messages vocaux E2E** — Enregistrement audio, chiffrement AES-256-GCM, envoi via le ratchet, lecteur inline dans le chat
- [ ] **Reply / Quote** — Répondre à un message spécifique avec citation (bulle citée + nouveau message)
- [ ] **Groupes** — Conversations à 3+ participants (Sender Keys)
- [ ] **Suppression pour tous** — Supprimer un message côté local + Firebase
- [ ] **Typing indicators** — « En train d’écrire... » (chiffré E2E, opt-in)

### 🛡️ Infrastructure
- [ ] **Relay privé** — Serveur relay dédié pour réduire la dépendance Firebase

</details>

---

<div align="center">

[← Retour au README](../../README.md)

</div>
