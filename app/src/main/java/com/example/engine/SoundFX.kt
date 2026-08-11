package com.example.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.sin

object SoundFX {
    private val scope = CoroutineScope(Dispatchers.Default)

    enum class SoundType {
        LASER_SHOT, PLASMA_BLAST, EXPLOSION, STEALTH_TAKEDOWN, RELOAD, HIT_SHIELD, ALERT_SIREN, MISSION_WIN, HIT_WALL, RICOCHET
    }

    fun play(sound: SoundType) {
        scope.launch {
            try {
                when (sound) {
                    SoundType.LASER_SHOT -> generatePew(800f, 200f, 80)
                    SoundType.PLASMA_BLAST -> generatePew(400f, 80f, 160)
                    SoundType.EXPLOSION -> generateNoise(250)
                    SoundType.STEALTH_TAKEDOWN -> generatePew(300f, 100f, 100)
                    SoundType.RELOAD -> generateClickSeries(120)
                    SoundType.HIT_SHIELD -> generatePew(1200f, 600f, 60)
                    SoundType.ALERT_SIREN -> generateSiren(600f, 900f, 200)
                    SoundType.MISSION_WIN -> generateFanfare()
                    SoundType.HIT_WALL -> generatePew(500f, 150f, 40)
                    SoundType.RICOCHET -> generatePew(1400f, 2200f, 60)
                }
            } catch (e: Exception) {
                // Ignore audio hardware exception fallback
            }
        }
    }

    private fun generatePew(startFreq: Float, endFreq: Float, durationMs: Int) {
        val sampleRate = 22050
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            val freq = startFreq + (endFreq - startFreq) * t
            val angle = 2.0 * Math.PI * freq * (i.toDouble() / sampleRate)
            val envelope = 1.0 - t
            buffer[i] = (sin(angle) * 32767 * envelope * 0.4).toInt().toShort()
        }
        playRawPCM(buffer, sampleRate)
    }

    private fun generateNoise(durationMs: Int) {
        val sampleRate = 22050
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            val envelope = (1.0 - t) * (1.0 - t)
            val noise = (Math.random() * 2.0 - 1.0)
            buffer[i] = (noise * 32767 * envelope * 0.5).toInt().toShort()
        }
        playRawPCM(buffer, sampleRate)
    }

    private fun generateClickSeries(durationMs: Int) {
        val sampleRate = 22050
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val isClick = (i % 600) < 50
            buffer[i] = if (isClick) (30000 * (1 - i.toDouble() / numSamples)).toInt().toShort() else 0
        }
        playRawPCM(buffer, sampleRate)
    }

    private fun generateSiren(freq1: Float, freq2: Float, durationMs: Int) {
        val sampleRate = 22050
        val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
        val buffer = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / numSamples
            val freq = if ((i / 800) % 2 == 0) freq1 else freq2
            val angle = 2.0 * Math.PI * freq * (i.toDouble() / sampleRate)
            buffer[i] = (sin(angle) * 25000 * (1 - t)).toInt().toShort()
        }
        playRawPCM(buffer, sampleRate)
    }

    private fun generateFanfare() {
        val sampleRate = 22050
        val notes = floatArrayOf(440f, 554f, 659f, 880f)
        val noteDur = 120
        for (f in notes) {
            generatePew(f, f * 1.05f, noteDur)
            try { Thread.sleep(60) } catch (e: Exception) {}
        }
    }

    private fun playRawPCM(buffer: ShortArray, sampleRate: Int) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
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

        track.write(buffer, 0, buffer.size)
        track.play()
    }
}
