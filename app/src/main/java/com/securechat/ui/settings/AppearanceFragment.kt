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
package com.securechat.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.card.MaterialCardView
import com.securechat.R
import com.securechat.util.ThemeManager

class AppearanceFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_settings_appearance, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { findNavController().navigateUp() }

        val grid = view.findViewById<LinearLayout>(R.id.themeGrid)
        val current = ThemeManager.getTheme(requireContext())

        for (info in ThemeManager.ALL_THEMES) {
            val card = layoutInflater.inflate(R.layout.item_theme_card, grid, false) as MaterialCardView
            val preview = card.findViewById<View>(R.id.themePreview)
            val accent = card.findViewById<View>(R.id.themeAccent)
            val name = card.findViewById<TextView>(R.id.themeName)
            val check = card.findViewById<View>(R.id.themeCheck)

            preview.setBackgroundColor(info.previewBg)
            accent.setBackgroundColor(info.previewAccent)
            name.text = getString(info.nameRes)
            check.visibility = if (info.id == current) View.VISIBLE else View.GONE

            if (info.id == current) {
                card.strokeColor = info.previewAccent
                card.strokeWidth = 4
            } else {
                card.strokeColor = 0x33FFFFFF
                card.strokeWidth = 2
            }

            card.setOnClickListener {
                if (info.id != ThemeManager.getTheme(requireContext())) {
                    ThemeManager.setTheme(requireContext(), info.id)
                    requireActivity().recreate()
                }
            }

            grid.addView(card)
        }
    }
}
