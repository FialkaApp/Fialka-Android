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
package com.fialkaapp.fialka.data.local

import android.content.Context
import android.util.Base64
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.fialkaapp.fialka.data.model.Contact
import com.fialkaapp.fialka.data.model.Conversation
import com.fialkaapp.fialka.data.model.MessageLocal
import com.fialkaapp.fialka.data.model.OutboxMessage
import com.fialkaapp.fialka.data.model.RatchetState
import com.fialkaapp.fialka.data.model.UserLocal
import com.fialkaapp.fialka.util.DeviceSecurityManager
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

/**
 * Room database for Fialka, encrypted with SQLCipher.
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
        RatchetState::class,
        OutboxMessage::class
    ],
    version = 24,
    exportSchema = false
)
abstract class FialkaDatabase : RoomDatabase() {

    abstract fun userLocalDao(): UserLocalDao
    abstract fun contactDao(): ContactDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageLocalDao(): MessageLocalDao
    abstract fun ratchetStateDao(): RatchetStateDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        @Volatile
        private var INSTANCE: FialkaDatabase? = null

        private const val PREFS_FILE = "fialka_db_key"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"

        fun getInstance(context: Context): FialkaDatabase {
            return INSTANCE ?: synchronized(this) {
                val passphrase = getOrCreatePassphrase(context)
                val factory = SupportOpenHelperFactory(passphrase)

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FialkaDatabase::class.java,
                    "fialka_db"
                )
                    .openHelperFactory(factory)
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            db.execSQL("PRAGMA cipher_memory_security = ON;")
                        }
                    })
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
            val profile = DeviceSecurityManager.getSecurityProfile(context.applicationContext)
            val masterKey = MasterKey.Builder(context.applicationContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .setRequestStrongBoxBacked(profile.isStrongBoxAvailable)
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
