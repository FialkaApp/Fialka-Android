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
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import javax.crypto.KeyGenerator

private const val TAG = "DeviceSecurityManager"

// ============================================================================
// Data classes & enums
// ============================================================================

enum class StrongBoxStatus { AVAILABLE, NOT_AVAILABLE, DECLARED_BUT_UNAVAILABLE }
enum class UserProfileType { OWNER, SECONDARY, UNKNOWN }
enum class SecurityLevel { MAXIMUM, STANDARD }

data class SecurityProfile(
    val strongBoxStatus: StrongBoxStatus,
    val userProfileType: UserProfileType,
    val deviceName: String,
    val securityLevel: SecurityLevel
) {
    val isStrongBoxAvailable: Boolean get() = strongBoxStatus == StrongBoxStatus.AVAILABLE
    val isSecondaryProfile: Boolean   get() = userProfileType == UserProfileType.SECONDARY
    val securityLevelLabel: String    get() = when (securityLevel) {
        SecurityLevel.MAXIMUM  -> "Maximum"
        SecurityLevel.STANDARD -> "Standard"
    }
}

// ============================================================================
// Singleton
// ============================================================================

object DeviceSecurityManager {

    @Volatile
    private var cachedProfile: SecurityProfile? = null

    /**
     * Returns the SecurityProfile for this device.
     * Result is cached after the first call — safe to call from any thread.
     * The StrongBox probe (only done once) may take 100–300 ms on first call.
     */
    fun getSecurityProfile(context: Context): SecurityProfile {
        cachedProfile?.let { return it }
        synchronized(this) {
            cachedProfile?.let { return it }
            val profile = buildProfile(context.applicationContext)
            cachedProfile = profile
            Log.i(TAG, "SecurityProfile built: $profile")
            return profile
        }
    }

    // -------------------------------------------------------------------------
    // Internal builders
    // -------------------------------------------------------------------------

    private fun buildProfile(context: Context): SecurityProfile {
        val strongBox     = probeStrongBox(context)
        val userProfile   = detectUserProfile()
        val deviceName    = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val securityLevel = if (strongBox == StrongBoxStatus.AVAILABLE) {
            SecurityLevel.MAXIMUM
        } else {
            SecurityLevel.STANDARD
        }
        return SecurityProfile(
            strongBoxStatus    = strongBox,
            userProfileType    = userProfile,
            deviceName         = deviceName,
            securityLevel      = securityLevel
        )
    }

    // -------------------------------------------------------------------------
    // StrongBox probe
    // -------------------------------------------------------------------------

    private fun probeStrongBox(context: Context): StrongBoxStatus {
        val pm = context.packageManager
        if (!pm.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
            return StrongBoxStatus.NOT_AVAILABLE
        }

        // FEATURE declared — now verify it actually works via a live key-gen probe
        val alias = "securechat_strongbox_probe_${System.currentTimeMillis()}"
        return try {
            val spec = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setIsStrongBoxBacked(true)
                .build()

            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            kg.init(spec)
            kg.generateKey()

            // Delete probe key immediately
            try {
                val ks = KeyStore.getInstance("AndroidKeyStore").also { it.load(null) }
                ks.deleteEntry(alias)
            } catch (ignore: Exception) { /* best-effort */ }

            StrongBoxStatus.AVAILABLE
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("StrongBox", ignoreCase = true)
                || msg.contains("FEATURE_STRONGBOX", ignoreCase = true)
                || e.javaClass.name.contains("StrongBox", ignoreCase = true)
            ) {
                StrongBoxStatus.DECLARED_BUT_UNAVAILABLE
            } else {
                Log.w(TAG, "StrongBox probe threw unexpected exception: ${e.javaClass.name}: $msg")
                StrongBoxStatus.DECLARED_BUT_UNAVAILABLE
            }
        }
    }

    // -------------------------------------------------------------------------
    // User profile detection (owner = 0, secondary > 0)
    // -------------------------------------------------------------------------

    private fun detectUserProfile(): UserProfileType {
        return try {
            val handle = Process.myUserHandle()
            // UserHandle.getIdentifier() is a public API since API 24
            val identifier = handle.javaClass
                .getMethod("getIdentifier")
                .invoke(handle) as Int
            if (identifier == 0) UserProfileType.OWNER else UserProfileType.SECONDARY
        } catch (e: Exception) {
            Log.w(TAG, "User profile detection failed", e)
            UserProfileType.UNKNOWN
        }
    }
}
