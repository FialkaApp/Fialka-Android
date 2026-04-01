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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.FragmentSettingsAppearanceBinding
import com.fialkaapp.fialka.util.ThemeManager

/**
 * Appearance settings with inline theme selection.
 */
class AppearanceFragment : Fragment() {

    private var _binding: FragmentSettingsAppearanceBinding? = null
    private val binding get() = _binding!!
    private lateinit var themeAdapter: ThemeAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsAppearanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        setupThemeList()
        updateCurrentTheme()
    }

    override fun onResume() {
        super.onResume()
        updateCurrentTheme()
        if (::themeAdapter.isInitialized) {
            themeAdapter.notifyDataSetChanged()
        }
    }

    private fun setupThemeList() {
        themeAdapter = ThemeAdapter(requireContext(), ThemeManager.ALL_THEMES) { themeId ->
            val currentTheme = ThemeManager.getTheme(requireContext())
            if (themeId != currentTheme) {
                ThemeManager.setTheme(requireContext(), themeId)
                requireActivity().recreate()
            }
        }

        binding.rvThemes.layoutManager = LinearLayoutManager(requireContext())
        binding.rvThemes.adapter = themeAdapter
    }

    private fun updateCurrentTheme() {
        val themeInfo = ThemeManager.getThemeInfo(ThemeManager.getTheme(requireContext()))
        binding.tvCurrentTheme.text = getString(themeInfo.nameRes)
        binding.tvCurrentThemeSummary.text = getThemeSummary(themeInfo.id)
        binding.themePreviewBg.setBackgroundColor(themeInfo.previewBg)
        binding.themePreviewAccent.setBackgroundColor(themeInfo.previewAccent)
    }

    private fun getThemeSummary(themeId: Int): String {
        val summaryRes = when (themeId) {
            ThemeManager.THEME_MIDNIGHT -> R.string.theme_midnight_summary
            ThemeManager.THEME_HACKER -> R.string.theme_hacker_summary
            ThemeManager.THEME_PHANTOM -> R.string.theme_phantom_summary
            ThemeManager.THEME_AURORA -> R.string.theme_aurora_summary
            ThemeManager.THEME_DAYLIGHT -> R.string.theme_daylight_summary
            else -> R.string.theme_phantom_summary
        }
        return getString(summaryRes)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
