package com.securechat.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.securechat.R

/**
 * Single-activity architecture. Navigation is handled by the NavHostFragment.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // White icons on dark green status bar
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        // Apply system bar insets as padding so content doesn't draw under status/nav bars
        val rootView = findViewById<android.view.View>(R.id.nav_host_fragment)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(
                top = systemBars.top,
                bottom = systemBars.bottom
            )
            insets
        }
    }
}
