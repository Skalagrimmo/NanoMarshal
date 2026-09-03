package com.example.engine

import com.example.data.model.AIState
import com.example.data.model.CoverHeight
import com.example.data.model.Enemy
import com.example.data.model.FlankDirection
import com.example.data.model.FlankManeuverType
import com.example.data.model.PlayerMovementState
import com.example.data.model.PlayerState
import com.example.data.model.VoxelType
import java.util.PriorityQueue
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Result of tactical flanking evaluation around player cover.
 */
data class TacticalFlankTarget(
    val targetGx: Int,
    val targetGy: Int,
    val worldX: Float,
    val worldY: Float,
    val direction: FlankDirection,
    val maneuverType: FlankManeuverType,
    val angleDiffFromCoverDeg: Float,
    val isExposedFlank: Boolean,
    val score: Float
)

/**
 * A* Pathfinding and Tactical Flanking Navigation Engine for Voxel Terrain.
 * Uses noise-based voxel map costs to calculate optimal paths and flanking maneuvers around player cover.
 */
object VoxelPathfinder {

    private data class Node(
        val gx: Int,
        val gy: Int,
        val gCost: Float,
        val hCost: Float,
        val parent: Node? = null
    ) : Comparable<Node> {
        val fCost: Float get() = gCost + hCost
        override fun compareTo(other: Node): Int = fCost.compareTo(other.fCost)
    }

    /**
     * Finds an optimal path from (startGx, startGy) to (targetGx, targetGy) on the voxel terrain map.
     * Takes into account voxel obstacle walkability, hazard costs (acid pools), and optional threat line-of-fire avoidance.
     */
    fun findPath(
        terrain: VoxelTerrain,
        startGx: Int,
        startGy: Int,
        targetGx: Int,
        targetGy: Int,
        isFlanking: Boolean = false,
        playerState: PlayerState? = null
    ): List<Pair<Float, Float>> {
        val width = terrain.width
        val height = terrain.height

        val clampedStartGx = startGx.coerceIn(0, width - 1)
        val clampedStartGy = startGy.coerceIn(0, height - 1)
        var clampedTargetGx = targetGx.coerceIn(0, width - 1)
        var clampedTargetGy = targetGy.coerceIn(0, height - 1)

        // If target cell is solid obstacle (e.g. cover tile itself), find closest walkable neighbor
        if (isSolidObstacle(terrain, clampedTargetGx, clampedTargetGy)) {
            val neighbor = findClosestWalkableNeighbor(terrain, clampedTargetGx, clampedTargetGy, clampedStartGx, clampedStartGy)
            if (neighbor != null) {
                clampedTargetGx = neighbor.first
                clampedTargetGy = neighbor.second
            }
        }

        if (clampedStartGx == clampedTargetGx && clampedStartGy == clampedTargetGy) {
            val worldX = (clampedTargetGx + 0.5f) * terrain.tileSize
            val worldY = (clampedTargetGy + 0.5f) * terrain.tileSize
            return listOf(Pair(worldX, worldY))
        }

        val openSet = PriorityQueue<Node>()
        val closedSet = HashSet<Pair<Int, Int>>()
        val gScores = HashMap<Pair<Int, Int>, Float>()

        val startNode = Node(
            clampedStartGx, clampedStartGy, 0f,
            heuristic(clampedStartGx, clampedStartGy, clampedTargetGx, clampedTargetGy)
        )
        openSet.add(startNode)
        gScores[Pair(clampedStartGx, clampedStartGy)] = 0f

        // 8-directional neighbor offsets
        val dx = intArrayOf(0, 0, 1, -1, 1, 1, -1, -1)
        val dy = intArrayOf(1, -1, 0, 0, 1, -1, 1, -1)
        val stepCosts = floatArrayOf(1f, 1f, 1f, 1f, 1.414f, 1.414f, 1.414f, 1.414f)

        var iterations = 0
        val maxIterations = 350 // Prevent long loops on complex maps

        while (openSet.isNotEmpty() && iterations < maxIterations) {
            iterations++
            val current = openSet.poll() ?: break

            if (current.gx == clampedTargetGx && current.gy == clampedTargetGy) {
                return reconstructPath(current, terrain.tileSize)
            }

            val currentCoord = Pair(current.gx, current.gy)
            if (closedSet.contains(currentCoord)) continue
            closedSet.add(currentCoord)

            for (i in 0 until 8) {
                val nx = current.gx + dx[i]
                val ny = current.gy + dy[i]

                if (nx !in 0 until width || ny !in 0 until height) continue
                if (closedSet.contains(Pair(nx, ny))) continue

                // Check obstacle walkability
                if (isSolidObstacle(terrain, nx, ny)) continue

                // Calculate traversal cost based on noise terrain type and tactical hazard/flank penalty
                val tileCost = calculateTileCost(terrain, nx, ny, isFlanking, playerState)
                val newG = current.gCost + stepCosts[i] * tileCost

                val neighborCoord = Pair(nx, ny)
                val existingG = gScores[neighborCoord] ?: Float.MAX_VALUE

                if (newG < existingG) {
                    gScores[neighborCoord] = newG
                    val h = heuristic(nx, ny, clampedTargetGx, clampedTargetGy)
                    val neighborNode = Node(nx, ny, newG, h, current)
                    openSet.add(neighborNode)
                }
            }
        }

        // Fallback: Return straight line or partial path if complete path blocked
        val targetWorldX = (clampedTargetGx + 0.5f) * terrain.tileSize
        val targetWorldY = (clampedTargetGy + 0.5f) * terrain.tileSize
        return listOf(Pair(targetWorldX, targetWorldY))
    }

