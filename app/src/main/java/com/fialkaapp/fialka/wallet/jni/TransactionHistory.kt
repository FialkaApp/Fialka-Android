package com.fialkaapp.fialka.wallet.jni

/** Wraps a C++ Monero::TransactionHistory* */
class TransactionHistory(@JvmField val handle: Long) {
    external fun getCount(): Int
    external fun refreshJ()
    fun refresh() = refreshJ()

    /** Returns all transactions as a list. Calls refresh() internally. */
    external fun getAllJ(): List<TransactionInfo>

    fun getAll(): List<TransactionInfo> = getAllJ()
}
