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
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
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

        // Navigate to Add Contact if app opened via securechat://invite deep link
        handleInviteIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update so fragments read the new intent via requireActivity().intent
        handleInviteIntent(intent)
    }

    /**
     * When the app receives a securechat://invite deep link, auto-navigate to
     * AddContactFragment as soon as the user lands on ConversationsFragment.
     */
    private fun handleInviteIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "securechat" || uri.host != "invite") return

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as? NavHostFragment ?: return
        val navController = navHostFragment.navController

        navController.addOnDestinationChangedListener(object : NavController.OnDestinationChangedListener {
            override fun onDestinationChanged(
                controller: NavController,
                destination: NavDestination,
                arguments: Bundle?
            ) {
                if (destination.id == R.id.conversationsFragment) {
                    controller.removeOnDestinationChangedListener(this)
                    try {
                        controller.navigate(R.id.action_conversations_to_addContact)
                    } catch (_: Exception) { /* already navigating */ }
                }
            }
        })
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
