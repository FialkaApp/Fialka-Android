package com.fialkaapp.fialka.wallet.jni

/**
 * Kotlin mirror of the C++ WalletListener callbacks.
 * Implement this interface and pass to Wallet.setListener().
 */
interface WalletListener {
    fun updated()
    fun newBlock(height: Long)
    fun refreshed()
    fun unconfirmedMoneyReceived(txId: String, amount: Long)
}
