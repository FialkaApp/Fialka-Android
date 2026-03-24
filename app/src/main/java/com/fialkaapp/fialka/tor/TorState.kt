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
package com.fialkaapp.fialka.tor

sealed class TorState {
    data object IDLE : TorState()
    data object STARTING : TorState()
    data class BOOTSTRAPPING(val percent: Int) : TorState()
    data object CONNECTED : TorState()
    data class PUBLISHING_ONION(val percent: Int) : TorState()
    data class ONION_PUBLISHED(val address: String) : TorState()
    data class ERROR(val message: String) : TorState()
    data object DISCONNECTED : TorState()
}

/**
 * Tor circuit relay info — one entry per hop in the circuit.
 */
data class TorRelay(
    val name: String,
    val ip: String,
    val country: String
)

/**
 * Full circuit info: 3 relays (guard, middle, exit) + .onion address.
 */
data class CircuitInfo(
    val guard: TorRelay,
    val middle: TorRelay,
    val exit: TorRelay,
    val onionAddress: String?
)

/**
 * A single Tor circuit with its relay chain and purpose.
 */
data class TorCircuit(
    val id: String,
    val relays: List<TorRelay>,
    val purpose: String
)
