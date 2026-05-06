/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.fialkaapp.fialka.backup

import android.content.Context
import android.util.Base64
import com.fialkaapp.fialka.data.model.Contact
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Handles creation and restoration of encrypted .fialka backup files.
 *
 * File format:
 *   [4B magic: 0xF1 0xA1 0x5A 0x5E]
 *   [1B version: 0x01]
 *   [1B flags: bit0=identity, bit1=wallet, bit2=contacts, bit3=conversations, bit4=messages]
 *   [2B reserved: 0x00 0x00]
 *   [32B PBKDF2 salt]
 *   [12B AES-GCM IV]
 *   [4B payload length, big-endian]
 *   [N bytes AES-256-GCM ciphertext (payload + 16B GCM auth tag)]
 *
 * Key derivation: PBKDF2-HMAC-SHA256, 600 000 iterations, 256-bit output.
 */
object FialkaBackupManager {

    // File magic: 0xF1 0xA1 0x5A 0x5E — "FiAlKa" in hex puns
    private val MAGIC = byteArrayOf(0xF1.toByte(), 0xA1.toByte(), 0x5A.toByte(), 0x5E.toByte())
    private const val FORMAT_VERSION: Byte = 0x01

    const val FLAG_IDENTITY: Int      = 0x01
    const val FLAG_WALLET: Int        = 0x02
    const val FLAG_CONTACTS: Int      = 0x04
    const val FLAG_CONVERSATIONS: Int = 0x08
    const val FLAG_MESSAGES: Int      = 0x10

    private const val PBKDF2_ITERATIONS = 600_000
    private const val SALT_LEN = 32
    private const val IV_LEN   = 12
    private const val TAG_BITS = 128
    private const val HEADER_LEN = 4 + 1 + 1 + 2 + SALT_LEN + IV_LEN + 4 // 56 bytes

    // ─── Public API ──────────────────────────────────────────────────────────────

    data class BackupOptions(
        val includeIdentity: Boolean = true,   // always true; identity is the root secret
        val includeWallet:   Boolean = false,
        val includeContacts: Boolean = true
        // Conversations and messages are intentionally excluded: they are ephemeral ratchet-
        // bound state. Restoring them causes Double Ratchet desync with all contacts.
    )

    data class BackupContent(
        val options:         BackupOptions,
        val identitySeedB64: String?,   // Base64-encoded 32-byte Ed25519 seed
        val displayName:     String?,
        val walletMnemonic:  String?,   // 25-word Monero mnemonic (space-separated)
        val contacts:        List<Contact>
    )

    sealed class OpenResult {
        data class Success(val content: BackupContent) : OpenResult()
        object WrongPassphrase : OpenResult()
        data class Invalid(val reason: String) : OpenResult()
    }

    /**
     * Serialize and encrypt a backup.
     * Caller is responsible for passing non-sensitive options; identity seed must be valid.
     *
     * @throws IllegalArgumentException if identity seed is required but missing.
     */
    fun createBackup(passphrase: CharArray, content: BackupContent): ByteArray {
        require(content.identitySeedB64 != null) { "Identity seed is required for backup" }

        val payload = buildJson(content).toByteArray(Charsets.UTF_8)

        // Derive key
        val salt = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }
        val key  = deriveKey(passphrase, salt)

        // Encrypt
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(payload)
        key.fill(0)

        // Build flags
        var flags = FLAG_IDENTITY
        if (content.options.includeWallet && content.walletMnemonic != null) flags = flags or FLAG_WALLET
        if (content.options.includeContacts && content.contacts.isNotEmpty()) flags = flags or FLAG_CONTACTS
        // Conversations and messages are never backed up (ratchet desync prevention)

        val lenBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(ciphertext.size).array()

