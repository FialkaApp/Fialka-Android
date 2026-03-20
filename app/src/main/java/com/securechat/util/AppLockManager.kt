/*
 * SecureChat — Post-quantum encrypted messenger
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
package com.securechat.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Manages app lock (PIN code + biometric preference).
 *
 * PIN is stored as PBKDF2-HMAC-SHA256 hash (600k iterations + 16-byte salt)
 * in EncryptedSharedPreferences (Keystore-backed).
 * The user never stores plaintext PINs.
 *
 * All heavy crypto operations (verify/set PIN) are suspend functions
 * that run on Dispatchers.Default to avoid UI freezes.
 */
object AppLockManager {

    private const val PREFS_NAME = "securechat_lock"
    private const val KEY_PIN_HASH = "pin_hash"
    private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
    private const val KEY_AUTO_LOCK_DELAY = "auto_lock_delay"
    private const val PBKDF2_ITERATIONS = 600_000
    private const val PBKDF2_KEY_LENGTH = 256  // bits
    private const val SALT_LENGTH = 16  // bytes

    /** Auto-lock delay options in milliseconds. */
    val AUTO_LOCK_OPTIONS = longArrayOf(0L, 5_000L, 15_000L, 30_000L, 60_000L, 300_000L)
    val AUTO_LOCK_LABELS = arrayOf(
        "Immédiat", "5 secondes", "15 secondes",
        "30 secondes", "1 minute", "5 minutes"
    )

    @Volatile
    private var cachedPrefs: SharedPreferences? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return cachedPrefs ?: synchronized(this) {
            cachedPrefs ?: EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_NAME,
                MasterKey.Builder(context.applicationContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            ).also { cachedPrefs = it }
        }
    }

    /** Check if a PIN code has been configured. */
    fun isPinSet(context: Context): Boolean {
        return getPrefs(context).getString(KEY_PIN_HASH, null) != null
    }

    /** Set or update the PIN code (stored as PBKDF2 hash with salt). Runs off main thread. */
    suspend fun setPin(context: Context, pin: String) {
        val hash = withContext(Dispatchers.Default) { hashPin(pin) }
        getPrefs(context).edit().putString(KEY_PIN_HASH, hash).apply()
    }

    /** Verify the entered PIN against the stored hash. Runs off main thread. */
    suspend fun verifyPin(context: Context, pin: String): Boolean {
        val stored = getPrefs(context).getString(KEY_PIN_HASH, null) ?: return false
        return withContext(Dispatchers.Default) {
            stored == hashPinWithSalt(pin, stored.substringBefore(":"))
        }
    }

    /** Remove the PIN code (disables app lock). */
    fun removePin(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_PIN_HASH)
            .remove(KEY_BIOMETRIC_ENABLED)
            .apply()
    }

    /** Get the auto-lock delay in milliseconds (default 5 s). */
    fun getAutoLockDelay(context: Context): Long {
        return getPrefs(context).getLong(KEY_AUTO_LOCK_DELAY, 5_000L)
    }

    /** Set the auto-lock delay in milliseconds. */
    fun setAutoLockDelay(context: Context, delayMs: Long) {
        getPrefs(context).edit().putLong(KEY_AUTO_LOCK_DELAY, delayMs).apply()
    }

    /** Get a human-readable label for the current auto-lock delay. */
    fun getAutoLockLabel(context: Context): String {
        val delay = getAutoLockDelay(context)
        val idx = AUTO_LOCK_OPTIONS.indexOf(delay)
        return if (idx >= 0) AUTO_LOCK_LABELS[idx] else "5 secondes"
    }

    /** Check if biometric unlock is enabled. */
    fun isBiometricEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BIOMETRIC_ENABLED, false)
    }

    /** Enable or disable biometric unlock. */
    fun setBiometricEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    /** Hash PIN with PBKDF2-HMAC-SHA256 using a fresh random salt. */
    private fun hashPin(pin: String): String {
        val salt = ByteArray(SALT_LENGTH)
        SecureRandom().nextBytes(salt)
        return hashPinWithSalt(pin, salt.joinToString("") { "%02x".format(it) })
    }

    /** Hash PIN with PBKDF2-HMAC-SHA256 using provided hex salt. Returns "salt_hex:hash_hex". */
    private fun hashPinWithSalt(pin: String, saltHex: String): String {
        val saltBytes = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return "$saltHex:${hash.joinToString("") { "%02x".format(it) }}"
    }
}
