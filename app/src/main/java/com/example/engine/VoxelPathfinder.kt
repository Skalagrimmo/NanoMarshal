package com.example.engine

import com.example.data.model.CoverHeight
import com.example.data.model.PlayerState
import com.example.data.model.VoxelType
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

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
     * Calculates an optimal flanking position cell surrounding the player's cover.
     * Evaluates angles to maneuver around player's front arc and hit them from exposed sides/rear.
     */
    fun calculateFlankTarget(
        terrain: VoxelTerrain,
        enemyX: Float,
        enemyY: Float,
        player: PlayerState
    ): Pair<Int, Int> {
        val playerGx = (player.x / terrain.tileSize).toInt().coerceIn(1, terrain.width - 2)
        val playerGy = (player.y / terrain.tileSize).toInt().coerceIn(1, terrain.height - 2)

        val playerFacing = player.facingAngle
        val coverGx = player.coverTileX
        val coverGy = player.coverTileY

        // Determine cover threat vector (from cover tile to player or player facing direction)
        val threatAngle = if (coverGx != null && coverGy != null) {
            val coverX = (coverGx + 0.5f) * terrain.tileSize
            val coverY = (coverGy + 0.5f) * terrain.tileSize
            atan2(player.y - coverY, player.x - coverX)
        } else {
            playerFacing
        }

        var bestGx = playerGx
        var bestGy = playerGy
        var bestScore = -99999f

        // Search candidates in 3-6 tile radius ring around player
        val radiusTiles = 4
        for (dx in -radiusTiles..radiusTiles) {
            for (dy in -radiusTiles..radiusTiles) {
                val distSq = dx * dx + dy * dy
                if (distSq < 4 || distSq > radiusTiles * radiusTiles + 2) continue

                val candidateGx = playerGx + dx
                val candidateGy = playerGy + dy

                if (candidateGx !in 1 until terrain.width - 1 || candidateGy !in 1 until terrain.height - 1) continue
                if (isSolidObstacle(terrain, candidateGx, candidateGy)) continue

                val candWorldX = (candidateGx + 0.5f) * terrain.tileSize
                val candWorldY = (candidateGy + 0.5f) * terrain.tileSize

                // Calculate angle from candidate to player
                val candAngle = atan2(player.y - candWorldY, player.x - candWorldX)
                val angleDiff = abs(candAngle - threatAngle)

                // High score for flanking angles (> 70 degrees from player threat front)
                var score = if (angleDiff > Math.toRadians(70.0)) 150f else -50f

                // Bonus if candidate position offers cover for the enemy
                val candTile = terrain.tiles[candidateGx][candidateGy]
                if (candTile.coverHeight != CoverHeight.NONE) {
                    score += 40f
                }

                // Distance penalty from enemy to encourage reachable flanks
                val distToEnemy = sqrt((candWorldX - enemyX) * (candWorldX - enemyX) + (candWorldY - enemyY) * (candWorldY - enemyY))
                score -= distToEnemy * 0.1f

                if (score > bestScore) {
                    bestScore = score
                    bestGx = candidateGx
                    bestGy = candidateGy
                }
            }
        }

        return Pair(bestGx, bestGy)
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

        // Flanking maneuver line-of-fire avoidance penalty
        if (isFlanking && playerState != null) {
            val tileWorldX = (gx + 0.5f) * terrain.tileSize
            val tileWorldY = (gy + 0.5f) * terrain.tileSize

            // Penalty for tiles in front of player's cover face / facing vector
            val playerFacing = playerState.facingAngle
            val angleToTile = atan2(tileWorldY - playerState.y, tileWorldX - playerState.x)
            val angleDiff = abs(angleToTile - playerFacing)

            if (angleDiff < Math.toRadians(50.0)) {
                // High cost penalty for traversing directly in player's sightline when flanking!
                cost += 8.0f
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
