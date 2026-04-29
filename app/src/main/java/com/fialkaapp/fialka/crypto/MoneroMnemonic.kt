/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667 — GPL-3.0
 *
 * Monero wordlist: Copyright (c) 2014-2024 The Monero Project — BSD-3-Clause
 */
package com.fialkaapp.fialka.crypto

import android.content.Context
import java.util.zip.CRC32

/**
 * Native Monero 25-word mnemonic encoding / decoding (English).
 *
 * This is NOT BIP-39.
 */
object MoneroMnemonic {

    private const val PREFIX_LEN = 3
    private const val NWORDS = 1626
    private const val DATA_WORDS = 24
    private const val TOTAL_WORDS = 25

    private var wordList: List<String> = emptyList()
    private var wordIndex: Map<String, Int> = emptyMap()

    fun init(context: Context) {
        if (wordList.isNotEmpty()) return
        val words = context.resources.openRawResource(
            context.resources.getIdentifier("monero_wordlist", "raw", context.packageName)
        ).bufferedReader().readLines().filter { it.isNotBlank() }

        require(words.size == NWORDS) {
            "Monero wordlist must have $NWORDS words, got ${words.size}"
        }

        wordList = words
        wordIndex = words.mapIndexed { i, w -> w.take(PREFIX_LEN) to i }.toMap()
    }

    fun seedToMnemonic(seed: ByteArray): List<String> {
        require(seed.size == 32) { "Seed must be 32 bytes, got ${seed.size}" }
        require(wordList.isNotEmpty()) { "MoneroMnemonic not initialized" }

        val n = NWORDS.toLong()
        val dataWords = mutableListOf<String>()

        for (i in 0 until 8) {
            val x = readUInt32LE(seed, i * 4)
            val w1 = (x % n).toInt()
            val w2 = ((x / n + w1) % n).toInt()
            val w3 = ((x / n / n + w2) % n).toInt()
            dataWords += wordList[w1]
            dataWords += wordList[w2]
            dataWords += wordList[w3]
        }

        val checksum = checksumWord(dataWords)
        return dataWords + checksum
    }

    fun mnemonicToSeed(words: List<String>): ByteArray? {
        if (words.size != TOTAL_WORDS) return null
        if (wordList.isEmpty()) return null

        val lower = words.map { it.lowercase().trim() }
        val indices = lower.map { w ->
            wordIndex[w.take(PREFIX_LEN)] ?: return null
        }

        val dataWordStrings = indices.take(DATA_WORDS).map { wordList[it] }
        val expectedChecksum = checksumWord(dataWordStrings)
        if (lower[DATA_WORDS] != expectedChecksum) return null

        val n = NWORDS.toLong()
        val seed = ByteArray(32)

        for (i in 0 until 8) {
            val i1 = indices[i * 3 + 0].toLong()
            val i2 = indices[i * 3 + 1].toLong()
            val i3 = indices[i * 3 + 2].toLong()
            val x = i1 + n * ((i2 - i1 + n) % n) + n * n * ((i3 - i2 + n) % n)
            writeUInt32LE(seed, i * 4, x)
        }

        return seed
    }

    fun isValidMnemonic(words: List<String>): Boolean {
        if (words.size != TOTAL_WORDS) return false
        val seed = mnemonicToSeed(words) ?: return false
        seed.fill(0)
        return true
    }

    private fun checksumWord(dataWords: List<String>): String {
        require(dataWords.size == DATA_WORDS)
        val prefixConcat = dataWords.joinToString("") { it.take(PREFIX_LEN) }
        val crc = CRC32().apply { update(prefixConcat.toByteArray(Charsets.UTF_8)) }.value
        val idx = (crc % DATA_WORDS).toInt()
        return dataWords[idx]
    }

    private fun readUInt32LE(buf: ByteArray, offset: Int): Long {
        return (buf[offset].toLong() and 0xFF) or
            ((buf[offset + 1].toLong() and 0xFF) shl 8) or
            ((buf[offset + 2].toLong() and 0xFF) shl 16) or
            ((buf[offset + 3].toLong() and 0xFF) shl 24)
    }

    private fun writeUInt32LE(buf: ByteArray, offset: Int, value: Long) {
        buf[offset + 0] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
