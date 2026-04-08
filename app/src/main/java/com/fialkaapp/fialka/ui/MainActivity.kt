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
import android.view.View
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.appcompat.app.AlertDialog
import com.fialkaapp.fialka.R
import com.fialkaapp.fialka.data.local.FialkaDatabase
import com.fialkaapp.fialka.util.AppLockManager
import com.fialkaapp.fialka.util.DummyTrafficManager
import com.fialkaapp.fialka.util.NotificationHelper
import com.fialkaapp.fialka.util.ThemeManager


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
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
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

        // Warn before destructive database migration (DROP ALL TABLES)
        if (FialkaDatabase.needsDestructiveMigration(this)) {
            showDestructiveMigrationWarning()
            return
        }

        // Show lock screen on first launch
        if (AppLockManager.isPinSet(this)) {
            isLocked = true
            showLockScreen()
        }

        // Navigate to Add Contact if app opened via fialka://invite deep link
        handleInviteIntent(intent)
        // Navigate to Mailbox Settings if opened via fialka://mailbox deep link
        handleMailboxDeepLink(intent)
        // Navigate directly to a chat if opened from a message notification
        handleChatNotificationIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent) // Update so fragments read the new intent via requireActivity().intent
        handleInviteIntent(intent)
        handleMailboxDeepLink(intent)
        handleChatNotificationIntent(intent)
    }

    /**
     * When the app receives a fialka://invite deep link, auto-navigate to
     * AddContactFragment as soon as the user lands on ConversationsFragment.
     */
    private fun handleInviteIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme?.lowercase() != "fialka" || uri.host != "invite") return

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

    /**
     * When the app receives a fialka://mailbox deep link, store the data
     * and navigate to MailboxSettingsFragment when ready.
     */
    private fun handleMailboxDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme?.lowercase() != "fialka" || uri.host != "mailbox") return

        val onion = uri.getQueryParameter("onion") ?: return
        val pubkey = uri.getQueryParameter("pubkey") ?: ""
        val type = uri.getQueryParameter("type") ?: "PERSONAL"
        val invite = uri.getQueryParameter("invite") ?: ""

        if (!onion.endsWith(".onion")) return

        pendingMailboxJoin = arrayOf(onion, pubkey, type, invite)

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
                        controller.navigate(R.id.action_conversations_to_mailboxSettings)
                    } catch (_: Exception) {}
                }
            }
        })
    }

    companion object {
        /** Pending mailbox join data: [onion, pubkey, type, inviteCode]. */
        @Volatile
        var pendingMailboxJoin: Array<String>? = null

        /** Pending chat open from notification: [conversationId, contactName]. */
        @Volatile
        var pendingChatOpen: Array<String>? = null
    }

    /**
     * When a new-message notification is tapped, navigate directly to the correct chat.
     * Stores the pending destination so ConversationsFragment can pick it up when ready.
     */
    private fun handleChatNotificationIntent(intent: Intent?) {
        if (intent?.action != "com.fialkaapp.fialka.ACTION_OPEN_CHAT") return
        val conversationId = intent.getStringExtra(NotificationHelper.EXTRA_CONVERSATION_ID) ?: return
        val contactName = intent.getStringExtra(NotificationHelper.EXTRA_CONTACT_NAME) ?: ""

        pendingChatOpen = arrayOf(conversationId, contactName)

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
                    pendingChatOpen = null
                    try {
                        val bundle = Bundle().apply {
                            putString("conversationId", conversationId)
                            putString("contactName", contactName)
                        }
                        controller.navigate(R.id.action_conversations_to_chat, bundle)
                    } catch (_: Exception) {}
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

    /**
     * Blocking dialog shown when a Room schema upgrade would trigger
     * fallbackToDestructiveMigration (DROP ALL TABLES).
     *
     * The user must explicitly acknowledge the data loss before the app
     * is allowed to open (and migrate) the database.
     */
    private fun showDestructiveMigrationWarning() {
        AlertDialog.Builder(this)
            .setTitle("Mise à jour — données locales")
            .setMessage(
                "Cette mise à jour de Fialka nécessite une réinitialisation de la base de données locale.\n\n" +
                "⚠ Tous vos messages, contacts et sessions seront effacés de cet appareil.\n\n" +
                "Vos clés d'identité (seed phrase) ne sont PAS affectées — vous pourrez recréer votre compte.\n\n" +
                "Si vous souhaitez conserver vos données, ne continuez pas et exportez votre backup d'abord."
            )
            .setCancelable(false)
            .setPositiveButton("Compris, continuer") { _, _ ->
                // User acknowledged — let the app open normally (Room will migrate on first DB access)
                FialkaDatabase.recordCurrentVersion(this)
                // Re-run startup sequence
                if (AppLockManager.isPinSet(this)) {
                    isLocked = true
                    showLockScreen()
                }
                handleInviteIntent(intent)
                handleMailboxDeepLink(intent)
                handleChatNotificationIntent(intent)
            }
            .setNegativeButton("Annuler (quitter)") { _, _ ->
                finishAffinity()
            }
            .show()
    }
}
