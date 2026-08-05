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
        val rand = Random(seed)

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

                // Default floor
                val isPlaza = (x + y) % 4 == 0 || (x in 8..16 && y in 8..16)
                tiles[x][y] = VoxelTile(
                    gridX = x, gridY = y, elevationZ = 0,
                    type = if (isPlaza) VoxelType.FLOOR_PLAZA else VoxelType.FLOOR_DIRT,
                    currentHp = 100f, maxHp = 100f,
                    coverHeight = CoverHeight.NONE
                )

                // Skip spawn zone (top-left) and objective zone (bottom-right)
                if ((x in 0..3 && y in 0..3) || (x in (width - 4)..<width && y in (height - 4)..<height)) {
                    continue
                }

                // Procedural cover & obstacle generation
                val roll = rand.nextFloat()
                when {
                    roll < 0.12f -> {
                        // Low cover crate (nano crate)
                        tiles[x][y] = VoxelTile(
                            gridX = x, gridY = y, elevationZ = 1,
                            type = VoxelType.LOW_COVER_CRATE,
                            currentHp = 60f, maxHp = 60f,
                            coverHeight = CoverHeight.LOW
                        )
                    }
                    roll < 0.22f -> {
                        // High cover destructible wall
                        tiles[x][y] = VoxelTile(
                            gridX = x, gridY = y, elevationZ = 2,
                            type = VoxelType.HIGH_COVER_WALL,
                            currentHp = 120f, maxHp = 120f,
                            coverHeight = CoverHeight.HIGH
                        )
                    }
                    roll < 0.26f -> {
                        // Explosive Plasma Barrel
                        tiles[x][y] = VoxelTile(
                            gridX = x, gridY = y, elevationZ = 1,
                            type = VoxelType.EXPLOSIVE_BARREL,
                            currentHp = 35f, maxHp = 35f,
                            coverHeight = CoverHeight.LOW
                        )
                    }
                    roll < 0.30f -> {
                        // Energy Shield Barrier
                        tiles[x][y] = VoxelTile(
                            gridX = x, gridY = y, elevationZ = 1,
                            type = VoxelType.ENERGY_BARRIER,
                            currentHp = 90f, maxHp = 90f,
                            coverHeight = CoverHeight.LOW
                        )
                    }
                    roll < 0.34f -> {
                        // Destructible Energy Pillar
                        tiles[x][y] = VoxelTile(
                            gridX = x, gridY = y, elevationZ = 3,
                            type = VoxelType.DESTRUCTIBLE_PILLAR,
                            currentHp = 180f, maxHp = 180f,
                            coverHeight = CoverHeight.HIGH
                        )
                    }
                    roll < 0.38f -> {
                        // Acid pool hazard
                        tiles[x][y] = VoxelTile(
                            gridX = x, gridY = y, elevationZ = 0,
                            type = VoxelType.ACID_POOL,
                            currentHp = 100f, maxHp = 100f,
                            coverHeight = CoverHeight.NONE,
                            isDestructible = false
                        )
                    }
                }
            }
        }

        // Set objective tile
        val objX = width - 2
        val objY = height - 2
        tiles[objX][objY] = VoxelTile(
            gridX = objX, gridY = objY, elevationZ = 1,
            type = VoxelType.OBJECTIVE_NODE,
            currentHp = 200f, maxHp = 200f,
            coverHeight = CoverHeight.LOW
        )

        spawnPointX = 2.5f * tileSize
        spawnPointY = 2.5f * tileSize
        objectivePointX = (objX + 0.5f) * tileSize
        objectivePointY = (objY + 0.5f) * tileSize
    }

    fun getTileAtWorld(worldX: Float, worldY: Float): VoxelTile? {
        val gx = (worldX / tileSize).toInt()
        val gy = (worldY / tileSize).toInt()
        if (gx in 0 until width && gy in 0 until height) {
            return tiles[gx][gy]
        }
        return null
    }

    fun applyDamageToTile(gx: Int, gy: Int, damage: Float): Boolean {
        if (gx !in 0 until width || gy !in 0 until height) return false
        val tile = tiles[gx][gy]
        if (!tile.isDestructible || tile.isDisintegrated) return false

        tile.currentHp -= damage
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
                }
                2 -> {
                    tile.elevationZ = 1
                    tile.coverHeight = CoverHeight.LOW
                    tile.type = VoxelType.LOW_COVER_CRATE
                    tile.currentHp = 30f
                    tile.maxHp = 30f
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
