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

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.ItemSettingSimpleBinding
import com.fialkaapp.fialka.databinding.ItemSettingSwitchBinding
import com.fialkaapp.fialka.databinding.ItemSettingsGroupBinding

/**
 * Adapter for the main settings list with grouped items.
 */
class SettingsAdapter(
    private val context: Context,
    private val onSettingClicked: (SettingItem) -> Unit,
    private val onSwitchToggled: (SettingItem, Boolean) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var settings = listOf<SettingItem>()
    private var groupedSettings = listOf<GroupedSettings>()

    companion object {
        private const val TYPE_GROUP = 0
        private const val TYPE_SIMPLE = 1
        private const val TYPE_SWITCH = 2
    }

    data class GroupedSettings(
        val category: String,
        val groupTitle: String,
        val items: List<SettingItem>
    )

    fun submitList(newSettings: List<SettingItem>) {
        settings = newSettings
        groupSettings()
        notifyDataSetChanged()
    }

    private fun groupSettings() {
        groupedSettings = settings
            .groupBy { it.category }
            .map { (category, items) ->
                GroupedSettings(
                    category = category,
                    groupTitle = getCategoryTitle(category),
                    items = items.sortedBy { it.title }
                )
            }
            .sortedBy { getCategoryOrder(it.category) }
    }

    private fun getCategoryTitle(category: String): String {
        return when (category) {
            SettingsViewModel.CATEGORY_APPEARANCE -> context.getString(R.string.settings_group_appearance)
            SettingsViewModel.CATEGORY_NOTIFICATIONS -> context.getString(R.string.settings_group_notifications)
            SettingsViewModel.CATEGORY_PRIVACY -> context.getString(R.string.settings_group_privacy)
            SettingsViewModel.CATEGORY_SECURITY -> context.getString(R.string.settings_group_security)
            SettingsViewModel.CATEGORY_NETWORK -> context.getString(R.string.settings_group_network)
            SettingsViewModel.CATEGORY_ABOUT -> context.getString(R.string.settings_group_about)
            else -> category
        }
    }

    private fun getCategoryOrder(category: String): Int {
        return when (category) {
            SettingsViewModel.CATEGORY_APPEARANCE -> 0
            SettingsViewModel.CATEGORY_NOTIFICATIONS -> 1
            SettingsViewModel.CATEGORY_PRIVACY -> 2
            SettingsViewModel.CATEGORY_SECURITY -> 3
            SettingsViewModel.CATEGORY_NETWORK -> 4
            SettingsViewModel.CATEGORY_ABOUT -> 5
            else -> 99
        }
    }

    override fun getItemCount(): Int {
        var count = 0
        groupedSettings.forEach { group ->
            count++
            count += group.items.size
        }
        return count
    }

    override fun getItemViewType(position: Int): Int {
        var currentPos = 0
        for (group in groupedSettings) {
            if (position == currentPos) return TYPE_GROUP
            currentPos++
            if (position in currentPos until currentPos + group.items.size) {
                val item = group.items[position - currentPos]
                return if (item.type == SettingType.SWITCH) TYPE_SWITCH else TYPE_SIMPLE
            }
            currentPos += group.items.size
        }
        return TYPE_SIMPLE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_GROUP -> GroupViewHolder(ItemSettingsGroupBinding.inflate(inflater, parent, false))
            TYPE_SWITCH -> SwitchViewHolder(ItemSettingSwitchBinding.inflate(inflater, parent, false))
            else -> SimpleViewHolder(ItemSettingSimpleBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is GroupViewHolder -> bindGroup(holder, position)
            is SimpleViewHolder -> bindSimple(holder, position)
            is SwitchViewHolder -> bindSwitch(holder, position)
        }
    }

    private fun getItemAt(position: Int): SettingItem? {
        var currentPos = 0
        for (group in groupedSettings) {
            currentPos++
            if (position in currentPos until currentPos + group.items.size) {
                return group.items[position - currentPos]
            }
            currentPos += group.items.size
        }
        return null
    }

    private fun getGroupAt(position: Int): String? {
        var currentPos = 0
        for (group in groupedSettings) {
            if (position == currentPos) return group.groupTitle
            currentPos++
            currentPos += group.items.size
        }
        return null
    }

    private fun bindGroup(holder: GroupViewHolder, position: Int) {
        holder.binding.tvGroupTitle.text = getGroupAt(position)
    }

    private fun bindSimple(holder: SimpleViewHolder, position: Int) {
        val item = getItemAt(position) ?: return
        holder.binding.ivIcon.setImageResource(item.iconRes)
        holder.binding.tvTitle.text = item.title
        holder.binding.tvSummary.text = item.summary
        holder.binding.tvSummary.visibility = if (item.summary.isBlank()) View.GONE else View.VISIBLE
        holder.binding.ivChevron.visibility = View.VISIBLE
        holder.itemView.setOnClickListener { onSettingClicked(item) }
    }

    private fun bindSwitch(holder: SwitchViewHolder, position: Int) {
        val item = getItemAt(position) ?: return
        holder.binding.ivIcon.setImageResource(item.iconRes)
        holder.binding.tvTitle.text = item.title

        val prefs = context.getSharedPreferences("fialka_settings", Context.MODE_PRIVATE)
        val isChecked = when (item.key) {
            "dummy_traffic_enabled" -> prefs.getBoolean("dummy_traffic_enabled", false)
            "screenshot_protection" -> prefs.getBoolean("screenshot_protection", true)
            else -> false
        }

        holder.binding.switchValue.setOnCheckedChangeListener(null)
        holder.binding.switchValue.isChecked = isChecked
        holder.binding.switchValue.setOnCheckedChangeListener { _, checked ->
            onSwitchToggled(item, checked)
        }

        holder.binding.tvSummary.text = if (isChecked) "Active" else item.summary
        holder.itemView.setOnClickListener { holder.binding.switchValue.toggle() }
    }

    inner class GroupViewHolder(val binding: ItemSettingsGroupBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class SimpleViewHolder(val binding: ItemSettingSimpleBinding) :
        RecyclerView.ViewHolder(binding.root)

    inner class SwitchViewHolder(val binding: ItemSettingSwitchBinding) :
        RecyclerView.ViewHolder(binding.root)
}