    /**
     * Calculates an optimal tactical flanking target adapting specifically to the player's cover state:
     * - COVER_VAULTING: intercepts player during vault vulnerability window.
     * - COVER_TRAVERSING: cuts off corner along the cover wall traversal vector.
     * - COVER_PEEKING: maneuvers behind player's blind side (opposite to aim vector).
     * - COVER_SNAPPED (HIGH/LOW): routes to exposed left/right wings or unshielded rear (pincer movement).
     * - OPEN: encirclement positioning.
     */
    fun calculateTacticalFlankTarget(
        terrain: VoxelTerrain,
        enemy: Enemy,
        player: PlayerState,
        allEnemies: List<Enemy> = emptyList(),
        preferredDirection: FlankDirection = FlankDirection.NONE,
        requestedManeuver: FlankManeuverType = FlankManeuverType.NONE
    ): TacticalFlankTarget {
        val tileSize = terrain.tileSize
        val playerGx = (player.x / tileSize).toInt().coerceIn(1, terrain.width - 2)
        val playerGy = (player.y / tileSize).toInt().coerceIn(1, terrain.height - 2)

        // 1. Detect Player Cover Geometry & Vector
        val coverGx = player.coverTileX
        val coverGy = player.coverTileY
        val isSnapped = player.isCoverSnapped
        val movementState = player.movementState
        val isHighCover = player.coverHeight == CoverHeight.HIGH

        val coverThreatAngle: Float = when {
            isSnapped && (player.coverSnapNormalX != 0f || player.coverSnapNormalY != 0f) -> {
                atan2(player.coverSnapNormalY, player.coverSnapNormalX)
            }
            coverGx != null && coverGy != null -> {
                val cWorldX = (coverGx + 0.5f) * tileSize
                val cWorldY = (coverGy + 0.5f) * tileSize
                atan2(player.y - cWorldY, player.x - cWorldX)
            }
            else -> player.facingAngle
        }

        // 2. Multi-unit Pincer Coordination: Check if allies are already flanking LEFT or RIGHT
        var alliesFlankingLeft = 0
        var alliesFlankingRight = 0
        for (other in allEnemies) {
            if (other.id != enemy.id && other.isAlive && other.state == AIState.FLANKING) {
                if (other.flankDirection == FlankDirection.LEFT) alliesFlankingLeft++
                if (other.flankDirection == FlankDirection.RIGHT) alliesFlankingRight++
            }
        }

        // Determine preferred flank side (pincer balancing)
        val targetFlankSide: FlankDirection = when {
            preferredDirection != FlankDirection.NONE -> preferredDirection
            alliesFlankingLeft > alliesFlankingRight -> FlankDirection.RIGHT
            alliesFlankingRight > alliesFlankingLeft -> FlankDirection.LEFT
            else -> {
                // Pick side closest to current enemy position
                val angleToEnemy = atan2(enemy.y - player.y, enemy.x - player.x)
                var diff = angleToEnemy - coverThreatAngle
                while (diff > PI) diff -= (2 * PI).toFloat()
                while (diff < -PI) diff += (2 * PI).toFloat()
                if (diff >= 0) FlankDirection.LEFT else FlankDirection.RIGHT
            }
        }

        // 3. Determine Tactical Maneuver Type based on Player State
        val maneuverType: FlankManeuverType = when {
            requestedManeuver != FlankManeuverType.NONE -> requestedManeuver
            movementState == PlayerMovementState.COVER_VAULTING -> FlankManeuverType.INTERCEPT_VAULT
            movementState == PlayerMovementState.COVER_TRAVERSING -> FlankManeuverType.CUT_OFF_CORNER
            movementState == PlayerMovementState.COVER_PEEKING -> FlankManeuverType.BLIND_SIDE_FLANK
            isSnapped && isHighCover -> FlankManeuverType.WIDE_ARC_FLANK
            isSnapped || player.isBehindCover -> FlankManeuverType.TIGHT_COVER_FLANK
            else -> FlankManeuverType.ENCIRCLE
        }

        // 4. Candidate Search Radius & Desired Angle Offset
        val radiusTiles: Int
        val desiredAngleOffsetRad: Float

        when (maneuverType) {
            FlankManeuverType.INTERCEPT_VAULT -> {
                radiusTiles = 3
                desiredAngleOffsetRad = 0f // Intercept directly in path of vault
            }
            FlankManeuverType.CUT_OFF_CORNER -> {
                radiusTiles = 4
                // Lead corner along traversal velocity vector
                val travelAngle = atan2(player.vy, player.vx)
                desiredAngleOffsetRad = travelAngle - coverThreatAngle
            }
            FlankManeuverType.BLIND_SIDE_FLANK -> {
                radiusTiles = 5
                // Target rear 160-180° away from player's aim angle
                val blindAngle = player.aimAngle + PI.toFloat()
                desiredAngleOffsetRad = blindAngle - coverThreatAngle
            }
            FlankManeuverType.WIDE_ARC_FLANK -> {
                radiusTiles = 5
                desiredAngleOffsetRad = if (targetFlankSide == FlankDirection.LEFT) 1.75f else -1.75f // ~100 deg
            }
            FlankManeuverType.TIGHT_COVER_FLANK -> {
                radiusTiles = 3
                desiredAngleOffsetRad = if (targetFlankSide == FlankDirection.LEFT) 1.50f else -1.50f // ~85 deg
            }
            FlankManeuverType.SUPPRESS_AND_CHIP -> {
                radiusTiles = 4
                desiredAngleOffsetRad = 0.2f // Frontal suppression arc
            }
            FlankManeuverType.ENCIRCLE, FlankManeuverType.NONE -> {
                radiusTiles = 4
                desiredAngleOffsetRad = if (targetFlankSide == FlankDirection.LEFT) 1.4f else -1.4f
            }
        }

        val targetAngle = coverThreatAngle + desiredAngleOffsetRad

        var bestGx = playerGx
        var bestGy = playerGy
        var bestWorldX = (playerGx + 0.5f) * tileSize
        var bestWorldY = (playerGy + 0.5f) * tileSize
        var bestScore = -99999f
        var bestAngleDiffDeg = 0f
        var bestIsExposed = false

        // Search candidates in ring around player
        val minR = (radiusTiles - 1).coerceAtLeast(2)
        val maxR = (radiusTiles + 2).coerceAtMost(7)

        for (dx in -maxR..maxR) {
            for (dy in -maxR..maxR) {
                val distSq = dx * dx + dy * dy
                if (distSq < minR * minR || distSq > maxR * maxR) continue

                val candidateGx = playerGx + dx
                val candidateGy = playerGy + dy

                if (candidateGx !in 1 until terrain.width - 1 || candidateGy !in 1 until terrain.height - 1) continue
                if (isSolidObstacle(terrain, candidateGx, candidateGy)) continue

                val candWorldX = (candidateGx + 0.5f) * tileSize
                val candWorldY = (candidateGy + 0.5f) * tileSize

                // Candidate angle relative to player position
                val candAngleFromPlayer = atan2(candWorldY - player.y, candWorldX - player.x)
                var angleDiffFromThreat = abs(candAngleFromPlayer - coverThreatAngle)
                if (angleDiffFromThreat > PI) {
                    angleDiffFromThreat = (2 * PI - angleDiffFromThreat).toFloat()
                }
                val angleDiffDeg = Math.toDegrees(angleDiffFromThreat.toDouble()).toFloat()

                // Exposed flank if angle difference from cover face exceeds 70 degrees
                val isExposed = angleDiffDeg > 70f

                // Alignment with desired target tactical angle
                var angleFromTarget = abs(candAngleFromPlayer - targetAngle)
                if (angleFromTarget > PI) angleFromTarget = (2 * PI - angleFromTarget).toFloat()

                var score = 200f - Math.toDegrees(angleFromTarget.toDouble()).toFloat() * 1.5f

                // High bonus for exposed flank when player is snapped/behind cover
                if ((isSnapped || player.isBehindCover) && isExposed) {
                    score += 120f
                }

                // If player is peeking, bonus for being in player's blind rear
                if (movementState == PlayerMovementState.COVER_PEEKING) {
                    var diffFromAim = abs(candAngleFromPlayer - player.aimAngle)
                    if (diffFromAim > PI) diffFromAim = (2 * PI - diffFromAim).toFloat()
                    if (diffFromAim > 2.0f) {
                        score += 90f // Blind side ambush bonus!
                    }
                }

                // Bonus if candidate position offers partial cover for the enemy
                val candTile = terrain.tiles[candidateGx][candidateGy]
                if (candTile.coverHeight != CoverHeight.NONE) {
                    score += 45f
                }

                // Avoid hazard pools
                if (candTile.type == VoxelType.ACID_POOL) {
                    score -= 150f
                }

                // Distance penalty from enemy to encourage reachable flanks
                val distToEnemy = sqrt((candWorldX - enemy.x) * (candWorldX - enemy.x) + (candWorldY - enemy.y) * (candWorldY - enemy.y))
                score -= distToEnemy * 0.12f

                if (score > bestScore) {
                    bestScore = score
                    bestGx = candidateGx
                    bestGy = candidateGy
                    bestWorldX = candWorldX
                    bestWorldY = candWorldY
                    bestAngleDiffDeg = angleDiffDeg
                    bestIsExposed = isExposed
                }
            }
        }

        return TacticalFlankTarget(
            targetGx = bestGx,
            targetGy = bestGy,
            worldX = bestWorldX,
            worldY = bestWorldY,
            direction = targetFlankSide,
            maneuverType = maneuverType,
            angleDiffFromCoverDeg = bestAngleDiffDeg,
            isExposedFlank = bestIsExposed,
            score = bestScore
        )
    }

