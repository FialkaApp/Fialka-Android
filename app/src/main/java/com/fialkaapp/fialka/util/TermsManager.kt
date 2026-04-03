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
package com.fialkaapp.fialka.util

import android.content.Context

/**
 * TermsManager — stores and checks per-version terms acceptance.
 * Bump CURRENT_TERMS_VERSION whenever TERMS.md changes materially.
 */
object TermsManager {
    private const val PREFS_NAME = "fialka_terms"
    private const val KEY_ACCEPTED_VERSION = "terms_accepted_version"

    /** Increment this whenever terms change materially. */
    const val CURRENT_TERMS_VERSION = 4

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_ACCEPTED_VERSION, 0) >= CURRENT_TERMS_VERSION

    fun setAccepted(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_ACCEPTED_VERSION, CURRENT_TERMS_VERSION)
            .apply()
    }
}
