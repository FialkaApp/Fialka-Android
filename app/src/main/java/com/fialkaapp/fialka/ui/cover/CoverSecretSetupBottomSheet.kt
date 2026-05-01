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
package com.fialkaapp.fialka.ui.cover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.databinding.BottomSheetCoverSecretSetupBinding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.snackbar.Snackbar

/**
 * Two-step bottom sheet to define the cover calculator secret sequence.
 *
 * Step 1 — User presses buttons to define their sequence (e.g. "1337+584−33=").
 *           The sequence is displayed verbatim in the display as they type.
 *           Tapping "Suivant" moves to step 2.
 *
 * Step 2 — User re-enters the same sequence to confirm.
 *           If both match → [onSecretDefined] is called → sheet dismisses.
 *           If they don't match → error, back to step 1.
 *
 * Sequence format: digits 0-9, operators +−×÷, equals =, comma ,
 * No arithmetic is performed — we only record the raw key presses.
 */
class CoverSecretSetupBottomSheet : BottomSheetDialogFragment() {

    /** Called when the user successfully defines and confirms their sequence. */
    var onSecretDefined: ((secret: String) -> Unit)? = null

    private var _binding: BottomSheetCoverSecretSetupBinding? = null
    private val binding get() = _binding!!

    private enum class Step { ENTER, CONFIRM }
    private var step = Step.ENTER

    private val sequence1 = StringBuilder()  // first entry
    private val sequence2 = StringBuilder()  // confirmation

    // Active buffer for the current step
    private val current get() = if (step == Step.ENTER) sequence1 else sequence2

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetCoverSecretSetupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Expand fully — the calc grid is too tall for peek
        (dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet))
            ?.let { sheet ->
                BottomSheetBehavior.from(sheet).apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    skipCollapsed = true
                }
            }

        updateStepUI()
        wireButtons()
    }

    // ── UI state ─────────────────────────────────────────────────────────────

    private fun updateStepUI() {
        val isEnter = step == Step.ENTER
        binding.tvSetupStep.text = if (isEnter)
            getString(R.string.cover_setup_step1_indicator)
        else
            getString(R.string.cover_setup_step2_indicator)

        binding.tvSetupTitle.text = if (isEnter)
            getString(R.string.cover_setup_step1_title)
        else
            getString(R.string.cover_setup_step2_title)

        binding.btnSetupNext.text = if (isEnter)
            getString(R.string.cover_setup_next)
        else
            getString(R.string.cover_setup_save)

        updateDisplay()
        updateNextButton()
    }

    private fun updateDisplay() {
        binding.tvSequenceDisplay.text = current.toString().ifEmpty { null }
    }

    private fun updateNextButton() {
        binding.btnSetupNext.isEnabled = current.isNotEmpty()
    }

    // ── Button wiring ─────────────────────────────────────────────────────────

    private fun wireButtons() {
        // Digits
        binding.setupBtn0.setOnClickListener { appendKey("0") }
        binding.setupBtn1.setOnClickListener { appendKey("1") }
        binding.setupBtn2.setOnClickListener { appendKey("2") }
        binding.setupBtn3.setOnClickListener { appendKey("3") }
        binding.setupBtn4.setOnClickListener { appendKey("4") }
        binding.setupBtn5.setOnClickListener { appendKey("5") }
        binding.setupBtn6.setOnClickListener { appendKey("6") }
        binding.setupBtn7.setOnClickListener { appendKey("7") }
        binding.setupBtn8.setOnClickListener { appendKey("8") }
        binding.setupBtn9.setOnClickListener { appendKey("9") }

        // Operators
        binding.setupBtnPlus.setOnClickListener     { appendKey("+") }
        binding.setupBtnMinus.setOnClickListener    { appendKey("−") }
        binding.setupBtnMultiply.setOnClickListener { appendKey("×") }
        binding.setupBtnDivide.setOnClickListener   { appendKey("÷") }
        binding.setupBtnEquals.setOnClickListener   { appendKey("=") }
        binding.setupBtnDot.setOnClickListener      { appendKey(",") }

        // AC clears current sequence
        binding.setupBtnClear.setOnClickListener {
            current.clear()
            updateDisplay()
            updateNextButton()
        }

        // Backspace
        binding.btnSequenceBackspace.setOnClickListener {
            if (current.isNotEmpty()) {
                current.deleteCharAt(current.length - 1)
                updateDisplay()
                updateNextButton()
            }
        }
        binding.btnSequenceBackspace.setOnLongClickListener {
            current.clear()
            updateDisplay()
            updateNextButton()
            true
        }

        // Cancel
        binding.btnSetupCancel.setOnClickListener { dismiss() }

        // Next / Save
        binding.btnSetupNext.setOnClickListener {
            when (step) {
                Step.ENTER -> {
                    if (sequence1.isEmpty()) return@setOnClickListener
                    step = Step.CONFIRM
                    updateStepUI()
                }
                Step.CONFIRM -> {
                    if (sequence2.toString() == sequence1.toString()) {
                        onSecretDefined?.invoke(sequence1.toString())
                        dismiss()
                    } else {
                        // Mismatch — show error, back to step 1
                        Snackbar.make(
                            binding.root,
                            getString(R.string.cover_setup_mismatch),
                            Snackbar.LENGTH_LONG
                        ).show()
                        sequence1.clear()
                        sequence2.clear()
                        step = Step.ENTER
                        updateStepUI()
                    }
                }
            }
        }
    }

    // ── Key append ───────────────────────────────────────────────────────────

    private fun appendKey(key: String) {
        if (current.length >= 32) return  // safety cap
        current.append(key)
        updateDisplay()
        updateNextButton()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val TAG = "CoverSecretSetup"
    }
}
