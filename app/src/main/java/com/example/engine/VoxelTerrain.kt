package com.example.engine

import com.example.data.model.CoverHeight
import com.example.data.model.VoxelTile
import com.example.data.model.VoxelType
import kotlin.math.sqrt
import kotlin.random.Random

class VoxelTerrain(
    val width: Int = 24,
    val height: Int = 24,
    val tileSize: Float = 64f
) {
    val tiles = Array(width) { x ->
        Array(height) { y ->
            VoxelTile(
                gridX = x,
                gridY = y,
                elevationZ = 0,
                type = VoxelType.FLOOR_DIRT,
                currentHp = 100f,
                maxHp = 100f,
                coverHeight = CoverHeight.NONE
            )
        }
    }

    var spawnPointX: Float = 120f
    var spawnPointY: Float = 120f
    var objectivePointX: Float = (width - 2) * tileSize
    var objectivePointY: Float = (height - 2) * tileSize

    fun generateProceduralMap(missionId: String, seed: Long = System.currentTimeMillis()) {
        val noiseGen = PerlinNoise(seed)
        val objectNoiseGen = PerlinNoise(seed + 9999L)
        val hazardNoiseGen = PerlinNoise(seed + 424242L)

        val spawnX = 2
        val spawnY = 2
        val objX = width - 3
        val objY = height - 3

        for (x in 0 until width) {
            for (y in 0 until height) {
                // Outer boundary walls
                if (x == 0 || y == 0 || x == width - 1 || y == height - 1) {
                    tiles[x][y] = VoxelTile(
                        gridX = x, gridY = y, elevationZ = 3,
                        type = VoxelType.HIGH_COVER_WALL,
                        currentHp = 500f, maxHp = 500f,
                        isDestructible = false,
                        coverHeight = CoverHeight.HIGH
                    )
                    continue
                }

                // Distance to start/end zones
                val distToSpawn = kotlin.math.hypot((x - spawnX).toDouble(), (y - spawnY).toDouble())
                val distToObj = kotlin.math.hypot((x - objX).toDouble(), (y - objY).toDouble())

                // Keep spawn and objective clearings walkable
                if (distToSpawn <= 2.2 || distToObj <= 2.2) {
                    tiles[x][y] = VoxelTile(
                        gridX = x, gridY = y, elevationZ = 0,
                        type = VoxelType.FLOOR_PLAZA,
                        currentHp = 100f, maxHp = 100f,
                        coverHeight = CoverHeight.NONE
                    )
                    continue
                }

                // Sample octave noise maps
                val elevationVal = noiseGen.octaveNoise(x * 0.18, y * 0.18, octaves = 3, persistence = 0.5)
                val objectDensityVal = objectNoiseGen.octaveNoise(x * 0.25, y * 0.25, octaves = 2, persistence = 0.5)
                val hazardVal = hazardNoiseGen.octaveNoise(x * 0.12, y * 0.12, octaves = 2, persistence = 0.5)

                // Tactical corridor check: line from spawn to objective
                val distToCorridor = pointToSegmentDistance(
                    px = x.toDouble(), py = y.toDouble(),
                    ax = spawnX.toDouble(), ay = spawnY.toDouble(),
                    bx = objX.toDouble(), by = objY.toDouble()
                )

                // Populate terrain & destructible environmental objects based on noise thresholds
                when {
                    // Alien Biomass Nests in High Hazard / Organic pockets
                    hazardVal > 0.72 && objectDensityVal in 0.50..0.70 -> {
                        tiles[x][y] = VoxelTile(
                            gridX = x, gridY = y, elevationZ = 2,
                            type = VoxelType.ALIEN_BIOMASS,
                            currentHp = 180f, maxHp = 180f,
                            isDestructible = true,
                            coverHeight = CoverHeight.HIGH
                        )
                    }

                    // Hazard acid pools in low elevation / high hazard noise pockets
                    hazardVal > 0.78 && objectDensityVal < 0.50 -> {
                        tiles[x][y] = VoxelTile(
                            gridX = x, gridY = y, elevationZ = 0,
                            type = VoxelType.ACID_POOL,
                            currentHp = 100f, maxHp = 100f,
                            isDestructible = false,
                            coverHeight = CoverHeight.NONE
                        )
                    }

                    // High Density Noise Cluster: Concrete Walls, Titanium Metal & Destructible Pillars
                    objectDensityVal > 0.70 -> {
                        // High walls or Pillars (avoid blocking main tactical corridor completely)
                        if (distToCorridor > 1.8) {
                            when {
                                elevationVal > 0.65 -> {
                                    tiles[x][y] = VoxelTile(
                                        gridX = x, gridY = y, elevationZ = 3,
                                        type = VoxelType.REINFORCED_METAL,
                                        currentHp = 300f, maxHp = 300f,
                                        isDestructible = true,
                                        coverHeight = CoverHeight.HIGH
                                    )
                                }
                                elevationVal > 0.50 -> {
                                    tiles[x][y] = VoxelTile(
                                        gridX = x, gridY = y, elevationZ = 2,
                                        type = VoxelType.CONCRETE_WALL,
                                        currentHp = 220f, maxHp = 220f,
                                        isDestructible = true,
                                        coverHeight = CoverHeight.HIGH
                                    )
                                }
                                else -> {
                                    tiles[x][y] = VoxelTile(
                                        gridX = x, gridY = y, elevationZ = 2,
                                        type = VoxelType.DESTRUCTIBLE_PILLAR,
                                        currentHp = 180f, maxHp = 180f,
                                        isDestructible = true,
                                        coverHeight = CoverHeight.HIGH
                                    )
                                }
                            }
                        } else {
                            // Low cover in corridor
                            tiles[x][y] = VoxelTile(
                                gridX = x, gridY = y, elevationZ = 1,
                                type = VoxelType.LOW_COVER_CRATE,
                                currentHp = 80f, maxHp = 80f,
                                isDestructible = true,
                                coverHeight = CoverHeight.LOW
                            )
                        }
                    }

                    // Medium Density Noise Cluster: Low Cover Crates & Energy Barriers
                    objectDensityVal in 0.54..0.70 -> {
                        if (elevationVal > 0.55) {
                            tiles[x][y] = VoxelTile(
                                gridX = x, gridY = y, elevationZ = 1,
                                type = VoxelType.ENERGY_BARRIER,
                                currentHp = 110f, maxHp = 110f,
                                isDestructible = true,
                                coverHeight = CoverHeight.LOW
                            )
                        } else {
                            tiles[x][y] = VoxelTile(
                                gridX = x, gridY = y, elevationZ = 1,
                                type = VoxelType.LOW_COVER_CRATE,
                                currentHp = 75f, maxHp = 75f,
                                isDestructible = true,
                                coverHeight = CoverHeight.LOW
                            )
                        }
                    }

                    // Explosive Plasma Barrel Hotspots
                    objectDensityVal in 0.48..0.54 && hazardVal > 0.60 -> {
                        tiles[x][y] = VoxelTile(
                            gridX = x, gridY = y, elevationZ = 1,
                            type = VoxelType.EXPLOSIVE_BARREL,
                            currentHp = 40f, maxHp = 40f,
                            isDestructible = true,
                            coverHeight = CoverHeight.LOW
                        )
                    }

                    // Base floor tiles (Plaza vs Dirt based on elevation noise)
                    else -> {
                        val isPlaza = elevationVal > 0.48
                        tiles[x][y] = VoxelTile(
                            gridX = x, gridY = y, elevationZ = 0,
                            type = if (isPlaza) VoxelType.FLOOR_PLAZA else VoxelType.FLOOR_DIRT,
                            currentHp = 100f, maxHp = 100f,
                            isDestructible = false,
                            coverHeight = CoverHeight.NONE
                        )
                    }
                }
            }
        }

        // Place Objective Node at destination tile
        tiles[objX][objY] = VoxelTile(
            gridX = objX, gridY = objY, elevationZ = 1,
            type = VoxelType.OBJECTIVE_NODE,
            currentHp = 300f, maxHp = 300f,
            isDestructible = true,
            coverHeight = CoverHeight.LOW
        )

        spawnPointX = (spawnX + 0.5f) * tileSize
        spawnPointY = (spawnY + 0.5f) * tileSize
        objectivePointX = (objX + 0.5f) * tileSize
        objectivePointY = (objY + 0.5f) * tileSize
    }

    private fun pointToSegmentDistance(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
        val l2 = (bx - ax) * (bx - ax) + (by - ay) * (by - ay)
        if (l2 == 0.0) return kotlin.math.hypot(px - ax, py - ay)
        val t = (((px - ax) * (bx - ax) + (py - ay) * (by - ay)) / l2).coerceIn(0.0, 1.0)
        val projX = ax + t * (bx - ax)
        val projY = ay + t * (by - ay)
        return kotlin.math.hypot(px - projX, py - projY)
    }

    fun getTileAtWorld(worldX: Float, worldY: Float): VoxelTile? {
        val gx = (worldX / tileSize).toInt()
        val gy = (worldY / tileSize).toInt()
        if (gx in 0 until width && gy in 0 until height) {
            return tiles[gx][gy]
        }
        return null
    }

    fun applyDamageToTile(
        gx: Int,
        gy: Int,
        damage: Float,
        hitAngleRad: Float? = null
    ): Boolean {
        if (gx !in 0 until width || gy !in 0 until height) return false
        val tile = tiles[gx][gy]
        if (!tile.isDestructible || tile.isDisintegrated) return false

        tile.currentHp -= damage
        tile.hitFlashTimer = 1.0f
        tile.damageCracksCount += Random.nextInt(1, 3)

        // Mesh deformation & compression calculations
        val hpRatio = (tile.currentHp / tile.maxHp).coerceIn(0f, 1f)
        val deformForce = (1.0f - hpRatio) * 6f

        if (hitAngleRad != null) {
            tile.deformationX += kotlin.math.cos(hitAngleRad) * deformForce * 0.8f
            tile.deformationY += kotlin.math.sin(hitAngleRad) * deformForce * 0.8f
        } else {
            tile.deformationX += (Random.nextFloat() * 2f - 1f) * deformForce * 0.5f
            tile.deformationY += (Random.nextFloat() * 2f - 1f) * deformForce * 0.5f
        }

        // Clamp max deformation shift
        tile.deformationX = tile.deformationX.coerceIn(-10f, 10f)
        tile.deformationY = tile.deformationY.coerceIn(-10f, 10f)

        // Asymmetric voxel mesh scale compression
        tile.meshScaleX = (0.75f + hpRatio * 0.25f).coerceIn(0.7f, 1.0f)
        tile.meshScaleY = (0.75f + hpRatio * 0.25f).coerceIn(0.7f, 1.0f)
        tile.rotationAngle += (Random.nextFloat() * 0.08f - 0.04f)

        if (tile.currentHp <= 0f) {
            tile.currentHp = 0f
            // Downgrade elevation structure
            when (tile.elevationZ) {
                3 -> {
                    tile.elevationZ = 1
                    tile.coverHeight = CoverHeight.LOW
                    tile.type = VoxelType.LOW_COVER_CRATE
                    tile.currentHp = 40f
                    tile.maxHp = 40f
                    tile.damageCracksCount = 2
                }
                2 -> {
                    tile.elevationZ = 1
                    tile.coverHeight = CoverHeight.LOW
                    tile.type = VoxelType.LOW_COVER_CRATE
                    tile.currentHp = 30f
                    tile.maxHp = 30f
                    tile.damageCracksCount = 2
                }
                else -> {
                    tile.elevationZ = 0
                    tile.coverHeight = CoverHeight.NONE
                    tile.type = VoxelType.FLOOR_PLAZA
                    tile.isDisintegrated = true
                }
            }
            return true // Tile destroyed/downgraded
        }
        return false
    }

    // Find nearest cover tile to a position
    fun findBestCoverNear(
        x: Float,
        y: Float,
        threatX: Float,
        threatY: Float,
        searchRadius: Float = 350f
    ): VoxelTile? {
        var bestTile: VoxelTile? = null
        var bestScore = -9999f

        val startGx = ((x - searchRadius) / tileSize).toInt().coerceIn(1, width - 2)
        val endGx = ((x + searchRadius) / tileSize).toInt().coerceIn(1, width - 2)
        val startGy = ((y - searchRadius) / tileSize).toInt().coerceIn(1, height - 2)
        val endGy = ((y + searchRadius) / tileSize).toInt().coerceIn(1, height - 2)

        for (gx in startGx..endGx) {
            for (gy in startGy..endGy) {
                val tile = tiles[gx][gy]
                if (tile.coverHeight == CoverHeight.NONE || tile.isDisintegrated) continue

                val tileWorldX = (gx + 0.5f) * tileSize
                val tileWorldY = (gy + 0.5f) * tileSize

                val distToEnemy = sqrt((tileWorldX - x) * (tileWorldX - x) + (tileWorldY - y) * (tileWorldY - y))
                if (distToEnemy > searchRadius) continue

                // Check if cover is between enemy and threat
                val dxThreat = threatX - tileWorldX
                val dyThreat = threatY - tileWorldY
                val dxEnemy = x - tileWorldX
                val dyEnemy = y - tileWorldY

                // Vector dot product alignment
                val coverScore = when (tile.coverHeight) {
                    CoverHeight.HIGH -> 100f
                    CoverHeight.LOW -> 50f
                    else -> 0f
                } - (distToEnemy * 0.1f)

                if (coverScore > bestScore) {
                    bestScore = coverScore
                    bestTile = tile
                }
            }
        }
        return bestTile
    }

    // Dynamic Sparse LOD evaluation
    fun updateLODLevels(cameraX: Float, cameraY: Float) {
        for (x in 0 until width) {
            for (y in 0 until height) {
                val worldX = (x + 0.5f) * tileSize
                val worldY = (y + 0.5f) * tileSize
                val dist = sqrt((worldX - cameraX) * (worldX - cameraX) + (worldY - cameraY) * (worldY - cameraY))
                tiles[x][y].lodLevel = if (dist < 420f) 0 else 1
            }
        }
    }
}
