# Privacy Policy — SecureChat

*Last updated: March 20, 2026*

---

## 1. Introduction

SecureChat is a privacy-first, end-to-end encrypted messaging application. This Privacy Policy describes what data is (and is not) collected, how it is handled, and your rights regarding your personal data.

**TL;DR: SecureChat collects virtually nothing. Messages are end-to-end encrypted. The developer cannot read your messages. No analytics, no tracking, no ads.**

## 2. Data Controller

SecureChat is developed by an independent developer based in France under the pseudonym **DevBot667**. For privacy inquiries, contact via [GitHub](https://github.com/DevBot667/SecureChat).

## 3. Data We Do NOT Collect

SecureChat is designed to **minimize data collection by design**:

- **Message content** — All messages are end-to-end encrypted using PQXDH (X25519 + ML-KEM-1024) and Double Ratchet. The developer and relay infrastructure **cannot read, access, or decrypt** any message content.
- **Private keys** — Your cryptographic keys are generated and stored **exclusively on your device**. They never leave your device.
- **Contacts or address book** — SecureChat does not access or upload your phone contacts.
- **Location data** — Not collected.
- **Usage analytics** — No analytics SDK, no telemetry, no tracking.
- **Advertising identifiers** — No ads, no ad SDKs.
- **Browsing or search history** — Not applicable.

## 4. Data That May Be Processed

### 4.1. Firebase Authentication (Anonymous)

SecureChat uses Firebase Anonymous Authentication to generate a unique, anonymous user ID. This ID:
- Contains **no personal information** (no email, no phone number, no name)
- Is used solely to route encrypted messages
- Is stored by Google Firebase under their [Privacy Policy](https://firebase.google.com/support/privacy)

### 4.2. Firebase Realtime Database (Relay)

Encrypted message payloads are temporarily stored on Firebase Realtime Database for message delivery. These payloads are:
- **Fully encrypted** — only the intended recipient can decrypt them
- **Automatically deleted** after delivery
- Contain no plaintext metadata about the sender or recipient identity

### 4.3. Firebase Cloud Messaging (Push Notifications)

If push notifications are enabled, Firebase Cloud Messaging (FCM) is used to notify the recipient device. FCM tokens:
- Are device-specific identifiers managed by Google
- Do not contain message content
- Can be disabled by the user in app settings

### 4.4. Network Metadata (Tor)

All network traffic is routed through **Tor** (The Onion Router), which means:
- Your **real IP address is not visible** to the relay server
- The relay server sees only the Tor exit node IP
- No IP addresses are logged by the developer

## 5. Data Stored on Your Device

The following data is stored **locally on your device only** and is **never transmitted** to any server:

- Your cryptographic keys (identity, signed pre-key, one-time pre-keys, ratchet state)
- Your recovery phrase (BIP-39 / 24 words)
- Your message history (encrypted with SQLCipher)
- Your contact list
- App settings and preferences

This local data is protected by:
- **SQLCipher** encryption for the local database
- **Android Keystore** (StrongBox/TEE) for key material
- Optional **app lock** (PIN/biometric)

## 6. Third-Party Services

| Service | Purpose | Data shared | Privacy policy |
|---|---|---|---|
| Firebase Auth | Anonymous user ID | Anonymous UID only | [Link](https://firebase.google.com/support/privacy) |
| Firebase RTDB | Encrypted message relay | E2E encrypted blobs | [Link](https://firebase.google.com/support/privacy) |
| Firebase Cloud Messaging | Push notifications | FCM token | [Link](https://firebase.google.com/support/privacy) |
| Tor Network | IP anonymization | Network traffic (encrypted) | [Link](https://www.torproject.org/about/privacy/) |

**No data is shared with advertisers, data brokers, or any other third party.**

## 7. Data Retention

- **Messages on relay**: Deleted immediately after delivery (or after 30 days if undelivered)
- **Local messages**: Stored until you delete them, or until ephemeral message timers expire
- **Firebase anonymous accounts**: May be cleaned up by Firebase after extended inactivity
- **No server-side backups** of message content exist

## 8. Your Rights (GDPR — EU Users)

Under the General Data Protection Regulation (GDPR), you have the right to:

- **Access** — Request what data is held about you (answer: virtually none)
- **Rectification** — Correct inaccurate data
- **Erasure** ("Right to be forgotten") — Delete your account and all associated data
- **Data portability** — Export your data
- **Objection** — Object to data processing
- **Restriction** — Restrict how your data is processed

To exercise these rights, contact the developer via [GitHub](https://github.com/DevBot667/SecureChat).

**In practice**, since SecureChat collects almost no personal data and uses anonymous authentication, most of these rights are satisfied by design.

To delete all your data:
1. Delete the app from your device (removes all local data)
2. Your anonymous Firebase UID will be automatically cleaned up

## 9. Children's Privacy

SecureChat is **not intended for use by anyone under the age of 16** (or the minimum digital age of consent in your jurisdiction). We do not knowingly collect data from children under 16. See the [Terms of Service](TERMS.md) for details.

## 10. Security

SecureChat implements multiple layers of security:
- **PQXDH** (X25519 + ML-KEM-1024) for post-quantum key exchange
- **Double Ratchet** for forward secrecy and self-healing
- **AES-256-GCM** for message encryption
- **Ed25519** for message authentication
- **SQLCipher** for local database encryption
- **Tor** for network anonymization
- **Android Keystore** (StrongBox/TEE) for key protection

For more details, see [SECURITY.md](SECURITY.md) and the [Cryptography Documentation](docs/en/CRYPTO.md).

## 11. Changes to This Policy

This Privacy Policy may be updated from time to time. Changes will be published in this file and reflected in the "Last updated" date above.

## 12. Contact

For privacy-related questions or concerns:
- GitHub: [github.com/DevBot667/SecureChat](https://github.com/DevBot667/SecureChat)

---

© 2024-2026 DevBot667. All rights reserved.
