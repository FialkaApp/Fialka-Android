package com.fialkaapp.fialka.wallet.jni

/**
 * Status result from wallet2_api statusWithErrorString().
 * status == 0  → OK
 * status == 1  → ERROR
 */
data class WalletStatus(
    val status: Int,
    val errorString: String
) {
    val isOk: Boolean get() = status == 0
}
