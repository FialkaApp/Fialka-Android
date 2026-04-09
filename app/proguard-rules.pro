# ==============================================================================
# Fialka ProGuard / R8 Rules
# ==============================================================================

# --- Data models (Room entities) ---
-keepclassmembers class com.fialkaapp.fialka.data.model.** { *; }

# --- Room DB ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# --- AndroidX / Navigation ---
-keep class androidx.navigation.** { *; }

# --- SQLCipher ---
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# --- jtorctl (Tor control port) ---
-keep class net.freehaven.tor.control.** { *; }
-dontwarn net.freehaven.tor.control.**

# --- ZXing (QR codes) ---
-keep class com.journeyapps.** { *; }
-keep class com.google.zxing.** { *; }

# --- Suppress ALL logs in release (security: no sensitive info in logcat) ---
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

# --- Wallet: keep JNI-called external methods on FialkaNative ---
-keepclasseswithmembernames class com.fialkaapp.fialka.crypto.FialkaNative {
    native <methods>;
}

# --- Wallet: keep companion object field names used in DonationConfig.hexToBytes ---
-keepclassmembers class com.fialkaapp.fialka.wallet.DonationConfig {
    *;
}

# --- Wallet: WalletSeedManager accesses EncryptedSharedPreferences by name ---
-keep class com.fialkaapp.fialka.crypto.WalletSeedManager { *; }
-keepclassmembers class com.fialkaapp.fialka.crypto.WalletSeedManager$XmrKeys { *; }

# --- Wallet: Room entity + DAO field names must survive shrinking ---
-keepclassmembers class com.fialkaapp.fialka.data.model.WalletTransaction { *; }
-keepclassmembers class com.fialkaapp.fialka.data.model.WalletAddress { *; }
-keepclassmembers class com.fialkaapp.fialka.data.model.WalletSyncState { *; }

# --- WorkManager: MoneroSyncWorker must be instantiated by class name ---
-keep class com.fialkaapp.fialka.wallet.MoneroSyncWorker { *; }

# --- Strip wallet-related log tags in release (already covered by general Log rule above,
#     but keep the class names stable for crash reporters) ---
-keepnames class com.fialkaapp.fialka.wallet.** { *; }

# --- Obfuscation ---
-repackageclasses 'a'
-allowaccessmodification
