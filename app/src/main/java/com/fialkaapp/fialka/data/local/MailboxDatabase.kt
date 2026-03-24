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
import com.fialkaapp.fialka.data.model.MailboxBlob
import com.fialkaapp.fialka.data.model.MailboxInvite
import com.fialkaapp.fialka.data.model.MailboxMember
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory
import java.security.SecureRandom

/**
 * Separate Room database for MAILBOX mode — encrypted with SQLCipher.
 * Stores blobs, members, and invites. NEVER used in NORMAL mode.
 *
 * The mailbox has NO access to message content (blobs are opaque encrypted data).
 */
@Database(
    entities = [MailboxBlob::class, MailboxMember::class, MailboxInvite::class],
    version = 1,
    exportSchema = false
)
abstract class MailboxDatabase : RoomDatabase() {

    abstract fun blobDao(): MailboxBlobDao
    abstract fun memberDao(): MailboxMemberDao
    abstract fun inviteDao(): MailboxInviteDao

    companion object {
        @Volatile
        private var INSTANCE: MailboxDatabase? = null

        private const val PREFS_FILE = "mailbox_db_key"
        private const val KEY_DB_PASSPHRASE = "db_passphrase"

        fun getInstance(context: Context): MailboxDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): MailboxDatabase {
            val passphrase = getOrCreatePassphrase(context)
            val factory = SupportFactory(
                passphrase,
                object : net.sqlcipher.database.SQLiteDatabaseHook {
                    override fun preKey(database: SQLiteDatabase?) {}
                    override fun postKey(database: SQLiteDatabase?) {
                        database?.rawExecSQL("PRAGMA cipher_memory_security = ON;")
                    }
                }
            )

            return Room.databaseBuilder(
                context.applicationContext,
                MailboxDatabase::class.java,
                "mailbox_db"
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }

        /**
         * Close and destroy the database instance.
         * Used when resetting the mailbox.
         */
        fun destroyInstance() {
            synchronized(this) {
                try { INSTANCE?.close() } catch (_: Exception) {}
                INSTANCE = null
            }
        }

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

            val passphrase = ByteArray(32)
            SecureRandom().nextBytes(passphrase)
            val encoded = Base64.encodeToString(passphrase, Base64.NO_WRAP)
            prefs.edit().putString(KEY_DB_PASSPHRASE, encoded).apply()
            return passphrase
        }
    }
}
