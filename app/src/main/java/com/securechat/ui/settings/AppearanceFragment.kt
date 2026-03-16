package com.securechat.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.securechat.databinding.FragmentSettingsAppearanceBinding
import com.securechat.util.ThemeManager

class AppearanceFragment : Fragment() {

    private var _binding: FragmentSettingsAppearanceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsAppearanceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        val current = ThemeManager.getTheme(requireContext())
        updateRadio(current)

        binding.rowThemeSystem.setOnClickListener { selectTheme(ThemeManager.THEME_SYSTEM) }
        binding.rowThemeLight.setOnClickListener { selectTheme(ThemeManager.THEME_LIGHT) }
        binding.rowThemeDark.setOnClickListener { selectTheme(ThemeManager.THEME_DARK) }
    }

    private fun selectTheme(theme: Int) {
        updateRadio(theme)
        ThemeManager.setTheme(requireContext(), theme)
    }

    private fun updateRadio(selected: Int) {
        binding.radioSystem.isChecked = selected == ThemeManager.THEME_SYSTEM
        binding.radioLight.isChecked = selected == ThemeManager.THEME_LIGHT
        binding.radioDark.isChecked = selected == ThemeManager.THEME_DARK
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
