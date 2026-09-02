package com.example.engine

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Control point definition for 1D and 2D Spline Curves.
 * Maps input value x (typically normalized noise in [0.0, 1.0]) to mapped output value y.
 */
data class SplineControlPoint(val x: Double, val y: Double)

/**
 * Non-linear Spline Curve evaluator utilizing Catmull-Rom cubic spline interpolation
 * to shape raw fractal noise distributions into geological terrain features, terraced elevation steps,
 * obstacle density profiles, and tactical corridor clearings.
 */
class SplineCurve(val controlPoints: List<SplineControlPoint>) {
    private val sortedPoints: List<SplineControlPoint> = controlPoints.sortedBy { it.x }

    init {
        require(sortedPoints.size >= 2) { "SplineCurve requires at least 2 control points." }
    }

    /**
     * Evaluates the spline curve at the given input [x] (clamped or normalized).
     * Uses 4-point Catmull-Rom cubic spline interpolation for smooth C1-continuous transitions.
     */
    fun evaluate(x: Double): Double {
        if (x <= sortedPoints.first().x) return sortedPoints.first().y
        if (x >= sortedPoints.last().x) return sortedPoints.last().y

        // Find the segment containing x
        var index = 0
        while (index < sortedPoints.size - 1 && sortedPoints[index + 1].x < x) {
            index++
        }

        val p1 = sortedPoints[index]
        val p2 = sortedPoints[index + 1]

        val p0 = if (index > 0) sortedPoints[index - 1] else p1
        val p3 = if (index + 2 < sortedPoints.size) sortedPoints[index + 2] else p2

        val segmentWidth = p2.x - p1.x
        if (segmentWidth <= 0.000001) return p1.y

        val t = (x - p1.x) / segmentWidth

        // Catmull-Rom cubic spline interpolation math:
        val t2 = t * t
        val t3 = t2 * t

        val y0 = p0.y
        val y1 = p1.y
        val y2 = p2.y
        val y3 = p3.y

        val c0 = y1
        val c1 = 0.5 * (y2 - y0)
        val c2 = y0 - 2.5 * y1 + 2.0 * y2 - 0.5 * y3
        val c3 = 0.5 * (y3 - y0) + 1.5 * (y1 - y2)

        return c0 + c1 * t + c2 * t2 + c3 * t3
    }

    companion object {
        /**
         * Terrain Elevation Spline:
         * Maps raw FBM noise to geological elevation layers:
         * Flat plains -> Stepped tactical platforms -> Reinforced concrete high-cover walls -> Fortress peaks
         */
        val ELEVATION = SplineCurve(
            listOf(
                SplineControlPoint(0.00, 0.00), // Deep trench / lowest level
                SplineControlPoint(0.30, 0.00), // Open tactical floor plaza (extended flat region)
                SplineControlPoint(0.48, 0.20), // Low cover platform step
                SplineControlPoint(0.62, 0.55), // Concrete wall tier
                SplineControlPoint(0.80, 0.85), // High reinforced titanium structure
                SplineControlPoint(1.00, 1.00)  // Fortress peak
            )
        )

        /**
         * Terraced Steps Elevation Spline:
         * Creates sharp plateau terracing with flat walking steps and steep vertical drops.
         */
        val STEPPED_TERRACE = SplineCurve(
            listOf(
                SplineControlPoint(0.00, 0.00),
                SplineControlPoint(0.25, 0.00),
                SplineControlPoint(0.30, 0.33),
                SplineControlPoint(0.55, 0.33),
                SplineControlPoint(0.60, 0.66),
                SplineControlPoint(0.85, 0.66),
                SplineControlPoint(0.90, 1.00),
                SplineControlPoint(1.00, 1.00)
            )
        )

        /**
         * Obstacle & Cover Density Spline:
         * Keeps open sightlines in corridors while clustering low/high cover obstacles together.
         */
        val OBJECT_DENSITY = SplineCurve(
            listOf(
                SplineControlPoint(0.00, 0.00), // Clear open sightlines
                SplineControlPoint(0.45, 0.05), // Sparse low crates
                SplineControlPoint(0.65, 0.40), // Medium cover barricades
                SplineControlPoint(0.82, 0.80), // Dense wall pillars
                SplineControlPoint(1.00, 1.00)  // Solid fortified bunker walls
            )
        )

        /**
         * Environmental Hazard Spline:
         * Creates isolated acid pools and organic alien biomass pockets with sharp hazard drop-offs.
         */
        val HAZARD = SplineCurve(
            listOf(
                SplineControlPoint(0.00, 0.00), // Zero hazard
                SplineControlPoint(0.60, 0.00), // Safe zone
                SplineControlPoint(0.72, 0.40), // Explosive barrel hotspot threshold
                SplineControlPoint(0.85, 0.85), // Acid pool / alien biomass nest
                SplineControlPoint(1.00, 1.00)  // Concentrated bio-hazard zone
            )
        )

        /**
         * Biome Climate Spline:
         * Maps temperature and moisture FBM noise to distinct environmental biome weights.
         */
        val BIOME_CLIMATE = SplineCurve(
            listOf(
                SplineControlPoint(0.00, 0.00), // Crater Outpost (default)
                SplineControlPoint(0.35, 0.20), // Crystalline Canyon transition
                SplineControlPoint(0.60, 0.65), // Toxic Wasteland transition
                SplineControlPoint(0.85, 0.90), // Alien Infestation zone
                SplineControlPoint(1.00, 1.00)
            )
        )

        /**
         * Tactical Pathing Corridor Spline:
         * Shapes navigable movement corridors with gentle ease-in/ease-out curves.
         */
        val CORRIDOR = SplineCurve(
            listOf(
                SplineControlPoint(0.00, 1.00), // Center of path (100% clear)
                SplineControlPoint(0.35, 0.85), // Inner corridor buffer
                SplineControlPoint(0.70, 0.20), // Edge cover transition
                SplineControlPoint(1.00, 0.00)  // Outside corridor
            )
        )
    }
}

