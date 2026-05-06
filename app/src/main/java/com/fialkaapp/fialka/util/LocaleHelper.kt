package com.fialkaapp.fialka.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/**
 * Manages per-app language selection.
 *
 * On API 33+ (minSdk = 33) AppCompatDelegate.setApplicationLocales() stores the locale
 * permanently in the system — no attachBaseContext override needed.
 *
 * We only store our own boolean "has the user explicitly chosen a language" because
 * AppCompatDelegate cannot distinguish "user picked French" from "device is French".
 */
object LocaleHelper {

    private const val PREFS_NAME   = "fialka_locale"
    private const val KEY_SELECTED = "language_selected"
    private const val KEY_CODE     = "language_code"

    /** Returns true if the user has gone through the language selection screen. */
    fun isLanguageSelected(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SELECTED, false)
    }

    /**
     * Saves and applies the chosen language.
     * [code] is a BCP-47 language tag: "fr" or "en".
     * This triggers Activity recreation automatically on API 33+.
     */
    fun setLocale(context: Context, code: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_SELECTED, true)
            .putString(KEY_CODE, code)
            .apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(code))
    }

    /** Returns the saved language code, or the device locale tag as fallback. */
    fun getLocaleCode(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CODE, "fr") ?: "fr"
    }
}
