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

import android.content.Intent
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.fialkaapp.fialka.databinding.ActivityCoverCalculatorBinding
import com.fialkaapp.fialka.ui.SplashActivity
import com.fialkaapp.fialka.ui.disguise.AppDisguiseManager
import kotlin.math.abs
import kotlin.math.floor

/**
 * Cover activity: looks exactly like a real calculator.
 *
 * Unlock mechanism: sequence matching.
 * Every button press (digit, operator, =, .) is appended to [keySequence].
 * After each press, we check if [keySequence] ends with the stored secret.
 *
 * Examples of valid secrets:
 *   "1337"            → type 1-3-3-7, unlocks immediately on last digit
 *   "1337+584−33="    → type that exact sequence, unlocks when = is pressed
 *   "7+7+8+5+9−1="    → same idea
 *
 * The secret is stored verbatim using the same symbols as button labels:
 *   digits: 0-9  |  operators: + − × ÷  |  equals: =  |  dot: ,
 */
class CoverCalculatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCoverCalculatorBinding

    // ── Calculator arithmetic state ─────────────────────────────────────────────────────
    private var currentInput = "0"
    private var operator: String? = null
    private var previousValue: Double? = null
    private var isNewInput = false
    private var expressionStr = ""

    // ── Sequence tracking ───────────────────────────────────────────────────────────────
    // Holds a rolling window of the last N key presses (N = secret length + buffer).
    // Never grows beyond 2× the secret length to avoid memory waste.
    private val keySequence = StringBuilder(64)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        binding = ActivityCoverCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupButtons()
        updateDisplay()
    }

    // ── Button wiring ─────────────────────────────────────────────────────────────────────

    private fun setupButtons() {
        binding.btn0.setOnClickListener { inputDigit("0") }
        binding.btn1.setOnClickListener { inputDigit("1") }
        binding.btn2.setOnClickListener { inputDigit("2") }
        binding.btn3.setOnClickListener { inputDigit("3") }
        binding.btn4.setOnClickListener { inputDigit("4") }
        binding.btn5.setOnClickListener { inputDigit("5") }
        binding.btn6.setOnClickListener { inputDigit("6") }
        binding.btn7.setOnClickListener { inputDigit("7") }
        binding.btn8.setOnClickListener { inputDigit("8") }
        binding.btn9.setOnClickListener { inputDigit("9") }
        binding.btnDot.setOnClickListener      { inputDot() }
        binding.btnPlus.setOnClickListener     { inputOperator("+") }
        binding.btnMinus.setOnClickListener    { inputOperator("−") }
        binding.btnMultiply.setOnClickListener { inputOperator("×") }
        binding.btnDivide.setOnClickListener   { inputOperator("÷") }
        binding.btnEquals.setOnClickListener   { calculate() }
        binding.btnAc.setOnClickListener       { clear() }
        binding.btnPlusMinus.setOnClickListener { toggleSign() }
        binding.btnPercent.setOnClickListener  { percent() }
    }

    // ── Arithmetic logic ──────────────────────────────────────────────────────────────────

    private fun inputDigit(d: String) {
        if (isNewInput) { currentInput = d; isNewInput = false }
        else currentInput = when {
            currentInput == "0" -> d
            currentInput.length >= 12 -> currentInput
            else -> currentInput + d
        }
        updateDisplay()
        appendKey(d)
    }

    private fun inputDot() {
        if (isNewInput) { currentInput = "0,"; isNewInput = false; updateDisplay(); return }
        if (!currentInput.contains(",")) { currentInput += ","; updateDisplay() }
        appendKey(",")
    }

    private fun inputOperator(op: String) {
        if (operator != null && !isNewInput) calculate(trackKey = false)
        previousValue = parseDisplay(currentInput)
        operator = op
        expressionStr = formatResult(previousValue!!) + " $op"
        isNewInput = true
        updateDisplay()
        appendKey(op)
    }

    /** @param trackKey false when called internally to chain ops (no extra = in sequence). */
    private fun calculate(trackKey: Boolean = true) {
        val op = operator ?: run { if (trackKey) appendKey("="); return }
        val a = previousValue ?: return
        val b = parseDisplay(currentInput)
        val result = when (op) {
            "+"  -> a + b
            "−"  -> a - b
            "×"  -> a * b
            "÷"  -> if (b == 0.0) Double.NaN else a / b
            else -> b
        }
        expressionStr = ""
        currentInput = formatResult(result)
        previousValue = null
        operator = null
        isNewInput = true
        updateDisplay()
        if (trackKey) appendKey("=")
    }

    private fun clear() {
        if (currentInput != "0" || expressionStr.isNotEmpty()) {
            currentInput = "0"
            if (isNewInput) { operator = null; previousValue = null; expressionStr = "" }
        } else {
            operator = null; previousValue = null; expressionStr = ""
        }
        isNewInput = false
        updateDisplay()
        // AC/C resets the rolling sequence window — prevents accidental unlock
        keySequence.clear()
    }

    private fun toggleSign() {
        val v = parseDisplay(currentInput)
        if (v == 0.0) return
        currentInput = formatResult(-v)
        updateDisplay()
        // +/− does not append to sequence — not representable as a single symbol
    }

    private fun percent() {
        val v = parseDisplay(currentInput)
        currentInput = formatResult(v / 100.0)
        updateDisplay()
        appendKey("%")
    }

    // ── Display ───────────────────────────────────────────────────────────────────────────

    private fun updateDisplay() {
        binding.tvExpression.text = expressionStr
        binding.tvDisplay.text = currentInput
        binding.btnAc.text = if (currentInput == "0" && expressionStr.isEmpty()) "AC" else "C"
    }

    // ── Formatting ────────────────────────────────────────────────────────────────────────

    private fun parseDisplay(s: String): Double = s.replace(",", ".").toDoubleOrNull() ?: 0.0

    private fun formatResult(d: Double): String {
        if (d.isNaN() || d.isInfinite()) return "Erreur"
        return if (d == floor(d) && abs(d) < 1e12) d.toLong().toString()
        else "%.8g".format(d).replace(".", ",").trimEnd('0').trimEnd(',')
    }

    // ── Sequence tracking + secret check ─────────────────────────────────────────────────

    /**
     * Appends [key] to the rolling sequence window, then checks for the secret.
     * The window is kept to at most 2× the secret length to bound memory use.
     */
    private fun appendKey(key: String) {
        if (!AppDisguiseManager.isCoverModeEnabled(this)) return
        val secret = AppDisguiseManager.getCoverSecret(this)
        if (secret.isEmpty()) return

        keySequence.append(key)

        // Trim to avoid unbounded growth
        val maxLen = (secret.length * 2).coerceAtLeast(32)
        if (keySequence.length > maxLen) {
            keySequence.delete(0, keySequence.length - maxLen)
        }

        if (keySequence.endsWith(secret)) {
            keySequence.clear()  // prevent double-trigger
            unlockFialka()
        }
    }

 // Unlock

    private fun unlockFialka() {
        runCatching {
            val vib = getSystemService(Vibrator::class.java)
            vib?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        }
        val intent = Intent(this, SplashActivity::class.java)
        intent.putExtra(SplashActivity.EXTRA_BYPASS_COVER, true)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
