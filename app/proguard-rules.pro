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

# --- Monero JNI bridge (wallet/jni) ---
# libfialka_monero.so calls FindClass() by name at JNI_OnLoad.
# R8 sees no Kotlin references → strips these classes → SIGABRT ClassNotFoundException.
# Keep all classes, their fields and constructors used by the C++ JNI layer.
-keep class com.fialkaapp.fialka.wallet.jni.** { *; }
-keepclassmembers class com.fialkaapp.fialka.wallet.jni.** { *; }

# --- Obfuscation ---
-repackageclasses 'a'
-allowaccessmodification
