package com.fialkaapp.fialka.wallet.jni

/** Wraps a C++ Monero::PendingTransaction* */
class PendingTransaction(@JvmField val handle: Long) {
    external fun getStatusJ(): Int
    external fun getErrorString(): String
    external fun commit(filename: String, overwrite: Boolean): Boolean
    external fun getAmount(): Long
    external fun getFee(): Long
    external fun getFirstTxIdJ(): String?

    val isOk: Boolean get() = getStatusJ() == 0
    val txId: String? get() = getFirstTxIdJ()
}
