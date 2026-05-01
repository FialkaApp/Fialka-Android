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
package com.fialkaapp.fialka.ui.disguise

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.FragmentAppDisguiseBinding
import com.fialkaapp.fialka.ui.cover.CoverSecretSetupBottomSheet
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlin.system.exitProcess

/**
 * Selection screen for the app disguise (launcher icon + label).
 *
 * Shows one card per available [AppDisguiseManager.Disguise]. A checkmark
 * indicates the active one. Tapping a different one shows a confirmation
 * dialog before applying.
 */
class AppDisguiseFragment : Fragment() {

    private var _binding: FragmentAppDisguiseBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppDisguiseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        refreshChecks()
        refreshCoverOptions()

        binding.cardDisguiseFialka.setOnClickListener { onDisguiseSelected(AppDisguiseManager.Disguise.FIALKA) }
        binding.cardDisguiseCalculator.setOnClickListener { onDisguiseSelected(AppDisguiseManager.Disguise.CALCULATOR) }
        binding.cardDisguiseWeather.setOnClickListener { onDisguiseSelected(AppDisguiseManager.Disguise.WEATHER) }
        binding.cardDisguiseNotes.setOnClickListener { onDisguiseSelected(AppDisguiseManager.Disguise.NOTES) }
        binding.cardDisguiseClock.setOnClickListener { onDisguiseSelected(AppDisguiseManager.Disguise.CLOCK) }

        setupCoverOptionsListeners()
    }

    // ── Cover options section ─────────────────────────────────────────────────

    private fun refreshCoverOptions() {
        val isCalc = AppDisguiseManager.getActive(requireContext()) == AppDisguiseManager.Disguise.CALCULATOR
        binding.cardCoverOptions.visibility = if (isCalc) View.VISIBLE else View.GONE
        if (!isCalc) return

        val coverEnabled = AppDisguiseManager.isCoverModeEnabled(requireContext())
        binding.switchCoverMode.isChecked = coverEnabled
        binding.layoutSecretRow.visibility = if (coverEnabled) View.VISIBLE else View.GONE

        val secret = AppDisguiseManager.getCoverSecret(requireContext())
        binding.tvCurrentSecret.text = if (secret.isEmpty())
            getString(R.string.cover_not_configured)
        else
            secret.take(22) + if (secret.length > 22) "\u2026" else ""
    }

    private fun setupCoverOptionsListeners() {
        binding.switchCoverMode.setOnCheckedChangeListener { _, checked ->
            binding.layoutSecretRow.visibility = if (checked) View.VISIBLE else View.GONE
            if (!checked) {
                AppDisguiseManager.setCoverMode(
                    requireContext(), false,
                    AppDisguiseManager.getCoverSecret(requireContext())
                )
            }
        }

        binding.btnEditSecret.setOnClickListener {
            CoverSecretSetupBottomSheet().apply {
                onSecretDefined = { secret ->
                    AppDisguiseManager.setCoverMode(requireContext(), true, secret)
                    refreshCoverOptions()
                    Snackbar.make(binding.root, getString(R.string.cover_saved), Snackbar.LENGTH_SHORT).show()
                }
            }.show(childFragmentManager, CoverSecretSetupBottomSheet.TAG)
        }
    }

    private fun onDisguiseSelected(disguise: AppDisguiseManager.Disguise) {
        val current = AppDisguiseManager.getActive(requireContext())
        if (disguise == current) return

        val label = getString(disguise.labelRes)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.disguise_apply_confirm_title))
            .setMessage(getString(R.string.disguise_apply_confirm_msg, label))
            .setPositiveButton(getString(R.string.disguise_apply_confirm_ok)) { _, _ ->
                // Si on quitte calculatrice, désactiver le cover mode
                if (disguise != AppDisguiseManager.Disguise.CALCULATOR) {
                    AppDisguiseManager.setCoverMode(
                        requireContext(), false,
                        AppDisguiseManager.getCoverSecret(requireContext())
                    )
                }
                AppDisguiseManager.apply(requireContext(), disguise)
                requireActivity().finishAffinity()
                Handler(Looper.getMainLooper()).postDelayed({ exitProcess(0) }, 300)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshChecks() {
        val active = AppDisguiseManager.getActive(requireContext())

        setCheck(binding.checkDisguiseFialka, AppDisguiseManager.Disguise.FIALKA == active)
        setCheck(binding.checkDisguiseCalculator, AppDisguiseManager.Disguise.CALCULATOR == active)
        setCheck(binding.checkDisguiseWeather, AppDisguiseManager.Disguise.WEATHER == active)
        setCheck(binding.checkDisguiseNotes, AppDisguiseManager.Disguise.NOTES == active)
        setCheck(binding.checkDisguiseClock, AppDisguiseManager.Disguise.CLOCK == active)
    }

    private fun setCheck(view: ImageView, visible: Boolean) {
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
