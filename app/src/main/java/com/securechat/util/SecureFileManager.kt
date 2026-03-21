/*
 * SecureChat — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.securechat.util

import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom

/**
 * Secure file deletion — overwrites file content before unlinking.
 * Prevents recovery via NAND forensics on unencrypted flash storage.
 */
object SecureFileManager {

    private val secureRandom = SecureRandom()

    /**
     * Securely delete a file by overwriting its content with random data
     * before calling delete(). Two passes: random then zeros.
     */
    fun secureDelete(file: File) {
        if (!file.exists() || !file.isFile) return
        val fileSize = file.length()
        if (fileSize <= 0) {
            file.delete()
            return
        }
        try {
            val buf = ByteArray(8192)
            FileOutputStream(file).use { fos ->
                // Pass 1: random data
                var remaining = fileSize
                while (remaining > 0) {
                    secureRandom.nextBytes(buf)
                    val toWrite = minOf(buf.size.toLong(), remaining).toInt()
                    fos.write(buf, 0, toWrite)
                    remaining -= toWrite
                }
                fos.fd.sync()
            }
            FileOutputStream(file).use { fos ->
                // Pass 2: zeros
                buf.fill(0)
                var remaining = fileSize
                while (remaining > 0) {
                    val toWrite = minOf(buf.size.toLong(), remaining).toInt()
                    fos.write(buf, 0, toWrite)
                    remaining -= toWrite
                }
                fos.fd.sync()
            }
        } catch (_: Exception) {
            // Best effort — even if overwrite fails, still delete
        }
        file.delete()
    }

    /**
     * Securely delete all files in a directory, then remove the directory.
     */
    fun secureDeleteDirectory(dir: File) {
        if (!dir.exists()) return
        dir.walkBottomUp().forEach { file ->
            if (file.isFile) secureDelete(file)
            else file.delete()
        }
    }
}
