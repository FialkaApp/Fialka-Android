package com.securechat.util

import android.content.Context

/**
 * Manages ephemeral message settings (global default + per-conversation).
 * Duration values in milliseconds. 0 = disabled.
 */
object EphemeralManager {

    private const val PREFS_NAME = "securechat_settings"
    private const val KEY_DEFAULT_EPHEMERAL = "default_ephemeral_duration"

    /** All available durations in milliseconds. */
    val DURATION_OPTIONS = longArrayOf(
        0L,                     // Off
        30_000L,                // 30 seconds
        300_000L,               // 5 minutes
        3_600_000L,             // 1 hour
        21_600_000L,            // 6 hours
        43_200_000L,            // 12 hours
        86_400_000L,            // 24 hours
        604_800_000L,           // 7 days
        1_209_600_000L,         // 2 weeks
        2_592_000_000L          // 1 month (~30 days)
    )

    val DURATION_LABELS = arrayOf(
        "Désactivé",
        "30 secondes",
        "5 minutes",
        "1 heure",
        "6 heures",
        "12 heures",
        "24 heures",
        "7 jours",
        "2 semaines",
        "1 mois"
    )

    /** Icon shown in the chat toolbar when ephemeral is enabled. */
    fun getLabelForDuration(durationMs: Long): String {
        val idx = DURATION_OPTIONS.indexOf(durationMs)
        if (idx >= 0) return DURATION_LABELS[idx]
        return formatCustomDuration(durationMs)
    }

    /** Get the global default ephemeral duration (0 = off). */
    fun getDefaultDuration(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_DEFAULT_EPHEMERAL, 0L)
    }

    /** Set the global default ephemeral duration. */
    fun setDefaultDuration(context: Context, durationMs: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putLong(KEY_DEFAULT_EPHEMERAL, durationMs).apply()
    }

    /** Short label for chat toolbar / badge. */
    fun getShortLabel(durationMs: Long): String {
        return when (durationMs) {
            0L -> ""
            30_000L -> "30s"
            300_000L -> "5m"
            3_600_000L -> "1h"
            21_600_000L -> "6h"
            43_200_000L -> "12h"
            86_400_000L -> "24h"
            604_800_000L -> "7j"
            1_209_600_000L -> "2sem"
            2_592_000_000L -> "1mois"
            else -> formatCustomShort(durationMs)
        }
    }

    private fun formatCustomDuration(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "$days jour${if (days > 1) "s" else ""}"
            hours > 0 -> "$hours heure${if (hours > 1) "s" else ""}"
            minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""}"
            else -> "$seconds seconde${if (seconds > 1) "s" else ""}"
        }
    }

    private fun formatCustomShort(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days > 0 -> "${days}j"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "${seconds}s"
        }
    }
}
