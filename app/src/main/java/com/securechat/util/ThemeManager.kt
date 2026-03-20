/*
 * SecureChat — Post-quantum encrypted messenger
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
package com.securechat.util

import android.content.Context
import com.securechat.R

/**
 * Manages the app theme — five named themes.
 * Default is PHANTOM (dark anthracite, purple accent).
 */
object ThemeManager {

    private const val PREFS_NAME = "securechat_settings"
    private const val KEY_THEME = "app_theme"

    const val THEME_MIDNIGHT = 0  // Dark teal/cyan
    const val THEME_HACKER   = 1  // AMOLED black + Matrix green
    const val THEME_PHANTOM  = 2  // Default — dark anthracite + purple
    const val THEME_AURORA   = 3  // Dark + amber/orange
    const val THEME_DAYLIGHT = 4  // Clean light + blue

    data class ThemeInfo(
        val id: Int,
        val nameRes: Int,
        val styleRes: Int,
        val previewBg: Int,   // ARGB for card preview
        val previewAccent: Int
    )

    val ALL_THEMES = listOf(
        ThemeInfo(THEME_MIDNIGHT, R.string.theme_midnight, R.style.Theme_SecureChat,           0xFF0D0D0D.toInt(), 0xFF00BFA5.toInt()),
        ThemeInfo(THEME_HACKER,   R.string.theme_hacker,   R.style.Theme_SecureChat_Hacker,    0xFF000000.toInt(), 0xFF00E676.toInt()),
        ThemeInfo(THEME_PHANTOM,  R.string.theme_phantom,  R.style.Theme_SecureChat_Phantom,   0xFF0F0F14.toInt(), 0xFFBB86FC.toInt()),
        ThemeInfo(THEME_AURORA,   R.string.theme_aurora,   R.style.Theme_SecureChat_Aurora,    0xFF0D0D0D.toInt(), 0xFFFFB74D.toInt()),
        ThemeInfo(THEME_DAYLIGHT, R.string.theme_daylight, R.style.Theme_SecureChat_Daylight,  0xFFF5F5F5.toInt(), 0xFF1976D2.toInt()),
    )

    fun getTheme(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_THEME, THEME_PHANTOM)
    }

    fun setTheme(context: Context, theme: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_THEME, theme).apply()
    }

    fun getThemeStyleRes(context: Context): Int {
        return ALL_THEMES.getOrNull(getTheme(context))?.styleRes
            ?: R.style.Theme_SecureChat
    }

    fun getThemeInfo(themeId: Int): ThemeInfo {
        return ALL_THEMES.getOrNull(themeId) ?: ALL_THEMES[0]
    }

    /** Call in Activity.onCreate() BEFORE super.onCreate(). */
    fun applyToActivity(context: Context) {
        if (context is android.app.Activity) {
            context.setTheme(getThemeStyleRes(context))
        }
    }
}
