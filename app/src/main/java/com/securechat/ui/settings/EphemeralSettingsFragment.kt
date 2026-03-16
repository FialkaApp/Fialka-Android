package com.securechat.ui.settings

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.securechat.R
import com.securechat.databinding.FragmentSettingsEphemeralBinding
import com.securechat.util.EphemeralManager

class EphemeralSettingsFragment : Fragment() {

    private var _binding: FragmentSettingsEphemeralBinding? = null
    private val binding get() = _binding!!

    /** Map of preset duration -> (row, radio) pairs. */
    private lateinit var presetRows: List<Pair<View, RadioButton>>

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsEphemeralBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }

        presetRows = listOf(
            binding.rowOff to binding.radioOff,             // 0
            binding.row30s to binding.radio30s,             // 30_000
            binding.row5m to binding.radio5m,               // 300_000
            binding.row1h to binding.radio1h,               // 3_600_000
            binding.row24h to binding.radio24h,             // 86_400_000
            binding.row7d to binding.radio7d                // 604_800_000
        )

        val presetDurations = longArrayOf(0L, 30_000L, 300_000L, 3_600_000L, 86_400_000L, 604_800_000L)

        // Set click listeners on preset rows
        for ((index, pair) in presetRows.withIndex()) {
            val (row, _) = pair
            row.setOnClickListener { selectDuration(presetDurations[index]) }
        }

        // Custom row
        binding.rowCustom.setOnClickListener { showCustomPicker() }

        // Load current value
        updateSelection(EphemeralManager.getDefaultDuration(requireContext()))
    }

    private fun selectDuration(durationMs: Long) {
        EphemeralManager.setDefaultDuration(requireContext(), durationMs)
        updateSelection(durationMs)
    }

    private fun updateSelection(currentDuration: Long) {
        val presetDurations = longArrayOf(0L, 30_000L, 300_000L, 3_600_000L, 86_400_000L, 604_800_000L)

        // Clear all radios
        for ((_, radio) in presetRows) radio.isChecked = false
        binding.radioCustom.isChecked = false

        val presetIdx = presetDurations.indexOf(currentDuration)
        if (presetIdx >= 0) {
            presetRows[presetIdx].second.isChecked = true
            binding.tvCustomValue.text = "Choisir une durée"
        } else {
            binding.radioCustom.isChecked = true
            binding.tvCustomValue.text = EphemeralManager.getLabelForDuration(currentDuration)
        }
    }

    private fun showCustomPicker() {
        val input = EditText(requireContext()).apply {
            hint = "Durée en heures (ex: 48)"
            inputType = android.text.InputType.TYPE_CLASS_NUMBER
            setPadding(48, 32, 48, 32)
        }

        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 16, 48, 0)
            addView(input)
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Durée personnalisée")
            .setMessage("Entrez la durée en heures :")
            .setView(container)
            .setPositiveButton("Valider") { _, _ ->
                val hours = input.text.toString().toLongOrNull()
                if (hours != null && hours > 0) {
                    val durationMs = hours * 3_600_000L
                    selectDuration(durationMs)
                } else {
                    Toast.makeText(requireContext(), "Valeur invalide", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
