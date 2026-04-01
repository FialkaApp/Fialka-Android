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
package com.fialkaapp.fialka.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.BottomSheetThemeSelectorBinding
import com.fialkaapp.fialka.databinding.ItemThemePreviewBinding
import com.fialkaapp.fialka.util.ThemeManager

/**
 * Bottom sheet for selecting app theme with live previews.
 */
class ThemeSelectorBottomSheet(
    private val onThemeSelected: () -> Unit
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetThemeSelectorBinding? = null
    private val binding get() = _binding!!

    override fun getTheme(): Int {
        return R.style.ThemeOverlay_Fialka_BottomSheet
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.attributes?.windowAnimations = R.style.BottomSheetAnimation
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetThemeSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val currentTheme = ThemeManager.getTheme(requireContext())
        val themes = ThemeManager.ALL_THEMES
        
        binding.rvThemes.adapter = ThemeAdapter(requireContext(), themes) { themeId ->
            if (themeId != currentTheme) {
                ThemeManager.setTheme(requireContext(), themeId)
                requireActivity().recreate()
                onThemeSelected()
            }
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun show(fragmentManager: FragmentManager, onThemeSelected: () -> Unit) {
            val bottomSheet = ThemeSelectorBottomSheet(onThemeSelected)
            bottomSheet.show(fragmentManager, "theme_selector")
        }
    }
}

/**
 * Adapter for theme preview items in bottom sheet.
 */
class ThemeAdapter(
    private val context: android.content.Context,
    private val themes: List<ThemeManager.ThemeInfo>,
    private val onThemeClick: (Int) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<ThemeAdapter.ThemeViewHolder>() {

    inner class ThemeViewHolder(
        val binding: ItemThemePreviewBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onThemeClick(themes[position].id)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ThemeViewHolder {
        val binding = ItemThemePreviewBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ThemeViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ThemeViewHolder, position: Int) {
        val theme = themes[position]
        val currentTheme = ThemeManager.getTheme(context)
        
        holder.binding.themePreviewBg.setBackgroundColor(theme.previewBg)
        holder.binding.themePreviewAccent.setBackgroundColor(theme.previewAccent)
        holder.binding.themeName.text = context.getString(theme.nameRes)
        holder.binding.themeSummary.text = getThemeSummary(theme.id)
        
        holder.binding.themeCheck.visibility = if (theme.id == currentTheme) {
            View.VISIBLE
        } else {
            View.GONE
        }
        
        if (theme.id == currentTheme) {
            holder.binding.root.strokeColor = theme.previewAccent
            holder.binding.root.strokeWidth = 2
        } else {
            holder.binding.root.strokeColor = 0x1FFFFFFF
            holder.binding.root.strokeWidth = 1
        }
    }

    override fun getItemCount(): Int = themes.size

    private fun getThemeSummary(themeId: Int): String {
        val summaryRes = when (themeId) {
            ThemeManager.THEME_MIDNIGHT -> R.string.theme_midnight_summary
            ThemeManager.THEME_HACKER -> R.string.theme_hacker_summary
            ThemeManager.THEME_PHANTOM -> R.string.theme_phantom_summary
            ThemeManager.THEME_AURORA -> R.string.theme_aurora_summary
            ThemeManager.THEME_DAYLIGHT -> R.string.theme_daylight_summary
            else -> R.string.theme_phantom_summary
        }
        return context.getString(summaryRes)
    }
}
