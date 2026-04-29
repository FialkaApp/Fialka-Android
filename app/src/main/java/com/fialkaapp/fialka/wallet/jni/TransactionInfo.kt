package com.fialkaapp.fialka.wallet.jni

/**
 * Mirrors C++ Monero::TransactionInfo.
 * Constructed by native code via JNI (TransactionHistory.getAll()).
 */
class TransactionInfo {
    @JvmField var direction: Int = 0        // 0=in, 1=out, 2=pending
    @JvmField var isPending: Boolean = false
    @JvmField var isFailed: Boolean = false
    @JvmField var amount: Long = 0L         // piconero
    @JvmField var fee: Long = 0L            // piconero
    @JvmField var blockHeight: Long = 0L    // 0 if pending
    @JvmField var confirmations: Long = 0L
    @JvmField var timestamp: Long = 0L      // epoch seconds
    @JvmField var txId: String = ""
    @JvmField var paymentId: String = ""
    @JvmField var subaddressLabel: String = ""

    companion object {
        const val DIRECTION_IN  = 0
        const val DIRECTION_OUT = 1
    }
}
