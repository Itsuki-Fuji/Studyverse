package com.example.notifications

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin
import kotlin.random.Random

/**
 * Generates soothing, soft brown-noise focus ambience & gentle bell chime via AudioTrack.
 */
class AmbientSoundPlayer {
    private var playJob: Job? = null
    private var audioTrack: AudioTrack? = null

    fun startAmbient(scope: CoroutineScope) {
        if (playJob?.isActive == true) return
        playJob = scope.launch(Dispatchers.IO) {
            val sampleRate = 22050
            val bufferSamples = 2048
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(bufferSamples * 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            track.play()

            val buffer = ShortArray(bufferSamples)
            var lastOut = 0.0
            while (isActive) {
                for (i in buffer.indices) {
                    val white = Random.nextDouble(-1.0, 1.0)
                    lastOut = (lastOut + 0.02 * white) / 1.02
                    val sample = (lastOut * 0.35 * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    buffer[i] = sample.toShort()
                }
                track.write(buffer, 0, buffer.size)
            }
        }
    }

    fun stopAmbient() {
        playJob?.cancel()
        playJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
    }

    fun playCompletionChime(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            val sampleRate = 22050
            val durationSamples = sampleRate / 2
            val buffer = ShortArray(durationSamples)
            val freq = 528.0
            for (i in buffer.indices) {
                val t = i.toDouble() / sampleRate
                val env = (1.0 - (i.toDouble() / durationSamples)).let { it * it }
                buffer[i] = (sin(2.0 * Math.PI * freq * t) * env * 0.4 * Short.MAX_VALUE).toInt().toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            try {
                track.write(buffer, 0, buffer.size)
                track.play()
                Thread.sleep(550)
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }
    }
}
