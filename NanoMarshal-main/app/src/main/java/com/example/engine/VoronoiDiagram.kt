package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.data.model.Enemy
import com.example.data.model.PlayerState
import com.example.ui.theme.HazardYellow
import com.example.ui.theme.LaserRed
import com.example.ui.theme.NanoCyan
import com.example.ui.theme.NanoPurple
import com.example.ui.theme.NaniteGreen
import com.example.ui.theme.ShieldBlue
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Data structure representing a generator site seed in a Voronoi Diagram.
 */
data class VoronoiSite(
    val id: Int,
    val x: Float,
    val y: Float,
    val color: Color,
    val siteType: String,
    val intensityWeight: Float = 1.0f
)

/**
 * Data structure representing a Voronoi boundary edge segment between two sites.
 */
data class VoronoiEdge(
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val site1Id: Int,
    val site2Id: Int
)

/**
 * Data structure representing a bounded Voronoi Cell polygon.
 */
data class VoronoiCell(
    val site: VoronoiSite,
    val vertices: List<Pair<Float, Float>>,
    val edges: List<VoronoiEdge>
)

/**
 * Voronoi Diagram generator using mathematical bisector intersection solver via [GaussianElimination].
 * Partition tactical spaces, destructible voxel shatter patterns, and biome territory domains.
 */
class VoronoiDiagram(
    val minX: Float = 0f,
    val minY: Float = 0f,
    val maxX: Float = 1600f,
    val maxY: Float = 1600f
) {
    val sites = mutableListOf<VoronoiSite>()
    val edges = mutableListOf<VoronoiEdge>()
    val cells = mutableListOf<VoronoiCell>()

    /**
     * Re-populates Voronoi sites based on dynamic game world entities:
     * Player position, enemy positions, cover blocks, and explosive hazards.
     */
    fun updateTacticalSites(
        player: PlayerState,
        enemies: List<Enemy>,
        hazardPositions: List<Pair<Float, Float>> = emptyList()
    ) {
        sites.clear()
        var siteId = 0

        // 1. Player Site (Cyan Control Point)
        if (player.isAlive) {
            sites.add(
                VoronoiSite(
                    id = siteId++,
                    x = player.x,
                    y = player.y,
                    color = NanoCyan,
                    siteType = "PLAYER",
                    intensityWeight = 1.2f
                )
            )
        }

        // 2. Enemy Sites (Red/Purple Threat Seeds)
        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            val enemyColor = when (enemy.state.name) {
                "FLANKING" -> LaserRed
                "ENGAGED" -> LaserRed
                "SEEKING_COVER" -> ShieldBlue
                else -> NanoPurple
            }
            sites.add(
                VoronoiSite(
                    id = siteId++,
                    x = enemy.x,
                    y = enemy.y,
                    color = enemyColor,
                    siteType = "ENEMY_${enemy.state.name}",
                    intensityWeight = 1.0f
                )
            )
        }

        // 3. Environmental Hazards (Yellow Explosion/Biomass Seeds)
        for (haz in hazardPositions) {
            sites.add(
                VoronoiSite(
                    id = siteId++,
                    x = haz.first,
                    y = haz.second,
                    color = HazardYellow,
                    siteType = "HAZARD",
                    intensityWeight = 0.8f
                )
            )
        }

        // Compute Voronoi Cell Boundaries
        computeDiagram()
    }

    /**
     * Computes Voronoi bisector edges and polygon cells by solving perpendicular bisector equations
     * using [GaussianElimination.solve2DIntersection].
     */
    fun computeDiagram() {
        edges.clear()
        cells.clear()

        if (sites.size < 2) return

        // Compute perpendicular bisector lines between pairs of sites
        for (i in 0 until sites.size) {
            val s1 = sites[i]
            val cellVertices = mutableListOf<Pair<Float, Float>>()

            for (j in i + 1 until sites.size) {
                val s2 = sites[j]

                // Midpoint between s1 and s2
                val midX = (s1.x + s2.x) / 2.0
                val midY = (s1.y + s2.y) / 2.0

                // Direction vector s1 -> s2
                val dx = (s2.x - s1.x).toDouble()
                val dy = (s2.y - s1.y).toDouble()

                if (hypot(dx, dy) < 0.001) continue

                // Perpendicular line equation: dx * x + dy * y = c
                // where c = dx * midX + dy * midY
                val line1A = dx
                val line1B = dy
                val line1C = dx * midX + dy * midY

                // Intersect with bounding box edges or third site bisectors using Gaussian Elimination
                for (k in 0 until sites.size) {
                    if (k == i || k == j) continue
                    val s3 = sites[k]

                    val midX2 = (s1.x + s3.x) / 2.0
                    val midY2 = (s1.y + s3.y) / 2.0
                    val dx2 = (s3.x - s1.x).toDouble()
                    val dy2 = (s3.y - s1.y).toDouble()

                    val line2A = dx2
                    val line2B = dy2
                    val line2C = dx2 * midX2 + dy2 * midY2

                    // Solve 2x2 linear equation system via Gaussian Elimination
                    val intersect = GaussianElimination.solve2DIntersection(
                        line1A, line1B, line1C,
                        line2A, line2B, line2C
                    )

                    if (intersect != null) {
                        val ix = intersect[0].toFloat()
                        val iy = intersect[1].toFloat()

                        if (ix in minX..maxX && iy in minY..maxY) {
                            cellVertices.add(Pair(ix, iy))
                        }
                    }
                }

                // Create edge segment representation
                val edgeLength = 120f
                val perpX = -dy.toFloat()
                val perpY = dx.toFloat()
                val len = hypot(perpX, perpY)
                if (len > 0.001f) {
                    val nx = (perpX / len) * edgeLength
                    val ny = (perpY / len) * edgeLength

                    edges.add(
                        VoronoiEdge(
                            startX = midX.toFloat() - nx,
                            startY = midY.toFloat() - ny,
                            endX = midX.toFloat() + nx,
                            endY = midY.toFloat() + ny,
                            site1Id = s1.id,
                            site2Id = s2.id
                        )
                    )
                }
            }

            if (cellVertices.size >= 3) {
                // Sort vertices around site center angle for valid convex polygon
                val sortedVertices = cellVertices.distinctBy { Pair((it.first / 5).toInt(), (it.second / 5).toInt()) }
                    .sortedBy { atan2(it.second - s1.y, it.first - s1.x) }

                cells.add(
                    VoronoiCell(
                        site = s1,
                        vertices = sortedVertices,
                        edges = emptyList()
                    )
                )
            }
        }
    }

    /**
     * Evaluates the nearest Voronoi site for a given world coordinate (x, y).
     * Returns a Pair of (VoronoiSite, distanceToSite).
     */
    fun findNearestSite(x: Float, y: Float): Pair<VoronoiSite, Float>? {
        if (sites.isEmpty()) return null
        var closest: VoronoiSite? = null
        var minSqDist = Float.MAX_VALUE

        for (site in sites) {
            val dx = x - site.x
            val dy = y - site.y
            val sqDist = dx * dx + dy * dy
            if (sqDist < minSqDist) {
                minSqDist = sqDist
                closest = site
            }
        }

        return if (closest != null) Pair(closest, sqrt(minSqDist)) else null
    }
}
