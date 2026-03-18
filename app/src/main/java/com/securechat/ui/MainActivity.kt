package com.securechat.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.securechat.R
import com.securechat.util.AppLockManager
import com.securechat.util.DummyTrafficManager
import com.securechat.util.ThemeManager


/**
 * Single-activity architecture. Navigation is handled by the NavHostFragment.
 * If a PIN is configured, LockScreenActivity is shown before granting access.
 */
class MainActivity : AppCompatActivity() {

    private var isLocked = false
    private var lastPausedAt = 0L

    /** Grace period loaded from user preference. */
    private var lockGraceMs = 3_000L

    private val lockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            isLocked = false
            lastPausedAt = 0L // Prevent onResume from re-locking immediately
        } else {
            finishAffinity()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyToActivity(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // White icons on dark green status bar
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        // Size the status bar background View to match the actual status bar height
        val statusBarBg = findViewById<android.view.View>(R.id.status_bar_background)
        val navHost = findViewById<android.view.View>(R.id.nav_host_fragment)

        ViewCompat.setOnApplyWindowInsetsListener(statusBarBg) { view, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.layoutParams.height = statusBarHeight
            view.requestLayout()
            insets
        }

        // Handle bottom nav bar + keyboard (IME) insets on the fragment container
        ViewCompat.setOnApplyWindowInsetsListener(navHost) { view, insets ->
            val navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            val imeHeight = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            view.updatePadding(bottom = maxOf(navBarHeight, imeHeight))
            insets
        }

        // Show lock screen on first launch
        if (AppLockManager.isPinSet(this)) {
            isLocked = true
            showLockScreen()
        }

    }

    override fun onPause() {
        super.onPause()
        lastPausedAt = System.currentTimeMillis()
        DummyTrafficManager.setAppActive(false)
    }

    override fun onResume() {
        super.onResume()
        DummyTrafficManager.setAppActive(true)
        lockGraceMs = AppLockManager.getAutoLockDelay(this)
        if (!isLocked && AppLockManager.isPinSet(this)) {
            val elapsed = System.currentTimeMillis() - lastPausedAt
            if (lastPausedAt > 0 && elapsed > lockGraceMs) {
                isLocked = true
                showLockScreen()
            }
        }
    }

    private fun showLockScreen() {
        lockLauncher.launch(Intent(this, LockScreenActivity::class.java))
    }
}
