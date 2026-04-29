package com.fialkaapp.fialka.wallet.jni

/**
 * Mirrors C++ Monero::Transfer — a destination address + amount within a TransactionInfo.
 * Constructed by native code via JNI.
 */
class Transfer {
    @JvmField var amount: Long = 0L
    @JvmField var address: String = ""
}
