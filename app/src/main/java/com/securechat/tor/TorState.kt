package com.securechat.tor

sealed class TorState {
    data object IDLE : TorState()
    data object STARTING : TorState()
    data class BOOTSTRAPPING(val percent: Int) : TorState()
    data object CONNECTED : TorState()
    data class ERROR(val message: String) : TorState()
    data object DISCONNECTED : TorState()
}
