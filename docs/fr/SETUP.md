<div align="right">
  🇫🇷 Français | <a href="../en/SETUP.md">🇬🇧 English</a>
</div>

<div align="center">

# 🛠 Installation & Configuration

<img src="https://img.shields.io/badge/IDE-Android_Studio-7B2D8E?style=for-the-badge&logo=android-studio" />
<img src="https://img.shields.io/badge/JDK-17-9C4DCC?style=for-the-badge" />
<img src="https://img.shields.io/badge/Firebase-Blaze_(gratuit)-6A1B9A?style=for-the-badge&logo=firebase" />

</div>

---

## Prérequis

- **Android Studio** Hedgehog (2023.1.1) ou plus récent
- **JDK 17**
- Un projet **Firebase** (plan Blaze — gratuit jusqu'à 2M invocations/mois)
- **Node.js 18+** (pour déployer la Cloud Function)

---

## 1. Cloner le repo

```bash
git clone https://github.com/DevBot667/SecureChat.git
cd SecureChat
```

---

## 2. Configuration Firebase

### Étape 1 — Créer le projet

1. Aller sur [Firebase Console](https://console.firebase.google.com/)
2. **Créer un projet** (désactiver Google Analytics si souhaité)
3. **Ajouter une app Android** :
   - Package : `com.securechat`
   - Télécharger `google-services.json`

### Étape 2 — Configurer les services

1. **Copier** `google-services.json` dans `app/`
2. Firebase Console → **Authentication** → Méthode de connexion → **Anonyme** → Activer
3. Firebase Console → **Realtime Database** → Créer (région la plus proche)
4. Onglet **Règles** → coller le contenu de [`firebase-rules.json`](../firebase-rules.json)

### Étape 3 — Déployer la Cloud Function (push notifications)

```bash
# Installer Firebase CLI
npm install -g firebase-tools

# Se connecter
firebase login

# Depuis la racine du projet
cd functions
npm install
cd ..
firebase deploy --only functions
```

La Cloud Function se déclenche automatiquement à chaque nouveau message et envoie un push aux destinataires qui ont activé les notifications.

> ⚠️ **`google-services.json` est dans le `.gitignore`** — il ne sera jamais poussé sur GitHub.
> Le fichier `app/google-services.json.template` montre la structure attendue.

---

## 3. Compiler

```bash
./gradlew assembleDebug
```

Ou ouvrir dans Android Studio → **Run** sur un émulateur ou device physique.

---

## Dépendances

| Dépendance | Version | Usage |
|------------|---------|-------|
| Kotlin | 2.1.0 | Langage |
| AndroidX Core / AppCompat / Material | Latest | UI Material Design |
| AndroidX Navigation | 2.8.9 | Navigation single-activity |
| AndroidX Lifecycle | 2.8.7 | ViewModels, LiveData, coroutines |
| Room + KSP | 2.7.1 | Base de données locale SQLite |
| SQLCipher | 4.5.4 | Chiffrement AES-256 de la base Room |
| Firebase BOM | 34.10.0 | Auth anonyme + Realtime Database + Cloud Messaging |
| firebase-functions (Node.js) | 7.0.0 | Cloud Function trigger (push notifications) |
| firebase-admin (Node.js) | 13.6.0 | Admin SDK pour RTDB + FCM côté serveur |
| AndroidX Security Crypto | 1.1.0-alpha06 | Stockage sécurisé |
| AndroidX Biometric | 1.1.0 | BiometricPrompt (empreinte, visage) |
| Kotlinx Coroutines | 1.9.0 | Async + Flow |
| ZXing Android Embedded | 4.3.0 | Génération et scan QR codes |

---

<div align="center">

[← Retour au README](../../README.md)

</div>