/**
 * High-performance Fractal Brownian Motion (FBM) Noise Generator supporting 2D and 3D space.
 * Features customizable octaves, lacunarity, gain, Ridged Multifractal noise, Domain Warping,
 * and direct integration with non-linear [SplineCurve] terrain mapping.
 */
class FbmNoise(
    seed: Long,
    val defaultOctaves: Int = 4,
    val lacunarity: Double = 2.0,
    val gain: Double = 0.5
) {
    private val p = IntArray(512)

    init {
        val rand = Random(seed)
        val permutation = IntArray(256) { it }
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

    private fun grad2D(hash: Int, x: Double, y: Double): Double {
        val h = hash and 7
        val u = if (h < 4) x else y
        val v = if (h < 4) y else x
        return (if ((h and 1) == 0) u else -u) + (if ((h and 2) == 0) v else -v)
    }

    private fun grad3D(hash: Int, x: Double, y: Double, z: Double): Double {
        val h = hash and 15
        val u = if (h < 8) x else y
        val v = if (h < 4) y else if (h == 12 || h == 14) x else z
        return (if ((h and 1) == 0) u else -u) + (if ((h and 2) == 0) v else -v)
    }

    /**
     * Single octave 2D gradient noise in range [-1.0, 1.0].
     */
    fun rawNoise2D(x: Double, y: Double): Double {
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

        val x1 = lerp(u, grad2D(p[aa], xf, yf), grad2D(p[ba], xf - 1, yf))
        val x2 = lerp(u, grad2D(p[ab], xf, yf - 1), grad2D(p[bb], xf - 1, yf - 1))

        return lerp(v, x1, x2)
    }

    /**
     * Single octave 3D gradient noise in range [-1.0, 1.0].
     */
    fun rawNoise3D(x: Double, y: Double, z: Double): Double {
        val xi = floor(x).toInt() and 255
        val yi = floor(y).toInt() and 255
        val zi = floor(z).toInt() and 255

        val xf = x - floor(x)
        val yf = y - floor(y)
        val zf = z - floor(z)

        val u = fade(xf)
        val v = fade(yf)
        val w = fade(zf)

        val a = p[xi] + yi
        val aa = p[a] + zi
        val ab = p[a + 1] + zi
        val b = p[xi + 1] + yi
        val ba = p[b] + zi
        val bb = p[b + 1] + zi

        val x1 = lerp(u, grad3D(p[aa], xf, yf, zf), grad3D(p[ba], xf - 1, yf, zf))
        val x2 = lerp(u, grad3D(p[ab], xf, yf - 1, zf), grad3D(p[bb], xf - 1, yf - 1, zf))
        val y1 = lerp(v, x1, x2)

        val x3 = lerp(u, grad3D(p[aa + 1], xf, yf, zf - 1), grad3D(p[ba + 1], xf - 1, yf, zf - 1))
        val x4 = lerp(u, grad3D(p[ab + 1], xf, yf - 1, zf - 1), grad3D(p[bb + 1], xf - 1, yf - 1, zf - 1))
        val y2 = lerp(v, x3, x4)

        return lerp(w, y1, y2)
    }

    /**
     * Standard Fractal Brownian Motion (FBM) in 2D.
     * Combines multiple octaves to produce rich fractal detail.
     * Returns normalized value in range [0.0, 1.0].
     */
    fun eval(
        x: Double,
        y: Double,
        octaves: Int = defaultOctaves,
        lacunarity: Double = this.lacunarity,
        gain: Double = this.gain
    ): Double {
        var total = 0.0
        var frequency = 1.0
        var amplitude = 1.0
        var maxValue = 0.0

        for (i in 0 until octaves) {
            total += rawNoise2D(x * frequency, y * frequency) * amplitude
            maxValue += amplitude
            amplitude *= gain
            frequency *= lacunarity
        }

        val rawNormalized = total / maxValue
        return ((rawNormalized + 1.0) / 2.0).coerceIn(0.0, 1.0)
    }

    /**
     * Standard Fractal Brownian Motion (FBM) in 3D.
     * Returns normalized value in range [0.0, 1.0].
     */
    fun eval3D(
        x: Double,
        y: Double,
        z: Double,
        octaves: Int = defaultOctaves,
        lacunarity: Double = this.lacunarity,
        gain: Double = this.gain
    ): Double {
        var total = 0.0
        var frequency = 1.0
        var amplitude = 1.0
        var maxValue = 0.0

        for (i in 0 until octaves) {
            total += rawNoise3D(x * frequency, y * frequency, z * frequency) * amplitude
            maxValue += amplitude
            amplitude *= gain
            frequency *= lacunarity
        }

        val rawNormalized = total / maxValue
        return ((rawNormalized + 1.0) / 2.0).coerceIn(0.0, 1.0)
    }

    /**
     * Ridged Multifractal FBM in 2D.
     * Takes 1.0 - |noise| to create sharp mountain ridges, cliffs, ravines, and structural walls.
     * Returns normalized value in range [0.0, 1.0].
     */
    fun ridgedEval(
        x: Double,
        y: Double,
        octaves: Int = defaultOctaves,
        lacunarity: Double = this.lacunarity,
        gain: Double = this.gain
    ): Double {
        var total = 0.0
        var frequency = 1.0
        var amplitude = 1.0
        var maxValue = 0.0

        for (i in 0 until octaves) {
            val signal = 1.0 - abs(rawNoise2D(x * frequency, y * frequency))
            total += signal * signal * amplitude
            maxValue += amplitude
            amplitude *= gain
            frequency *= lacunarity
        }

        return (total / maxValue).coerceIn(0.0, 1.0)
    }

    /**
     * Domain Warping FBM in 2D.
     * Displaces (x, y) coordinates using secondary FBM evaluations to create organic, fluid biome borders,
     * winding rivers, and natural rock erosion curves.
     */
    fun domainWarpEval(
        x: Double,
        y: Double,
        warpStrength: Double = 0.5,
        octaves: Int = defaultOctaves
    ): Double {
        val qx = eval(x + 0.0, y + 0.0, octaves)
        val qy = eval(x + 5.2, y + 1.3, octaves)

        val rx = eval(x + warpStrength * qx + 1.7, y + warpStrength * qy + 9.2, octaves)
        val ry = eval(x + warpStrength * qx + 8.3, y + warpStrength * qy + 2.8, octaves)

        return eval(x + warpStrength * rx, y + warpStrength * ry, octaves)
    }

    /**
     * Evaluates FBM noise and passes the normalized result through a non-linear [SplineCurve].
     */
    fun evalWithSpline(
        x: Double,
        y: Double,
        spline: SplineCurve,
        octaves: Int = defaultOctaves
    ): Double {
        val rawFbm = eval(x, y, octaves)
        return spline.evaluate(rawFbm)
    }

    /**
     * Evaluates 3D FBM noise mapped through a non-linear [SplineCurve].
     */
    fun eval3DWithSpline(
        x: Double,
        y: Double,
        z: Double,
        spline: SplineCurve,
        octaves: Int = defaultOctaves
    ): Double {
        val rawFbm = eval3D(x, y, z, octaves)
        return spline.evaluate(rawFbm)
    }

    /**
     * Evaluates Ridged Multifractal FBM noise mapped through a non-linear [SplineCurve].
     */
    fun ridgedEvalWithSpline(
        x: Double,
        y: Double,
        spline: SplineCurve,
        octaves: Int = defaultOctaves
    ): Double {
        val rawRidged = ridgedEval(x, y, octaves)
        return spline.evaluate(rawRidged)
    }
}

/**
 * Backward-compatible PerlinNoise wrapper that delegates to [FbmNoise].
 * Ensures existing engine calls function seamlessly while upgrading underlying world building to FBM + Spline curves.
 */
class PerlinNoise(seed: Long) {
    private val fbm = FbmNoise(seed, defaultOctaves = 4)

    fun noise(x: Double, y: Double): Double = fbm.rawNoise2D(x, y)

    fun octaveNoise(x: Double, y: Double, octaves: Int, persistence: Double = 0.5): Double {
        return fbm.eval(x, y, octaves = octaves, gain = persistence)
    }
}

/**
 * 3D Simplex Noise generator based on Ken Perlin & Stefan Gustavson's simplex algorithm.
 * Evaluates continuous, isotropic 3D noise using simplex tetrahedra decomposition.
 * Features single-sample 3D noise, multi-octave fractal noise (FBM), and ridged multifractal noise
 * tailored for generating procedural terrain height maps, cavern overhangs, and geological features.
 */
class SimplexNoise3D(seed: Long = 1337L) {
    private val perm = IntArray(512)
    private val permMod12 = IntArray(512)

    init {
        reseed(seed)
    }

    fun reseed(seed: Long) {
        val rand = Random(seed)
        val p = IntArray(256) { it }
        for (i in 255 downTo 1) {
            val j = rand.nextInt(i + 1)
            val tmp = p[i]
            p[i] = p[j]
            p[j] = tmp
        }
        for (i in 0 until 512) {
            perm[i] = p[i and 255]
            permMod12[i] = perm[i] % 12
        }
    }

    /**
     * Evaluates 3D Simplex noise at (xin, yin, zin).
     * Output is normalized in range [-1.0, 1.0].
     */
    fun noise(xin: Double, yin: Double, zin: Double): Double {
        val s = (xin + yin + zin) * F3
        val i = floor(xin + s).toInt()
        val j = floor(yin + s).toInt()
        val k = floor(zin + s).toInt()

        val t = (i + j + k) * G3
        val X0 = i - t
        val Y0 = j - t
        val Z0 = k - t

        val x0 = xin - X0
        val y0 = yin - Y0
        val z0 = zin - Z0

        val i1: Int; val j1: Int; val k1: Int
        val i2: Int; val j2: Int; val k2: Int

        if (x0 >= y0) {
            if (y0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 1; k2 = 0
            } else if (x0 >= z0) {
                i1 = 1; j1 = 0; k1 = 0; i2 = 1; j2 = 0; k2 = 1
            } else {
                i1 = 0; j1 = 0; k1 = 1; i2 = 1; j2 = 0; k2 = 1
            }
        } else {
            if (y0 < z0) {
                i1 = 0; j1 = 0; k1 = 1; i2 = 0; j2 = 1; k2 = 1
            } else if (x0 < z0) {
                i1 = 0; j1 = 1; k1 = 0; i2 = 0; j2 = 1; k2 = 1
            } else {
                i1 = 0; j1 = 1; k1 = 0; i2 = 1; j2 = 1; k2 = 0
            }
        }

        val x1 = x0 - i1 + G3
        val y1 = y0 - j1 + G3
        val z1 = z0 - k1 + G3

        val x2 = x0 - i2 + 2.0 * G3
        val y2 = y0 - j2 + 2.0 * G3
        val z2 = z0 - k2 + 2.0 * G3

        val x3 = x0 - 1.0 + 3.0 * G3
        val y3 = y0 - 1.0 + 3.0 * G3
        val z3 = z0 - 1.0 + 3.0 * G3

        val ii = i and 255
        val jj = j and 255
        val kk = k and 255

        val gi0 = permMod12[ii + perm[jj + perm[kk]]]
        val gi1 = permMod12[ii + i1 + perm[jj + j1 + perm[kk + k1]]]
        val gi2 = permMod12[ii + i2 + perm[jj + j2 + perm[kk + k2]]]
        val gi3 = permMod12[ii + 1 + perm[jj + 1 + perm[kk + 1]]]

        var t0 = 0.6 - x0 * x0 - y0 * y0 - z0 * z0
        val n0 = if (t0 < 0.0) 0.0 else {
            t0 *= t0
            t0 * t0 * dot(GRAD3[gi0], x0, y0, z0)
        }

        var t1 = 0.6 - x1 * x1 - y1 * y1 - z1 * z1
        val n1 = if (t1 < 0.0) 0.0 else {
            t1 *= t1
            t1 * t1 * dot(GRAD3[gi1], x1, y1, z1)
        }

        var t2 = 0.6 - x2 * x2 - y2 * y2 - z2 * z2
        val n2 = if (t2 < 0.0) 0.0 else {
            t2 *= t2
            t2 * t2 * dot(GRAD3[gi2], x2, y2, z2)
        }

        var t3 = 0.6 - x3 * x3 - y3 * y3 - z3 * z3
        val n3 = if (t3 < 0.0) 0.0 else {
            t3 *= t3
            t3 * t3 * dot(GRAD3[gi3], x3, y3, z3)
        }

        return (32.0 * (n0 + n1 + n2 + n3)).coerceIn(-1.0, 1.0)
    }

    /**
     * Multi-octave Fractal Simplex Noise (FBM) in 3D.
     * Returns a normalized value in range [0.0, 1.0].
     */
    fun fractalNoise3D(
        x: Double,
        y: Double,
        z: Double,
        octaves: Int = 4,
        lacunarity: Double = 2.0,
        gain: Double = 0.5
    ): Double {
        var total = 0.0
        var frequency = 1.0
        var amplitude = 1.0
        var maxAmp = 0.0

        for (i in 0 until octaves) {
            total += noise(x * frequency, y * frequency, z * frequency) * amplitude
            maxAmp += amplitude
            amplitude *= gain
            frequency *= lacunarity
        }

        val normalized = total / maxAmp
        return ((normalized + 1.0) / 2.0).coerceIn(0.0, 1.0)
    }

    /**
     * Ridged Multifractal 3D Simplex Noise for sharp geological cliffs, canyon walls, and crags.
     * Returns a normalized value in range [0.0, 1.0].
     */
    fun ridgedNoise3D(
        x: Double,
        y: Double,
        z: Double,
        octaves: Int = 4,
        lacunarity: Double = 2.0,
        gain: Double = 0.5
    ): Double {
        var total = 0.0
        var frequency = 1.0
        var amplitude = 1.0
        var maxAmp = 0.0

        for (i in 0 until octaves) {
            val raw = noise(x * frequency, y * frequency, z * frequency)
            val signal = 1.0 - abs(raw)
            total += signal * signal * amplitude
            maxAmp += amplitude
            amplitude *= gain
            frequency *= lacunarity
        }

        return (total / maxAmp).coerceIn(0.0, 1.0)
    }

    /**
     * Evaluates 3D Simplex Fractal noise mapped through a non-linear [SplineCurve].
     */
    fun eval3DWithSpline(
        x: Double,
        y: Double,
        z: Double,
        spline: SplineCurve,
        octaves: Int = 4
    ): Double {
        val raw = fractalNoise3D(x, y, z, octaves = octaves)
        return spline.evaluate(raw)
    }

    companion object {
        private const val F3 = 1.0 / 3.0
        private const val G3 = 1.0 / 6.0

        private val GRAD3 = arrayOf(
            intArrayOf(1, 1, 0), intArrayOf(-1, 1, 0), intArrayOf(1, -1, 0), intArrayOf(-1, -1, 0),
            intArrayOf(1, 0, 1), intArrayOf(-1, 0, 1), intArrayOf(1, 0, -1), intArrayOf(-1, 0, -1),
            intArrayOf(0, 1, 1), intArrayOf(0, -1, 1), intArrayOf(0, 1, -1), intArrayOf(0, -1, -1)
        )

        private fun dot(g: IntArray, x: Double, y: Double, z: Double): Double {
            return g[0] * x + g[1] * y + g[2] * z
        }
    }
}
