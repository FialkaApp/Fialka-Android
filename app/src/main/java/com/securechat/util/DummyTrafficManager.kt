package com.securechat.util

import android.content.Context
import com.securechat.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.SecureRandom

object DummyTrafficManager {

    private const val PREFS_FILE = "securechat_settings"
    private const val KEY_ENABLED = "dummy_traffic_enabled"

    private val random = SecureRandom()
    private var job: Job? = null

    private const val MIN_DELAY_MS = 45_000L
    private const val MAX_DELAY_MS = 120_000L

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun start(scope: CoroutineScope, context: Context, repository: ChatRepository) {
        if (job?.isActive == true) return
        if (!isEnabled(context)) return

        job = scope.launch {
            while (isActive) {
                val delayMs = MIN_DELAY_MS + random.nextLong(MAX_DELAY_MS - MIN_DELAY_MS)
                delay(delayMs)

                try {
                    val conversations = repository.getAcceptedConversationsList()
                    if (conversations.isNotEmpty()) {
                        val target = conversations[random.nextInt(conversations.size)]
                        repository.sendDummyMessage(target.conversationId)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
