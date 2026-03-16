package com.securechat.ui

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.securechat.R
import com.securechat.util.AppLockManager

/**
 * Lock screen — PIN entry + optional biometric unlock.
 * Shown when the app is opened and a PIN is configured.
 */
class LockScreenActivity : AppCompatActivity() {

    private var enteredPin = ""
    private val pinLength = 4

    private lateinit var tvTitle: TextView
    private lateinit var dotsContainer: LinearLayout
    private lateinit var dots: List<ImageView>

    // Single BiometricPrompt instance — reused to prevent duplicate prompts
    private var biometricPrompt: BiometricPrompt? = null
    private var isBiometricShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lock_screen)

        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        @Suppress("DEPRECATION") // Safe on minSdk 33; no replacement until API 35
        window.statusBarColor = ContextCompat.getColor(this, R.color.primary_dark)

        tvTitle = findViewById(R.id.tvLockTitle)
        dotsContainer = findViewById(R.id.dotsContainer)

        // Create 4 dot indicators
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
    }

    private fun onDigitPressed(digit: String) {
        if (enteredPin.length >= pinLength) return
        enteredPin += digit
        updateDots()

        if (enteredPin.length == pinLength) {
            if (AppLockManager.verifyPin(this, enteredPin)) {
                unlock()
            } else {
                // Wrong PIN — shake and reset
                val shake = AnimationUtils.loadAnimation(this, R.anim.shake)
                dotsContainer.startAnimation(shake)
                Toast.makeText(this, "Code incorrect", Toast.LENGTH_SHORT).show()
                enteredPin = ""
                dotsContainer.postDelayed({ updateDots() }, 300)
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
