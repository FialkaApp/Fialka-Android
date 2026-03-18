# ==============================================================================
# SecureChat ProGuard / R8 Rules
# ==============================================================================

# --- Data models (Room entities + Firebase deserialization) ---
-keepclassmembers class com.securechat.data.model.** { *; }

# --- Firebase RTDB deserialization needs default constructors + fields ---
-keep class com.securechat.data.model.FirebaseMessage { *; }
-keepclassmembers class com.securechat.data.model.FirebaseMessage {
    <init>();
    <fields>;
}

# --- Room DB ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# --- Firebase ---
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# --- AndroidX / Navigation ---
-keep class androidx.navigation.** { *; }

# --- SQLCipher ---
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**

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
