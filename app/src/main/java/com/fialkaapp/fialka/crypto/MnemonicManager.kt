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
package com.fialkaapp.fialka.crypto

import android.content.Context
import java.security.MessageDigest

/**
 * BIP-39 mnemonic encoding for the Ed25519 identity seed.
 * 32 bytes (256 bits) → 24 words, with 8-bit SHA-256 checksum.
 *
 * In the "1 Seed → Everything" architecture, these 24 words encode the
 * Ed25519 seed from which ALL keys derive (Ed25519, X25519, ML-KEM-1024,
 * Account ID). Restoring from mnemonic = re-deriving everything.
 */
object MnemonicManager {

    private var wordList: List<String> = emptyList()

    /** Expose the loaded BIP-39 wordlist for autocomplete UI. */
    fun getWordList(): List<String> = wordList

    fun init(context: Context) {
        if (wordList.isNotEmpty()) return
        wordList = context.resources.openRawResource(
            context.resources.getIdentifier("bip39_english", "raw", context.packageName)
        ).bufferedReader().readLines().filter { it.isNotBlank() }
        require(wordList.size == 2048) { "BIP-39 word list must have 2048 words" }
    }

    /**
     * Encode 32-byte Ed25519 seed into 24 BIP-39 words.
     * 256 bits entropy + 8 bits SHA-256 checksum = 264 bits = 24 × 11 bits.
     */
    fun seedToMnemonic(seedBytes: ByteArray): List<String> {
        require(seedBytes.size == 32) { "Seed must be 32 bytes" }
        require(wordList.isNotEmpty()) { "MnemonicManager not initialized" }

        val checksum = MessageDigest.getInstance("SHA-256").digest(seedBytes)
        // 256 bits + 8 bits checksum = 264 bits
        val data = seedBytes + byteArrayOf(checksum[0])

        // Convert to bit string and split into 11-bit groups
        val bits = StringBuilder()
        for (b in data) {
            bits.append(String.format("%8s", Integer.toBinaryString(b.toInt() and 0xFF)).replace(' ', '0'))
        }

        val words = mutableListOf<String>()
        for (i in 0 until 24) {
            val index = bits.substring(i * 11, i * 11 + 11).toInt(2)
            words.add(wordList[index])
        }
        data.fill(0)
        checksum.fill(0)
        bits.clear()
        return words
    }

    /**
     * Decode 24 BIP-39 words back into the 32-byte Ed25519 seed.
     */
    fun mnemonicToSeed(words: List<String>): ByteArray {
        require(words.size == 24) { "Mnemonic must be 24 words" }
        require(wordList.isNotEmpty()) { "MnemonicManager not initialized" }

        val bits = StringBuilder()
        for (word in words) {
            val index = wordList.indexOf(word.lowercase().trim())
            require(index >= 0) { "Unknown word: $word" }
            bits.append(String.format("%11s", Integer.toBinaryString(index)).replace(' ', '0'))
        }

        // 264 bits = 256 bits entropy + 8 bits checksum
        val allBytes = ByteArray(33)
        for (i in allBytes.indices) {
            allBytes[i] = bits.substring(i * 8, i * 8 + 8).toInt(2).toByte()
        }

        val seedBytes = allBytes.copyOfRange(0, 32)
        val checksumByte = allBytes[32]

        val expectedChecksum = MessageDigest.getInstance("SHA-256").digest(seedBytes)[0]
        allBytes.fill(0)
        bits.clear()
        require(checksumByte == expectedChecksum) { "Invalid checksum" }

        return seedBytes
    }

    /**
     * Validate a 24-word BIP-39 mnemonic (all words known + valid checksum).
     */
    fun validateMnemonic(words: List<String>): Boolean {
        if (words.size != 24 || wordList.isEmpty()) return false
        return try {
            mnemonicToSeed(words)
            true
        } catch (_: Exception) {
            false
        }
    }
}
