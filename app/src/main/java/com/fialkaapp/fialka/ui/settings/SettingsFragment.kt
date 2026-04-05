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
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Main Settings screen with search, category filters and a clean settings list.
 */
class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SettingsViewModel
    private lateinit var adapter: SettingsAdapter
    private var filtersExpanded = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupToolbar()
        setupViewModel()
        setupFiltersPanel()
        setupSearch()
        setupChips()
        setupRecyclerView()
        observeSettings()
        updateFilterSummary()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
    }

    private fun setupViewModel() {
        viewModel = SettingsViewModel(requireActivity().application)
    }

    private fun setupFiltersPanel() {
        val toggleFilters = { setFiltersExpanded(!filtersExpanded) }
        binding.searchFiltersHeader.setOnClickListener { toggleFilters() }
        setFiltersExpanded(false)
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.setSearchQuery(s?.toString() ?: "")
                updateFilterSummary()
            }
        })
    }

    private fun setupChips() {
        binding.chipAll.isChecked = true
        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: View.NO_ID
            val category = when (checkedId) {
                R.id.chipAll -> SettingsViewModel.CATEGORY_ALL
                R.id.chipAppearance -> SettingsViewModel.CATEGORY_APPEARANCE
                R.id.chipNotifications -> SettingsViewModel.CATEGORY_NOTIFICATIONS
                R.id.chipPrivacy -> SettingsViewModel.CATEGORY_PRIVACY
                R.id.chipSecurity -> SettingsViewModel.CATEGORY_SECURITY
                R.id.chipNetwork -> SettingsViewModel.CATEGORY_NETWORK
                R.id.chipAbout -> SettingsViewModel.CATEGORY_ABOUT
                else -> SettingsViewModel.CATEGORY_ALL
            }
            viewModel.setCategory(category)
            updateFilterSummary()
        }
    }

    private fun setupRecyclerView() {
        adapter = SettingsAdapter(
            context = requireContext(),
            onSettingClicked = { item -> handleSettingClick(item) },
            onSwitchToggled = { item, isChecked -> handleSwitchToggle(item, isChecked) }
        )
        binding.rvSettings.layoutManager = LinearLayoutManager(requireContext())
        binding.rvSettings.adapter = adapter
    }

    private fun observeSettings() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.filteredSettings.collect { settings ->
                adapter.submitList(settings)
                val showEmpty = settings.isEmpty() &&
                    (viewModel.searchQuery.value.isNotBlank()
                        || viewModel.selectedCategory.value != SettingsViewModel.CATEGORY_ALL)
                binding.emptyState.visibility = if (showEmpty) View.VISIBLE else View.GONE
                binding.rvSettings.visibility = if (showEmpty) View.GONE else View.VISIBLE
            }
        }
    }

    private fun handleSettingClick(item: SettingItem) {
        when (item.type) {
            SettingType.NAVIGATE -> {
                when (item.destination) {
                    "appearanceFragment" -> navigateTo(R.id.action_settings_to_appearance)
                    "notificationsFragment" -> navigateTo(R.id.action_settings_to_notifications)
                    "securityFragment" -> navigateTo(R.id.action_settings_to_security)
                    "ephemeralSettingsFragment" -> navigateTo(R.id.action_settings_to_ephemeral)
                    "mailboxSettingsFragment" -> navigateTo(R.id.action_settings_to_mailboxSettings)
                    else -> getNavigationAction(item.destination)?.let(::navigateTo)
                }
            }

            SettingType.ACTION -> handleActionItem(item.id)
            SettingType.INFO -> showAppInfoDialog()
            SettingType.SWITCH -> Unit
        }
    }

    private fun handleActionItem(itemId: String) {
        when (itemId) {
            "legal" -> showLegalDialog()
            "licenses" -> showLicensesDialog()
            "storage" -> showSimpleDialog(
                title = getString(R.string.setting_storage_management),
                message = getString(R.string.setting_storage_management_summary)
            )

            "backup_export" -> showSimpleDialog(
                title = getString(R.string.setting_backup_export),
                message = getString(R.string.setting_backup_export_summary)
            )

            "import_backup" -> findNavController().navigate(R.id.restoreFragment)
            "tor_advanced" -> showSimpleDialog(
                title = getString(R.string.setting_tor_advanced),
                message = getString(R.string.setting_tor_advanced_summary)
            )
        }
    }

    private fun getNavigationAction(destination: String?): Int? {
        return when (destination) {
            "appearanceFragment" -> R.id.action_settings_to_appearance
            "notificationsFragment" -> R.id.action_settings_to_notifications
            "securityFragment" -> R.id.action_settings_to_security
            "ephemeralSettingsFragment" -> R.id.action_settings_to_ephemeral
            "privacyFragment" -> R.id.action_settings_to_privacy
            "mailboxSettingsFragment" -> R.id.action_settings_to_mailboxSettings
            else -> null
        }
    }

    private fun handleSwitchToggle(item: SettingItem, isChecked: Boolean) {
        val prefs = requireContext().getSharedPreferences("fialka_settings", Context.MODE_PRIVATE)
        val editor = prefs.edit()
        when (item.key) {
            "dummy_traffic_enabled" -> editor.putBoolean("dummy_traffic_enabled", isChecked)
            "screenshot_protection" -> {
                editor.putBoolean("screenshot_protection", isChecked)
                if (isChecked) {
                    requireActivity().window.setFlags(
                        android.view.WindowManager.LayoutParams.FLAG_SECURE,
                        android.view.WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    requireActivity().window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        editor.apply()
        adapter.notifyDataSetChanged()
    }

    private fun showAppInfoDialog() {
        val info = try {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
        } catch (_: Exception) { null }
        val version = info?.versionName ?: "?"
        val build = info?.longVersionCode ?: 0
        val content = buildString {
            appendLine("Version : $version")
            appendLine("Build   : $build")
            appendLine()
            appendLine("Identite crypto : Kyber-1024 + Dilithium-5")
            appendLine("Chiffrement     : AES-256-GCM + ChaCha20-Poly1305")
            appendLine("Transport       : Tor (SOCKS5)")
            appendLine()
            appendLine("Licence         : GPLv3")
            appendLine("ECCN            : 5D002.C.1 (open source — exempt)")
            appendLine("Controles UE    : Reglement UE 2021/821")
            appendLine()
            appendLine("Code source : github.com/FialkaApp/Fialka-Android")
        }
        showScrollableDialog(getString(R.string.settings_app_info_dialog_title), content)
    }

    private fun showLegalDialog() {
        val content = buildString {
            appendLine("TERMES D'UTILISATION")
            appendLine("Version 4 — Applicable depuis le 03/04/2026")
            appendLine()
            appendLine("1. OUTIL — PAS UN SERVICE")
            appendLine("Fialka est un outil logiciel distribue sous GPLv3. Les developpeurs n'operent aucune infrastructure, ne stockent aucune donnee et ne peuvent acceder a aucun message.")
            appendLine()
            appendLine("2. RESPONSABILITE DE L'UTILISATEUR")
            appendLine("Vous etes seul(e) et entierement responsable de toute utilisation. Les developpeurs declinent toute responsabilite, quelle que soit la nature de l'usage.")
            appendLine()
            appendLine("3. ELIGIBILITE")
            appendLine("Vous devez avoir au moins 16 ans (ou l'age minimum legal de consentement numerique dans votre pays).")
            appendLine()
            appendLine("4. ABSENCE DE GARANTIE")
            appendLine("Fialka est fourni EN L'ETAT, sans garantie. L'implementation cryptographique n'a pas ete auditee par un cabinet tiers.")
            appendLine()
            appendLine("5. CONTROLE DES EXPORTATIONS")
            appendLine("Ce logiciel contient de la cryptographie soumise aux reglementations Wassenaar, ECCN 5D002.C.1 (US BIS) et UE 2021/821. En tant que logiciel open source GPLv3, il est exempte en France et dans l'UE. Verifiez les lois de votre pays.")
            appendLine()
            appendLine("POLITIQUE DE CONFIDENTIALITE")
            appendLine("Fialka ne collecte, ne stocke ni ne transmet aucune donnee personnelle. Toutes les donnees restent sur votre appareil. Aucune telemetrie, aucun compte, aucun serveur central.")
            appendLine()
            appendLine("Texte complet : github.com/FialkaApp/Fialka-Android/blob/main/TERMS.md")
        }
        showScrollableDialog(getString(R.string.settings_legal_dialog_title), content)
    }

    private fun showLicensesDialog() {
        val content = buildString {
            appendLine("LICENCE PRINCIPALE")
            appendLine("Fialka — GNU General Public License v3 (GPLv3)")
            appendLine("Copyright (C) 2024-2026 DevBot667")
            appendLine("https://www.gnu.org/licenses/gpl-3.0.html")
            appendLine()
            appendLine("COMPOSANTS TIERS")
            appendLine()
            appendLine("Tor — The Tor Project")
            appendLine("Licence : BSD modifiee")
            appendLine("https://www.torproject.org")
            appendLine()
            appendLine("Fialka-Core (cryptographie Rust)")
            appendLine("Licence : GPLv3")
            appendLine("https://github.com/FialkaApp/fialka-core")
            appendLine()
            appendLine("AndroidX / Jetpack")
            appendLine("Licence : Apache License 2.0")
            appendLine("https://developer.android.com/jetpack")
            appendLine()
            appendLine("Material Components for Android")
            appendLine("Licence : Apache License 2.0")
            appendLine("https://github.com/material-components/material-components-android")
            appendLine()
            appendLine("Kotlin Standard Library")
            appendLine("Licence : Apache License 2.0")
            appendLine("https://kotlinlang.org")
            appendLine()
            appendLine("SQLCipher for Android")
            appendLine("Licence : BSD")
            appendLine("https://www.zetetic.net/sqlcipher/")
            appendLine()
            appendLine("Texte complet : github.com/FialkaApp/Fialka-Android/blob/main/LICENSE")
        }
        showScrollableDialog(getString(R.string.settings_licenses_dialog_title), content)
    }

    private fun showScrollableDialog(title: String, content: String) {
        val scrollView = android.widget.ScrollView(requireContext())
        val textView = android.widget.TextView(requireContext()).apply {
            text = content
            textSize = 13f
            setPadding(48, 24, 48, 24)
            setTextColor(androidx.core.content.ContextCompat.getColor(
                requireContext(), android.R.color.darker_gray
            ))
        }
        scrollView.addView(textView)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setView(scrollView)
            .setPositiveButton(R.string.settings_action_ok, null)
            .show()
    }

    private fun showSimpleDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.settings_action_ok, null)
            .show()
    }

    private fun setFiltersExpanded(expanded: Boolean) {
        filtersExpanded = expanded
        binding.searchFiltersPanel.visibility = if (expanded) View.VISIBLE else View.GONE
        binding.ivFilterChevron.animate().rotation(if (expanded) 90f else 0f).setDuration(180).start()
    }

    private fun updateFilterSummary() {
        val query = binding.etSearch.text?.toString()?.trim().orEmpty()
        val category = viewModel.selectedCategory.value
        val categoryLabel = getCategoryLabel(category)

        binding.tvFilterSummary.text = when {
            query.isBlank() && category == SettingsViewModel.CATEGORY_ALL -> {
                getString(R.string.settings_filters_default_summary)
            }

            query.isNotBlank() && category != SettingsViewModel.CATEGORY_ALL -> {
                getString(R.string.settings_filters_active_both, query, categoryLabel)
            }

            query.isNotBlank() -> {
                getString(R.string.settings_filters_active_query, query)
            }

            else -> categoryLabel
        }
    }

    private fun getCategoryLabel(category: String): String {
        return when (category) {
            SettingsViewModel.CATEGORY_APPEARANCE -> getString(R.string.settings_appearance_category)
            SettingsViewModel.CATEGORY_NOTIFICATIONS -> getString(R.string.settings_notifications_category)
            SettingsViewModel.CATEGORY_PRIVACY -> getString(R.string.settings_privacy_category)
            SettingsViewModel.CATEGORY_SECURITY -> getString(R.string.settings_security_category)
            SettingsViewModel.CATEGORY_NETWORK -> getString(R.string.settings_network_category)
            SettingsViewModel.CATEGORY_ABOUT -> getString(R.string.settings_about_category)
            else -> getString(R.string.settings_all)
        }
    }

    private fun navigateTo(actionId: Int) {
        findNavController().navigate(actionId)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
