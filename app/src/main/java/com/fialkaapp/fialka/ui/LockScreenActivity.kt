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
package com.fialkaapp.fialka.ui

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.crypto.CryptoManager
import com.fialkaapp.fialka.crypto.MnemonicManager
import com.fialkaapp.fialka.util.AppLockManager
import com.fialkaapp.fialka.util.ThemeManager
import kotlinx.coroutines.launch

/**
 * Lock screen — PIN entry + optional biometric unlock.
 * Shown when the app is opened and a PIN is configured.
 *
 * Rate limiting (persisted across restarts):
 *   ≥ 3 wrong attempts → 30 s lockout
 *   ≥ 5 wrong attempts → 5 min lockout
 *   ≥ 7 wrong attempts → 30 min lockout
 * Counter resets on correct PIN.
 */
class LockScreenActivity : AppCompatActivity() {

    private var enteredPin = ""
    private val pinLength = 6

    private lateinit var tvTitle: TextView
    private lateinit var tvLockoutMessage: TextView
    private lateinit var dotsContainer: LinearLayout
    private lateinit var dots: List<ImageView>
    private lateinit var numpad: android.widget.GridLayout

    // Single BiometricPrompt instance — reused to prevent duplicate prompts
    private var biometricPrompt: BiometricPrompt? = null
    private var isBiometricShowing = false

    // ── Rate limiting ─────────────────────────────────────────────────────────
    private var wrongAttempts = 0
    private var countDownTimer: CountDownTimer? = null

