package com.example.engine

import com.example.data.model.AIState
import com.example.data.model.CoverHeight
import com.example.data.model.Enemy
import com.example.data.model.PlayerStance
import com.example.data.model.PlayerState
import com.example.data.model.SquadMember
import com.example.data.model.VoxelTile
import com.example.data.model.VoxelType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class TileVisibilityState {
    UNEXPLORED,          // Pitch darkness / dense alien mist (0% direct visibility)
    SHROUDED_EXPLORED,   // Explored tactical memory (terrain visible, but dynamic threats hidden)
    ACTIVELY_VISIBLE     // Real-time direct line-of-sight from player or squad
}

enum class StealthStatus {
    HIDDEN,     // Player is concealed in shadows/fog or behind full cover (Ambush ready: +75% crit)
    CAUTION,    // Enemies suspicious from noise / investigating nearby voxels
    DETECTED    // Enemies have unbroken line of sight to player (active combat)
}

data class TacticalStealthEvaluation(
    val status: StealthStatus = StealthStatus.HIDDEN,
    val awarenessLevel: Float = 0f, // 0.0 (ghost) to 1.0 (fully compromised)
    val detectingEnemiesCount: Int = 0,
    val nearestDetectingEnemyDistance: Float? = null,
    val isAmbushReady: Boolean = true, // True when hidden, granting crit multiplier
    val statusLabel: String = "STEALTH: 100% HIDDEN",
    val statusColorHex: Long = 0xFF10B981L // Emerald Green
)

