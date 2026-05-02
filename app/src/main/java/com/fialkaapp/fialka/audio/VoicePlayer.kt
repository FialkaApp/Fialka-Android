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

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import java.util.Arrays
import java.util.concurrent.atomic.AtomicBoolean

/**
 * VoicePlayer — in-memory OGG/Opus playback without writing to disk.
 *
 * Security properties:
 * - The caller passes already-decrypted OGG/Opus bytes (ByteArray) obtained from
 *   CryptoManager.decryptFile(). These bytes live only in memory.
 * - PCM buffers are zeroed immediately after being written to AudioTrack.
 * - No temp file is created at any point.
 * - The decrypted byte array should be zeroed by the caller after play() returns.
 *
 * Usage:
 *   val player = VoicePlayer()
 *   player.play(opusBytes, onProgress = { posMs, durationMs -> ... }, onFinish = { ... })
 *   // later:
 *   player.stop()
 *
 * Thread safety: play() runs the decode loop on a background thread.
 * stop() is safe to call from any thread.
 */
class VoicePlayer {

    private val stopSignal = AtomicBoolean(false)
    private var playThread: Thread? = null

    /** True if currently playing. */
    var isPlaying: Boolean = false
        private set

    /**
     * In-memory MediaDataSource backed by a ByteArray.
     * Required for MediaExtractor to read from memory rather than disk.
     */
    private class ByteArrayMediaDataSource(private val data: ByteArray) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= data.size) return -1
            val available = minOf(size.toLong(), data.size - position).toInt()
            System.arraycopy(data, position.toInt(), buffer, offset, available)
            return available
        }

        override fun getSize(): Long = data.size.toLong()
        override fun close() { /* intentionally empty — caller zeros the array */ }
    }

    /**
     * Start async playback of the provided OGG/Opus bytes.
     *
     * @param audioBytes  Decrypted OGG/Opus container bytes (never written to disk).
     * @param onProgress  Called periodically with (currentPositionMs, totalDurationMs).
     * @param onFinish    Called when playback completes normally.
     * @param onError     Called if decoding fails.
     */
    fun play(
        audioBytes: ByteArray,
        onProgress: ((currentMs: Long, totalMs: Long) -> Unit)? = null,
        onFinish: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (isPlaying) stop()
        stopSignal.set(false)
        isPlaying = true

        val thread = Thread {
            try {
                playInternal(audioBytes, onProgress, onFinish)
            } catch (e: Exception) {
                onError?.invoke(e.message ?: "Playback error")
            } finally {
                isPlaying = false
            }
        }
        thread.isDaemon = true
        thread.start()
        playThread = thread
    }

    private fun playInternal(
        audioBytes: ByteArray,
        onProgress: ((Long, Long) -> Unit)?,
        onFinish: (() -> Unit)?
    ) {
        val extractor = MediaExtractor()
        extractor.setDataSource(ByteArrayMediaDataSource(audioBytes))

        // Find the audio track
        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = trackFormat
                break
            }
        }

        if (audioTrackIndex < 0 || format == null) {
            extractor.release()
            throw IllegalStateException("No audio track found in voice message")
        }

        extractor.selectTrack(audioTrackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION))
            format.getLong(MediaFormat.KEY_DURATION) else 0L
        val durationMs = durationUs / 1000

        // Configure MediaCodec decoder
        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        // Configure AudioTrack
        val channelMask = if (channelCount == 1)
            AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
        val minBufSize = AudioTrack.getMinBufferSize(
            sampleRate, channelMask, AudioFormat.ENCODING_PCM_16BIT
        )
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setBufferSizeInBytes(minBufSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack.play()

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false
        var lastProgressMs = -1L

        try {
            while (!outputDone && !stopSignal.get()) {
                // Feed input
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val inputBuf = codec.getInputBuffer(inIdx)!!
                        val sampleSize = extractor.readSampleData(inputBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(
                                inIdx, 0, sampleSize, extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                // Drain output
                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIdx >= 0) {
                    val outputBuf = codec.getOutputBuffer(outIdx)
                    if (outputBuf != null && bufferInfo.size > 0) {
                        val pcmData = ByteArray(bufferInfo.size)
                        outputBuf.get(pcmData)
                        audioTrack.write(pcmData, 0, pcmData.size)

                        // Progress callback (at most once every 100ms)
                        val currentMs = bufferInfo.presentationTimeUs / 1000
                        if (currentMs - lastProgressMs >= 100) {
                            lastProgressMs = currentMs
                            onProgress?.invoke(currentMs, durationMs)
                        }

                        // Zero PCM buffer immediately after writing to AudioTrack
                        Arrays.fill(pcmData, 0)
                    }
                    codec.releaseOutputBuffer(outIdx, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }

            // Wait for AudioTrack to finish draining before releasing
            if (!stopSignal.get()) {
                audioTrack.stop()
                onFinish?.invoke()
            } else {
                audioTrack.pause()
                audioTrack.flush()
            }
        } finally {
            try { audioTrack.release() } catch (_: Exception) {}
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            isPlaying = false
        }
    }

    /**
     * Stop playback. Safe to call from any thread, including UI thread.
     */
    fun stop() {
        stopSignal.set(true)
        playThread?.interrupt()
        playThread = null
        isPlaying = false
    }

    /**
     * Format duration in ms to mm:ss string for display.
     */
    fun formatDuration(ms: Int): String {
        val totalSec = ms / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }
}
