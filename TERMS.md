# Terms of Use — Fialka

*Last updated: April 30, 2026 · Version 5 · Applicable from V4.2.0-alpha*

---

## ⚠️ Important Notice

**Fialka is a tool.** The developers do not operate any infrastructure, do not store any user data, and cannot access any messages or wallet data. There is no central server — all communication is peer-to-peer via Tor. Users are solely responsible for their use of the software.

---

## 1. Acceptance

By downloading, installing, or using Fialka, you agree to these Terms of Use. If you do not agree, do not use the software.

If you have previously accepted an earlier version of these Terms, continued use of Fialka following a version bump notification constitutes acceptance of the updated Terms.

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
- **Your backup files**: Encrypted `.fialka` backup files are protected by a passphrase you choose. If you lose the passphrase, the backup cannot be decrypted. The developers have no ability to help.
- **Your Mailbox node** (if self-hosted): You deploy, configure, and secure your own Mailbox. The developers have no access to it.
- **Your messages**: The developers cannot read, moderate, delete, or intercept any message. There is no reporting system and no content moderation — because there is no central server.
- **Your compliance**: You are responsible for complying with all applicable laws when using Fialka, including export control regulations for cryptographic software and virtual asset regulations.
- **Your data**: All data is stored locally on your device. The developers store nothing.
- **Your network security**: While Tor provides strong anonymity, you are responsible for your own operational security practices.

## 5. Prohibited Uses

You agree **not** to use Fialka:

- To violate any applicable law or regulation
- To transmit illegal content (as defined by applicable law)
- To harass, threaten, or harm others
- To distribute malware or malicious payloads
- To circumvent lawful court orders or legal obligations
- To conduct financial transactions that violate sanctions, AML/CFT obligations, or tax law

**Note:** The developers have **no technical ability** to enforce these restrictions. Fialka is fully peer-to-peer with no central point of control. This clause reflects ethical expectations, not operational control.

## 6. Monero (XMR) Wallet — Legal Notice

Fialka includes an **optional, local, non-custodial Monero (XMR) wallet**. This section explains its legal status in key jurisdictions.

### 6.1. Nature of the Wallet

- The wallet is **entirely local** — private keys are generated and stored exclusively on your device
- The developers have **zero custody, zero access, zero visibility** over any XMR holdings or transactions
- Fialka is **not an exchange**, **not a broker**, and **not a custodian**
- Fialka does **not** facilitate the exchange of XMR for fiat currency
- Fialka takes **zero percentage, zero fee** on any transaction — the only fees are standard Monero network fees (paid to miners), shown to you before confirmation
- The wallet is a **software tool** that allows you to manage your own XMR address locally

### 6.2. France (Régulation AMF / PSAN)

**Prestataires de Services sur Actifs Numériques (PSAN) — Loi PACTE n° 2019-486 du 22 mai 2019 :**
- The PSAN regime applies to custodial service providers (exchanges, custodial wallets, brokerage)
- Fialka does NOT provide custodial services — it is a self-hosted, non-custodial wallet tool
- The PSAN registration requirement (AMF) does **not apply** to self-custody software

**Anti-blanchiment (AML/CFT) — Ordonnance n° 2020-1544 du 9 décembre 2020 :**
- AML/CFT obligations apply to VASPs (Virtual Asset Service Providers) as defined by FATF
- Fialka is **not a VASP** — no exchange, no custody, no intermediation
- However, **you** are responsible for your own tax declarations on any XMR gains (Article 150 VH bis du CGI — Prélèvement Forfaitaire Unique, 30%)

**TRACFIN:** Reporting obligations under TRACFIN apply to PSAN and financial institutions, not to individual users of self-custody wallets.

### 6.3. European Union (MiCA — Regulation 2023/1114)

**Markets in Crypto-Assets Regulation (MiCA), fully applicable from December 2024:**
- MiCA regulates **Crypto-Asset Service Providers (CASPs)** — exchanges, custodial wallets, issuers
- Fialka is **not a CASP** — it provides no exchange, custody, or brokerage service
- **Self-hosted (non-custodial) wallets are explicitly outside the scope of MiCA**
- When interacting with regulated CASPs, you may be subject to the Transfer of Funds Regulation (Regulation 2023/1113 — "Travel Rule"), which requires CASPs to collect information on transfers to/from unhosted wallets

