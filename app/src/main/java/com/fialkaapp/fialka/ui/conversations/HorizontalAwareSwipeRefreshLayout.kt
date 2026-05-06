/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.ui.conversations

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

/**
 * SwipeRefreshLayout that doesn't intercept horizontal swipes.
 *
 * Problem: ViewPager2 sits inside SwipeRefreshLayout. SwipeRefreshLayout's
 * onInterceptTouchEvent runs before ViewPager2 can consume the event, so any
 * diagonal or slow horizontal drag may be stolen, making tab-switching feel
 * unreliable.
 *
 * Fix: on ACTION_MOVE, compare |ΔX| vs |ΔY|. If the gesture is more horizontal
 * than vertical, call requestDisallowInterceptTouchEvent(true) on ourselves so
 * the event propagates down to ViewPager2 unimpeded.
 */
class HorizontalAwareSwipeRefreshLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SwipeRefreshLayout(context, attrs) {

    private var downX = 0f
    private var downY = 0f

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(ev.x - downX)
                val dy = Math.abs(ev.y - downY)
                if (dx > dy) {
                    // Horizontal gesture — let ViewPager2 handle it entirely
                    return false
                }
            }
            MotionEvent.ACTION_CANCEL,
            MotionEvent.ACTION_UP -> {
                downX = 0f
                downY = 0f
            }
        }
        return super.onInterceptTouchEvent(ev)
    }
}
