package com.example.engine

import org.junit.Assert.*
import org.junit.Test

class VoxelManagerProceduralTest {

    @Test
    fun testSimplexNoise3DValueRanges() {
        val simplex = SimplexNoise3D(42L)

        for (i in 0 until 100) {
            val x = i * 0.13
            val y = i * 0.27
            val z = i * 0.41

            val raw = simplex.noise(x, y, z)
            assertTrue("Raw 3D Simplex noise should be >= -1.0, was $raw", raw >= -1.0)
            assertTrue("Raw 3D Simplex noise should be <= 1.0, was $raw", raw <= 1.0)

            val fractal = simplex.fractalNoise3D(x, y, z, octaves = 4)
            assertTrue("Fractal 3D Simplex noise should be >= 0.0, was $fractal", fractal >= 0.0)
            assertTrue("Fractal 3D Simplex noise should be <= 1.0, was $fractal", fractal <= 1.0)

            val ridged = simplex.ridgedNoise3D(x, y, z, octaves = 3)
            assertTrue("Ridged 3D Simplex noise should be >= 0.0, was $ridged", ridged >= 0.0)
            assertTrue("Ridged 3D Simplex noise should be <= 1.0, was $ridged", ridged <= 1.0)
        }
    }

    @Test
    fun testSimplexNoise3DDeterminismAndVariation() {
        val simplex1 = SimplexNoise3D(12345L)
        val simplex2 = SimplexNoise3D(12345L)
        val simplexOtherSeed = SimplexNoise3D(99999L)

        val v1 = simplex1.noise(1.23, 2.45, 3.67)
        val v2 = simplex2.noise(1.23, 2.45, 3.67)
        val vDiff = simplexOtherSeed.noise(1.23, 2.45, 3.67)

        assertEquals("Same seed must yield deterministic 3D Simplex noise", v1, v2, 0.00001)
        assertNotEquals("Different seeds should yield different noise", v1, vDiff, 0.00001)
    }

    @Test
    fun testGenerateHeightMapVariedTerrain() {
        val width = 32
        val height = 32
        val depth = 6
        val manager = VoxelManager(width = width, height = height, depth = depth)

        val heightMap = manager.generateHeightMap(seed = 1337L)
        assertNotNull(heightMap)
        assertEquals(width, heightMap.size)
        assertEquals(height, heightMap[0].size)

        var minH = Float.MAX_VALUE
        var maxH = Float.MIN_VALUE
        val heights = mutableSetOf<Float>()

        for (x in 0 until width) {
            for (y in 0 until height) {
                val h = manager.getHeightAt(x, y)
                assertTrue("Height must be >= 0f", h >= 0f)
                assertTrue("Height must be <= depth - 1", h <= (depth - 1).toFloat())
                if (h < minH) minH = h
                if (h > maxH) maxH = h
                heights.add(h)
            }
        }

        // Must produce varied heights, not a flat monotonous surface
        assertTrue("Terrain height map must have variation across points", heights.size > 10)
        assertTrue("Max height should exceed min height significantly", maxH > minH)
    }

    @Test
    fun testHeightMapAccessors() {
        val manager = VoxelManager(width = 16, height = 16, depth = 6, voxelSize = 32f)
        manager.generateHeightMap(seed = 777L)

        val h = manager.getHeightAt(4, 4)
        val normH = manager.getNormalizedHeightAt(4, 4)
        assertEquals(h / 5f, normH, 0.01f)

        // World coordinates mapping
        val worldH = manager.getHeightAtWorld(4f * 32f + 16f, 4f * 32f + 16f)
        assertEquals(h, worldH, 0.001f)

        // Out of bounds safety
        assertEquals(0f, manager.getHeightAt(-5, -5), 0.001f)
        assertEquals(0f, manager.getHeightAt(100, 100), 0.001f)
    }

    @Test
    fun testGenerateProceduralWorldPopulation() {
        val width = 20
        val height = 20
        val depth = 6
        val manager = VoxelManager(width = width, height = height, depth = depth)

        manager.generateProceduralWorld(seed = 424242L)

        // Ground floor (z=0) must be populated
        for (x in 0 until width) {
            for (y in 0 until height) {
                val groundBlock = manager.getBlock(x, y, 0)
                assertNotNull("Ground floor block must exist", groundBlock)
            }
        }

        // Check that structures are generated on higher levels based on height map
        var solidUpperBlockCount = 0
        for (x in 0 until width) {
            for (y in 0 until height) {
                for (z in 1 until depth) {
                    val block = manager.getBlock(x, y, z)
                    if (block != null && block.isSolid) {
                        solidUpperBlockCount++
                    }
                }
            }
        }

        assertTrue("Procedural world must populate solid upper blocks according to height map", solidUpperBlockCount > 0)

        // Verify biome distribution is computed
        val distribution = manager.getBiomeDistribution()
        assertTrue("Biome distribution must not be empty", distribution.isNotEmpty())
    }
}