**AML/CFT Directives (5th/6th AMLD):** AML obligations apply to obliged entities (CASPs, financial institutions), not to individuals using non-custodial wallets for personal use.

**Privacy coins (Monero):** MiCA does not specifically ban privacy-preserving cryptocurrencies. However, some CASPs may restrict XMR trading for compliance reasons. Fialka itself conducts no exchanges. Note: some EU member states may have additional national requirements — check the laws of your specific country.

### 6.4. United States

**FinCEN (Financial Crimes Enforcement Network):**
- Money Services Business (MSB) registration is required for money transmitters and exchangers under the Bank Secrecy Act (BSA)
- Fialka is **not an MSB** — it does not transmit, exchange, or custody funds
- Per FinCEN guidance (FIN-2019-G001), users of self-hosted crypto wallets for personal transactions are generally **not** required to register as MSBs

**OFAC (Office of Foreign Assets Control) Sanctions:**
- You are required to comply with all applicable OFAC sanctions programs
- Do **not** use Fialka's wallet to transact with sanctioned individuals, entities, or jurisdictions (including OFAC SDN List designees)
- Sanctions compliance is **your sole responsibility**

**IRS Tax Treatment:**
- The IRS treats cryptocurrency as property (IRS Notice 2014-21, Rev. Rul. 2019-24)
- Gains and losses from XMR transactions may be taxable as capital gains or ordinary income
- You are responsible for tracking transactions and reporting on IRS Form 8949 / Schedule D
- Consult a qualified tax advisor regarding privacy-preserving coins

**Privacy coins (XMR) — US-specific:**
- Monero is a legal privacy-preserving cryptocurrency in the United States
- Some regulated exchanges have delisted XMR for compliance reasons — Fialka does not interface with any exchange

### 6.5. Other Jurisdictions

**Japan:** The FSA has restricted privacy coins on regulated exchanges. Self-custody of XMR for personal use remains legal.

**South Korea:** Privacy coins have faced restrictions on regulated platforms. Personal self-custody use remains legal. Check current regulations.

**Australia:** AUSTRAC regulates digital currency exchanges. Self-custody wallets are not regulated exchanges. CGT may apply to XMR transactions.

**United Kingdom:** HMRC treats crypto as property. CGT may apply. The FCA crypto regulation applies to exchanges and custodians, not self-hosted wallet software.

**⚠️ General notice for all jurisdictions:** Laws and regulations regarding cryptocurrencies and privacy-preserving assets evolve rapidly. **You are solely responsible for verifying and complying with all applicable laws in your jurisdiction.** The developers provide no legal advice.

### 6.6. Developer Limitations

The developers:
- Have **no visibility** into any XMR address, balance, or transaction
- Cannot **freeze, seize, or recover** any XMR holdings
- Cannot comply with court orders, regulatory demands, or law enforcement requests for wallet data — because **no such data is held by the developers**
- Cannot provide **tax reporting** — you must manage your own records

## 7. No Warranty

Fialka is provided **"AS IS"**, without warranty of any kind, express or implied.

Specifically:

- The cryptographic implementation has **not yet been independently audited** by a third-party security firm (audit planned — see [SECURITY.md](SECURITY.md))
- The developers make **no guarantee** that the software is free of vulnerabilities
- The developers are **not responsible** for any data loss, security breach, or damage resulting from the use of the software
- The Monero wallet integration relies on the Monero network infrastructure operated by third parties — the developers are not responsible for their availability or continuity

## 8. Limitation of Liability

To the maximum extent permitted by applicable law, the developers and contributors shall **not be liable** for any direct, indirect, incidental, special, consequential, or punitive damages arising from:

- Your use or inability to use Fialka
- Any unauthorized access to your data
- Any loss of cryptographic keys, recovery phrases, or backup passphrases
- Any loss of XMR funds, whether due to software bugs, hardware failure, user error, or network issues
- Any compromise of your Tor Hidden Service or Mailbox node
- Any third-party actions or services (Tor, UnifiedPush, Monero network, etc.)
- Any regulatory action taken against you in connection with your XMR usage

## 9. Decentralized Architecture — No Central Control

Fialka is designed with **zero central authority**:

- **No central server**: Messages travel directly between devices via Tor
- **No account database**: Your identity is your Ed25519 key, stored only on your device
- **No content moderation**: The developers cannot see, read, or filter any message
- **No kill switch**: The developers cannot disable, block, or restrict any user
- **No telemetry**: The app sends zero data to the developers
- **No wallet custody**: XMR private keys never leave your device

