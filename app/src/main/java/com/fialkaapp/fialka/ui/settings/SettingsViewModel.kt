/*
 * Fialka â€” Post-quantum encrypted messenger
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
package com.fialkaapp.fialka.ui.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fialkaapp.fialka.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the main Settings screen with search and category filtering.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedCategory = MutableStateFlow(CATEGORY_ALL)
    val selectedCategory: StateFlow<String> = _selectedCategory

    val filteredSettings: StateFlow<List<SettingItem>> = combine(
        _searchQuery,
        _selectedCategory
    ) { query, category ->
        filterSettings(query, category)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategory(category: String) {
        _selectedCategory.value = category
    }

    private fun filterSettings(query: String, category: String): List<SettingItem> {
        val allSettings = getAllSettings()
        return allSettings.filter { setting ->
            val categoryMatch = category == CATEGORY_ALL || setting.category == category
            val searchMatch = query.isBlank()
                || setting.title.contains(query, ignoreCase = true)
                || setting.summary.contains(query, ignoreCase = true)
            categoryMatch && searchMatch
        }
    }

    private fun getAllSettings(): List<SettingItem> {
        val app = getApplication<Application>()
        return listOf(
            SettingItem(
                id = "theme",
                title = app.getString(R.string.settings_appearance_category),
                summary = app.getString(R.string.setting_theme_summary),
                iconRes = R.drawable.ic_palette,
                category = CATEGORY_APPEARANCE,
                type = SettingType.NAVIGATE,
                destination = "appearanceFragment"
            ),
            SettingItem(
                id = "notifications",
                title = app.getString(R.string.settings_notifications_category),
                summary = app.getString(R.string.setting_notifications_summary),
                iconRes = R.drawable.ic_notifications,
                category = CATEGORY_NOTIFICATIONS,
                type = SettingType.NAVIGATE,
                destination = "notificationsFragment"
            ),
            SettingItem(
                id = "ephemeral",
                title = app.getString(R.string.setting_ephemeral_title),
                summary = app.getString(R.string.setting_ephemeral_default_summary),
                iconRes = R.drawable.ic_oneshot,
                category = CATEGORY_PRIVACY,
                type = SettingType.NAVIGATE,
                destination = "ephemeralSettingsFragment"
            ),
            SettingItem(
                id = "dummy_traffic",
                title = app.getString(R.string.setting_dummy_traffic_clean),
                summary = app.getString(R.string.setting_dummy_traffic_clean_summary),
                iconRes = R.drawable.ic_shield,
                category = CATEGORY_PRIVACY,
                type = SettingType.SWITCH,
                key = "dummy_traffic_enabled"
            ),
            SettingItem(
                id = "screenshot_protection",
                title = app.getString(R.string.setting_screenshot_title),
                summary = app.getString(R.string.setting_screenshot_summary),
                iconRes = R.drawable.ic_lock,
                category = CATEGORY_PRIVACY,
                type = SettingType.SWITCH,
                key = "screenshot_protection"
            ),
            SettingItem(
                id = "security",
                title = app.getString(R.string.settings_security_category),
                summary = app.getString(R.string.setting_security_summary),
                iconRes = R.drawable.ic_lock,
                category = CATEGORY_SECURITY,
                type = SettingType.NAVIGATE,
                destination = "securityFragment"
            ),
            SettingItem(
                id = "mailbox",
                title = "Mailbox",
                summary = app.getString(R.string.setting_mailbox_summary),
                iconRes = R.drawable.ic_mailbox,
                category = CATEGORY_NETWORK,
                type = SettingType.NAVIGATE,
                destination = "mailboxSettingsFragment"
            ),
            SettingItem(
                id = "tor_advanced",
                title = app.getString(R.string.setting_tor_advanced_title_clean),
                summary = app.getString(R.string.setting_tor_advanced_summary_clean),
                iconRes = R.drawable.ic_info,
                category = CATEGORY_NETWORK,
                type = SettingType.ACTION
            ),
            SettingItem(
                id = "storage",
                title = app.getString(R.string.setting_storage_title_clean),
                summary = app.getString(R.string.setting_storage_summary_clean),
                iconRes = R.drawable.ic_delete,
                category = CATEGORY_ABOUT,
                type = SettingType.ACTION
            ),
            SettingItem(
                id = "backup_export",
                title = app.getString(R.string.setting_backup_export_title_clean),
                summary = app.getString(R.string.setting_backup_export_summary_clean),
                iconRes = R.drawable.ic_file,
                category = CATEGORY_ABOUT,
                type = SettingType.ACTION
            ),
            SettingItem(
                id = "import_backup",
                title = app.getString(R.string.setting_import_backup_title_clean),
                summary = app.getString(R.string.setting_import_backup_summary_clean),
                iconRes = R.drawable.ic_file,
                category = CATEGORY_ABOUT,
                type = SettingType.ACTION
            ),
            SettingItem(
                id = "legal",
                title = app.getString(R.string.settings_legal_title),
                summary = app.getString(R.string.settings_legal_subtitle),
                iconRes = R.drawable.ic_info,
                category = CATEGORY_ABOUT,
                type = SettingType.ACTION
            ),
            SettingItem(
                id = "licenses",
                title = app.getString(R.string.settings_licenses_title),
                summary = app.getString(R.string.settings_licenses_subtitle),
                iconRes = R.drawable.ic_info,
                category = CATEGORY_ABOUT,
                type = SettingType.ACTION
            ),
            SettingItem(
                id = "donation",
                title = app.getString(R.string.donation_settings_title),
                summary = app.getString(R.string.donation_settings_subtitle),
                iconRes = R.drawable.ic_monero,
                category = CATEGORY_ABOUT,
                type = SettingType.ACTION
            ),
            SettingItem(
                id = "version",
                title = app.getString(R.string.setting_version),
                summary = getVersionName(app),
                iconRes = R.drawable.ic_info,
                category = CATEGORY_ABOUT,
                type = SettingType.INFO
            )
        )
    }

    private fun getVersionName(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            val version = info.versionName ?: "1.0"
            "Version $version · build ${info.longVersionCode}"
        } catch (_: Exception) {
            "Version inconnue"
        }
    }

    companion object {
        const val CATEGORY_ALL = "all"
        const val CATEGORY_APPEARANCE = "appearance"
        const val CATEGORY_NOTIFICATIONS = "notifications"
        const val CATEGORY_PRIVACY = "privacy"
        const val CATEGORY_SECURITY = "security"
        const val CATEGORY_NETWORK = "network"
        const val CATEGORY_ABOUT = "about"
    }
}

enum class SettingType {
    NAVIGATE,
    SWITCH,
    ACTION,
    INFO
}

data class SettingItem(
    val id: String,
    val title: String,
    val summary: String,
    val iconRes: Int,
    val category: String,
    val type: SettingType,
    val destination: String? = null,
    val key: String? = null,
    val action: ((Context) -> Unit)? = null
)
