# Terms of Use — Fialka

*Last updated: April 3, 2026*

---

## ⚠️ Important Notice

**Fialka is a tool.** The developers do not operate any infrastructure, do not store any user data, and cannot access any messages. There is no central server — all communication is peer-to-peer via Tor. Users are solely responsible for their use of the software.

---

## 1. Acceptance

By downloading, installing, or using Fialka, you agree to these Terms of Use. If you do not agree, do not use the software.

## 2. Nature of the Software

Fialka is **free, open-source software** distributed under the [GNU General Public License v3.0](LICENSE) (GPLv3).

- Fialka is a **tool**, not a service
- The developers provide **source code and pre-built APKs** — nothing more
- The developers **do not operate any servers, relays, databases, or infrastructure**
- There is **no central server** — communication is fully peer-to-peer via Tor Hidden Services
- The developers **cannot access, read, decrypt, moderate, or delete** any message
- Your identity is your **cryptographic key** — there are no accounts, no registration, no database

## 3. Eligibility

You must be at least **16 years old** (or the applicable minimum digital age of consent in your jurisdiction) to use Fialka. By using the app, you confirm that you meet this requirement.

**USA — COPPA Notice:** Fialka is not directed to children under 13. Since Fialka collects no personal information from any user (P2P architecture, zero server-side storage), the Children's Online Privacy Protection Act (COPPA) requirements are satisfied by design. If you are under 13, do not use this application.

## 4. User Responsibilities

Since Fialka is a fully decentralized tool with no central authority, **you are solely responsible for**:

- **Your cryptographic identity**: Your Ed25519 seed is your identity. If you lose your 24-word recovery phrase, your identity is irrecoverable. No one can reset it for you.
- **Your Mailbox node** (if self-hosted): You deploy, configure, and secure your own Mailbox. The developers have no access to it.
- **Your messages**: The developers cannot read, moderate, delete, or intercept any message. There is no reporting system and no content moderation — because there is no central server.
- **Your compliance**: You are responsible for complying with all applicable laws when using Fialka, including export control regulations for cryptographic software.
- **Your data**: All data is stored locally on your device. The developers store nothing.
- **Your network security**: While Tor provides strong anonymity, you are responsible for your own operational security practices.

## 5. Prohibited Uses

You agree **not** to use Fialka:

- To violate any applicable law or regulation
- To transmit illegal content (as defined by applicable law)
- To harass, threaten, or harm others
- To distribute malware or malicious payloads
- To circumvent lawful court orders or legal obligations

**Note:** The developers have **no technical ability** to enforce these restrictions. Fialka is fully peer-to-peer with no central point of control. This clause reflects ethical expectations, not operational control.

## 6. No Warranty

Fialka is provided **"AS IS"**, without warranty of any kind, express or implied, including but not limited to the warranties of merchantability, fitness for a particular purpose, and non-infringement.

Specifically:

- The cryptographic implementation has **not yet been independently audited** by a third-party security firm (audit planned — see [SECURITY.md](SECURITY.md))
- The developers make **no guarantee** that the software is free of vulnerabilities
- The developers are **not responsible** for any data loss, security breach, or damage resulting from the use of the software

## 7. Limitation of Liability

To the maximum extent permitted by applicable law, the developers and contributors shall **not be liable** for any direct, indirect, incidental, special, consequential, or punitive damages arising from:

- Your use or inability to use Fialka
- Any unauthorized access to your data
- Any loss of cryptographic keys or recovery phrases
- Any compromise of your Tor Hidden Service or Mailbox node
- Any third-party actions or services (Tor, UnifiedPush, etc.)

## 8. Decentralized Architecture — No Central Control

Fialka is designed with **zero central authority**:

- **No central server**: Messages travel directly between devices via Tor
- **No account database**: Your identity is your Ed25519 key, stored only on your device
- **No content moderation**: The developers cannot see, read, or filter any message
- **No kill switch**: The developers cannot disable, block, or restrict any user
- **No telemetry**: The app sends zero data to the developers

This means the developers have **no ability to comply with takedown requests, content removal orders, or user data requests** — because no such data exists on any server they control.

## 9. Intellectual Property

- Fialka source code is licensed under **GPLv3**
- The Fialka name, logo, and branding are used by the FialkaApp organization; reuse should follow standard open-source community conventions
- Third-party libraries used by Fialka retain their respective licenses (see `app/build.gradle.kts` for dependency list)

## 10. Third-Party Software

Fialka includes or interacts with the following third-party components:

| Component | License | Developers' role |
|-----------|---------|-----------------|
| Tor (The Onion Router) | BSD | Bundled for anonymous transport |
| BouncyCastle | MIT | Bundled for cryptography |
| UnifiedPush / ntfy.sh | Open-source | Optional, user-configured |
| SQLCipher | BSD | Bundled for local DB encryption |

**The developers are not responsible for the availability, security, or privacy practices of any third-party software.**

## 11. Open-Source License

The full license text is available in the [LICENSE](LICENSE) file. In case of conflict between these Terms and the GPLv3 license, **the GPLv3 prevails** for matters related to source code distribution and modification.

## 12. Modifications to These Terms

These Terms may be updated at any time. Changes will be published in this file, reflected in the "Last updated" date, and noted in the [Changelog](docs/en/CHANGELOG.md). Continued use of Fialka constitutes acceptance of updated Terms.

## 13. Governing Law

These Terms are governed by the laws of **France** and applicable European Union regulations (including GDPR, Regulation 2016/679; and the Loi Informatique et Libertés n° 78-17 du 6 janvier 1978), without regard to conflict of law provisions. Any dispute arising from these Terms shall be subject to the exclusive jurisdiction of the courts of France.

**Regarding the EU Cyber Resilience Act (CRA — Regulation 2024/2847):** Fialka is a non-commercial open-source software product. It is currently exempt under the CRA's open-source non-commercial provisions. This position will be reviewed as CRA implementing acts are finalized.

## 14. Contact

- GitHub: [github.com/FialkaApp/Fialka-Android](https://github.com/FialkaApp/Fialka-Android)
- Security issues: See [SECURITY.md](SECURITY.md)

---

**Fialka is a tool. The developers do not operate any infrastructure, do not store any user data, and cannot access any messages. There is no central server — all communication is peer-to-peer via Tor. Users are solely responsible for their use of the software.**

© 2024-2026 FialkaApp Contributors. Licensed under [GPLv3](LICENSE).
