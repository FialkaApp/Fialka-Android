package com.securechat.util

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Manages app lock (PIN code + biometric preference).
 *
 * PIN is stored as PBKDF2-HMAC-SHA256 hash (600k iterations + 16-byte salt)
 * in EncryptedSharedPreferences (Keystore-backed).
 * The user never stores plaintext PINs.
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

    private fun getPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Check if a PIN code has been configured. */
    fun isPinSet(context: Context): Boolean {
        return getPrefs(context).getString(KEY_PIN_HASH, null) != null
    }

    /** Set or update the PIN code (stored as PBKDF2 hash with salt). */
    fun setPin(context: Context, pin: String) {
        getPrefs(context).edit().putString(KEY_PIN_HASH, hashPin(pin)).apply()
    }

    /** Verify the entered PIN against the stored hash. Handles legacy SHA-256 migration. */
    fun verifyPin(context: Context, pin: String): Boolean {
        val stored = getPrefs(context).getString(KEY_PIN_HASH, null) ?: return false
        if (stored.contains(":")) {
            // New PBKDF2 format: "salt_hex:hash_hex"
            return stored == hashPinWithSalt(pin, stored.substringBefore(":"))
        }
        // Legacy SHA-256 format (no colon) — verify and auto-migrate
        val legacyHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(pin.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        if (stored == legacyHash) {
            // Migrate to PBKDF2 on successful verification
            setPin(context, pin)
            return true
        }
        return false
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