    /**
     * Backward-compatible calculateFlankTarget adapter.
     */
    fun calculateFlankTarget(
        terrain: VoxelTerrain,
        enemyX: Float,
        enemyY: Float,
        player: PlayerState
    ): Pair<Int, Int> {
        val dummyEnemy = Enemy(
            id = "temp",
            name = "temp",
            type = com.example.data.model.EnemyType.FLANKER,
            x = enemyX,
            y = enemyY,
            health = 100f,
            maxHealth = 100f
        )
        val result = calculateTacticalFlankTarget(terrain, dummyEnemy, player)
        return Pair(result.targetGx, result.targetGy)
    }

    private fun isSolidObstacle(terrain: VoxelTerrain, gx: Int, gy: Int): Boolean {
        if (gx !in 0 until terrain.width || gy !in 0 until terrain.height) return true
        val tile = terrain.tiles[gx][gy]
        if (tile.isDisintegrated) return false
        return tile.coverHeight != CoverHeight.NONE || tile.type == VoxelType.HIGH_COVER_WALL || tile.type == VoxelType.DESTRUCTIBLE_PILLAR
    }

    private fun findClosestWalkableNeighbor(
        terrain: VoxelTerrain,
        gx: Int,
        gy: Int,
        startGx: Int,
        startGy: Int
    ): Pair<Int, Int>? {
        var bestNeighbor: Pair<Int, Int>? = null
        var minDist = Float.MAX_VALUE

        val dxs = intArrayOf(0, 0, 1, -1, 1, 1, -1, -1)
        val dys = intArrayOf(1, -1, 0, 0, 1, -1, 1, -1)

        for (i in 0 until 8) {
            val nx = gx + dxs[i]
            val ny = gy + dys[i]
            if (nx in 0 until terrain.width && ny in 0 until terrain.height) {
                if (!isSolidObstacle(terrain, nx, ny)) {
                    val dist = (nx - startGx) * (nx - startGx) + (ny - startGy) * (ny - startGy)
                    if (dist < minDist) {
                        minDist = dist.toFloat()
                        bestNeighbor = Pair(nx, ny)
                    }
                }
            }
        }
        return bestNeighbor
    }

