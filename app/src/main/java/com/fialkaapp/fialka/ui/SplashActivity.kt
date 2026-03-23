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

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.fialkaapp.fialka.R

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen, no status bar
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )

        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.splash_logo)

        // Fade in + scale up animation
        val fadeIn = ObjectAnimator.ofFloat(logo, "alpha", 0f, 1f).apply {
            duration = 600
        }
        val scaleX = ObjectAnimator.ofFloat(logo, "scaleX", 0.6f, 1f).apply {
            duration = 800
            interpolator = OvershootInterpolator(1.5f)
        }
        val scaleY = ObjectAnimator.ofFloat(logo, "scaleY", 0.6f, 1f).apply {
            duration = 800
            interpolator = OvershootInterpolator(1.5f)
        }

        AnimatorSet().apply {
            playTogether(fadeIn, scaleX, scaleY)
            start()
        }

        // Navigate to MainActivity after delay
        logo.postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }, 1500)
    }
}
