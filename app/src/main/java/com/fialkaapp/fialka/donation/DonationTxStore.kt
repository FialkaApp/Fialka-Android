package com.fialkaapp.fialka.donation

import android.content.Context
import org.json.JSONArray

/**
 * Persists the txIds of donations sent from this device via DonationFragment.
 * Each user's donation subaddress is unique, so we store per-user (keyed by accountId hash).
 */
object DonationTxStore {

    private const val PREFS_NAME = "fialka_donation_tx"

    /** Save a txId as a known donation for the given accountId. */
    fun saveTxId(context: Context, accountId: String, txId: String) {
        val key = prefKey(accountId)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val existing = prefs.getString(key, "[]") ?: "[]"
        val arr = try { JSONArray(existing) } catch (_: Exception) { JSONArray() }
        // Avoid duplicates
        for (i in 0 until arr.length()) {
            if (arr.getString(i) == txId) return
        }
        arr.put(txId)
        prefs.edit().putString(key, arr.toString()).apply()
    }

    /** Returns all txIds saved for this accountId. */
    fun getTxIds(context: Context, accountId: String): Set<String> {
        val key = prefKey(accountId)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(key, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            buildSet { for (i in 0 until arr.length()) add(arr.getString(i)) }
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun prefKey(accountId: String): String {
        // Use a simple hash so the key is always a valid pref name
        return "txids_${accountId.hashCode().toUInt()}"
    }
}