        return MAGIC +
               byteArrayOf(FORMAT_VERSION, flags.toByte(), 0x00, 0x00) +
               salt + iv + lenBytes + ciphertext
    }

    /**
     * Decrypt and parse a .fialka backup file.
     * Returns [OpenResult.WrongPassphrase] if GCM auth tag fails,
     * [OpenResult.Invalid] if the file is not a valid Fialka backup.
     */
    fun openBackup(data: ByteArray, passphrase: CharArray, context: Context): OpenResult {
        // Validate size and magic
        if (data.size < HEADER_LEN + 16) {
            return OpenResult.Invalid(context.getString(com.fialkaapp.fialka.R.string.backup_file_too_short))
        }
        if (!data.copyOfRange(0, 4).contentEquals(MAGIC)) {
            return OpenResult.Invalid("Ce fichier n'est pas une sauvegarde Fialka (magic invalide).")
        }

        val version = data[4]
        if (version != FORMAT_VERSION) {
            return OpenResult.Invalid(context.getString(com.fialkaapp.fialka.R.string.backup_version_unsupported, version))
        }

        val flags      = data[5].toInt() and 0xFF
        val salt       = data.copyOfRange(8, 8 + SALT_LEN)
        val iv         = data.copyOfRange(8 + SALT_LEN, 8 + SALT_LEN + IV_LEN)
        val payloadLen = ByteBuffer.wrap(data, 8 + SALT_LEN + IV_LEN, 4).order(ByteOrder.BIG_ENDIAN).int

        val expectedEnd = HEADER_LEN + payloadLen
        if (data.size < expectedEnd) {
            return OpenResult.Invalid(context.getString(com.fialkaapp.fialka.R.string.backup_file_corrupted))
        }

        val ciphertext = data.copyOfRange(HEADER_LEN, expectedEnd)

        val key = deriveKey(passphrase, salt)
        val plaintext: ByteArray
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
            plaintext = cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            key.fill(0)
            return OpenResult.WrongPassphrase
        } finally {
            key.fill(0)
        }

        return try {
            val content = parseJson(String(plaintext, Charsets.UTF_8), flags)
            plaintext.fill(0)
            OpenResult.Success(content)
        } catch (e: Exception) {
            plaintext.fill(0)
            OpenResult.Invalid(context.getString(com.fialkaapp.fialka.R.string.backup_payload_corrupted, e.message))
        }
    }

    // ─── Key derivation ──────────────────────────────────────────────────────────

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): ByteArray {
        val spec    = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val key     = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return key
    }

    // ─── JSON serialization ──────────────────────────────────────────────────────

    private fun buildJson(content: BackupContent): String {
        val obj = JSONObject()
        obj.put("v",  1)
        obj.put("ts", System.currentTimeMillis())

        // Identity
        if (content.identitySeedB64 != null) {
            obj.put("identity", JSONObject().apply {
                put("seed",         content.identitySeedB64)
                put("display_name", content.displayName ?: "")
            })
        }

        // Wallet
        if (content.options.includeWallet && content.walletMnemonic != null) {
            obj.put("wallet", JSONObject().apply {
                put("mnemonic", content.walletMnemonic)
            })
        }

        // Contacts
        if (content.options.includeContacts) {
            val arr = JSONArray()
            content.contacts.forEach { c ->
                arr.put(JSONObject().apply {
                    put("contactId",          c.contactId)
                    put("displayName",        c.displayName)
                    put("publicKey",          c.publicKey)
                    put("verificationStatus", c.verificationStatus)
                    put("addedAt",            c.addedAt)
                    c.signingPublicKey?.let { put("signingPublicKey", it) }
                    c.mlkemPublicKey?.let   { put("mlkemPublicKey",   it) }
                    c.mldsaPublicKey?.let   { put("mldsaPublicKey",   it) }
                    c.onionAddress?.let     { put("onionAddress",     it) }
                    c.mailboxOnion?.let     { put("mailboxOnion",     it) }
                })
            }
            obj.put("contacts", arr)
        }

        // Conversations and messages are not backed up (ratchet desync prevention).

        return obj.toString()
    }

    private fun parseJson(json: String, flags: Int): BackupContent {
        val obj = JSONObject(json)

        // Identity
        var identitySeedB64: String? = null
        var displayName: String?     = null
        if (flags and FLAG_IDENTITY != 0 && obj.has("identity")) {
            val id = obj.getJSONObject("identity")
            identitySeedB64 = id.optString("seed").takeIf { it.isNotEmpty() }
            displayName     = id.optString("display_name").takeIf { it.isNotEmpty() }
        }

        // Wallet
        var walletMnemonic: String? = null
        if (flags and FLAG_WALLET != 0 && obj.has("wallet")) {
            walletMnemonic = obj.getJSONObject("wallet").optString("mnemonic").takeIf { it.isNotEmpty() }
        }

        // Contacts
        val contacts = mutableListOf<Contact>()
        if (flags and FLAG_CONTACTS != 0 && obj.has("contacts")) {
            val arr = obj.getJSONArray("contacts")
            for (i in 0 until arr.length()) {
                val c = arr.getJSONObject(i)
                contacts.add(Contact(
                    contactId          = c.getString("contactId"),
                    displayName        = c.getString("displayName"),
                    publicKey          = c.getString("publicKey"),
                    verificationStatus = c.optString("verificationStatus", "unverified"),
                    addedAt            = c.optLong("addedAt", System.currentTimeMillis()),
                    signingPublicKey   = c.optString("signingPublicKey").takeIf { it.isNotEmpty() },
                    mlkemPublicKey     = c.optString("mlkemPublicKey").takeIf    { it.isNotEmpty() },
                    mldsaPublicKey     = c.optString("mldsaPublicKey").takeIf    { it.isNotEmpty() },
                    onionAddress       = c.optString("onionAddress").takeIf      { it.isNotEmpty() },
                    mailboxOnion       = c.optString("mailboxOnion").takeIf      { it.isNotEmpty() }
                ))
            }
        }

        // Conversations and messages are not restored (ratchet desync prevention).
        // The accountRestored presence flag will signal contacts to wipe their ratchet state.

        return BackupContent(
            options = BackupOptions(
                includeIdentity = flags and FLAG_IDENTITY != 0,
                includeWallet   = flags and FLAG_WALLET   != 0,
                includeContacts = flags and FLAG_CONTACTS != 0
            ),
            identitySeedB64 = identitySeedB64,
            displayName     = displayName,
            walletMnemonic  = walletMnemonic,
            contacts        = contacts
        )
    }
}
