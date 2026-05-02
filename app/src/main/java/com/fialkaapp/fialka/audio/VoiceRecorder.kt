/*
 * Fialka — Post-quantum encrypted messenger
 * Copyright (C) 2024-2026 DevBot667
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.fialkaapp.fialka.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileInputStream

/**
 * VoiceRecorder — in-memory OGG/Opus recording via pipe.
 *
 * Security properties:
 * - Audio data NEVER touches disk unencrypted: MediaRecorder writes to a pipe fd,
 *   we drain it into a ByteArrayOutputStream entirely in memory.
 * - On stop(), PCM data is already discarded — only Opus frames remain in OGG container.
 * - The returned ByteArray is encrypted by the caller (CryptoManager.encryptFile) before
 *   it is persisted or transmitted.
 *
 * Waveform: amplitude is sampled from MediaRecorder.maxAmplitude at ~100ms intervals
 * and normalized to 0-100 range. Stored alongside the audio for display in the chat bubble.
 *
 * Max duration: 120 seconds (configurable via MAX_DURATION_MS).
 * On overflow: recording is stopped automatically and the result is returned.
 */
class VoiceRecorder(private val context: Context) {

    companion object {
        const val MAX_DURATION_MS = 120_000
        private const val WAVEFORM_SAMPLE_INTERVAL_MS = 150L
        private const val WAVEFORM_MAX_SAMPLES = 60 // ~9s worth at 150ms
    }

    /** OGG/Opus bytes produced by the last recording. Null if not yet recorded. */
    var recordedBytes: ByteArray? = null
        private set

    /** Duration in ms of the last recording. */
    var durationMs: Int = 0
        private set

    /** Normalized waveform amplitudes (0-100), comma-separated. */
    var waveformData: String = ""
        private set

    /** Whether a recording is currently in progress. */
    var isRecording: Boolean = false
        private set

    private var recorder: MediaRecorder? = null
    private var pipePair: Array<ParcelFileDescriptor>? = null
    private var readerThread: Thread? = null
    private var outputStream: ByteArrayOutputStream? = null
    private var startTimeMs: Long = 0L
    private val amplitudeSamples = mutableListOf<Int>()
    private var samplerThread: Thread? = null

    /**
     * Start recording. Must be called from a coroutine (suspending while the pipe reader spins up).
     * Throws if RECORD_AUDIO permission is not granted.
     */
    fun start() {
        if (isRecording) return

        amplitudeSamples.clear()
        val output = ByteArrayOutputStream()
        outputStream = output

        // Create a pipe: write end → MediaRecorder, read end → our ByteArrayOutputStream
        val pipe = ParcelFileDescriptor.createPipe()
        pipePair = pipe
        val readFd = pipe[0]
        val writeFd = pipe[1]

        // Background thread draining the pipe
        val reader = Thread {
            try {
                FileInputStream(readFd.fileDescriptor).use { stream ->
                    val buf = ByteArray(8192)
                    var n: Int
                    while (stream.read(buf).also { n = it } >= 0) {
                        output.write(buf, 0, n)
                    }
                }
            } catch (_: Exception) {
                // Pipe closed — normal termination
            } finally {
                try { readFd.close() } catch (_: Exception) {}
            }
        }
        reader.isDaemon = true
        reader.start()
        readerThread = reader

        startTimeMs = System.currentTimeMillis()

        recorder = MediaRecorder(context).apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.OGG)
            setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            setAudioSamplingRate(16000)
            setAudioChannels(1)
            setAudioEncodingBitRate(24_000) // 24kbps — good quality / low size
            setMaxDuration(MAX_DURATION_MS)
            setOutputFile(writeFd.fileDescriptor)
            setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                    stopAndFinalize()
                }
            }
            prepare()
            start()
        }

        // Close write end from our side — MediaRecorder owns it now
        try { writeFd.close() } catch (_: Exception) {}

        isRecording = true

        // Amplitude sampler thread
        val sampler = Thread {
            try {
                while (isRecording) {
                    Thread.sleep(WAVEFORM_SAMPLE_INTERVAL_MS)
                    if (!isRecording) break
                    val amp = recorder?.maxAmplitude ?: 0
                    // Normalize: maxAmplitude is 0-32767
                    val normalized = (amp * 100 / 32767).coerceIn(0, 100)
                    synchronized(amplitudeSamples) {
                        if (amplitudeSamples.size < WAVEFORM_MAX_SAMPLES) {
                            amplitudeSamples.add(normalized)
                        }
                    }
                }
            } catch (_: InterruptedException) {}
        }
        sampler.isDaemon = true
        sampler.start()
        samplerThread = sampler
    }

    /**
     * Stop the recording and return the raw OGG/Opus bytes.
     * This is a blocking call — it waits for the pipe reader to drain.
     */
    fun stop(): ByteArray {
        return stopAndFinalize()
    }

    private fun stopAndFinalize(): ByteArray {
        if (!isRecording) return recordedBytes ?: ByteArray(0)
        isRecording = false

        samplerThread?.interrupt()
        samplerThread = null

        durationMs = (System.currentTimeMillis() - startTimeMs).coerceAtMost(MAX_DURATION_MS.toLong()).toInt()

        try {
            recorder?.stop()
        } catch (_: Exception) {}
        try {
            recorder?.release()
        } catch (_: Exception) {}
        recorder = null

        // Wait for pipe reader to finish (pipe auto-closes when recorder releases the write fd)
        try { readerThread?.join(3000) } catch (_: InterruptedException) {}
        readerThread = null

        val bytes = outputStream?.toByteArray() ?: ByteArray(0)
        outputStream = null
        pipePair = null

        recordedBytes = bytes

        // Build waveform string
        val samples = synchronized(amplitudeSamples) { amplitudeSamples.toList() }
        waveformData = samples.joinToString(",")

        return bytes
    }

    /**
     * Cancel the recording without returning data.
     */
    fun cancel() {
        isRecording = false
        samplerThread?.interrupt()
        samplerThread = null
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { readerThread?.interrupt() } catch (_: Exception) {}
        readerThread = null
        outputStream = null
        pipePair = null
        recordedBytes = null
        durationMs = 0
        waveformData = ""
        amplitudeSamples.clear()
    }

    /** Current elapsed recording time in ms. */
    fun elapsedMs(): Long = if (isRecording) System.currentTimeMillis() - startTimeMs else 0L
}
