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
package com.securechat.ui

import android.content.Intent
import android.os.Bundle
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
import com.securechat.R
import com.securechat.crypto.CryptoManager
import com.securechat.crypto.MnemonicManager
import com.securechat.util.AppLockManager
import com.securechat.util.ThemeManager
import kotlinx.coroutines.launch

/**
 * Lock screen — PIN entry + optional biometric unlock.
 * Shown when the app is opened and a PIN is configured.
 */
class LockScreenActivity : AppCompatActivity() {

    private var enteredPin = ""
    private val pinLength = 6

    private lateinit var tvTitle: TextView
    private lateinit var dotsContainer: LinearLayout
    private lateinit var dots: List<ImageView>

    // Single BiometricPrompt instance — reused to prevent duplicate prompts
    private var biometricPrompt: BiometricPrompt? = null
    private var isBiometricShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyToActivity(this)
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_lock_screen)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        tvTitle = findViewById(R.id.tvLockTitle)
        dotsContainer = findViewById(R.id.dotsContainer)

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

        // Try biometric immediately if enabled
        if (AppLockManager.isBiometricEnabled(this)) {
            initBiometricPrompt()
            val biometricBtn = findViewById<ImageView>(R.id.btnBiometric)
            biometricBtn.visibility = android.view.View.VISIBLE
            biometricBtn.setOnClickListener { showBiometricPrompt() }
            showBiometricPrompt()
        }

        // Forgot PIN → verify recovery phrase
        findViewById<TextView>(R.id.tvForgotPin).setOnClickListener { showForgotPinDialog() }
    }

    private fun showForgotPinDialog() {
        val input = EditText(this).apply {
            hint = "Entrez vos 24 mots séparés par des espaces"
            minLines = 3
            setPadding(48, 32, 48, 32)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Récupération par phrase secrète")
            .setMessage("Entrez votre phrase de récupération (24 mots) pour réinitialiser votre code PIN.")
            .setView(input)
            .setPositiveButton("Vérifier") { _, _ ->
                val text = input.text.toString().trim()
                val words = text.lowercase().split("\\s+".toRegex()).filter { it.isNotBlank() }

                if (words.size != 24) {
                    Toast.makeText(this, "La phrase doit contenir exactement 24 mots", Toast.LENGTH_LONG).show()
                    return@setPositiveButton
                }

                if (!MnemonicManager.validateMnemonic(words)) {
                    Toast.makeText(this, "Phrase invalide", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }

                try {
                    val mnemonicKey = MnemonicManager.mnemonicToPrivateKey(words)
                    val storedKey = CryptoManager.getIdentityPrivateKeyBytes()
                    if (mnemonicKey.contentEquals(storedKey)) {
                        // Identity verified — remove PIN and unlock
                        mnemonicKey.fill(0)
                        storedKey.fill(0)
                        AppLockManager.removePin(this)
                        Toast.makeText(this, "PIN supprimé. Vous pouvez en configurer un nouveau dans les paramètres.", Toast.LENGTH_LONG).show()
                        unlock()
                    } else {
                        mnemonicKey.fill(0)
                        storedKey.fill(0)
                        Toast.makeText(this, "La phrase ne correspond pas à ce compte", Toast.LENGTH_LONG).show()
                    }
                } catch (_: Exception) {
                    Toast.makeText(this, "Erreur de vérification", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Annuler", null)
            .create()
        dialog.window?.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        dialog.setOnDismissListener { input.text?.clear() }
        dialog.show()
    }

    private fun onDigitPressed(digit: String) {
        if (enteredPin.length >= pinLength) return
        enteredPin += digit
        updateDots()

        if (enteredPin.length == pinLength) {
            lifecycleScope.launch {
                val valid = AppLockManager.verifyPin(this@LockScreenActivity, enteredPin)
                if (valid) {
                    unlock()
                } else {
                    val shake = AnimationUtils.loadAnimation(this@LockScreenActivity, R.anim.shake)
                    dotsContainer.startAnimation(shake)
                    Toast.makeText(this@LockScreenActivity, "Code incorrect", Toast.LENGTH_SHORT).show()
                    enteredPin = ""
                    dotsContainer.postDelayed({ updateDots() }, 300)
                }
            }
        }
    }

    private fun onBackspace() {
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

    private fun initBiometricPrompt() {
        val executor = ContextCompat.getMainExecutor(this)
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                isBiometricShowing = false
                unlock()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                isBiometricShowing = false
                // User cancelled or error — fall back to PIN
            }
            override fun onAuthenticationFailed() {
                // Single attempt failed — user can retry within the same prompt
            }
        })
    }

    private fun showBiometricPrompt() {
        if (isBiometricShowing) return

        val biometricManager = BiometricManager.from(this)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
            return
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("SecureChat")
            .setSubtitle("Déverrouillez avec votre empreinte ou visage")
            .setNegativeButtonText("Utiliser le code PIN")
            .build()

        isBiometricShowing = true
        biometricPrompt?.authenticate(promptInfo)
    }

    private fun unlock() {
        setResult(RESULT_OK)
        finish()
    }

    @Deprecated("Use OnBackPressedDispatcher")
    override fun onBackPressed() {
        // Don't allow back to bypass lock
        finishAffinity()
    }
}
