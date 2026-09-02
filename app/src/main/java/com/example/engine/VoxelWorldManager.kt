package com.example.engine

import com.example.data.model.CoverHeight
import com.example.data.model.DestructibleVoxel
import com.example.data.model.VoxelTile
import com.example.data.model.VoxelType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tactical damage and integrity states for individual 3D voxel blocks.
 */
enum class VoxelDamageState {
    PRISTINE,         // 100% HP, intact structural integrity
    MINOR_CRACKS,     // 75%..99% HP, minor surface fractures & stress
    SEVERELY_DAMAGED, // 40%..74% HP, major structural fractures, deformation
    CRITICAL_BREACH,  // 1%..39% HP, heavily crumbled, low cover integrity
    DESTROYED         // 0% HP, completely obliterated / disintegrated
}

/**
 * Result data payload returned after damaging or deforming a voxel block.
 */
data class VoxelDamageResult(
    val cell: Voxel3DCell,
    val damageDealt: Float,
    val wasDestroyed: Boolean,
    val previousState: VoxelDamageState = VoxelDamageState.PRISTINE,
    val state: VoxelDamageState = VoxelDamageState.PRISTINE,
    val craterDepth: Float = 0f,
    val scorchIntensity: Float = 0f
)

/**
 * Result summary of voxel destruction triggered when the player fires.
 */
data class PlayerFireDestructionResult(
    val impactPoint: Vector3D?,
    val damagedVoxels: List<VoxelDamageResult>,
    val destroyedVoxelCoordinates: List<Pair<Int, Int>>,
    val penetratedCount: Int
)

/**
 * 3D Voxel Cell representing an individual volume element in the 3D grid,
 * supporting damage states, mesh deformation, strain vectors, and destruction mechanics.
 */
