package com.example.engine

import com.example.data.model.CoverHeight
import com.example.data.model.VoxelTile
import com.example.data.model.VoxelType
import kotlin.math.sqrt

/**
 * Sparse Voxel Directed Acyclic Graph (SVDAG) Engine with Multi-Tier Level of Detail (LOD) Management.
 *
 * Constructs a quadtree/octree DAG over the terrain grid, deduplicating identical voxel subtrees
 * into shared DAG nodes to minimize memory overhead while providing hierarchical LOD sampling (LOD 0, LOD 1, LOD 2).
 */
class SvdagEngine(val gridWidth: Int = 24, val gridHeight: Int = 24) {

    sealed class DagNode {
        abstract val hashKey: String
        abstract val isDisintegrated: Boolean
        abstract val coverHeight: CoverHeight

        data class LeafNode(
            val tile: VoxelTile,
            override val hashKey: String = "${tile.type}_${tile.elevationZ}_${tile.coverHeight}_${tile.isDisintegrated}"
        ) : DagNode() {
            override val isDisintegrated: Boolean get() = tile.isDisintegrated
            override val coverHeight: CoverHeight get() = tile.coverHeight
        }

        data class InnerNode(
            val nw: DagNode,
            val ne: DagNode,
            val sw: DagNode,
            val se: DagNode,
            override val hashKey: String = "${nw.hashKey}|${ne.hashKey}|${sw.hashKey}|${se.hashKey}"
        ) : DagNode() {
            override val isDisintegrated: Boolean get() = nw.isDisintegrated && ne.isDisintegrated && sw.isDisintegrated && se.isDisintegrated
            override val coverHeight: CoverHeight
                get() {
                    val heights = listOf(nw.coverHeight, ne.coverHeight, sw.coverHeight, se.coverHeight)
                    return when {
                        heights.contains(CoverHeight.HIGH) -> CoverHeight.HIGH
                        heights.contains(CoverHeight.LOW) -> CoverHeight.LOW
                        else -> CoverHeight.NONE
                    }
                }
        }
    }

    private var rootNode: DagNode? = null
    private val dagPool = HashMap<String, DagNode>()

    var totalUncompressedNodes: Int = 0
        private set
    var uniqueDagNodesCount: Int = 0
        private set
    var compressionRatio: Float = 0f
        private set

    var lod0Count: Int = 0
        private set
    var lod1Count: Int = 0
        private set
    var lod2Count: Int = 0
        private set

    /**
     * Rebuilds the Sparse Voxel DAG from the current 2D Voxel Terrain grid.
     * Deduplicates identical quad subtrees into canonical DAG nodes.
     */
    fun rebuildDag(terrain: VoxelTerrain) {
        dagPool.clear()
        totalUncompressedNodes = 0

        val leaves = Array(gridWidth) { x ->
            Array(gridHeight) { y ->
                val leaf = DagNode.LeafNode(terrain.tiles[x][y])
                getOrCreateCanonicalNode(leaf) as DagNode.LeafNode
            }
        }

        rootNode = buildQuadDag(leaves, 0, 0, gridWidth)
        uniqueDagNodesCount = dagPool.size
        
        val maxQuadtreeNodes = calculateQuadtreeNodeCount(gridWidth)
        totalUncompressedNodes = maxQuadtreeNodes
        compressionRatio = if (totalUncompressedNodes > 0) {
            ((1f - uniqueDagNodesCount.toFloat() / totalUncompressedNodes.toFloat()) * 100f).coerceIn(0f, 99.9f)
        } else 0f
    }

    private fun getOrCreateCanonicalNode(node: DagNode): DagNode {
        return dagPool.getOrPut(node.hashKey) { node }
    }

    private fun buildQuadDag(grid: Array<Array<DagNode.LeafNode>>, x: Int, y: Int, size: Int): DagNode {
        if (size == 1) {
            return grid[x][y]
        }
        val half = size / 2
        val nw = buildQuadDag(grid, x, y, half)
        val ne = buildQuadDag(grid, x + half, y, half)
        val sw = buildQuadDag(grid, x, y + half, half)
        val se = buildQuadDag(grid, x + half, y + half, half)

        val inner = DagNode.InnerNode(nw, ne, sw, se)
        return getOrCreateCanonicalNode(inner)
    }

    private fun calculateQuadtreeNodeCount(size: Int): Int {
        var count = 0
        var s = size
        while (s >= 1) {
            count += s * s
            s /= 2
        }
        return count
    }

    /**
     * Dynamically updates Level of Detail (LOD) for all terrain tiles based on SVDAG level hierarchy and camera distance.
     *
     * - LOD 0: Distance < 320f (Ultra-Detail: Mesh deformation, crack fractures, hit flash, bevels)
     * - LOD 1: Distance 320f..560f (Medium-Detail: Condensed aggregated quad geometry)
     * - LOD 2: Distance > 560f (Macro-Detail: Low-overhead DAG macro blocks)
     */
    fun updateTerrainLOD(terrain: VoxelTerrain, cameraX: Float, cameraY: Float) {
        var l0 = 0
        var l1 = 0
        var l2 = 0

        val tileSize = terrain.tileSize

        for (x in 0 until gridWidth) {
            for (y in 0 until gridHeight) {
                val worldX = (x + 0.5f) * tileSize
                val worldY = (y + 0.5f) * tileSize
                val dist = sqrt((worldX - cameraX) * (worldX - cameraX) + (worldY - cameraY) * (worldY - cameraY))

                val lod = when {
                    dist < 320f -> 0
                    dist < 560f -> 1
                    else -> 2
                }

                terrain.tiles[x][y].lodLevel = lod

                when (lod) {
                    0 -> l0++
                    1 -> l1++
                    else -> l2++
                }
            }
        }

        lod0Count = l0
        lod1Count = l1
        lod2Count = l2
    }
}
