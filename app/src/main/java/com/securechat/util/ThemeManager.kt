package com.securechat.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate

/**
 * Manages the app theme preference (System / Light / Dark).
 * Persisted in SharedPreferences.
 */
object ThemeManager {

    private const val PREFS_NAME = "securechat_settings"
    private const val KEY_THEME = "app_theme"

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    fun getTheme(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME, THEME_SYSTEM)
    }

    fun setTheme(context: Context, theme: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME, theme).apply()
        applyTheme(theme)
    }

    fun applyTheme(theme: Int) {
        val mode = when (theme) {
            THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    fun applySavedTheme(context: Context) {
        applyTheme(getTheme(context))
    }

    fun getThemeLabel(theme: Int): String = when (theme) {
        THEME_LIGHT -> "Clair"
        THEME_DARK -> "Sombre"
        else -> "Système"
    }
}