data class Voxel3DCell(
    val x: Int,
    val y: Int,
    val z: Int, // 0 = Bedrock/Floor, 1 = Low structures, 2 = High walls, 3 = Overlooks, 4 = Aerial canopy
    var type: VoxelType = VoxelType.FLOOR_DIRT,
    var density: Float = 1.0f,
    var isSolid: Boolean = true,
    var hp: Float = 100f,
    var maxHp: Float = 100f,
    override var isDestructible: Boolean = true,
    var deformationX: Float = 0f,
    var deformationY: Float = 0f,
    var deformationZ: Float = 0f,
    var fractureAngle: Float = 0f,
    var craterDepth: Float = 0f,
    var scorchMarkIntensity: Float = 0f,
    var damageState: VoxelDamageState = VoxelDamageState.PRISTINE,
    var hitFlashTimer: Float = 0f,
    override var durability: Float = 100f,
    override var maxDurability: Float = 100f
) : DestructibleVoxel {
    override var health: Float
        get() = hp
        set(value) { hp = value }

    override var maxHealth: Float
        get() = maxHp
        set(value) { maxHp = value }

    override val isDestroyed: Boolean
        get() = !isSolid || hp <= 0f

    override fun takeDamage(amount: Float, armorPenetration: Float): Float {
        if (!isDestructible || isDestroyed) return 0f
        val effectiveDur = (durability * (1.0f - armorPenetration.coerceIn(0f, 1f))).coerceAtLeast(0f)
        val mitigation = (effectiveDur / (effectiveDur + 50f)).coerceIn(0f, 0.70f)
        val healthDmg = amount * (1.0f - mitigation)
        val durDmg = amount * (0.35f + mitigation * 0.50f)
        durability = (durability - durDmg).coerceAtLeast(0f)
        hp = (hp - healthDmg).coerceAtLeast(0f)
        if (hp <= 0f) {
            isSolid = false
            computeDamageState()
        }
        return healthDmg
    }
    val damageRatio: Float
        get() = if (maxHp > 0f) (1f - (hp / maxHp)).coerceIn(0f, 1f) else 1f

    val remainingHpRatio: Float
        get() = if (maxHp > 0f) (hp / maxHp).coerceIn(0f, 1f) else 0f

    fun computeDamageState(): VoxelDamageState {
        val ratio = remainingHpRatio
        damageState = when {
            !isSolid || ratio <= 0f -> VoxelDamageState.DESTROYED
            ratio < 0.40f -> VoxelDamageState.CRITICAL_BREACH
            ratio < 0.75f -> VoxelDamageState.SEVERELY_DAMAGED
            ratio < 1.00f -> VoxelDamageState.MINOR_CRACKS
            else -> VoxelDamageState.PRISTINE
        }
        return damageState
    }

    /**
     * Applies point damage to the voxel cell, calculating HP depletion, damage states,
     * strain deformation vectors, and density degradation.
     */
    fun applyPointDamage(
        amount: Float,
        impactAngle: Float = 0f,
        impactForce: Float = 1.0f
    ): VoxelDamageResult {
        if (!isDestructible || !isSolid) {
            return VoxelDamageResult(
                cell = this,
                damageDealt = 0f,
                wasDestroyed = false,
                previousState = damageState,
                state = damageState
            )
        }

        val previousState = computeDamageState()
        val damageDealt = amount.coerceAtMost(hp)
        hp = (hp - amount).coerceAtLeast(0f)

        // Calculate mesh deformation & strain vectors
        val strainFactor = (amount / maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f) * impactForce
        deformationX += cos(impactAngle) * strainFactor * 10f
        deformationY += sin(impactAngle) * strainFactor * 10f
        deformationZ += strainFactor * 6f
        fractureAngle = impactAngle
        craterDepth = (craterDepth + strainFactor * 14f).coerceAtMost(32f)
        scorchMarkIntensity = (scorchMarkIntensity + strainFactor * 0.85f).coerceAtMost(1f)
        hitFlashTimer = 0.25f

        // Degrade structural density
        density = remainingHpRatio

        val newState = computeDamageState()
        if (newState == VoxelDamageState.DESTROYED) {
            isSolid = false
            density = 0f
        }

        return VoxelDamageResult(
            cell = this,
            damageDealt = damageDealt,
            wasDestroyed = newState == VoxelDamageState.DESTROYED,
            previousState = previousState,
            state = newState,
            craterDepth = craterDepth,
            scorchIntensity = scorchMarkIntensity
        )
    }

    /**
     * Directly deforms block mesh without dealing direct health damage (e.g. shockwaves).
     */
    fun deformMesh(deltaX: Float, deltaY: Float, deltaZ: Float) {
        deformationX += deltaX
        deformationY += deltaY
        deformationZ += deltaZ
        craterDepth = (craterDepth + hypot(deltaX, deltaY) * 0.5f).coerceAtMost(32f)
    }

    /**
     * Restores voxel structural integrity and clears deformation vectors.
     */
    fun repair(amount: Float) {
        if (!isSolid && hp <= 0f) {
            isSolid = true
        }
        hp = (hp + amount).coerceAtMost(maxHp)
        density = remainingHpRatio
        deformationX *= 0.5f
        deformationY *= 0.5f
        deformationZ *= 0.5f
        craterDepth = (craterDepth - amount * 0.1f).coerceAtLeast(0f)
        scorchMarkIntensity = (scorchMarkIntensity - amount * 0.05f).coerceAtLeast(0f)
        computeDamageState()
    }
}

/**
 * Statistics snapshot for the 3D Voxel World Environment.
 */
data class VoxelWorldStats(
    val gridWidth: Int,
    val gridHeight: Int,
    val gridDepth: Int,
    val total3DVoxels: Int,
    val activeSolidVoxels: Int,
    val svdagCompressionRatio: Float,
    val uniqueDagNodes: Int,
    val totalDagNodes: Int,
    val lod0Count: Int,
    val lod1Count: Int,
    val lod2Count: Int
)

/**
 * Raycast hit result in the 3D Voxel Environment.
 */
