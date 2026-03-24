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
package com.fialkaapp.fialka

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class AppModeType { NOT_SET, NORMAL, MAILBOX }
enum class MailboxType { NONE, PERSONAL, PRIVATE }

/**
 * App mode singleton — NORMAL (full chat) or MAILBOX (dedicated server).
 * Choice is IRRÉVERSIBLE once set. Reset only by clearing app data.
 */
object AppMode {

    private const val PREFS_NAME = "fialka_app_mode"
    private const val KEY_MODE = "app_mode"
    private const val KEY_MAILBOX_TYPE = "mailbox_type"
    private const val KEY_OWNER_QR_VALIDATED = "owner_qr_validated"

    @Volatile private var cachedMode: AppModeType? = null
    @Volatile private var cachedMailboxType: MailboxType? = null

    private fun prefs(context: Context) = EncryptedSharedPreferences.create(
        context.applicationContext,
        PREFS_NAME,
        MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getMode(context: Context): AppModeType {
        cachedMode?.let { return it }
        val stored = prefs(context).getString(KEY_MODE, null)
        val mode = when (stored) {
            "NORMAL" -> AppModeType.NORMAL
            "MAILBOX" -> AppModeType.MAILBOX
            else -> AppModeType.NOT_SET
        }
        cachedMode = mode
        return mode
    }

    /**
     * Set app mode. IRRÉVERSIBLE — once set, can only be changed
     * by clearing app data (full reset).
     */
    fun setMode(context: Context, mode: AppModeType) {
        require(mode != AppModeType.NOT_SET) { "Cannot set mode to NOT_SET" }
        val current = getMode(context)
        require(current == AppModeType.NOT_SET) { "Mode already set — irréversible" }
        prefs(context).edit().putString(KEY_MODE, mode.name).apply()
        cachedMode = mode
    }

    fun getMailboxType(context: Context): MailboxType {
        cachedMailboxType?.let { return it }
        val stored = prefs(context).getString(KEY_MAILBOX_TYPE, null)
        val type = when (stored) {
            "PERSONAL" -> MailboxType.PERSONAL
            "PRIVATE" -> MailboxType.PRIVATE
            else -> MailboxType.NONE
        }
        cachedMailboxType = type
        return type
    }

    /** Set mailbox type. Only valid in MAILBOX mode. Irréversible. */
    fun setMailboxType(context: Context, type: MailboxType) {
        require(type != MailboxType.NONE) { "Cannot set mailbox type to NONE" }
        require(getMode(context) == AppModeType.MAILBOX) { "Not in MAILBOX mode" }
        val current = getMailboxType(context)
        require(current == MailboxType.NONE) { "Mailbox type already set — irréversible" }
        prefs(context).edit().putString(KEY_MAILBOX_TYPE, type.name).apply()
        cachedMailboxType = type
    }

    fun isNormal(context: Context) = getMode(context) == AppModeType.NORMAL
    fun isMailbox(context: Context) = getMode(context) == AppModeType.MAILBOX
    fun isNotSet(context: Context) = getMode(context) == AppModeType.NOT_SET

    /**
     * Reset mailbox type to NONE — allows re-choosing Personal/Private.
     * Only resets the type, NOT the mode (still MAILBOX).
     * Used when user deletes/resets their mailbox from dashboard.
     */
    fun resetMailboxType(context: Context) {
        require(getMode(context) == AppModeType.MAILBOX) { "Not in MAILBOX mode" }
        prefs(context).edit()
            .remove(KEY_MAILBOX_TYPE)
            .remove(KEY_OWNER_QR_VALIDATED)
            .apply()
        cachedMailboxType = null
    }

    /** Mark the Owner QR as validated (shown once, never again). */
    fun setOwnerQrValidated(context: Context) {
        prefs(context).edit().putBoolean(KEY_OWNER_QR_VALIDATED, true).apply()
    }

    /** Has the owner QR already been shown and validated? */
    fun isOwnerQrValidated(context: Context): Boolean =
        prefs(context).getBoolean(KEY_OWNER_QR_VALIDATED, false)
}
