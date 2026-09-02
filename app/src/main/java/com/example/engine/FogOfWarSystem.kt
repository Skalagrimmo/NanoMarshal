package com.example.engine

import com.example.data.model.Enemy
import com.example.data.model.PlayerState
import com.example.data.model.SquadMember
import com.example.data.model.VoxelType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class TileVisibilityState {
    UNEXPLORED,          // Pitch darkness / dense alien mist (0% direct visibility)
    SHROUDED_EXPLORED,   // Explored tactical memory (terrain visible, but dynamic threats hidden)
    ACTIVELY_VISIBLE     // Real-time direct line-of-sight from player or squad
}

data class RadarPingPulse(
    val id: String,
    val originX: Float,
    val originY: Float,
    var currentRadius: Float = 0f,
    val maxRadius: Float = 750f,
    val speed: Float = 650f, // Pixels per second
    var life: Float = 1.0f
)

data class AlienFogMistParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var size: Float,
    var alpha: Float,
    var pulsePhase: Float
)

/**
 * High-performance Fog-of-War and Tactical Line-of-Sight system.
 * Simulates volumetric alien mist, multi-agent shared vision (player + squad drones/scouts),
 * raycasted voxel shadow occlusion, and radar sonar sweeps.
 */
class FogOfWarSystem(
    val width: Int,
    val height: Int,
    val tileSize: Float = 64f
) {
    val explored = Array(width) { BooleanArray(height) { false } }
    val currentVisible = Array(width) { BooleanArray(height) { false } }
    val visualAlpha = Array(width) { FloatArray(height) { 0f } }

    var exploredTileCount: Int = 0
        private set
    val totalTileCount: Int = width * height
    val explorationPercentage: Float get() = (exploredTileCount.toFloat() / totalTileCount.toFloat()) * 100f

    val activeRadarPings = mutableListOf<RadarPingPulse>()
    val mistParticles = mutableListOf<AlienFogMistParticle>()

    private var pingCounter = 0L

    init {
        // Initialize ambient drifting alien mist particles across the map
        for (i in 0 until 40) {
            mistParticles.add(
                AlienFogMistParticle(
                    x = (i * 73f) % (width * tileSize),
                    y = (i * 127f) % (height * tileSize),
                    vx = (kotlin.random.Random.nextFloat() * 10f - 5f),
                    vy = (kotlin.random.Random.nextFloat() * 8f - 4f),
                    size = kotlin.random.Random.nextFloat() * 80f + 50f,
                    alpha = kotlin.random.Random.nextFloat() * 0.25f + 0.15f,
                    pulsePhase = kotlin.random.Random.nextFloat() * Math.PI.toFloat() * 2f
                )
            )
        }
    }

    /**
     * Updates real-time line of sight for player, squad members, and active radar sweeps.
     */
    fun updateVisibility(
        player: PlayerState,
        squad: List<SquadMember>,
        enemies: List<Enemy>,
        terrain: VoxelTerrain,
        deltaSec: Float
    ) {
        // 1. Reset current visibility frame buffer
        for (x in 0 until width) {
            for (y in 0 until height) {
                currentVisible[x][y] = false
            }
        }

        // 2. Compute Player Line-of-Sight
        if (player.isAlive) {
            castVisionSource(
                originX = player.x,
                originY = player.y,
                facingAngle = player.facingAngle,
                fovAngleRad = Math.toRadians(150.0).toFloat(),
                range = 560f,
                closeProximityRange = 160f, // 360 degree close awareness
                terrain = terrain
            )
        }

        // 3. Compute Squad Members Shared Line-of-Sight
        for (member in squad) {
            if (!member.isAlive || !member.isActive) continue
            if (member.isOmnidirectionalVision) {
                castOmniVisionSource(
                    originX = member.x,
                    originY = member.y,
                    range = member.visionRange,
                    terrain = terrain
                )
            } else {
                castVisionSource(
                    originX = member.x,
                    originY = member.y,
                    facingAngle = member.facingAngle,
                    fovAngleRad = member.fovAngleRad,
                    range = member.visionRange,
                    closeProximityRange = 120f,
                    terrain = terrain
                )
            }
        }

        // 4. Update Explored map memory and smooth visualAlpha blending
        var count = 0
        val targetExploredAlpha = 0.42f
        val targetVisibleAlpha = 1.0f
        val blendSpeed = 5.0f * deltaSec

        for (x in 0 until width) {
            for (y in 0 until height) {
                val isVis = currentVisible[x][y]
                if (isVis) {
                    if (!explored[x][y]) {
                        explored[x][y] = true
                    }
                }
                if (explored[x][y]) count++

                val targetA = when {
                    isVis -> targetVisibleAlpha
                    explored[x][y] -> targetExploredAlpha
                    else -> 0.0f
                }
                visualAlpha[x][y] += (targetA - visualAlpha[x][y]) * blendSpeed.coerceIn(0f, 1f)
            }
        }
        exploredTileCount = count

        // 5. Update Active Radar Sweeps & enemy detection
        val pingIterator = activeRadarPings.iterator()
        while (pingIterator.hasNext()) {
            val ping = pingIterator.next()
            ping.currentRadius += ping.speed * deltaSec
            ping.life = (1.0f - ping.currentRadius / ping.maxRadius).coerceIn(0f, 1f)

            // Reveal enemies caught in the radar wave
            for (enemy in enemies) {
                if (!enemy.isAlive) continue
                val dist = sqrt((enemy.x - ping.originX) * (enemy.x - ping.originX) + (enemy.y - ping.originY) * (enemy.y - ping.originY))
                if (abs(dist - ping.currentRadius) < 45f) {
                    enemy.radarPingAlpha = 1.0f
                }
            }

            if (ping.currentRadius >= ping.maxRadius || ping.life <= 0f) {
                pingIterator.remove()
            }
        }

        // 6. Update Enemies visibility state in fog
        for (enemy in enemies) {
            if (!enemy.isAlive) {
                enemy.isVisibleInFog = false
                continue
            }
            val egx = (enemy.x / tileSize).toInt().coerceIn(0, width - 1)
            val egy = (enemy.y / tileSize).toInt().coerceIn(0, height - 1)

            val isDirectlyVisible = currentVisible[egx][egy]
            enemy.isVisibleInFog = isDirectlyVisible

            // Fade radar ping highlight
            if (enemy.radarPingAlpha > 0f) {
                enemy.radarPingAlpha = (enemy.radarPingAlpha - deltaSec * 0.8f).coerceAtLeast(0f)
            }

            // Audio tremor detection if moving fast or firing near explored region
            if (!isDirectlyVisible && explored[egx][egy] && enemy.vx * enemy.vx + enemy.vy * enemy.vy > 4f) {
                enemy.audioTremorDetected = true
            } else {
                enemy.audioTremorDetected = false
            }
        }

        // 7. Update ambient alien mist particles
        val mapMaxX = width * tileSize
        val mapMaxY = height * tileSize
        for (p in mistParticles) {
            p.x += p.vx * deltaSec
            p.y += p.vy * deltaSec
            p.pulsePhase += deltaSec * 1.5f
            if (p.x < 0) p.x = mapMaxX
            if (p.x > mapMaxX) p.x = 0f
            if (p.y < 0) p.y = mapMaxY
            if (p.y > mapMaxY) p.y = 0f
        }
    }

    /**
     * Casts directional vision cone with close-range ambient radius.
     */
    private fun castVisionSource(
        originX: Float,
        originY: Float,
        facingAngle: Float,
        fovAngleRad: Float,
        range: Float,
        closeProximityRange: Float,
        terrain: VoxelTerrain
    ) {
        val ogx = (originX / tileSize).toInt().coerceIn(0, width - 1)
        val ogy = (originY / tileSize).toInt().coerceIn(0, height - 1)
        currentVisible[ogx][ogy] = true

        val maxRadiusTiles = (range / tileSize).toInt() + 1
        val minGx = (ogx - maxRadiusTiles).coerceAtLeast(0)
        val maxGx = (ogx + maxRadiusTiles).coerceAtMost(width - 1)
        val minGy = (ogy - maxRadiusTiles).coerceAtLeast(0)
        val maxGy = (ogy + maxRadiusTiles).coerceAtMost(height - 1)

        val halfFov = fovAngleRad / 2f
        val rangeSq = range * range
        val closeSq = closeProximityRange * closeProximityRange

        for (gx in minGx..maxGx) {
            for (gy in minGy..maxGy) {
                val tileCenterX = (gx + 0.5f) * tileSize
                val tileCenterY = (gy + 0.5f) * tileSize
                val dx = tileCenterX - originX
                val dy = tileCenterY - originY
                val distSq = dx * dx + dy * dy

                if (distSq > rangeSq) continue

                if (distSq <= closeSq) {
                    if (hasLineOfSight(originX, originY, tileCenterX, tileCenterY, terrain)) {
                        currentVisible[gx][gy] = true
                    }
                    continue
                }

                // Check angle within FOV
                val angleToTile = atan2(dy, dx)
                var angleDiff = abs(angleToTile - facingAngle)
                if (angleDiff > Math.PI) {
                    angleDiff = (2 * Math.PI - angleDiff).toFloat()
                }

                if (angleDiff <= halfFov) {
                    if (hasLineOfSight(originX, originY, tileCenterX, tileCenterY, terrain)) {
                        currentVisible[gx][gy] = true
                    }
                }
            }
        }
    }

    /**
     * Casts 360-degree omnidirectional sensor vision (used by recon drones).
     */
    private fun castOmniVisionSource(
        originX: Float,
        originY: Float,
        range: Float,
        terrain: VoxelTerrain
    ) {
        val ogx = (originX / tileSize).toInt().coerceIn(0, width - 1)
        val ogy = (originY / tileSize).toInt().coerceIn(0, height - 1)
        currentVisible[ogx][ogy] = true

        val maxRadiusTiles = (range / tileSize).toInt() + 1
        val minGx = (ogx - maxRadiusTiles).coerceAtLeast(0)
        val maxGx = (ogx + maxRadiusTiles).coerceAtMost(width - 1)
        val minGy = (ogy - maxRadiusTiles).coerceAtLeast(0)
        val maxGy = (ogy + maxRadiusTiles).coerceAtMost(height - 1)

        val rangeSq = range * range

        for (gx in minGx..maxGx) {
            for (gy in minGy..maxGy) {
                val tileCenterX = (gx + 0.5f) * tileSize
                val tileCenterY = (gy + 0.5f) * tileSize
                val dx = tileCenterX - originX
                val dy = tileCenterY - originY
                val distSq = dx * dx + dy * dy

                if (distSq <= rangeSq) {
                    if (hasLineOfSight(originX, originY, tileCenterX, tileCenterY, terrain)) {
                        currentVisible[gx][gy] = true
                    }
                }
            }
        }
    }

    /**
     * Checks if line of sight is obstructed by tall opaque voxel obstacles.
     */
    fun hasLineOfSight(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        terrain: VoxelTerrain
    ): Boolean {
        val dist = sqrt((endX - startX) * (endX - startX) + (endY - startY) * (endY - startY))
        val stepSize = tileSize * 0.45f
        val steps = (dist / stepSize).toInt().coerceAtLeast(1)

        for (i in 1 until steps) {
            val t = i / steps.toFloat()
            val px = startX + (endX - startX) * t
            val py = startY + (endY - startY) * t
            val gx = (px / tileSize).toInt().coerceIn(0, width - 1)
            val gy = (py / tileSize).toInt().coerceIn(0, height - 1)

            val tile = terrain.tiles[gx][gy]
            if (tile.isOpaqueCover() && !tile.isDisintegrated) {
                return false
            }
        }
        return true
    }

    private fun com.example.data.model.VoxelTile.isOpaqueCover(): Boolean {
        return type == VoxelType.HIGH_COVER_WALL ||
                type == VoxelType.CONCRETE_WALL ||
                type == VoxelType.REINFORCED_METAL ||
                type == VoxelType.DESTRUCTIBLE_PILLAR
    }

    /**
     * Triggers an expanding tactical radar ping pulse that sweeps through the fog.
     */
    fun triggerRadarPing(originX: Float, originY: Float, maxRadius: Float = 750f) {
        pingCounter++
        activeRadarPings.add(
            RadarPingPulse(
                id = "radar_ping_$pingCounter",
                originX = originX,
                originY = originY,
                currentRadius = 10f,
                maxRadius = maxRadius,
                speed = 700f,
                life = 1.0f
            )
        )
    }

    /**
     * Checks if a world position is currently actively visible.
     */
    fun isPointVisible(worldX: Float, worldY: Float): Boolean {
        val gx = (worldX / tileSize).toInt().coerceIn(0, width - 1)
        val gy = (worldY / tileSize).toInt().coerceIn(0, height - 1)
        return currentVisible[gx][gy]
    }

    /**
     * Checks if a world position has been previously explored.
     */
    fun isPointExplored(worldX: Float, worldY: Float): Boolean {
        val gx = (worldX / tileSize).toInt().coerceIn(0, width - 1)
        val gy = (worldY / tileSize).toInt().coerceIn(0, height - 1)
        return explored[gx][gy]
    }
}