    private fun calculateTileCost(
        terrain: VoxelTerrain,
        gx: Int,
        gy: Int,
        isFlanking: Boolean,
        playerState: PlayerState?
    ): Float {
        val tile = terrain.tiles[gx][gy]
        var cost = 1.0f

        // Hazard terrain penalty (acid pools)
        if (tile.type == VoxelType.ACID_POOL) {
            cost += 6.0f
        }

        // Flanking maneuver line-of-fire and cover defense arc avoidance penalty
        if (isFlanking && playerState != null) {
            val tileWorldX = (gx + 0.5f) * terrain.tileSize
            val tileWorldY = (gy + 0.5f) * terrain.tileSize

            val dx = tileWorldX - playerState.x
            val dy = tileWorldY - playerState.y
            val angleToTile = atan2(dy, dx)

            // 1. Cover defense arc penalty (avoid walking into the player's fortified cover arc)
            if (playerState.isCoverSnapped && (playerState.coverSnapNormalX != 0f || playerState.coverSnapNormalY != 0f)) {
                val coverNormalAngle = atan2(playerState.coverSnapNormalY, playerState.coverSnapNormalX)
                var normalDiff = abs(angleToTile - coverNormalAngle)
                if (normalDiff > PI) normalDiff = (2 * PI - normalDiff).toFloat()
                if (normalDiff < Math.toRadians(65.0)) {
                    // Severely penalize walking in front of the fortified cover face
                    cost += 12.0f
                }
            } else {
                // Frontal sightline penalty
                val playerFacing = playerState.facingAngle
                var angleDiff = abs(angleToTile - playerFacing)
                if (angleDiff > PI) angleDiff = (2 * PI - angleDiff).toFloat()
                if (angleDiff < Math.toRadians(55.0)) {
                    cost += 9.0f
                }
            }

            // 2. Active aim cone penalty (if player is peeking/aiming)
            if (playerState.movementState == PlayerMovementState.COVER_PEEKING) {
                var aimDiff = abs(angleToTile - playerState.aimAngle)
                if (aimDiff > PI) aimDiff = (2 * PI - aimDiff).toFloat()
                if (aimDiff < Math.toRadians(40.0)) {
                    cost += 10.0f
                }
            }
        }

        return cost
    }

    private fun heuristic(gx1: Int, gy1: Int, gx2: Int, gy2: Int): Float {
        val dx = abs(gx1 - gx2)
        val dy = abs(gy1 - gy2)
        return dx + dy + (1.414f - 2f) * minOf(dx, dy)
    }

    private fun reconstructPath(node: Node, tileSize: Float): List<Pair<Float, Float>> {
        val path = mutableListOf<Pair<Float, Float>>()
        var curr: Node? = node
        while (curr != null) {
            val worldX = (curr.gx + 0.5f) * tileSize
            val worldY = (curr.gy + 0.5f) * tileSize
            path.add(0, Pair(worldX, worldY))
            curr = curr.parent
        }
        return path
    }
}
