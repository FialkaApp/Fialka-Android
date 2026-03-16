package com.securechat.data.local

import android.content.Context
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.securechat.data.model.Contact
import com.securechat.data.model.Conversation
import com.securechat.data.model.MessageLocal
import com.securechat.data.model.RatchetState
import com.securechat.data.model.UserLocal
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

/**
 * Room database for SecureChat, encrypted with SQLCipher.
 *
 * The 256-bit passphrase is:
 *  1. Generated once via SecureRandom (32 bytes → Base64)
 *  2. Stored in EncryptedSharedPreferences (AES-256-GCM, backed by Android Keystore)
 *  3. Loaded at each app start to unlock the database
 *
 * This ensures that even if the device storage is dumped (rooted phone, backup),
 * the SQLite database file is unreadable without the Keystore-protected passphrase.
 */
@Database(
    entities = [
        UserLocal::class,
        Contact::class,
        Conversation::class,
        MessageLocal::class,
        RatchetState::class
    ],
    version = 8,
    exportSchema = false
)
abstract class SecureChatDatabase : RoomDatabase() {

    abstract fun userLocalDao(): UserLocalDao
    abstract fun contactDao(): ContactDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageLocalDao(): MessageLocalDao
    abstract fun ratchetStateDao(): RatchetStateDao

    companion object {
        @Volatile
        private var INSTANCE: SecureChatDatabase? = null

        private const val PREFS_FILE = "securechat_db_key"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"

        fun getInstance(context: Context): SecureChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = getOrCreatePassphrase(context)
                val factory = SupportFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SecureChatDatabase::class.java,
                    "securechat_db"
                )
                    .openHelperFactory(factory)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Get or create the 256-bit database passphrase.
         * Stored in EncryptedSharedPreferences (Keystore-backed AES-256-GCM).
         */
        private fun getOrCreatePassphrase(context: Context): ByteArray {
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context.applicationContext,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            val existing = prefs.getString(KEY_DB_PASSPHRASE, null)
            if (existing != null) {
                return Base64.decode(existing, Base64.NO_WRAP)
            }

            // Generate new 256-bit passphrase
            val passphrase = ByteArray(32)
            SecureRandom().nextBytes(passphrase)
            val encoded = Base64.encodeToString(passphrase, Base64.NO_WRAP)
            prefs.edit().putString(KEY_DB_PASSPHRASE, encoded).apply()
            return passphrase
        }
    }
}
