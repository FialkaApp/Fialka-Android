package com.securechat.ui.settings

import android.app.Dialog
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.securechat.R
import com.securechat.util.AppLockManager
import kotlinx.coroutines.launch

/**
 * Full-screen dialog for creating or changing a 6-digit PIN.
 * Flow: Enter PIN → Confirm PIN → Done.
 * If changing: Verify old PIN → Enter new PIN → Confirm new PIN → Done.
 */
class PinSetupDialogFragment : DialogFragment() {

    private var firstPin: String? = null
    private var enteredPin = ""
    private val pinLength = 6
    private var isChanging = false
    private var verifyingOldPin = false

    private lateinit var tvTitle: TextView
    private lateinit var dotsContainer: LinearLayout
    private lateinit var dots: List<ImageView>

    var onPinSet: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        isChanging = arguments?.getBoolean(ARG_CHANGING, false) ?: false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.setContentView(R.layout.activity_lock_screen)

        val root = dialog.findViewById<android.view.View>(android.R.id.content)!!
        tvTitle = dialog.findViewById(R.id.tvLockTitle)
        dotsContainer = dialog.findViewById(R.id.dotsContainer)
        dots = (0 until pinLength).map { dotsContainer.getChildAt(it) as ImageView }

        if (isChanging) {
            tvTitle.text = "Entrez votre code actuel"
            verifyingOldPin = true
        } else {
            tvTitle.text = "Choisissez un code à 6 chiffres"
        }

        val padIds = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )
        for (id in padIds) {
            val btn = dialog.findViewById<TextView>(id)
            btn.setOnClickListener { onDigitPressed(btn.text.toString()) }
        }
        dialog.findViewById<ImageView>(R.id.btnBackspace).setOnClickListener { onBackspace() }

        // Hide biometric button during setup
        dialog.findViewById<ImageView>(R.id.btnBiometric).visibility = android.view.View.GONE

        return dialog
    }

    private fun onDigitPressed(digit: String) {
        if (enteredPin.length >= pinLength) return
        enteredPin += digit
        updateDots()

        if (verifyingOldPin) {
            if (enteredPin.length == pinLength) {
                val ctx = context ?: return
                lifecycleScope.launch {
                    val valid = AppLockManager.verifyPin(ctx, enteredPin)
                    if (valid) {
                        verifyingOldPin = false
                        enteredPin = ""
                        tvTitle.text = "Choisissez un nouveau code à 6 chiffres"
                        updateDots()
                    } else {
                        shakeAndReset("Code actuel incorrect")
                        verifyingOldPin = true
                    }
                }
            }
        } else if (enteredPin.length == pinLength) {
            dotsContainer.postDelayed({ handlePinComplete() }, 200)
        }
    }

    private fun handlePinComplete() {
        val ctx = context ?: return

        if (isChanging && firstPin == null) {
            // This path is no longer used; old PIN verification happens in onDigitPressed
            return
        }

        if (firstPin == null) {
            // Step 1: first entry
            firstPin = enteredPin
            enteredPin = ""
            tvTitle.text = "Confirmez votre code"
            updateDots()
        } else {
            // Step 2: confirm
            if (enteredPin == firstPin) {
                lifecycleScope.launch {
                    AppLockManager.setPin(ctx, enteredPin)
                    Toast.makeText(ctx, "Code de verrouillage activé ✓", Toast.LENGTH_SHORT).show()
                    onPinSet?.invoke()
                    dismiss()
                }
            } else {
                shakeAndReset("Les codes ne correspondent pas")
                firstPin = null
                tvTitle.text = "Choisissez un code à 6 chiffres"
            }
        }
    }

    private fun shakeAndReset(message: String) {
        val shake = AnimationUtils.loadAnimation(requireContext(), R.anim.shake)
        dotsContainer.startAnimation(shake)
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        enteredPin = ""
        dotsContainer.postDelayed({ updateDots() }, 300)
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

    companion object {
        private const val ARG_CHANGING = "changing"

        fun newInstance(changing: Boolean = false) = PinSetupDialogFragment().apply {
            arguments = Bundle().apply { putBoolean(ARG_CHANGING, changing) }
        }
    }
}