This means the developers have **no ability to comply with takedown requests, content removal orders, user data requests, or asset freeze orders** — because no such data exists on any server they control.

## 10. Intellectual Property

- Fialka source code is licensed under **GPLv3**
- The Fialka name, logo, and branding are used by the FialkaApp organization; reuse should follow standard open-source community conventions
- Third-party libraries used by Fialka retain their respective licenses

## 11. Third-Party Software

Fialka includes or interacts with the following third-party components:

| Component | License | Developers' role |
|-----------|---------|-----------------|
| Tor (The Onion Router) | BSD | Bundled for anonymous transport |
| Fialka-Core (Rust) | GPL-3.0 | Bundled cryptographic core — Ed25519, X25519, ML-KEM-1024, ML-DSA-44, AES-256-GCM, ChaCha20-Poly1305, HKDF, Double Ratchet |
| UnifiedPush / ntfy.sh | Open-source | Optional, user-configured push wake-up |
| SQLCipher | BSD | Bundled for local DB encryption |
| Monero wallet libraries | BSD / MIT | Bundled for local XMR address management |

**The developers are not responsible for the availability, security, or privacy practices of any third-party software.**

## 12. Open-Source License

The full license text is available in the [LICENSE](LICENSE) file. In case of conflict between these Terms and the GPLv3 license, **the GPLv3 prevails** for matters related to source code distribution and modification.

## 13. Backup & Restore — Legal Notice

Fialka supports encrypted backup files (`.fialka` format):

- Backup files are encrypted with AES-256-GCM using a key derived via PBKDF2-HMAC-SHA256 (600,000 iterations)
- **Backup files do NOT contain message history** — only identity keys, wallet seed (if selected), and contacts
- You are responsible for the security of your backup files and passphrases
- Lost backup passphrases cannot be recovered by the developers
- Backup files containing cryptographic material may be subject to export control regulations (see Section 14)

## 14. Export Controls

This software contains cryptographic elements subject to export control regulations:

| Regulation | Jurisdiction | Status |
|-----------|-------------|--------|
| Wassenaar Arrangement — Category 5 Part 2 | International | Open-source exemption applies |
| EAR — ECCN 5D002.C.1 | United States (BIS) | TSU exception for publicly available open-source |
| EU Dual-Use Regulation 2021/821 | European Union | Open-source exemption (Art. 2(21)) |
| Monero (XMR) — no specific crypto export restriction | All | Standard crypto software rules apply |

**In France and the EU:** Open-source cryptographic software distributed under GPLv3 is generally exempt from export authorization requirements under applicable EU and French regulations.

**In the US:** Under the EAR, publicly available open-source cryptographic software is eligible for the TSU (Technology and Software — Unrestricted) exception, provided it is not subject to an export license requirement for other reasons.

**⚠️ You are responsible for verifying compliance with the export control laws of your jurisdiction.** The developers do not provide export compliance advice.

## 15. Modifications to These Terms

These Terms may be updated at any time. Changes will be published in this file, reflected in the "Last updated" date, and noted in the [Changelog](docs/en/CHANGELOG.md). A material change to these Terms will increment the `CURRENT_TERMS_VERSION` in the app, prompting users to review and re-accept.

## 16. Governing Law

These Terms are governed by the laws of **France** and applicable European Union regulations, including:
- GDPR (Regulation 2016/679)
- Loi Informatique et Libertés n° 78-17 du 6 janvier 1978
- Loi PACTE n° 2019-486 (virtual asset regulation)
- MiCA (Regulation 2023/1114) where applicable

Any dispute arising from these Terms shall be subject to the exclusive jurisdiction of the courts of France.

**Regarding the EU Cyber Resilience Act (CRA — Regulation 2024/2847):** Fialka is a non-commercial open-source software product. It is currently exempt under the CRA's open-source non-commercial provisions.

## 17. Contact

- GitHub: [github.com/FialkaApp/Fialka-Android](https://github.com/FialkaApp/Fialka-Android)
- Security issues: See [SECURITY.md](SECURITY.md)

---

**Fialka is a tool. The developers do not operate any infrastructure, do not store any user data, and cannot access any messages or wallet data. There is no central server — all communication is peer-to-peer via Tor. Users are solely responsible for their use of the software.**

© 2024-2026 FialkaApp Contributors. Licensed under [GPLv3](LICENSE).
