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
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.BottomSheetDurationSelectorBinding
import com.fialkaapp.fialka.databinding.ItemDurationBinding
import com.fialkaapp.fialka.util.AppLockManager
import com.fialkaapp.fialka.util.EphemeralManager

/**
 * Bottom sheet for selecting duration values (ephemeral messages, auto-lock).
 */
class DurationSelectorBottomSheet(
    private val context: Context,
    private val mode: Mode,
    private val currentValue: Long,
    private val onDurationSelected: (Long) -> Unit
) : BottomSheetDialogFragment() {

    enum class Mode {
        EPHEMERAL_MESSAGES,
        AUTO_LOCK
    }

    private var _binding: BottomSheetDurationSelectorBinding? = null
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
        _binding = BottomSheetDurationSelectorBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupTitleAndSubtitle()
        setupRecyclerView()
    }

    private fun setupTitleAndSubtitle() {
        when (mode) {
            Mode.EPHEMERAL_MESSAGES -> {
                binding.tvTitle.text = getString(R.string.setting_ephemeral_default)
                binding.tvSubtitle.text = getString(R.string.setting_ephemeral_default_summary)
            }
            Mode.AUTO_LOCK -> {
                binding.tvTitle.text = getString(R.string.setting_auto_lock)
                binding.tvSubtitle.text = getString(R.string.setting_auto_lock_summary)
            }
        }
    }

    private fun setupRecyclerView() {
        val durations = getAvailableDurations()
        val currentIndex = durations.indexOfFirst { it.first == currentValue }
        
        binding.rvDurations.adapter = DurationAdapter(
            durations = durations,
            selectedIndex = currentIndex.coerceAtLeast(0)
        ) { selectedDuration ->
            onDurationSelected(selectedDuration)
            dismiss()
        }
    }

    private fun getAvailableDurations(): List<Pair<Long, String>> {
        return when (mode) {
            Mode.EPHEMERAL_MESSAGES -> {
                EphemeralManager.DURATION_OPTIONS.map { duration ->
                    duration to EphemeralManager.getLabelForDuration(duration)
                }
            }
            Mode.AUTO_LOCK -> {
                AppLockManager.AUTO_LOCK_OPTIONS.map { delay ->
                    delay to AppLockManager.AUTO_LOCK_LABELS[
                        AppLockManager.AUTO_LOCK_OPTIONS.indexOf(delay)
                    ]
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun show(
            fragmentManager: FragmentManager,
            mode: Mode,
            currentValue: Long,
            onDurationSelected: (Long) -> Unit
        ) {
            val bottomSheet = DurationSelectorBottomSheet(
                context = fragmentManager.findFragmentById(R.id.nav_host_fragment)?.requireContext()!!,
                mode = mode,
                currentValue = currentValue,
                onDurationSelected = onDurationSelected
            )
            bottomSheet.show(fragmentManager, "duration_selector")
        }
    }
}

/**
 * Adapter for duration items in bottom sheet.
 */
class DurationAdapter(
    private val durations: List<Pair<Long, String>>,
    private val selectedIndex: Int,
    private val onDurationClick: (Long) -> Unit
) : androidx.recyclerview.widget.RecyclerView.Adapter<DurationAdapter.DurationViewHolder>() {

    inner class DurationViewHolder(
        val binding: ItemDurationBinding
    ) : androidx.recyclerview.widget.RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onDurationClick(durations[position].first)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DurationViewHolder {
        val binding = ItemDurationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DurationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DurationViewHolder, position: Int) {
        val (duration, label) = durations[position]
        holder.binding.tvDurationLabel.text = label
        
        // Show checkmark if selected
        val isSelected = position == selectedIndex
        holder.binding.ivCheck.visibility = if (isSelected) View.VISIBLE else View.GONE
    }

    override fun getItemCount(): Int = durations.size
}
