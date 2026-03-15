package com.securechat.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import com.securechat.data.model.Contact

@Dao
interface ContactDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact)

    @Query("SELECT * FROM contacts ORDER BY addedAt DESC")
    fun getAllContacts(): LiveData<List<Contact>>

    @Query("SELECT * FROM contacts WHERE publicKey = :publicKey LIMIT 1")
    suspend fun getContactByPublicKey(publicKey: String): Contact?

    @Query("SELECT * FROM contacts WHERE contactId = :contactId LIMIT 1")
    suspend fun getContactById(contactId: String): Contact?

    @Delete
    suspend fun deleteContact(contact: Contact)
}