data class FogGridSnapshot(
    val width: Int,
    val height: Int,
    val tileSize: Float,
    val explored: Array<BooleanArray>,
    val currentVisible: Array<BooleanArray>,
    val visualAlpha: Array<FloatArray>,
    val mistParticles: List<AlienFogMistParticle>,
    val stealthEval: TacticalStealthEvaluation,
    val explorationPercentage: Float,
    val activeRadarPings: List<RadarPingPulse>
)

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
 * destructible voxel shadow occlusion, radar sonar sweeps, and real-time tactical stealth evaluation.
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

    var stealthEvaluation: TacticalStealthEvaluation = TacticalStealthEvaluation()
        private set

    val activeRadarPings = mutableListOf<RadarPingPulse>()
    val mistParticles = mutableListOf<AlienFogMistParticle>()

    private var pingCounter = 0L

    init {
        // Initialize ambient drifting alien mist particles across the map
        for (i in 0 until 45) {
            mistParticles.add(
                AlienFogMistParticle(
                    x = (i * 73f) % (width * tileSize),
                    y = (i * 127f) % (height * tileSize),
                    vx = (kotlin.random.Random.nextFloat() * 10f - 5f),
                    vy = (kotlin.random.Random.nextFloat() * 8f - 4f),
                    size = kotlin.random.Random.nextFloat() * 85f + 55f,
                    alpha = kotlin.random.Random.nextFloat() * 0.25f + 0.15f,
                    pulsePhase = kotlin.random.Random.nextFloat() * Math.PI.toFloat() * 2f
                )
            )
        }
    }

    /**
     * Updates real-time line of sight for player, squad members, enemies, and active radar sweeps.
     * Calculates destructible voxel visibility and unit line-of-sight stealth metrics.
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

        // 2. Compute Player Line-of-Sight based on stance, aim, and destructible obstacles
        if (player.isAlive) {
            val (baseRange, fovRad, closeRadius) = when (player.stance) {
                PlayerStance.STAND -> Triple(620f, Math.toRadians(150.0).toFloat(), 170f)
                PlayerStance.CROUCH -> Triple(530f, Math.toRadians(135.0).toFloat(), 140f)
                PlayerStance.PRONE -> Triple(450f, Math.toRadians(115.0).toFloat(), 105f)
            }

            // If player is aiming, extend vision along aim direction
            val effectiveRange = if (player.isFiring || player.isAutoAimLocked) baseRange + 75f else baseRange
            val effectiveFacing = if (player.aimAngle != 0f) player.aimAngle else player.facingAngle

            castVisionSource(
                originX = player.x,
                originY = player.y,
                facingAngle = effectiveFacing,
                fovAngleRad = fovRad,
                range = effectiveRange,
                closeProximityRange = closeRadius,
                viewerStance = player.stance,
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
                    viewerStance = PlayerStance.STAND,
                    terrain = terrain
                )
            }
        }

        // 4. Update Active Radar Sweeps & enemy detection in fog
        val pingIterator = activeRadarPings.iterator()
        while (pingIterator.hasNext()) {
            val ping = pingIterator.next()
            ping.currentRadius += ping.speed * deltaSec
            ping.life = (1.0f - ping.currentRadius / ping.maxRadius).coerceIn(0f, 1f)

            val minGxr = ((ping.originX - ping.currentRadius) / tileSize).toInt().coerceIn(0, width - 1)
            val maxGxr = ((ping.originX + ping.currentRadius) / tileSize).toInt().coerceIn(0, width - 1)
            val minGyr = ((ping.originY - ping.currentRadius) / tileSize).toInt().coerceIn(0, height - 1)
            val maxGyr = ((ping.originY + ping.currentRadius) / tileSize).toInt().coerceIn(0, height - 1)

            for (gx in minGxr..maxGxr) {
                for (gy in minGyr..maxGyr) {
                    val tcx = (gx + 0.5f) * tileSize
                    val tcy = (gy + 0.5f) * tileSize
                    val dist = sqrt((tcx - ping.originX) * (tcx - ping.originX) + (tcy - ping.originY) * (tcy - ping.originY))
                    if (abs(dist - ping.currentRadius) < 45f) {
                        currentVisible[gx][gy] = true
                    }
                }
            }

            // Reveal enemies caught in the radar wave
            for (enemy in enemies) {
                if (!enemy.isAlive) continue
                val dist = sqrt((enemy.x - ping.originX) * (enemy.x - ping.originX) + (enemy.y - ping.originY) * (enemy.y - ping.originY))
                if (abs(dist - ping.currentRadius) < 55f) {
                    enemy.radarPingAlpha = 1.0f
                }
            }

            if (ping.currentRadius >= ping.maxRadius || ping.life <= 0f) {
                pingIterator.remove()
            }
        }

        // 5. Update Explored map memory and smooth visualAlpha blending
        var count = 0
        val targetExploredAlpha = 0.44f
        val targetVisibleAlpha = 1.0f
        val blendSpeed = 6.0f * deltaSec

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

        // 6. Update Enemies visibility state in fog & acoustic tremor detection
        for (enemy in enemies) {
            if (!enemy.isAlive) {
                enemy.isVisibleInFog = false
                enemy.hasDirectLineOfSightToPlayer = false
                continue
            }
            val egx = (enemy.x / tileSize).toInt().coerceIn(0, width - 1)
            val egy = (enemy.y / tileSize).toInt().coerceIn(0, height - 1)

            val isDirectlyVisible = currentVisible[egx][egy]
            enemy.isVisibleInFog = isDirectlyVisible

            // Fade radar ping highlight
            if (enemy.radarPingAlpha > 0f) {
                enemy.radarPingAlpha = (enemy.radarPingAlpha - deltaSec * 0.75f).coerceAtLeast(0f)
            }

            // Audio tremor detection if moving fast or firing near explored region or player hearing radius
            val enemySpeedSq = enemy.vx * enemy.vx + enemy.vy * enemy.vy
            val distToPlayer = sqrt((enemy.x - player.x) * (enemy.x - player.x) + (enemy.y - player.y) * (enemy.y - player.y))
            if (!isDirectlyVisible && (explored[egx][egy] || distToPlayer < 450f) && (enemySpeedSq > 3.5f || enemy.shootCooldownMs > 0)) {
                enemy.audioTremorDetected = true
            } else {
                enemy.audioTremorDetected = false
            }
        }

        // 7. Tactical Stealth & Line-of-Sight Evaluation (Enemies Line-of-Sight to Player)
        stealthEvaluation = evaluateTacticalStealth(player, enemies, terrain, deltaSec)
        player.detectedByEnemiesCount = stealthEvaluation.detectingEnemiesCount

        // 8. Update ambient alien mist particles
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
     * Calculates tactical stealth status by checking enemy lines of sight against player,
     * accounting for player stance, cover occlusion, and acoustic noise.
     */
    private fun evaluateTacticalStealth(
        player: PlayerState,
        enemies: List<Enemy>,
        terrain: VoxelTerrain,
        deltaSec: Float
    ): TacticalStealthEvaluation {
        if (!player.isAlive) {
            return TacticalStealthEvaluation(
                status = StealthStatus.HIDDEN,
                awarenessLevel = 0f,
                detectingEnemiesCount = 0,
                nearestDetectingEnemyDistance = null,
                isAmbushReady = false,
                statusLabel = "OFFLINE",
                statusColorHex = 0xFF64748BL
            )
        }

        var detectingCount = 0
        var nearestDetectDist: Float? = null
        var isSuspiciousNoiseHeard = false

        for (enemy in enemies) {
            if (!enemy.isAlive) {
                enemy.hasDirectLineOfSightToPlayer = false
                continue
            }

            val dx = player.x - enemy.x
            val dy = player.y - enemy.y
            val dist = sqrt(dx * dx + dy * dy)

            // Effective enemy vision range against player adjusted by player stance & cover
            val stanceMultiplier = when (player.stance) {
                PlayerStance.STAND -> 1.0f
                PlayerStance.CROUCH -> 0.70f // Crouch grants 30% visual stealth
                PlayerStance.PRONE -> 0.45f  // Prone grants 55% visual stealth
            }

            val effectiveEnemyVisionRange = enemy.visionRange * stanceMultiplier
            val pointBlankHearingRange = 75f

            var seesPlayer = false

            if (dist <= effectiveEnemyVisionRange || dist <= pointBlankHearingRange) {
                val angleToPlayer = atan2(dy, dx)
                var angleDiff = abs(angleToPlayer - enemy.facingAngle)
                if (angleDiff > Math.PI) {
                    angleDiff = (2 * Math.PI - angleDiff).toFloat()
                }

                val inCone = angleDiff <= (enemy.visionAngleRad / 2f)
                val inHearing = dist <= pointBlankHearingRange

                if (inCone || inHearing) {
                    // Raycast through terrain voxels to check if line of sight is obstructed
                    val hasLoS = hasLineOfSight(
                        startX = enemy.x,
                        startY = enemy.y,
                        endX = player.x,
                        endY = player.y,
                        terrain = terrain,
                        viewerStance = if (enemy.isBehindCover) PlayerStance.CROUCH else PlayerStance.STAND,
                        targetCover = if (player.isBehindCover) player.coverHeight else CoverHeight.NONE
                    )

                    if (hasLoS) {
                        seesPlayer = true
                    }
                }
            }

            enemy.hasDirectLineOfSightToPlayer = seesPlayer

            if (seesPlayer) {
                detectingCount++
                if (nearestDetectDist == null || dist < nearestDetectDist!!) {
                    nearestDetectDist = dist
                }
                // Enemy alert level ramps up
                enemy.alertLevel = (enemy.alertLevel + deltaSec * 85f).coerceAtMost(100f)
                if (enemy.alertLevel >= 65f && enemy.state != AIState.ENGAGED && enemy.state != AIState.FLANKING) {
                    enemy.state = AIState.ENGAGED
                    enemy.lastKnownPlayerX = player.x
                    enemy.lastKnownPlayerY = player.y
                }
            } else {
                // Check if player noise alerted this enemy
                if (player.stealthNoiseRadius > 35f && dist <= player.stealthNoiseRadius) {
                    isSuspiciousNoiseHeard = true
                    if (enemy.state == AIState.PATROL) {
                        enemy.state = AIState.SUSPICIOUS
                        enemy.alertLevel = (enemy.alertLevel + deltaSec * 45f).coerceAtMost(60f)
                        enemy.lastKnownPlayerX = player.x
                        enemy.lastKnownPlayerY = player.y
                    }
                }
            }
        }

        val status = when {
            detectingCount > 0 -> StealthStatus.DETECTED
            isSuspiciousNoiseHeard -> StealthStatus.CAUTION
            else -> StealthStatus.HIDDEN
        }

        val awareness = when (status) {
            StealthStatus.DETECTED -> 1.0f
            StealthStatus.CAUTION -> 0.5f
            StealthStatus.HIDDEN -> 0.0f
        }

        val label = when (status) {
            StealthStatus.HIDDEN -> "STEALTH: 100% HIDDEN (AMBUSH +75%)"
            StealthStatus.CAUTION -> "STEALTH: SUSPECTED (NOISE DETECTED)"
            StealthStatus.DETECTED -> "DETECTED: $detectingCount HOSTILES IN COMBAT"
        }

        val colorHex = when (status) {
            StealthStatus.HIDDEN -> 0xFF10B981L // Emerald
            StealthStatus.CAUTION -> 0xFFF59E0BL // Amber
            StealthStatus.DETECTED -> 0xFFEF4444L // Red
        }

        return TacticalStealthEvaluation(
            status = status,
            awarenessLevel = awareness,
            detectingEnemiesCount = detectingCount,
            nearestDetectingEnemyDistance = nearestDetectDist,
            isAmbushReady = status == StealthStatus.HIDDEN,
            statusLabel = label,
            statusColorHex = colorHex
        )
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
        viewerStance: PlayerStance,
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
                    if (hasLineOfSight(originX, originY, tileCenterX, tileCenterY, terrain, viewerStance)) {
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
                    if (hasLineOfSight(originX, originY, tileCenterX, tileCenterY, terrain, viewerStance)) {
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
                    if (hasLineOfSight(originX, originY, tileCenterX, tileCenterY, terrain, PlayerStance.STAND)) {
                        currentVisible[gx][gy] = true
                    }
                }
            }
        }
    }

    /**
     * Checks if line of sight is obstructed by destructible voxel obstacles.
     * Takes destructible voxel destruction state and unit stance into account.
     */
    fun hasLineOfSight(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        terrain: VoxelTerrain,
        viewerStance: PlayerStance = PlayerStance.STAND,
        targetCover: CoverHeight = CoverHeight.NONE
    ): Boolean {
        val dx = endX - startX
        val dy = endY - startY
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < 8f) return true

        val stepSize = tileSize * 0.40f
        val steps = (dist / stepSize).toInt().coerceAtLeast(1)

        for (i in 1 until steps) {
            val t = i / steps.toFloat()
            val px = startX + dx * t
            val py = startY + dy * t
            val gx = (px / tileSize).toInt().coerceIn(0, width - 1)
            val gy = (py / tileSize).toInt().coerceIn(0, height - 1)

            val tile = terrain.tiles[gx][gy]
            // If the voxel tile is disintegrated or destroyed, it does NOT block line of sight!
            if (tile.isDisintegrated || tile.currentHp <= 0f) {
                continue
            }

            // High cover wall obstacles block line-of-sight unconditionally
            if (tile.isOpaqueWall()) {
                return false
            }

            // Low cover obstacles (crates, biomass) block line of sight if the viewer or target is crouching/prone
            if (tile.isLowCoverObstacle()) {
                val distFromStart = dist * t
                val distFromEnd = dist * (1f - t)

                // If viewer is crouching, low obstacles block line of sight unless viewer is right next to it
                if ((viewerStance == PlayerStance.CROUCH || viewerStance == PlayerStance.PRONE) && distFromStart > tileSize * 1.35f) {
                    return false
                }

                // If target is crouching in low cover, it blocks incoming line of sight unless observer is close
                if (targetCover == CoverHeight.LOW && distFromEnd < tileSize * 1.35f && dist > tileSize * 2.2f) {
                    return false
                }
            }
        }
        return true
    }

    private fun VoxelTile.isOpaqueWall(): Boolean {
        return type == VoxelType.HIGH_COVER_WALL ||
                type == VoxelType.CONCRETE_WALL ||
                type == VoxelType.REINFORCED_METAL ||
                type == VoxelType.DESTRUCTIBLE_PILLAR
    }

    private fun VoxelTile.isLowCoverObstacle(): Boolean {
        return type == VoxelType.LOW_COVER_CRATE ||
                type == VoxelType.ALIEN_BIOMASS ||
                type == VoxelType.EXPLOSIVE_BARREL
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
     * Creates a thread-safe snapshot of the current fog-of-war voxel grid and stealth evaluation.
     */
    fun createSnapshot(): FogGridSnapshot {
        return FogGridSnapshot(
            width = width,
            height = height,
            tileSize = tileSize,
            explored = explored,
            currentVisible = currentVisible,
            visualAlpha = visualAlpha,
            mistParticles = mistParticles.toList(),
            stealthEval = stealthEvaluation,
            explorationPercentage = explorationPercentage,
            activeRadarPings = activeRadarPings.toList()
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
