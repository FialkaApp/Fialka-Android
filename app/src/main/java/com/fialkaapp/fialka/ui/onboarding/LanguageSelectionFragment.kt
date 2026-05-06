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
package com.fialkaapp.fialka.ui.onboarding

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.databinding.FragmentLanguageSelectionBinding
import com.fialkaapp.fialka.util.LocaleHelper

/**
 * Language selection screen — shown on first launch, before terms consent.
 * Back navigation is blocked; user must choose a language to proceed.
 * Calling LocaleHelper.setLocale() triggers Activity recreation via
 * AppCompatDelegate.setApplicationLocales(), which re-runs TorBootstrapFragment.
 * TorBootstrap will then find isLanguageSelected() == true and proceed normally.
 */
class LanguageSelectionFragment : Fragment() {

    private var _binding: FragmentLanguageSelectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLanguageSelectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onStart() {
        super.onStart()
        // After Activity recreation (triggered by setApplicationLocales), the NavController
        // restores the backstack and lands here again. If language is already saved, pop
        // back to TorBootstrapFragment which will now pass the isLanguageSelected() check.
        if (LocaleHelper.isLanguageSelected(requireContext())) {
            findNavController().popBackStack()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Block back navigation — user must choose a language to proceed
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { /* intentionally blocked */ }
            }
        )

        binding.cardFrench.setOnClickListener {
            selectLanguage("fr")
        }

        binding.cardEnglish.setOnClickListener {
            selectLanguage("en")
        }
    }

    private fun selectLanguage(code: String) {
        // Show check icon on selected card for brief visual feedback
        if (code == "fr") {
            binding.checkFrench.visibility = View.VISIBLE
            binding.checkEnglish.visibility = View.GONE
        } else {
            binding.checkEnglish.visibility = View.VISIBLE
            binding.checkFrench.visibility = View.GONE
        }

        // Save the locale and apply it — this triggers Activity.recreate() automatically
        // via AppCompatDelegate.setApplicationLocales(). TorBootstrapFragment will
        // then find isLanguageSelected() == true and proceed to terms consent.
        binding.root.postDelayed({
            LocaleHelper.setLocale(requireContext(), code)
        }, 200L) // small delay for visual feedback
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
