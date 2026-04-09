/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 */
package com.fialkaapp.fialka.wallet

import org.json.JSONObject
import java.util.Locale

/**
 * Parses and serializes XMR payment request messages exchanged over the
 * Fialka ratchet channel.
 *
 * Wire format (inside the AES-GCM plaintext field):
 *   XMR_PAY:{"address":"8...","piconero":100000000000,"label":"coffee"}
 *
 * The prefix [PREFIX] is the unambiguous discriminant for [MessagesAdapter].
 * Everything after the colon is a compact JSON object.
 *
 * @param address    Monero subaddress (95 chars, starts with "8")
 * @param piconero   Requested amount in piconero (1 XMR = 1 000 000 000 000). 0 = open amount.
 * @param label      Human-readable memo attached to the request. May be empty.
 */
data class PaymentMessageData(
    val address: String,
    val piconero: Long,
    val label: String
) {
    /** Amount in XMR formatted for display, or empty if piconero == 0. */
    val amountXmr: String
        get() = if (piconero <= 0L) ""
        else String.format(Locale.US, "%.5f", piconero / 1_000_000_000_000.0)

    /** Address shortened for display: "8AbcDef…XYZ012". */
    val shortAddress: String
        get() = if (address.length > 22)
            "${address.take(12)}…${address.takeLast(6)}"
        else address

    /** Serialize to plaintext for the ratchet channel. */
    fun toPlaintext(): String {
        val json = JSONObject()
        json.put("address", address)
        json.put("piconero", piconero)
        json.put("label", label)
        return "$PREFIX${json}"
    }

    companion object {
        /** Discriminant prefix — checked by MessagesAdapter for view type selection. */
        const val PREFIX = "XMR_PAY:"

        /**
         * Parse a raw message plaintext into [PaymentMessageData].
         *
         * @return parsed data, or null if the plaintext is not a payment request
         */
        fun fromPlaintext(plaintext: String): PaymentMessageData? {
            if (!plaintext.startsWith(PREFIX)) return null
            return try {
                val json = JSONObject(plaintext.removePrefix(PREFIX))
                PaymentMessageData(
                    address   = json.optString("address", ""),
                    piconero  = json.optLong("piconero", 0L),
                    label     = json.optString("label", "")
                ).takeIf { it.address.isNotEmpty() }
            } catch (_: Exception) {
                null
            }
        }
    }
}
