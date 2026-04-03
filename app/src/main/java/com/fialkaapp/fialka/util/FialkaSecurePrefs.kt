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
package com.fialkaapp.fialka.util

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Direct Android Keystore encrypted preferences — zero third-party crypto dependency.
 *
 * Architecture (same approach as Signal Android):
 *   - One AES-256-GCM key per preferences file, stored in Android Keystore (TEE / StrongBox).
 *     The secret key never leaves secure hardware in cleartext.
 *   - Each value is encrypted as [12-byte random IV | ciphertext | 16-byte GCM tag],
 *     stored Base64-encoded under its key name in a plain SharedPreferences XML file.
 *   - Key names are stored in plaintext — consistent with the previous behaviour.
 *
 * IMPORTANT — migration note:
 *   File names are suffixed with "_v2" to avoid collisions with legacy
 *   EncryptedSharedPreferences files (Tink-based format). On fresh installs
 *   there is nothing to migrate. Existing test data requires re-onboarding.
 */
object FialkaSecurePrefs {

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS_PREFIX  = "fialka_ks_"
    private const val TRANSFORM         = "AES/GCM/NoPadding"
    private const val IV_LEN            = 12   // bytes — fixed for AES-GCM
    private const val TAG_BITS          = 128  // bits — full GCM authentication tag

    /**
     * Open (or create) an encrypted preferences file.
     *
     * @param context  Application context.
     * @param name     Logical preferences name (e.g. "fialka_identity_keys").
     * @param strongBox Request StrongBox-backed key when available.
     *                  Falls back silently to TEE if the device lacks StrongBox.
     */
    fun open(context: Context, name: String, strongBox: Boolean = false): Prefs {
        val alias = KEY_ALIAS_PREFIX + name
        val key   = getOrCreateKey(alias, strongBox)
        // "_v2" suffix avoids collision with legacy EncryptedSharedPreferences files.
        val raw   = context.applicationContext
            .getSharedPreferences(name + "_v2", Context.MODE_PRIVATE)
        return Prefs(raw, key)
    }

    // ── Key provisioning ─────────────────────────────────────────────────────

    private fun getOrCreateKey(alias: String, strongBox: Boolean): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (ks.containsAlias(alias)) {
            return (ks.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
        }
        val specBuilder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
        if (strongBox) {
            try { specBuilder.setIsStrongBoxBacked(true) } catch (_: Exception) {
                // Device does not support StrongBox — use TEE silently.
            }
        }
        return KeyGenerator
            .getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(specBuilder.build()) }
            .generateKey()
    }

    // ── Public Prefs class ───────────────────────────────────────────────────

    class Prefs internal constructor(
        private val raw: SharedPreferences,
        private val key: SecretKey
    ) : SharedPreferences {

        // ── Crypto primitives ─────────────────────────────────────────────

        private fun encrypt(plaintext: String): String {
            val cipher = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
            val iv      = cipher.iv                           // 12-byte random IV from JCA
            val payload = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
            // Layout: [IV (12)] [ciphertext + GCM tag (16)]
            return Base64.encodeToString(iv + payload, Base64.NO_WRAP)
        }

        private fun decrypt(encoded: String): String {
            val blob = Base64.decode(encoded, Base64.NO_WRAP)
            val iv   = blob.copyOfRange(0, IV_LEN)
            val ct   = blob.copyOfRange(IV_LEN, blob.size)
            val cipher = Cipher.getInstance(TRANSFORM).apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
            }
            return String(cipher.doFinal(ct), Charsets.UTF_8)
        }

        // ── SharedPreferences reads ───────────────────────────────────────

        override fun getString(key: String, defValue: String?): String? {
            val enc = raw.getString(key, null) ?: return defValue
            return try { decrypt(enc) } catch (_: Exception) { defValue }
        }

        override fun getBoolean(key: String, defValue: Boolean): Boolean =
            getString(key, null)?.toBooleanStrictOrNull() ?: defValue

        override fun getLong(key: String, defValue: Long): Long =
            getString(key, null)?.toLongOrNull() ?: defValue

        override fun getInt(key: String, defValue: Int): Int =
            getString(key, null)?.toIntOrNull() ?: defValue

        override fun getFloat(key: String, defValue: Float): Float =
            getString(key, null)?.toFloatOrNull() ?: defValue

        override fun contains(key: String): Boolean = raw.contains(key)

        /** Not supported — returns empty map (consistent with prior behaviour). */
        override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any?>()

        /** StringSet not supported — returns defValues. */
        override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? = defValues

        override fun edit(): SharedPreferences.Editor = EncEditor(raw, ::encrypt)

        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = raw.registerOnSharedPreferenceChangeListener(listener)

        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener
        ) = raw.unregisterOnSharedPreferenceChangeListener(listener)
    }

    // ── Editor ───────────────────────────────────────────────────────────────

    private class EncEditor(
        private val raw: SharedPreferences,
        private val encrypt: (String) -> String
    ) : SharedPreferences.Editor {

        private val delegate = raw.edit()

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value == null) delegate.remove(key)
            else delegate.putString(key, encrypt(value))
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
            putString(key, value.toString())

        override fun putLong(key: String, value: Long): SharedPreferences.Editor =
            putString(key, value.toString())

        override fun putInt(key: String, value: Int): SharedPreferences.Editor =
            putString(key, value.toString())

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
            putString(key, value.toString())

        /** StringSet not supported — no-op. */
        override fun putStringSet(
            key: String, values: MutableSet<String>?
        ): SharedPreferences.Editor = this

        override fun remove(key: String): SharedPreferences.Editor {
            delegate.remove(key); return this
        }

        override fun clear(): SharedPreferences.Editor { delegate.clear(); return this }
        override fun commit(): Boolean = delegate.commit()
        override fun apply() = delegate.apply()
    }
}