data class RaycastHit3D(
    val hitX: Float,
    val hitY: Float,
    val hitZ: Float,
    val gridX: Int,
    val gridY: Int,
    val gridZ: Int,
    val voxel: Voxel3DCell,
    val distance: Float
)

/**
 * VoxelWorldManager initializes and manages a procedural 3D voxel grid
 * representing the tactical combat environment for NanoMarshal.
 *
 * Integrates procedural 3D Perlin noise generation, 2D surface tile mapping,
 * Sparse Voxel Directed Acyclic Graph (SVDAG) compression, level-of-detail (LOD) sampling,
 * and 3D raycasting/environmental destruction & damage state mechanics.
 */
class VoxelWorldManager(
    val width: Int = 24,
    val height: Int = 24,
    val maxDepth: Int = 5, // Z layers: 0..4
    val tileSize: Float = 64f
) {
    val terrain: VoxelTerrain = VoxelTerrain(width, height, tileSize)
    val svdagEngine: SvdagEngine = SvdagEngine(width, height)
    val voxelManager: VoxelManager = VoxelManager(width, height, maxDepth, tileSize)
    val physicsIntegration: VoxelPhysicsIntegration = VoxelPhysicsIntegration(voxelManager)

    // 3D Voxel Grid: [x][y][z]
    private val voxelGrid3D: Array<Array<Array<Voxel3DCell>>> = Array(width) { x ->
        Array(height) { y ->
            Array(maxDepth) { z ->
                Voxel3DCell(x, y, z, type = VoxelType.FLOOR_DIRT, isSolid = z == 0)
            }
        }
    }

    var seed: Long = 1337L
        private set

    init {
        initializeWorld("default_mission", seed)
    }

    /**
     * Initializes a procedural 3D voxel grid tailored to NanoMarshal's tactical parameters.
     */
    fun initializeWorld(missionId: String, worldSeed: Long = System.currentTimeMillis()) {
        this.seed = worldSeed

        // 1. Generate primary terrain surface map
        terrain.generateProceduralMap(missionId, seed)
        voxelManager.generateProceduralWorld(seed)

        // 2. Populate 3D Voxel Grid based on terrain elevation and 3D FBM noise + Spline Curves
        val fbm3D = FbmNoise(seed + 77777L, defaultOctaves = 4, lacunarity = 2.1, gain = 0.5)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val surfaceTile = terrain.tiles[x][y]
                val targetElevation = surfaceTile.elevationZ

                for (z in 0 until maxDepth) {
                    val noise3D = fbm3D.eval3DWithSpline(
                        x * 0.15 + z * 0.08,
                        y * 0.15 + z * 0.08,
                        z * 0.20,
                        SplineCurve.ELEVATION,
                        octaves = 3
                    )

                    when {
                        // Level 0: Foundation / Floor
                        z == 0 -> {
                            voxelGrid3D[x][y][z] = Voxel3DCell(
                                x = x, y = y, z = z,
                                type = surfaceTile.type,
                                density = 1.0f,
                                isSolid = true,
                                hp = surfaceTile.maxHp,
                                maxHp = surfaceTile.maxHp,
                                isDestructible = surfaceTile.isDestructible
                            )
                        }

                        // Levels up to surface tile elevation
                        z <= targetElevation && targetElevation > 0 -> {
                            val isSurfaceSolid = !surfaceTile.isDisintegrated
                            voxelGrid3D[x][y][z] = Voxel3DCell(
                                x = x, y = y, z = z,
                                type = surfaceTile.type,
                                density = if (isSurfaceSolid) 0.9f + noise3D.toFloat() * 0.1f else 0f,
                                isSolid = isSurfaceSolid,
                                hp = surfaceTile.currentHp,
                                maxHp = surfaceTile.maxHp,
                                isDestructible = surfaceTile.isDestructible
                            )
                        }

                        // Overhanging structures / Canopy noise
                        z > targetElevation && z < maxDepth - 1 -> {
                            val isCanopy = noise3D > 0.72 && x in 3..(width - 4) && y in 3..(height - 4)
                            voxelGrid3D[x][y][z] = Voxel3DCell(
                                x = x, y = y, z = z,
                                type = if (isCanopy) VoxelType.ENERGY_BARRIER else VoxelType.FLOOR_DIRT,
                                density = if (isCanopy) 0.6f else 0f,
                                isSolid = isCanopy,
                                hp = 80f,
                                maxHp = 80f,
                                isDestructible = true
                            )
                        }

                        else -> {
                            // Empty air voxel
                            voxelGrid3D[x][y][z] = Voxel3DCell(
                                x = x, y = y, z = z,
                                type = VoxelType.FLOOR_DIRT,
                                density = 0f,
                                isSolid = false,
                                hp = 0f,
                                maxHp = 0f,
                                isDestructible = false
                            )
                        }
                    }
                    voxelGrid3D[x][y][z].computeDamageState()
                }
            }
        }

        // 3. Rebuild Sparse Voxel DAG
        rebuildWorldSvdag()
    }

    /**
     * Rebuilds the SVDAG compression hierarchy for the tactical world.
     */
    fun rebuildWorldSvdag() {
        svdagEngine.rebuildDag(terrain)
    }

    /**
     * Updates world LOD relative to camera / player focus.
     */
    fun updateWorldLOD(focusX: Float, focusY: Float) {
        terrain.updateLODLevels(focusX, focusY)
        svdagEngine.updateTerrainLOD(terrain, focusX, focusY)
    }

    /**
     * Check if coordinates are within the 3D grid bounds.
     */
    fun isInBounds(x: Int, y: Int, z: Int): Boolean {
        return x in 0 until width && y in 0 until height && z in 0 until maxDepth
    }

    /**
     * Query 3D voxel cell at specific grid coordinates.
     */
    fun get3DVoxel(x: Int, y: Int, z: Int): Voxel3DCell? {
        if (isInBounds(x, y, z)) {
            return voxelGrid3D[x][y][z]
        }
        return null
    }

    /**
     * Check if a 3D voxel coordinate is in an empty / non-solid state.
     */
    fun isVoxelEmpty(x: Int, y: Int, z: Int): Boolean {
        val cell = get3DVoxel(x, y, z) ?: return true
        return !cell.isSolid || cell.hp <= 0f || cell.damageState == VoxelDamageState.DESTROYED
    }

    /**
     * Sets a specific voxel coordinate to an empty state, implementing basic destructibility.
     * Clears solidity, zeros out density & HP, marks destroyed state, and synchronizes surface terrain.
     *
     * @return true if the voxel was previously solid and transitioned to empty state, false otherwise.
     */
    fun setEmpty(x: Int, y: Int, z: Int): Boolean {
        val cell = get3DVoxel(x, y, z) ?: return false
        val wasSolid = cell.isSolid

        cell.isSolid = false
        cell.density = 0f
        cell.hp = 0f
        cell.damageState = VoxelDamageState.DESTROYED
        cell.craterDepth = 32f

        syncVoxelCellToSurfaceTile(x, y)
        if (wasSolid) {
            rebuildWorldSvdag()
        }
        return wasSolid
    }

    /**
     * Alias for [setEmpty] to explicitly set a voxel coordinate to an empty state.
     */
    fun setVoxelEmpty(x: Int, y: Int, z: Int): Boolean = setEmpty(x, y, z)

    /**
     * Clears a specific voxel coordinate to an empty state.
     */
    fun clearVoxel(x: Int, y: Int, z: Int): Boolean = setEmpty(x, y, z)

    /**
     * Destroys a specific voxel coordinate, transitioning it to destroyed empty state.
     */
    fun destroyVoxel(x: Int, y: Int, z: Int): Boolean = setEmpty(x, y, z)

    /**
     * Updates or sets a voxel at specific coordinates with a defined type, solidity, and HP.
     */
    fun setVoxel(
        x: Int, y: Int, z: Int,
        type: VoxelType = VoxelType.FLOOR_DIRT,
        isSolid: Boolean = true,
        hp: Float = 100f,
        maxHp: Float = 100f
    ): Boolean {
        val cell = get3DVoxel(x, y, z) ?: return false
        cell.type = type
        cell.isSolid = isSolid
        cell.hp = if (isSolid) hp else 0f
        cell.maxHp = if (isSolid) maxHp else 0f
        cell.density = if (isSolid) (hp / maxHp.coerceAtLeast(1f)).coerceIn(0f, 1f) else 0f
        cell.computeDamageState()

        syncVoxelCellToSurfaceTile(x, y)
        rebuildWorldSvdag()
        return true
    }

    /**
     * Destroys all voxels within a discrete grid radius around (gx, gy, gz) setting them to empty.
     */
    fun destroyVoxelRadius(gx: Int, gy: Int, gz: Int, radius: Int): Int {
        var count = 0
        val minX = (gx - radius).coerceIn(0, width - 1)
        val maxX = (gx + radius).coerceIn(0, width - 1)
        val minY = (gy - radius).coerceIn(0, height - 1)
        val maxY = (gy + radius).coerceIn(0, height - 1)
        val minZ = (gz - radius).coerceIn(0, maxDepth - 1)
        val maxZ = (gz + radius).coerceIn(0, maxDepth - 1)

        val rSq = radius * radius
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val dx = x - gx
                    val dy = y - gy
                    val dz = z - gz
                    if (dx * dx + dy * dy + dz * dz <= rSq) {
                        if (setEmpty(x, y, z)) {
                            count++
                        }
                    }
                }
            }
        }
        return count
    }

    /**
     * Get damage state of voxel at grid position.
     */
    fun getVoxelDamageState(x: Int, y: Int, z: Int): VoxelDamageState {
        return get3DVoxel(x, y, z)?.damageState ?: VoxelDamageState.DESTROYED
    }

    /**
     * Check if 3D voxel coordinate is solid/impassable.
     */
    fun isSolid3D(x: Int, y: Int, z: Int): Boolean {
        val cell = get3DVoxel(x, y, z)
        return cell?.isSolid == true
    }

    /**
     * Synchronize a 3D voxel column state back to the 2D surface tile in [terrain].
     */
    private fun syncVoxelCellToSurfaceTile(gx: Int, gy: Int) {
        if (gx !in 0 until width || gy !in 0 until height) return
        val surfaceTile = terrain.tiles[gx][gy]
        val topZ = surfaceTile.elevationZ.coerceIn(0, maxDepth - 1)
        val cell = voxelGrid3D[gx][gy][topZ]

        surfaceTile.currentHp = cell.hp
        surfaceTile.deformationX = cell.deformationX
        surfaceTile.deformationY = cell.deformationY

        when (cell.damageState) {
            VoxelDamageState.DESTROYED -> {
                surfaceTile.isDisintegrated = true
                surfaceTile.coverHeight = CoverHeight.NONE
                surfaceTile.damageCracksCount = 4
            }
            VoxelDamageState.CRITICAL_BREACH -> {
                surfaceTile.coverHeight = CoverHeight.LOW
                surfaceTile.damageCracksCount = 3
            }
            VoxelDamageState.SEVERELY_DAMAGED -> {
                surfaceTile.damageCracksCount = 2
            }
            VoxelDamageState.MINOR_CRACKS -> {
                surfaceTile.damageCracksCount = 1
            }
            VoxelDamageState.PRISTINE -> {
                surfaceTile.damageCracksCount = 0
            }
        }
    }

    /**
     * Applies targeted point damage to a specific 3D voxel block, updating damage state
     * and synchronizing surface terrain tiles.
     */
    fun applyVoxelDamage(
        gx: Int, gy: Int, gz: Int,
        amount: Float,
        impactAngle: Float = 0f,
        impactForce: Float = 1.0f
    ): VoxelDamageResult? {
        val cell = get3DVoxel(gx, gy, gz) ?: return null
        val result = cell.applyPointDamage(amount, impactAngle, impactForce)

        syncVoxelCellToSurfaceTile(gx, gy)
        if (result.wasDestroyed || result.previousState != result.state) {
            rebuildWorldSvdag()
        }

        return result
    }

    /**
     * Applies deformation offset to a voxel block.
     */
    fun deformVoxelBlock(gx: Int, gy: Int, gz: Int, deltaX: Float, deltaY: Float, deltaZ: Float) {
        val cell = get3DVoxel(gx, gy, gz) ?: return
        cell.deformMesh(deltaX, deltaY, deltaZ)
        syncVoxelCellToSurfaceTile(gx, gy)
    }

    /**
     * Restores/repairs a damaged voxel block.
     */
    fun repairVoxelBlock(gx: Int, gy: Int, gz: Int, amount: Float) {
        val cell = get3DVoxel(gx, gy, gz) ?: return
        cell.repair(amount)
        syncVoxelCellToSurfaceTile(gx, gy)
        rebuildWorldSvdag()
    }

    /**
     * Applies explosive blast damage to the 3D voxel grid and surface terrain.
     */
    fun applyExplosion3D(
        worldX: Float,
        worldY: Float,
        radiusWorld: Float,
        damage: Float
    ): List<Pair<Int, Int>> {
        val destroyedTiles = mutableListOf<Pair<Int, Int>>()
        val centerGx = (worldX / tileSize).toInt()
        val centerGy = (worldY / tileSize).toInt()
        val gridRadius = (radiusWorld / tileSize).toInt().coerceAtLeast(1)

        val minGx = (centerGx - gridRadius).coerceIn(0, width - 1)
        val maxGx = (centerGx + gridRadius).coerceIn(0, width - 1)
        val minGy = (centerGy - gridRadius).coerceIn(0, height - 1)
        val maxGy = (centerGy + gridRadius).coerceIn(0, height - 1)

        for (gx in minGx..maxGx) {
            for (gy in minGy..maxGy) {
                val tileX = (gx + 0.5f) * tileSize
                val tileY = (gy + 0.5f) * tileSize
                val dist = hypot(tileX - worldX, tileY - worldY)

                if (dist <= radiusWorld) {
                    val falloff = (1.0f - dist / radiusWorld).coerceIn(0.2f, 1.0f)
                    val appliedDmg = damage * falloff
                    val angle = atan2(tileY - worldY, tileX - worldX)

                    val destroyed = terrain.applyDamageToTile(gx, gy, appliedDmg, angle)
                    if (destroyed) {
                        destroyedTiles.add(Pair(gx, gy))
                    }

                    // Apply damage & strain deformation across 3D voxel columns
                    for (z in 1 until maxDepth) {
                        val cell = voxelGrid3D[gx][gy][z]
                        if (cell.isSolid) {
                            cell.applyPointDamage(appliedDmg, impactAngle = angle, impactForce = falloff * 2.0f)
                        }
                    }
                    syncVoxelCellToSurfaceTile(gx, gy)
                }
            }
        }

        if (destroyedTiles.isNotEmpty()) {
            rebuildWorldSvdag()
        }

        return destroyedTiles
    }

    /**
     * Perform 3D raycasting through the voxel volume to detect line-of-sight or projectile impacts.
     */
    fun raycast3D(
        startX: Float, startY: Float, startZ: Float,
        dirX: Float, dirY: Float, dirZ: Float,
        maxDistance: Float
    ): RaycastHit3D? {
        val stepSize = tileSize * 0.25f
        var currentDist = 0f

        val normLen = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
        if (normLen <= 0.0001f) return null

        val ndx = dirX / normLen
        val ndy = dirY / normLen
        val ndz = dirZ / normLen

        while (currentDist < maxDistance) {
            val cx = startX + ndx * currentDist
            val cy = startY + ndy * currentDist
            val cz = startZ + ndz * currentDist

            val gx = (cx / tileSize).toInt()
            val gy = (cy / tileSize).toInt()
            val gz = (cz / (tileSize * 0.5f)).toInt().coerceIn(0, maxDepth - 1)

            if (gx !in 0 until width || gy !in 0 until height) break

            val cell = get3DVoxel(gx, gy, gz)
            if (cell != null && cell.isSolid) {
                return RaycastHit3D(
                    hitX = cx, hitY = cy, hitZ = cz,
                    gridX = gx, gridY = gy, gridZ = gz,
                    voxel = cell,
                    distance = currentDist
                )
            }

            currentDist += stepSize
        }
        return null
    }

    /**
     * Perform 3D trajectory hit test and deal linear kinetic/beam damage to first impacted voxel.
     */
    fun applyLinearDamage3D(
        startX: Float, startY: Float, startZ: Float,
        endX: Float, endY: Float, endZ: Float,
        damage: Float,
        force: Float = 1.0f
    ): VoxelDamageResult? {
        val dirX = endX - startX
        val dirY = endY - startY
        val dirZ = endZ - startZ
        val maxDist = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)

        val hit = raycast3D(startX, startY, startZ, dirX, dirY, dirZ, maxDist) ?: return null
        val impactAngle = atan2(dirY, dirX)

        return applyVoxelDamage(
            gx = hit.gridX,
            gy = hit.gridY,
            gz = hit.gridZ,
            amount = damage,
            impactAngle = impactAngle,
            impactForce = force
        )
    }

    /**
     * Get world statistics snapshot.
     */
    fun getWorldStats(): VoxelWorldStats {
        var solidCount = 0
        for (x in 0 until width) {
            for (y in 0 until height) {
                for (z in 0 until maxDepth) {
                    if (voxelGrid3D[x][y][z].isSolid) {
                        solidCount++
                    }
                }
            }
        }

        return VoxelWorldStats(
            gridWidth = width,
            gridHeight = height,
            gridDepth = maxDepth,
            total3DVoxels = width * height * maxDepth,
            activeSolidVoxels = solidCount,
            svdagCompressionRatio = svdagEngine.compressionRatio,
            uniqueDagNodes = svdagEngine.uniqueDagNodesCount,
            totalDagNodes = svdagEngine.totalUncompressedNodes,
            lod0Count = svdagEngine.lod0Count,
            lod1Count = svdagEngine.lod1Count,
            lod2Count = svdagEngine.lod2Count
        )
    }

    /**
     * Modifies the voxel grid data structure when the player fires, allowing for real-time
     * destruction of terrain blocks along the projectile trajectory.
     *
     * Performs continuous sub-stepping collision detection through the 3D voxel volume,
     * applies material-calibrated point damage & deformation to impacted voxel cells,
     * triggers explosive radial blasts for explosive weapons/barrels, and synchronizes
     * surface terrain and SVDAG tree structures.
     *
     * @param originX Starting world X position of the player's shot.
     * @param originY Starting world Y position of the player's shot.
     * @param originZ Starting world Z position of the player's shot (default 1.5f).
     * @param aimAngle Aim direction angle in radians.
     * @param damage Base damage value of the fired shot.
     * @param damageType Specific energy or kinetic damage category.
     * @param maxDistance Maximum travel distance of the shot.
     * @param kineticForce Physical impact impulse for mesh deformation.
     * @param isExplosive True if the shot causes radial explosive terrain destruction.
     * @param explosionRadius Radius of radial voxel blast if explosive.
     * @param pierceCover Whether the shot can penetrate through destroyed blocks.
     * @return [PlayerFireDestructionResult] containing all impacted, damaged, and destroyed voxel blocks.
     */
    fun processPlayerFireDestruction(
        originX: Float,
        originY: Float,
        originZ: Float = 1.5f,
        aimAngle: Float,
        damage: Float,
        damageType: com.example.data.model.WeaponDamageType = com.example.data.model.WeaponDamageType.KINETIC,
        maxDistance: Float = 800f,
        kineticForce: Float = 1.0f,
        isExplosive: Boolean = false,
        explosionRadius: Float = 0f,
        pierceCover: Boolean = false
    ): PlayerFireDestructionResult {
        val dirX = cos(aimAngle)
        val dirY = sin(aimAngle)
        val dirZ = 0f

        val damagedCells = mutableListOf<VoxelDamageResult>()
        val destroyedVoxels = mutableListOf<Pair<Int, Int>>()
        var remainingDamage = damage
        var currentOriginX = originX
        var currentOriginY = originY
        var currentOriginZ = originZ
        var totalDistanceTraveled = 0f
        var primaryHitLocation: Vector3D? = null

        while (totalDistanceTraveled < maxDistance && remainingDamage > 0f) {
            val stepMaxDist = maxDistance - totalDistanceTraveled
            val hit = raycast3D(
                startX = currentOriginX,
                startY = currentOriginY,
                startZ = currentOriginZ,
                dirX = dirX,
                dirY = dirY,
                dirZ = dirZ,
                maxDistance = stepMaxDist
            ) ?: break

            if (primaryHitLocation == null) {
                primaryHitLocation = Vector3D(hit.hitX, hit.hitY, hit.hitZ)
            }

            totalDistanceTraveled += hit.distance
            val gx = hit.gridX
            val gy = hit.gridY
            val gz = hit.gridZ
            val cell = hit.voxel

            // Calibrate damage against material properties
            val multiplier = com.example.data.model.VoxelDamageCalibrator.getDamageMultiplier(damageType, cell.type)
            val effectiveDmg = remainingDamage * multiplier

            // Apply direct point damage & deformation to the 3D voxel cell
            val dmgResult = applyVoxelDamage(
                gx = gx,
                gy = gy,
                gz = gz,
                amount = effectiveDmg,
                impactAngle = aimAngle,
                impactForce = kineticForce
            )

            if (dmgResult != null) {
                damagedCells.add(dmgResult)
                if (dmgResult.wasDestroyed) {
                    destroyedVoxels.add(Pair(gx, gy))
                }
            }

            // If weapon has explosive properties or hit an explosive barrel, detonate radial destruction
            if (isExplosive && explosionRadius > 0f || cell.type == VoxelType.EXPLOSIVE_BARREL) {
                val blastRad = if (cell.type == VoxelType.EXPLOSIVE_BARREL) 180f else explosionRadius
                val blastDmg = if (cell.type == VoxelType.EXPLOSIVE_BARREL) 300f else effectiveDmg * 1.5f
                val blastDestroyed = applyExplosion3D(
                    worldX = hit.hitX,
                    worldY = hit.hitY,
                    radiusWorld = blastRad,
                    damage = blastDmg
                )
                destroyedVoxels.addAll(blastDestroyed)
                break
            }

            // Check cover penetration
            if (pierceCover && dmgResult?.wasDestroyed == true) {
                remainingDamage *= 0.70f
                currentOriginX = hit.hitX + dirX * 6f
                currentOriginY = hit.hitY + dirY * 6f
                currentOriginZ = hit.hitZ
            } else {
                break
            }
        }

        return PlayerFireDestructionResult(
            impactPoint = primaryHitLocation,
            damagedVoxels = damagedCells,
            destroyedVoxelCoordinates = destroyedVoxels.distinct(),
            penetratedCount = (damagedCells.size - 1).coerceAtLeast(0)
        )
    }

    val spawnPointX: Float get() = terrain.spawnPointX
    val spawnPointY: Float get() = terrain.spawnPointY
    val objectivePointX: Float get() = terrain.objectivePointX
    val objectivePointY: Float get() = terrain.objectivePointY
}
