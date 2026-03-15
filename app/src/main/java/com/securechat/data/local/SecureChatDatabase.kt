package com.securechat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.securechat.data.model.Contact
import com.securechat.data.model.Conversation
import com.securechat.data.model.MessageLocal
import com.securechat.data.model.RatchetState
import com.securechat.data.model.UserLocal

/**
 * Room database for SecureChat.
 * Stores local user identity, contacts, conversations, messages, and ratchet state.
 */
@Database(
    entities = [
        UserLocal::class,
        Contact::class,
        Conversation::class,
        MessageLocal::class,
        RatchetState::class
    ],
    version = 3,
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

        fun getInstance(context: Context): SecureChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SecureChatDatabase::class.java,
                    "securechat_db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