    /** True when the numpad is blocked due to too many wrong attempts. */
    private var isInputLocked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyToActivity(this)
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_lock_screen)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        tvTitle          = findViewById(R.id.tvLockTitle)
        tvLockoutMessage = findViewById(R.id.tvLockoutMessage)
        dotsContainer    = findViewById(R.id.dotsContainer)
        numpad           = findViewById(R.id.numpad)

        // Create 6 dot indicators
        dots = (0 until pinLength).map { i ->
            dotsContainer.getChildAt(i) as ImageView
        }

        // Number pad buttons
        val padIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )
        for (id in padIds) {
            val btn = findViewById<TextView>(id)
            btn.setOnClickListener { onDigitPressed(btn.text.toString()) }
        }

        findViewById<ImageView>(R.id.btnBackspace).setOnClickListener { onBackspace() }

        // Load persisted attempt count and check for active lockout
        wrongAttempts = loadAttempts()
        val remaining = remainingLockoutMs()
        if (remaining > 0) {
            startCountdown(remaining)
        }

        // Try biometric immediately if enabled and input is not locked
        if (!isInputLocked && AppLockManager.isBiometricEnabled(this)) {
            initBiometricPrompt()
            val biometricBtn = findViewById<ImageView>(R.id.btnBiometric)
            biometricBtn.visibility = android.view.View.VISIBLE
            biometricBtn.setOnClickListener { showBiometricPrompt() }
            showBiometricPrompt()
        }

        // Forgot PIN → verify recovery phrase
        findViewById<TextView>(R.id.tvForgotPin).setOnClickListener { showForgotPinDialog() }
    }

    override fun onDestroy() {
        countDownTimer?.cancel()
        super.onDestroy()
    }

    // ── Rate limiting ─────────────────────────────────────────────────────────

    /** Lockout duration in ms based on current wrong attempt count. 0 = no lockout. */
    private fun lockoutDurationMs(): Long = when {
        wrongAttempts >= 7 -> 30 * 60_000L   // 30 min
        wrongAttempts >= 5 ->  5 * 60_000L   // 5 min
        wrongAttempts >= 3 ->       30_000L   // 30 s
        else -> 0L
    }

    /** How many ms remain in the current lockout. 0 = not locked. */
    private fun remainingLockoutMs(): Long {
        val lockedUntil = getSharedPreferences(PREFS_RATE, MODE_PRIVATE)
            .getLong(KEY_LOCKED_UNTIL, 0L)
        return (lockedUntil - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun loadAttempts(): Int =
        getSharedPreferences(PREFS_RATE, MODE_PRIVATE).getInt(KEY_ATTEMPTS, 0)

    private fun saveAttempts() {
        getSharedPreferences(PREFS_RATE, MODE_PRIVATE)
            .edit().putInt(KEY_ATTEMPTS, wrongAttempts).apply()
    }

    private fun saveLockoutUntil(durationMs: Long) {
        val until = if (durationMs > 0) System.currentTimeMillis() + durationMs else 0L
        getSharedPreferences(PREFS_RATE, MODE_PRIVATE)
            .edit().putLong(KEY_LOCKED_UNTIL, until).apply()
    }

    private fun clearRateLimit() {
        getSharedPreferences(PREFS_RATE, MODE_PRIVATE)
            .edit()
            .remove(KEY_ATTEMPTS)
            .remove(KEY_LOCKED_UNTIL)
            .apply()
        wrongAttempts = 0
    }

    /** Register a wrong attempt and start a lockout if the threshold is reached. */
    private fun recordWrongAttempt() {
        wrongAttempts++
        saveAttempts()

        val shake = AnimationUtils.loadAnimation(this, R.anim.shake)
        dotsContainer.startAnimation(shake)
        Toast.makeText(this, getString(R.string.lock_wrong_pin), Toast.LENGTH_SHORT).show()
        enteredPin = ""
        dotsContainer.postDelayed({ updateDots() }, 300)

        val lockout = lockoutDurationMs()
        if (lockout > 0) {
            saveLockoutUntil(lockout)
            startCountdown(lockout)
        } else {
            // Still show how many attempts remain before first lockout
            val remaining = 3 - wrongAttempts
            if (remaining > 0) {
                Toast.makeText(
                    this,
                    getString(R.string.lock_attempts_warning, remaining),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /** Start the countdown UI and disable the numpad for [durationMs]. */
    private fun startCountdown(durationMs: Long) {
        isInputLocked = true
        numpad.alpha = 0.35f
        tvLockoutMessage.visibility = android.view.View.VISIBLE

        countDownTimer?.cancel()
        countDownTimer = object : CountDownTimer(durationMs, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                tvLockoutMessage.text = getString(
                    R.string.lock_too_many_attempts,
                    formatCountdown(millisUntilFinished)
                )
            }

            override fun onFinish() {
                isInputLocked = false
                numpad.alpha = 1f
                tvLockoutMessage.visibility = android.view.View.GONE
                enteredPin = ""
                updateDots()
                tvTitle.text = getString(R.string.lock_enter_code)
            }
        }.start()
    }

    /** Format ms remaining as "mm:ss" or "Xs" for short durations. */
    private fun formatCountdown(ms: Long): String {
        val totalSec = ms / 1_000
        val minutes  = totalSec / 60
        val seconds  = totalSec % 60
        return if (minutes > 0) "%d:%02d".format(minutes, seconds)
               else "${seconds}s"
    }

    // ── PIN input ─────────────────────────────────────────────────────────────

    private fun onDigitPressed(digit: String) {
        if (isInputLocked) return
        if (enteredPin.length >= pinLength) return
        enteredPin += digit
        updateDots()

        if (enteredPin.length == pinLength) {
            lifecycleScope.launch {
                val valid = AppLockManager.verifyPin(this@LockScreenActivity, enteredPin)
                if (valid) {
                    clearRateLimit()
                    unlock()
                } else {
                    recordWrongAttempt()
                }
            }
        }
    }

    private fun onBackspace() {
        if (isInputLocked) return
        if (enteredPin.isNotEmpty()) {
            enteredPin = enteredPin.dropLast(1)
            updateDots()
        }
    }

    private fun updateDots() {
        for (i in 0 until pinLength) {
            dots[i].setImageResource(
                if (i < enteredPin.length) R.drawable.dot_filled else R.drawable.dot_empty
            )
        }
    }

    // ── Biometric ─────────────────────────────────────────────────────────────

    private fun initBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                isBiometricShowing = false
                clearRateLimit()
                unlock()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                isBiometricShowing = false
            }
            override fun onAuthenticationFailed() {
                // Single attempt failed — user can retry within the same prompt
            }
        })
    }

    private fun showBiometricPrompt() {
        if (isInputLocked || isBiometricShowing) return

        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) != BiometricManager.BIOMETRIC_SUCCESS) return

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Fialka")
            .setSubtitle(getString(R.string.lockscreen_biometric_subtitle))
            .setNegativeButtonText(getString(R.string.lock_use_pin))
            .build()

        isBiometricShowing = true
        biometricPrompt?.authenticate(promptInfo)
    }

    // ── Forgot PIN ────────────────────────────────────────────────────────────

    private fun showForgotPinDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.restore_words_hint)
            minLines = 3
            setPadding(48, 32, 48, 32)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.lockscreen_recovery_title))
            .setMessage(getString(R.string.lockscreen_recovery_message))
            .setView(input)
            .setPositiveButton(getString(R.string.action_verify)) { _, _ ->
                val text = input.text.toString().trim()
                val words = text.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

                if (words.size != 24) {
                    Toast.makeText(this, getString(R.string.lock_seed_wrong_count), Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                if (!MnemonicManager.validateMnemonic(words)) {
                    Toast.makeText(this, getString(R.string.lock_seed_invalid), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                try {
                    val mnemonicSeed = MnemonicManager.mnemonicToSeed(words)
                    val storedSeed   = CryptoManager.getIdentitySeed()
                    if (mnemonicSeed.contentEquals(storedSeed)) {
                        mnemonicSeed.fill(0)
                        storedSeed.fill(0)
                        AppLockManager.removePin(this)
                        clearRateLimit()
                        Toast.makeText(this, getString(R.string.lock_pin_removed), Toast.LENGTH_LONG).show()
                        unlock()
                    } else {
                        mnemonicSeed.fill(0)
                        storedSeed.fill(0)
                        Toast.makeText(this, getString(R.string.lock_seed_mismatch), Toast.LENGTH_LONG).show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(this, getString(R.string.lock_verify_error), Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .create()
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setOnDismissListener { input.text?.clear() }
        dialog.show()
    }

    // ── Unlock ────────────────────────────────────────────────────────────────

    private fun unlock() {
        setResult(RESULT_OK)
        finish()
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        finishAffinity()
    }

    companion object {
        private const val PREFS_RATE      = "fialka_pin_rate"
        private const val KEY_ATTEMPTS    = "wrong_attempts"
        private const val KEY_LOCKED_UNTIL = "locked_until"
    }
}
