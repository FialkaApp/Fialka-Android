# Privacy Policy — Fialka

*Last updated: April 3, 2026*

---

## ⚠️ Important Notice

**Fialka is a tool.** The developers do not operate any infrastructure, do not store any user data, and cannot access any messages. Users are solely responsible for their use of the software.

Fialka is **free, open-source software** (GPLv3) distributed as source code and pre-built APKs. The developers provide the code — nothing more.

---

## 1. Data Controller

Fialka is an open-source project maintained by independent contributors under the organization **FialkaApp** on GitHub.

- **No company, no legal entity** operates Fialka as a service
- **No central server** is operated by the developers — Fialka is fully peer-to-peer
- **No user data** is collected, processed, or stored by the developers
- Contact: [github.com/FialkaApp/Fialka-Android](https://github.com/FialkaApp/Fialka-Android)

## 2. Data We Do NOT Collect

Fialka is designed to **minimize data by design and by default**. There is no central server, no cloud service, and no analytics:

| Data type | Collected? | Details |
|-----------|------------|---------|
| Message content | ❌ Never | End-to-end encrypted (PQXDH + Double Ratchet + AES-256-GCM) |
| Private keys | ❌ Never | Generated and stored exclusively on your device (Android Keystore) |
| Phone number | ❌ Never | Not required — identity is a cryptographic key |
| Email address | ❌ Never | Not required |
| Contacts / address book | ❌ Never | Not accessed |
| Location data | ❌ Never | Not collected |
| Usage analytics | ❌ Never | No analytics SDK, no telemetry, no third-party services |
| Advertising identifiers | ❌ Never | No ads, no ad SDKs |
| IP addresses | ❌ Never | All traffic routed through Tor — your device is a Hidden Service |
| Biometrics | ❌ Never | Processed locally by Android, never leaves device |
| Metadata (who talks to whom) | ❌ Never | Peer-to-peer via Tor .onion — no central server sees traffic patterns |

**The developers have zero ability to read, intercept, decrypt, or even observe any message sent through Fialka.**

## 3. Network Architecture — No Central Server

Fialka does **not** rely on any cloud service, corporate infrastructure, or central relay:

| Component | How it works | Who sees what |
|-----------|-------------|---------------|
| **Identity** | Ed25519 key pair → Account ID (SHA3-256 → Base58) | No one — generated locally |
| **Transport** | Tor Hidden Services (.onion) — direct peer-to-peer | No one — Tor encrypts routing |
| **Offline delivery** | Fialka Mailbox (self-hosted or community nodes via Tor) | Mailbox sees encrypted blobs only — zero plaintext, zero metadata |
| **Push notifications** | UnifiedPush + ntfy.sh (optional, self-hostable) | Provider sees a wake-up signal — zero message content |
| **File transfer** | Peer-to-peer via Tor, per-file AES-256-GCM | No one — direct encrypted transfer |

### 3.1. Tor Hidden Services

- Your device runs a **Tor Hidden Service** (.onion address derived from your Ed25519 identity)
- Messages are exchanged **directly** between devices — no relay, no middleman
- Your real **IP address is never exposed** to anyone — not to contacts, not to any server
- No traffic metadata (who, when, how much) is visible to any third party

### 3.2. Fialka Mailbox (Offline Delivery)

When a recipient is offline, encrypted messages are temporarily stored on a **Mailbox node**:

| Mailbox mode | Description | Operated by |
|-------------|-------------|-------------|
| **Mode 0** — Direct P2P | No mailbox — direct .onion to .onion | No one |
| **Mode 1** — Personal Mailbox | Your own device (old phone, Raspberry Pi) | You |
| **Mode 2** — Private Node | A friend hosts for a trusted group (whitelist) | Your friend |
| **Mode 3** — Public Node | Community volunteers, auto-selected (default) | Volunteers |

- Mailbox nodes **only store encrypted blobs** — they cannot read, decrypt, or identify sender/recipient
- Messages are deleted after delivery (or after TTL expiration)
- The developers do **not** operate any Mailbox node

### 3.3. Push Notifications (Optional)

- Uses **UnifiedPush** protocol with **ntfy.sh** (open-source, self-hostable)
- Push payload contains **zero message content** — only a wake-up signal
- Can be completely disabled in app settings
- Can be self-hosted for maximum privacy

## 4. Data Stored Locally on Your Device

The following data is stored **exclusively on your device** and never transmitted to any server:

- Cryptographic identity (Ed25519 seed → derives all keys)
- Recovery phrase (BIP-39 / 24 words)
- Message history (encrypted with SQLCipher AES-256)
- Contact list
- Ratchet states (Double Ratchet + PQXDH + SPQR)
- App settings and preferences

This data is protected by:
- **SQLCipher** encryption (AES-256) for the local database
- **Android Keystore** (StrongBox/TEE) for key material
- **EncryptedSharedPreferences** for sensitive configuration
- Optional **app lock** (PIN + biometric)

## 5. Third-Party Software Components

| Component | Role | Data involved | Operated by developers? |
|-----------|------|---------------|------------------------|
| **Tor** (The Onion Router) | Anonymous transport layer | Encrypted network traffic | ❌ No — Tor Project |
| **UnifiedPush / ntfy.sh** | Optional push wake-up | Wake-up signal (zero content) | ❌ No — self-hostable |
| **Fialka-Core** (Rust) | Cryptographic core library | Local computation only | ❌ No — library |

**No Google services, no Firebase, no cloud APIs, no corporate infrastructure.**

## 6. Data Retention

- **Messages in transit**: Deleted from Mailbox after delivery (or after TTL expiration if undelivered)
- **Local messages**: Stored until user deletes them, or until ephemeral timers expire
- **No server-side backups** of message content exist anywhere
- **No account database** exists — your identity is your key, stored only on your device

## 7. Your Rights — EU/EEA Users (GDPR + French Law)

**GDPR (Regulation 2016/679)** — As an EU/EEA user, you have rights to access, rectification, erasure, portability, objection, and restriction of processing under Articles 15–22.

**French Law (Loi Informatique et Libertés — Loi n° 78-17 du 6 janvier 1978 modifiée)** — French users benefit from additional protections under the French Data Protection Act, aligned with and complementing the GDPR. The CNIL (Commission Nationale de l'Informatique et des Libertés) is the competent supervisory authority in France.

**In practice**, since:
- The developers collect **zero personal data**
- The developers operate **zero infrastructure**
- There is **no central server** and **no account database**
- All data is stored **locally on your device**
- Fialka applies **privacy by design and by default** (GDPR Art. 25 / RGPD Art. 25)

...these rights are satisfied **by architecture**. To delete all your data:
1. Delete the app (removes all local data instantly)

There is no remote account to delete, no server to contact, no data to request. The developers have no ability to respond to access or deletion requests because no data is held.

## 8. Children's Privacy

Fialka is **not intended for use by anyone under 16** (or the applicable minimum digital age of consent in your jurisdiction). See [TERMS.md](TERMS.md).

**COPPA (USA — Children's Online Privacy Protection Act):** Fialka is not directed to children under the age of 13 and does not knowingly collect personal information from children under 13. Since Fialka collects no personal data from any user whatsoever (P2P architecture, zero server-side storage), COPPA compliance is satisfied by design. If you are under 13, do not use this application.

## 9. International Data Transfers

The Fialka source code is hosted on GitHub (USA). All communications transit exclusively through the **Tor network** (global, decentralized). No user data is transferred to any corporate entity or jurisdiction.

## 10. Cyber Resilience Act (CRA) — EU Regulation 2024/2847

The EU Cyber Resilience Act enters into force progressively from 2026. Fialka is an **open-source, non-commercial software product**. Non-commercial open-source projects are broadly exempted under the CRA recitals. No commercial revenue is derived from Fialka. This policy will be updated if the CRA's final implementing acts affect this classification.

## 11. Changes to This Policy

Changes will be published in this file and reflected in the "Last updated" date above. Significant changes will be noted in the [Changelog](docs/en/CHANGELOG.md).

## 12. Contact

- GitHub: [github.com/FialkaApp/Fialka-Android](https://github.com/FialkaApp/Fialka-Android)
- Security issues: See [SECURITY.md](SECURITY.md)
- GDPR / CNIL inquiries: See GitHub organization profile

---

**Fialka is a tool. The developers do not operate any infrastructure, do not store any user data, and cannot access any messages. There is no central server — all communication is peer-to-peer via Tor. Users are solely responsible for their use of the software.**

© 2024-2026 FialkaApp Contributors. Licensed under [GPLv3](LICENSE).
