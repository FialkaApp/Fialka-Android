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
package com.securechat.util

import com.securechat.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Per-conversation dummy traffic to mask real messaging patterns.
 *
 * Only sends dummies on conversations where the user explicitly enabled it.
 * Uses Poisson-distributed intervals + variable padding + burst cover.
 */
object DummyTrafficManager {

    private val random = SecureRandom()
    private var job: Job? = null

    // Poisson mean interval
    private const val MEAN_DELAY_ACTIVE_MS = 60_000.0   // ~1 min when app in foreground
    private const val MEAN_DELAY_IDLE_MS = 180_000.0     // ~3 min when app in background
    private const val MIN_DELAY_MS = 8_000L
    private const val MAX_DELAY_MS = 300_000L

    // Burst cover after real messages
    private const val BURST_DELAY_MS = 3_000L
    private const val BURST_COUNT = 3

    @Volatile
    private var isAppActive = false

    @Volatile
    private var pendingBursts = 0

    /** Call when a real message is sent — triggers burst cover traffic. */
    fun onRealMessageSent() {
        pendingBursts = BURST_COUNT
    }

    /** Call from Activity.onResume / onPause to adapt rate. */
    fun setAppActive(active: Boolean) {
        isAppActive = active
    }

    /**
     * Start the dummy traffic loop.
     * Only conversations with dummyTrafficEnabled=true will receive dummies.
     */
    fun start(scope: CoroutineScope, repository: ChatRepository) {
        if (job?.isActive == true) return

        job = scope.launch {
            while (isActive) {
                val delayMs = if (pendingBursts > 0) {
                    pendingBursts--
                    BURST_DELAY_MS
                } else {
                    nextPoissonDelay()
                }
                delay(delayMs)

                try {
                    val targets = repository.getConversationsWithDummyTraffic()
                    if (targets.isNotEmpty()) {
                        // Send to 1-3 of the enabled conversations per round
                        val count = min(1 + random.nextInt(3), targets.size)
                        val chosen = targets.shuffled(random).take(count)
                        for (conv in chosen) {
                            repository.sendDummyMessage(conv.conversationId)
                        }
                    }
                } catch (_: Exception) { }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Exponential distribution (Poisson process inter-arrival time).
     */
    private fun nextPoissonDelay(): Long {
        val mean = if (isAppActive) MEAN_DELAY_ACTIVE_MS else MEAN_DELAY_IDLE_MS
        val u = random.nextDouble().coerceIn(1e-10, 1.0)
        val sample = (-mean * ln(u)).toLong()
        return max(MIN_DELAY_MS, min(sample, MAX_DELAY_MS))
    }
}
