package com.example.engine

import kotlin.math.floor
import kotlin.random.Random

/**
 * Fast 2D Perlin Noise implementation for procedural voxel terrain and environmental object generation.
 */
class PerlinNoise(seed: Long) {
    private val p = IntArray(512)

    init {
        val rand = Random(seed)
        val permutation = IntArray(256) { it }
        // Shuffle permutation table
        for (i in 255 downTo 1) {
            val j = rand.nextInt(i + 1)
            val tmp = permutation[i]
            permutation[i] = permutation[j]
            permutation[j] = tmp
        }
        for (i in 0 until 512) {
            p[i] = permutation[i and 255]
        }
    }

    private fun fade(t: Double): Double = t * t * t * (t * (t * 6 - 15) + 10)

    private fun lerp(t: Double, a: Double, b: Double): Double = a + t * (b - a)

    private fun grad(hash: Int, x: Double, y: Double): Double {
        val h = hash and 7
        val u = if (h < 4) x else y
        val v = if (h < 4) y else x
        return (if ((h and 1) == 0) u else -u) + (if ((h and 2) == 0) v else -v)
    }

    fun noise(x: Double, y: Double): Double {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255

        val xf = x - floor(x)
        val yf = y - floor(y)

        val u = fade(xf)
        val v = fade(yf)

        val aa = p[p[xi] + yi]
        val ab = p[p[xi] + yi + 1]
        val ba = p[p[xi + 1] + yi]
        val bb = p[p[xi + 1] + yi + 1]

        val x1 = lerp(u, grad(p[aa], xf, yf), grad(p[ba], xf - 1, yf))
        val x2 = lerp(u, grad(p[ab], xf, yf - 1), grad(p[bb], xf - 1, yf - 1))

        return lerp(v, x1, x2)
    }

    /**
     * Normalized Octave Fractal Noise returning range [0.0, 1.0]
     */
    fun octaveNoise(x: Double, y: Double, octaves: Int, persistence: Double = 0.5): Double {
        var total = 0.0
        var frequency = 1.0
        var amplitude = 1.0
        var maxValue = 0.0

        for (i in 0 until octaves) {
            total += noise(x * frequency, y * frequency) * amplitude
            maxValue += amplitude
            amplitude *= persistence
            frequency *= 2.0
        }

        // Normalize from [-1, 1] to [0, 1]
        val rawNormalized = (total / maxValue)
        return ((rawNormalized + 1.0) / 2.0).coerceIn(0.0, 1.0)
    }
}
