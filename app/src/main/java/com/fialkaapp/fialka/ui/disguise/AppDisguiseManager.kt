package com.fialkaapp.fialka.ui.disguise

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.fialkaapp.fialka.R

/**
 * Manages the app disguise feature.
 *
 * Mechanism: Android activity-alias entries in the manifest share the same
 * targetActivity (.ui.SplashActivity). Exactly one alias (including the real
 * launcher entry) is ENABLED at a time. The launcher reflects the change on the
 * next time the app is backgrounded.
 *
 * The active disguise ID is persisted in SharedPreferences so it can be
 * restored across process restarts (e.g. after a force-stop or reboot).
 */
object AppDisguiseManager {

    private const val PREFS_NAME = "disguise_prefs"
    private const val KEY_ACTIVE = "active_disguise"
    private const val KEY_COVER_ENABLED = "cover_mode_enabled"
    private const val KEY_COVER_SECRET = "cover_secret"
    private const val DEFAULT_DISGUISE = "fialka"

    /**
     * Each disguise maps to an activity-alias component name, a display label
     * (French), and a preview drawable resource.
     */
    enum class Disguise(
        /** Matches android:name in the manifest (short form without package). */
        val aliasShortName: String,
        /** Resource ID for the display label shown in the selection UI. */
        @StringRes val labelRes: Int,
        /** Drawable resource used as preview in the selection grid. */
        @DrawableRes val previewRes: Int
    ) {
        FIALKA(
            aliasShortName = ".ui.disguise.DisguiseFialkaAlias",
            labelRes = R.string.app_name,
            previewRes = R.mipmap.ic_launcher
        ),
        CALCULATOR(
            aliasShortName = ".ui.disguise.DisguiseCalculatorAlias",
            labelRes = R.string.disguise_label_calculator,
            previewRes = R.drawable.ic_disguise_calculator
        ),
        WEATHER(
            aliasShortName = ".ui.disguise.DisguiseWeatherAlias",
            labelRes = R.string.disguise_label_weather,
            previewRes = R.drawable.ic_disguise_weather
        ),
        NOTES(
            aliasShortName = ".ui.disguise.DisguiseNotesAlias",
            labelRes = R.string.disguise_label_notes,
            previewRes = R.drawable.ic_disguise_notes
        ),
        CLOCK(
            aliasShortName = ".ui.disguise.DisguiseClockAlias",
            labelRes = R.string.disguise_label_clock,
            previewRes = R.drawable.ic_disguise_clock
        );
    }

    /** Returns the currently active disguise (reads from prefs). */
    fun getActive(context: Context): Disguise {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ACTIVE, DEFAULT_DISGUISE) ?: DEFAULT_DISGUISE
        return Disguise.entries.firstOrNull { it.name == saved } ?: Disguise.FIALKA
    }

    /**
     * Switches to [disguise]. Disables all aliases except the target one.
     *
     * All 5 entries are activity-aliases — we can safely enable/disable any of them.
     * The real SplashActivity has no launcher intent-filter, so it never appears
     * directly in the launcher.
     *
     * Call this then kill the process so the launcher picks up the change immediately.
     */
    fun apply(context: Context, disguise: Disguise) {
        val pm = context.packageManager
        val pkg = context.packageName

        Disguise.entries.forEach { d ->
            val state = if (d == disguise) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val component = ComponentName(pkg, pkg + d.aliasShortName)
            pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
        }

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTIVE, disguise.name)
            .apply()
    }

    // ── Cover mode (Calculator real cover) ───────────────────────────────────

    /** True if the calculator cover mode is active (app opens as a real calculator). */
    fun isCoverModeEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_COVER_ENABLED, false)

    /** The secret number string that unlocks Fialka from the cover calculator. */
    fun getCoverSecret(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COVER_SECRET, "") ?: ""

    /**
     * Save cover mode settings.
     * @param enabled  Whether to show the calculator cover on launch.
     * @param secret   The number the user must display to unlock Fialka.
     *                 Ignored (but preserved) when [enabled] is false.
     */
    fun setCoverMode(context: Context, enabled: Boolean, secret: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COVER_ENABLED, enabled)
            .putString(KEY_COVER_SECRET, secret)
            .apply()
    }
}
