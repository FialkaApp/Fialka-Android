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
enum class SecurityLevel { MAXIMUM, HIGH, STANDARD }

data class SecurityProfile(
    val isGrapheneOS: Boolean,
    val strongBoxStatus: StrongBoxStatus,
    val userProfileType: UserProfileType,
    val deviceName: String,           // "$MANUFACTURER $MODEL"
    val securityLevel: SecurityLevel  // GOS+SB→MAX, GOS seul OU SB seul→HIGH, rien→STD
) {
    val isStrongBoxAvailable: Boolean get() = strongBoxStatus == StrongBoxStatus.AVAILABLE
    val isSecondaryProfile: Boolean   get() = userProfileType == UserProfileType.SECONDARY
    val securityLevelLabel: String    get() = when (securityLevel) {
        SecurityLevel.MAXIMUM  -> "Maximum"
        SecurityLevel.HIGH     -> "Élevé"
        SecurityLevel.STANDARD -> "Standard"
    }
    val osLabel: String get() = if (isGrapheneOS) "GrapheneOS" else "Android"
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
        val isGos         = detectGrapheneOS()
        val strongBox     = probeStrongBox(context)
        val userProfile   = detectUserProfile()
        val deviceName    = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        val securityLevel = when {
            isGos && strongBox == StrongBoxStatus.AVAILABLE                    -> SecurityLevel.MAXIMUM
            isGos || strongBox == StrongBoxStatus.AVAILABLE                    -> SecurityLevel.HIGH
            else                                                                -> SecurityLevel.STANDARD
        }
        return SecurityProfile(
            isGrapheneOS       = isGos,
            strongBoxStatus    = strongBox,
            userProfileType    = userProfile,
            deviceName         = deviceName,
            securityLevel      = securityLevel
        )
    }

    // -------------------------------------------------------------------------
    // GrapheneOS detection
    // -------------------------------------------------------------------------

    private fun detectGrapheneOS(): Boolean {
        return try {
            val flavor      = System.getProperty("ro.build.flavor") ?: ""
            val fingerprint = Build.FINGERPRINT ?: ""
            val brand       = Build.BRAND ?: ""
            val product     = Build.PRODUCT ?: ""

            flavor.contains("grapheneos", ignoreCase = true)
                || fingerprint.contains("grapheneos", ignoreCase = true)
                || brand.contains("graphene", ignoreCase = true)
                || product.contains("graphene", ignoreCase = true)
        } catch (e: Exception) {
            Log.w(TAG, "GrapheneOS detection failed", e)
            false
        }
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
