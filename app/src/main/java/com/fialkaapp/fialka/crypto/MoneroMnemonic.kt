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
 * Native Monero 25-word mnemonic encoding / decoding.
 *
 * This is **NOT** BIP-39. The algorithm is the one used by Monero core,
 * Cake Wallet, Feather Wallet, and all standard Monero clients:
 *
 * **Encoding** (32-byte seed → 25 words):
 * - Split seed into 8 little-endian uint32 values.
 * - For each uint32 `x` and wordlist size N=1626:
 *   `w1 = x % N`
 *   `w2 = (x / N + w1) % N`
 *   `w3 = (x / N² + w2) % N`
 * - Produces 24 words. The 25th is a checksum word (CRC32 over 3-char prefixes).
 *
 * **Decoding** (25 words → 32-byte seed):
 * - Reverse the formula for each group of 3 words, verify the checksum word.
 *
 * The prefix length for English is 3 — the first 3 characters of each word
 * uniquely identify it in the English Monero wordlist.
 */
object MoneroMnemonic {

    private const val PREFIX_LEN = 3
    private const val NWORDS = 1626
    private const val WORDS_PER_CHUNK = 3    // 8 chunks × 3 words = 24 data words
    private const val DATA_WORDS = 24
    private const val TOTAL_WORDS = 25

    private var wordList: List<String> = emptyList()
    private var wordIndex: Map<String, Int> = emptyMap()  // first PREFIX_LEN chars → index

    // ── Initialisation ──────────────────────────────────────────────────────

    /**
     * Load the Monero English wordlist from raw resources.
     * Must be called once before using [seedToMnemonic] or [mnemonicToSeed].
     * Safe to call multiple times (no-op after first call).
     */
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

    // ── Encoding ─────────────────────────────────────────────────────────────

    /**
     * Encode a 32-byte XMR wallet seed into 25 Monero mnemonic words.
     *
     * The output is compatible with Cake Wallet, Feather Wallet, and Monero CLI.
     *
     * @param seed  32-byte wallet seed (from [WalletSeedManager])
     * @return      List of 25 lowercase English words
     */
    fun seedToMnemonic(seed: ByteArray): List<String> {
        require(seed.size == 32) { "Seed must be 32 bytes, got ${seed.size}" }
        require(wordList.isNotEmpty()) { "MoneroMnemonic not initialized — call init() first" }

        val n = NWORDS.toLong()
        val dataWords = mutableListOf<String>()

        for (i in 0 until 8) {
            // Read 4 bytes as unsigned little-endian uint32
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

    // ── Decoding ─────────────────────────────────────────────────────────────

    /**
     * Decode a 25-word Monero mnemonic back to the 32-byte seed.
     *
     * @param words  list of exactly 25 words (case-insensitive)
     * @return       32-byte seed, or `null` if the mnemonic is invalid
     *               (wrong count, unknown word, bad checksum)
     */
    fun mnemonicToSeed(words: List<String>): ByteArray? {
        if (words.size != TOTAL_WORDS) return null
        if (wordList.isEmpty()) return null

        val lower = words.map { it.lowercase().trim() }

        // Resolve each word to its index (prefix-based lookup)
        val indices = lower.map { w ->
            wordIndex[w.take(PREFIX_LEN)] ?: return null
        }

        // Validate checksum (last word must match computed checksum)
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

    /**
     * Validate a 25-word Monero mnemonic without returning the seed.
     * Useful for real-time input validation in [WalletRestoreFragment].
     */
    fun isValidMnemonic(words: List<String>): Boolean {
        if (words.size != TOTAL_WORDS) return false
        val seed = mnemonicToSeed(words) ?: return false
        seed.fill(0) // immediately zeroize — we only needed the boolean
        return true
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Compute the checksum word for 24 data words.
     * CRC32 of the concatenated 3-char prefixes; result index = crc % 24.
     */
    private fun checksumWord(dataWords: List<String>): String {
        require(dataWords.size == DATA_WORDS)
        val prefixConcat = dataWords.joinToString("") { it.take(PREFIX_LEN) }
        val crc = CRC32().apply { update(prefixConcat.toByteArray(Charsets.UTF_8)) }.value
        val idx = (crc % DATA_WORDS).toInt()
        return dataWords[idx]
    }

    /** Read 4 bytes from [buf] at [offset] as unsigned little-endian uint32 → Long. */
    private fun readUInt32LE(buf: ByteArray, offset: Int): Long {
        return (buf[offset].toLong() and 0xFF) or
               ((buf[offset + 1].toLong() and 0xFF) shl 8) or
               ((buf[offset + 2].toLong() and 0xFF) shl 16) or
               ((buf[offset + 3].toLong() and 0xFF) shl 24)
    }

    /** Write [value] (lower 32 bits) into [buf] at [offset] as little-endian uint32. */
    private fun writeUInt32LE(buf: ByteArray, offset: Int, value: Long) {
        buf[offset + 0] = (value and 0xFF).toByte()
        buf[offset + 1] = ((value shr 8) and 0xFF).toByte()
        buf[offset + 2] = ((value shr 16) and 0xFF).toByte()
        buf[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }
}
