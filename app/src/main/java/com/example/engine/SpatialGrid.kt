package com.example.engine

import com.example.data.model.Enemy
import kotlin.math.hypot

/**
 * 2D Spatial Hash Grid for efficient entity collision detection and proximity queries.
 * Reduces bullet-enemy collision checks from O(n×m) to O(n + m) average case.
 *
 * World coordinate space: [0..worldWidth] × [0..worldHeight]
 * Cell size should match gameplay range: e.g., tileSize (64f) for voxel terrain.
 */
class SpatialGrid(
    val worldWidth: Float,
    val worldHeight: Float,
    val cellSize: Float = 64f // Match terrain tileSize for consistency
) {
    private val inverseCellSize: Float = 1f / cellSize
    private val gridWidth: Int = (worldWidth * inverseCellSize).toInt().coerceAtLeast(1)
    private val gridHeight: Int = (worldHeight * inverseCellSize).toInt().coerceAtLeast(1)
    private val cells: MutableMap<Pair<Int, Int>, MutableSet<Enemy>> = HashMap()

    /** Add an enemy to the grid at its current world position. */
    fun add(enemy: Enemy) {
        val (gx, gy) = worldToGrid(enemy.x, enemy.y)
        cells.getOrPut(gx to gy) { mutableSetOf() }.add(enemy)
    }

    /** Remove an enemy from the grid. Safe to call even if not present. */
    fun remove(enemy: Enemy) {
        val (gx, gy) = worldToGrid(enemy.x, enemy.y)
        cells[gx to gy]?.remove(enemy)
    }

    /** Query all enemies within spherical radius from point (x, y). */
    fun queryRadius(x: Float, y: Float, radius: Float): List<Enemy> {
        val gx0 = ((x - radius) * inverseCellSize).toInt().coerceIn(0, gridWidth - 1)
        val gx1 = ((x + radius) * inverseCellSize).toInt().coerceIn(0, gridWidth - 1)
        val gy0 = ((y - radius) * inverseCellSize).toInt().coerceIn(0, gridHeight - 1)
        val gy1 = ((y + radius) * inverseCellSize).toInt().coerceIn(0, gridHeight - 1)

        val result = mutableListOf<Enemy>()

        for (gx in gx0..gx1) {
            for (gy in gy0..gy1) {
                val cell = cells[gx to gy]
                if (cell != null) {
                    for (enemy in cell) {
                        if (!enemy.isAlive) continue
                        val dx = enemy.x - x
                        val dy = enemy.y - y
                        if (hypot(dx, dy) <= radius) {
                            result.add(enemy)
                        }
                    }
                }
            }
        }
        return result
    }

    /** Convert world coordinates to grid cell indices. */
    private fun worldToGrid(x: Float, y: Float): Pair<Int, Int> {
        val gx = (x * inverseCellSize).toInt().coerceIn(0, gridWidth - 1)
        val gy = (y * inverseCellSize).toInt().coerceIn(0, gridHeight - 1)
        return Pair(gx, gy)
    }

    /** Rebuild grid entries for all enemies (call after mass position changes or init). */
    fun rebuild(enemies: List<Enemy>) {
        // Clear all cells
        for (cell in cells.values) {
            cell.clear()
        }

        // Re-add all enemies
        for (enemy in enemies) {
            if (enemy.isAlive) {
                add(enemy)
            }
        }
    }
}