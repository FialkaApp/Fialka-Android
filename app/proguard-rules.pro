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

# --- Obfuscation ---
-repackageclasses 'a'
-allowaccessmodification
