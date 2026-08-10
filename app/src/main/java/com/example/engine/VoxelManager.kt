package com.example.engine

import com.example.data.model.CoverHeight
import com.example.data.model.VoxelType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Distinct biome regions defining the alien landscape environment.
 */
enum class VoxelBiome(
    val displayName: String,
    val description: String,
    val primaryFloor: VoxelType,
    val secondaryFloor: VoxelType,
    val dominantCoverType: VoxelType,
    val hazardType: VoxelType,
    val densityFactor: Float,
    val elevationFactor: Float,
    val hazardChance: Float
) {
    CRATER_OUTPOST(
        displayName = "Crater Outpost",
        description = "Industrial fortification constructed with reinforced concrete and plaza plating.",
        primaryFloor = VoxelType.FLOOR_PLAZA,
        secondaryFloor = VoxelType.FLOOR_DIRT,
        dominantCoverType = VoxelType.CONCRETE_WALL,
        hazardType = VoxelType.EXPLOSIVE_BARREL,
        densityFactor = 0.65f,
        elevationFactor = 0.50f,
        hazardChance = 0.15f
    ),
    ALIEN_INFESTATION(
        displayName = "Alien Infestation",
        description = "Organic alien hive biomass creeping over corrupted terrain with virulent acid pools.",
        primaryFloor = VoxelType.ALIEN_BIOMASS,
        secondaryFloor = VoxelType.ACID_POOL,
        dominantCoverType = VoxelType.ALIEN_BIOMASS,
        hazardType = VoxelType.ACID_POOL,
        densityFactor = 0.85f,
        elevationFactor = 0.70f,
        hazardChance = 0.35f
    ),
    CRYSTALLINE_CANYON(
        displayName = "Crystalline Canyon",
        description = "High-tech energy lattice gorges with reinforced metal pillars and kinetic barriers.",
        primaryFloor = VoxelType.FLOOR_PLAZA,
        secondaryFloor = VoxelType.FLOOR_DIRT,
        dominantCoverType = VoxelType.ENERGY_BARRIER,
        hazardType = VoxelType.OBJECTIVE_NODE,
        densityFactor = 0.55f,
        elevationFactor = 0.90f,
        hazardChance = 0.10f
    ),
    TOXIC_WASTELAND(
        displayName = "Toxic Wasteland",
        description = "Hazardous chemical testing grounds littered with explosive ordnance and crumbling debris.",
        primaryFloor = VoxelType.FLOOR_DIRT,
        secondaryFloor = VoxelType.ACID_POOL,
        dominantCoverType = VoxelType.LOW_COVER_CRATE,
        hazardType = VoxelType.EXPLOSIVE_BARREL,
        densityFactor = 0.75f,
        elevationFactor = 0.40f,
        hazardChance = 0.40f
    )
}

/**
 * Structural integrity & degradation levels for individual 3D voxel blocks.
 */
enum class BlockDamageLevel {
    INTACT,            // 100% durability - prime condition
    LIGHT_DAMAGE,      // 75%..99% durability - superficial hairline fractures
    MODERATE_DAMAGE,   // 40%..74% durability - visible structural fractures & cratering
    CRITICAL_DAMAGE,   // 1%..39% durability - severe structural breach, loose rubble
    DISINTEGRATED      // 0% durability - destroyed, path cleared
}

/**
 * Physical material properties governing voxel durability, armor mitigation,
 * blast resistance, and structural weight.
 */
data class VoxelBlockDurabilitySpec(
    val type: VoxelType,
    val maxDurability: Float,
    val armorRating: Float,        // Multiplier applied to incoming damage (e.g. 0.3f = 70% damage reduction)
    val blastResistance: Float,    // Resistance against explosive blast waves
    val isDestructible: Boolean,
    val coverHeight: CoverHeight,
    val isSolid: Boolean,
    val structuralWeight: Float,   // Used for gravity collapse & support checks
    val debrisChunkCount: Int
)

/**
 * Data structure representing a single destructible 3D voxel block in the grid.
 */
data class DestructibleVoxelBlock(
    val x: Int,
    val y: Int,
    val z: Int,
    var type: VoxelType,
    var currentDurability: Float,
    var maxDurability: Float,
    var isDestructible: Boolean = true,
    var isSolid: Boolean = true,
    var coverHeight: CoverHeight = CoverHeight.NONE,
    var damageLevel: BlockDamageLevel = BlockDamageLevel.INTACT,
    var deformationX: Float = 0f,
    var deformationY: Float = 0f,
    var deformationZ: Float = 0f,
    var craterDepth: Float = 0f,
    var scorchIntensity: Float = 0f,
    var fracturePatternSeed: Int = Random.nextInt(),
    var hitFlashTimer: Float = 0f
) {
    val remainingDurabilityRatio: Float
        get() = if (maxDurability > 0f) (currentDurability / maxDurability).coerceIn(0f, 1f) else 0f

    val isDisintegrated: Boolean
        get() = currentDurability <= 0f || !isSolid

    /**
     * Recalculates damage state based on current durability ratio.
     */
    fun updateDamageLevel(): BlockDamageLevel {
        val ratio = remainingDurabilityRatio
        damageLevel = when {
            !isSolid || ratio <= 0f -> BlockDamageLevel.DISINTEGRATED
            ratio < 0.40f -> BlockDamageLevel.CRITICAL_DAMAGE
            ratio < 0.75f -> BlockDamageLevel.MODERATE_DAMAGE
            ratio < 1.00f -> BlockDamageLevel.LIGHT_DAMAGE
            else -> BlockDamageLevel.INTACT
        }
        return damageLevel
    }
}

/**
 * Result payload generated whenever damage or explosive force is applied to a voxel.
 */
data class VoxelDamageReport(
    val blockX: Int,
    val blockY: Int,
    val blockZ: Int,
    val initialDurability: Float,
    val damageDealt: Float,
    val remainingDurability: Float,
    val wasDestroyed: Boolean,
    val previousLevel: BlockDamageLevel,
    val currentLevel: BlockDamageLevel,
    val blockType: VoxelType
)

/**
 * Result payload returned from 3D grid raycasting operations.
 */
data class VoxelRaycastHit(
    val block: DestructibleVoxelBlock,
    val hitX: Float,
    val hitY: Float,
    val hitZ: Float,
    val distance: Float,
    val normalX: Float,
    val normalY: Float,
    val normalZ: Float
)

/**
 * VoxelManager manages the 3D procedural voxel grid, storing destructible block state,
 * material durability models, 3D raycasting, ballistics penetration, explosive blast waves,
 * and gravity-driven structural integrity checks.
 */
class VoxelManager(
    val width: Int = 32,
    val height: Int = 32,
    val depth: Int = 6,
    val voxelSize: Float = 32f
) {
    private val materialCatalog = mutableMapOf<VoxelType, VoxelBlockDurabilitySpec>()

    init {
        registerMaterialDefaults()
    }

    // 3D Grid Storage [X][Y][Z]
    private val grid: Array<Array<Array<DestructibleVoxelBlock>>> = Array(width) { x ->
        Array(height) { y ->
            Array(depth) { z ->
                createDefaultBlock(x, y, z, VoxelType.FLOOR_DIRT)
            }
        }
    }

    // 2D Biome Map
    private val biomeGrid: Array<Array<VoxelBiome>> = Array(width) {
        Array(height) { VoxelBiome.CRATER_OUTPOST }
    }

    /**
     * Registers default physical & durability properties for all supported voxel types.
     */
    private fun registerMaterialDefaults() {
        materialCatalog[VoxelType.FLOOR_DIRT] = VoxelBlockDurabilitySpec(
            type = VoxelType.FLOOR_DIRT, maxDurability = 500f, armorRating = 0.9f, blastResistance = 0.8f,
            isDestructible = false, coverHeight = CoverHeight.NONE, isSolid = false, structuralWeight = 1.0f, debrisChunkCount = 2
        )
        materialCatalog[VoxelType.FLOOR_PLAZA] = VoxelBlockDurabilitySpec(
            type = VoxelType.FLOOR_PLAZA, maxDurability = 800f, armorRating = 0.5f, blastResistance = 0.9f,
            isDestructible = false, coverHeight = CoverHeight.NONE, isSolid = false, structuralWeight = 1.5f, debrisChunkCount = 3
        )
        materialCatalog[VoxelType.LOW_COVER_CRATE] = VoxelBlockDurabilitySpec(
            type = VoxelType.LOW_COVER_CRATE, maxDurability = 90f, armorRating = 1.0f, blastResistance = 0.6f,
            isDestructible = true, coverHeight = CoverHeight.LOW, isSolid = true, structuralWeight = 0.5f, debrisChunkCount = 6
        )
        materialCatalog[VoxelType.HIGH_COVER_WALL] = VoxelBlockDurabilitySpec(
            type = VoxelType.HIGH_COVER_WALL, maxDurability = 160f, armorRating = 0.75f, blastResistance = 0.7f,
            isDestructible = true, coverHeight = CoverHeight.HIGH, isSolid = true, structuralWeight = 2.0f, debrisChunkCount = 8
        )
        materialCatalog[VoxelType.CONCRETE_WALL] = VoxelBlockDurabilitySpec(
            type = VoxelType.CONCRETE_WALL, maxDurability = 240f, armorRating = 0.55f, blastResistance = 0.85f,
            isDestructible = true, coverHeight = CoverHeight.HIGH, isSolid = true, structuralWeight = 3.0f, debrisChunkCount = 10
        )
        materialCatalog[VoxelType.REINFORCED_METAL] = VoxelBlockDurabilitySpec(
            type = VoxelType.REINFORCED_METAL, maxDurability = 350f, armorRating = 0.30f, blastResistance = 0.95f,
            isDestructible = true, coverHeight = CoverHeight.HIGH, isSolid = true, structuralWeight = 5.0f, debrisChunkCount = 12
        )
        materialCatalog[VoxelType.DESTRUCTIBLE_PILLAR] = VoxelBlockDurabilitySpec(
            type = VoxelType.DESTRUCTIBLE_PILLAR, maxDurability = 200f, armorRating = 0.65f, blastResistance = 0.75f,
            isDestructible = true, coverHeight = CoverHeight.HIGH, isSolid = true, structuralWeight = 4.0f, debrisChunkCount = 10
        )
        materialCatalog[VoxelType.ALIEN_BIOMASS] = VoxelBlockDurabilitySpec(
            type = VoxelType.ALIEN_BIOMASS, maxDurability = 140f, armorRating = 1.20f, blastResistance = 0.5f,
            isDestructible = true, coverHeight = CoverHeight.HIGH, isSolid = true, structuralWeight = 1.2f, debrisChunkCount = 8
        )
        materialCatalog[VoxelType.EXPLOSIVE_BARREL] = VoxelBlockDurabilitySpec(
            type = VoxelType.EXPLOSIVE_BARREL, maxDurability = 45f, armorRating = 1.00f, blastResistance = 0.1f,
            isDestructible = true, coverHeight = CoverHeight.LOW, isSolid = true, structuralWeight = 0.4f, debrisChunkCount = 14
        )
        materialCatalog[VoxelType.ENERGY_BARRIER] = VoxelBlockDurabilitySpec(
            type = VoxelType.ENERGY_BARRIER, maxDurability = 180f, armorRating = 0.40f, blastResistance = 1.0f,
            isDestructible = true, coverHeight = CoverHeight.HIGH, isSolid = true, structuralWeight = 0.0f, debrisChunkCount = 6
        )
        materialCatalog[VoxelType.ACID_POOL] = VoxelBlockDurabilitySpec(
            type = VoxelType.ACID_POOL, maxDurability = 100f, armorRating = 1.0f, blastResistance = 0.0f,
            isDestructible = false, coverHeight = CoverHeight.NONE, isSolid = false, structuralWeight = 0.0f, debrisChunkCount = 0
        )
        materialCatalog[VoxelType.OBJECTIVE_NODE] = VoxelBlockDurabilitySpec(
            type = VoxelType.OBJECTIVE_NODE, maxDurability = 500f, armorRating = 0.2f, blastResistance = 0.9f,
            isDestructible = true, coverHeight = CoverHeight.HIGH, isSolid = true, structuralWeight = 10.0f, debrisChunkCount = 16
        )
    }

    /**
     * Constructs a default DestructibleVoxelBlock using registered material properties.
     */
    fun createDefaultBlock(x: Int, y: Int, z: Int, type: VoxelType): DestructibleVoxelBlock {
        val mat = materialCatalog[type] ?: VoxelBlockDurabilitySpec(
            type = type, maxDurability = 100f, armorRating = 1.0f, blastResistance = 0.5f,
            isDestructible = true, coverHeight = CoverHeight.NONE, isSolid = z > 0, structuralWeight = 1.0f, debrisChunkCount = 4
        )

        return DestructibleVoxelBlock(
            x = x,
            y = y,
            z = z,
            type = type,
            currentDurability = mat.maxDurability,
            maxDurability = mat.maxDurability,
            isDestructible = mat.isDestructible,
            isSolid = mat.isSolid,
            coverHeight = mat.coverHeight
        )
    }

    /**
     * Bounds check for 3D grid coordinates.
     */
    fun isInBounds(x: Int, y: Int, z: Int): Boolean {
        return x in 0 until width && y in 0 until height && z in 0 until depth
    }

    /**
     * Retrieves block at (x, y, z) coordinate or null if out of bounds.
     */
    fun getBlock(x: Int, y: Int, z: Int): DestructibleVoxelBlock? {
        if (!isInBounds(x, y, z)) return null
        return grid[x][y][z]
    }

    /**
     * Sets block at (x, y, z) coordinate.
     */
    fun setBlock(x: Int, y: Int, z: Int, block: DestructibleVoxelBlock) {
        if (isInBounds(x, y, z)) {
            grid[x][y][z] = block
        }
    }

    /**
     * Retrieves the active biome at (x, y) 2D grid coordinates.
     */
    fun getBiomeAt(x: Int, y: Int): VoxelBiome {
        if (x in 0 until width && y in 0 until height) {
            return biomeGrid[x][y]
        }
        return VoxelBiome.CRATER_OUTPOST
    }

    /**
     * Calculates the breakdown count of each biome across the procedural grid.
     */
    fun getBiomeDistribution(): Map<VoxelBiome, Int> {
        val distribution = mutableMapOf<VoxelBiome, Int>()
        for (x in 0 until width) {
            for (y in 0 until height) {
                val b = biomeGrid[x][y]
                distribution[b] = (distribution[b] ?: 0) + 1
            }
        }
        return distribution
    }

    /**
     * Procedurally populates the 3D voxel grid with multi-biome procedural generation
     * sampling temperature and moisture noise maps to form distinct landscape biomes.
     */
    fun generateProceduralWorld(seed: Long = 1337L) {
        val noise = PerlinNoise(seed)

        for (x in 0 until width) {
            for (y in 0 until height) {
                val nx = x * 0.08
                val ny = y * 0.08

                // Multi-biome climate sampling
                val tempVal = noise.octaveNoise(nx * 0.6 + 50.0, ny * 0.6 + 50.0, octaves = 2, persistence = 0.5)
                val moistureVal = noise.octaveNoise(nx * 0.6 + 250.0, ny * 0.6 + 250.0, octaves = 2, persistence = 0.5)

                // Select biome based on climate noise thresholds
                val biome = when {
                    tempVal > 0.62 && moistureVal > 0.58 -> VoxelBiome.ALIEN_INFESTATION
                    tempVal < 0.42 && moistureVal > 0.52 -> VoxelBiome.CRYSTALLINE_CANYON
                    tempVal > 0.58 && moistureVal < 0.44 -> VoxelBiome.TOXIC_WASTELAND
                    else -> VoxelBiome.CRATER_OUTPOST
                }
                biomeGrid[x][y] = biome

                val terrainElevation = noise.octaveNoise(nx, ny, octaves = 3, persistence = 0.5) * biome.elevationFactor
                val objectDensity = noise.octaveNoise(nx + 100.0, ny + 100.0, octaves = 2, persistence = 0.6) * biome.densityFactor
                val hazardVal = noise.octaveNoise(nx + 200.0, ny + 200.0, octaves = 2, persistence = 0.5)

                // Floor layer (z = 0)
                grid[x][y][0] = if (hazardVal > (1.0f - biome.hazardChance) && objectDensity < 0.5f) {
                    createDefaultBlock(x, y, 0, biome.hazardType)
                } else if ((x + y) % 2 == 0) {
                    createDefaultBlock(x, y, 0, biome.primaryFloor)
                } else {
                    createDefaultBlock(x, y, 0, biome.secondaryFloor)
                }

                // Structures & Barriers (z = 1..depth-1)
                for (z in 1 until depth) {
                    val isCorridor = abs(x - width / 2) <= 2 || abs(y - height / 2) <= 2

                    if (isCorridor) {
                        // Keep main tactical corridors navigable
                        if (z == 1 && objectDensity > 0.68f && hazardVal < 0.4f) {
                            grid[x][y][z] = createDefaultBlock(x, y, z, VoxelType.LOW_COVER_CRATE)
                        } else {
                            grid[x][y][z] = createDefaultBlock(x, y, z, biome.primaryFloor).apply { isSolid = false }
                        }
                    } else {
                        val maxStructureZ = (1 + (terrainElevation * (depth - 1)).toInt()).coerceIn(1, depth - 1)

                        val blockType = if (z <= maxStructureZ) {
                            when {
                                hazardVal > 0.72f && objectDensity > 0.40f && z == 1 -> biome.hazardType
                                objectDensity > 0.65f && z <= 2 -> biome.dominantCoverType
                                objectDensity > 0.55f && z <= 3 -> VoxelType.REINFORCED_METAL
                                objectDensity > 0.45f && z <= 2 -> VoxelType.CONCRETE_WALL
                                objectDensity > 0.38f && z == 1 -> VoxelType.DESTRUCTIBLE_PILLAR
                                objectDensity > 0.30f && z == 1 -> VoxelType.LOW_COVER_CRATE
                                else -> null
                            }
                        } else null

                        if (blockType != null) {
                            grid[x][y][z] = createDefaultBlock(x, y, z, blockType)
                        } else {
                            grid[x][y][z] = createDefaultBlock(x, y, z, biome.primaryFloor).apply { isSolid = false }
                        }
                    }
                }
            }
        }
    }

    /**
     * Applies direct targeted damage to a voxel block at (x, y, z), taking into account
     * material armor rating, structural durability, crater deformation, and destruction thresholds.
     */
    fun applyDamage(
        x: Int,
        y: Int,
        z: Int,
        rawDamage: Float,
        damageType: String = "KINETIC",
        impactAngle: Float = 0f
    ): VoxelDamageReport? {
        val block = getBlock(x, y, z) ?: return null
        if (!block.isDestructible || !block.isSolid) {
            return VoxelDamageReport(
                blockX = x, blockY = y, blockZ = z,
                initialDurability = block.currentDurability,
                damageDealt = 0f,
                remainingDurability = block.currentDurability,
                wasDestroyed = false,
                previousLevel = block.damageLevel,
                currentLevel = block.damageLevel,
                blockType = block.type
            )
        }

        val matProps = materialCatalog[block.type]
        val armor = matProps?.armorRating ?: 1.0f

        // Calculate damage reduction based on damage type and armor
        val effectiveDamage = when (damageType) {
            "EXPLOSIVE" -> rawDamage * (1.0f - (matProps?.blastResistance ?: 0.5f) * 0.5f)
            "PLASMA" -> if (block.type == VoxelType.ALIEN_BIOMASS) rawDamage * 1.5f else rawDamage * armor
            "CORROSIVE" -> if (block.type == VoxelType.REINFORCED_METAL) rawDamage * 1.4f else rawDamage
            else -> rawDamage * armor // KINETIC
        }

        val previousLevel = block.updateDamageLevel()
        val initialDurability = block.currentDurability
        val damageDealt = effectiveDamage.coerceAtMost(initialDurability)

        block.currentDurability = (block.currentDurability - damageDealt).coerceAtLeast(0f)
        val currentLevel = block.updateDamageLevel()
        val wasDestroyed = block.currentDurability <= 0f

        if (wasDestroyed) {
            block.isSolid = false
            block.coverHeight = CoverHeight.NONE
        } else {
            // Apply deformation & cratering
            block.craterDepth = (block.craterDepth + damageDealt * 0.05f).coerceAtMost(12f)
            block.scorchIntensity = (block.scorchIntensity + damageDealt * 0.08f).coerceAtMost(1f)
            block.deformationX += cos(impactAngle) * (damageDealt * 0.03f)
            block.deformationY += sin(impactAngle) * (damageDealt * 0.03f)
            block.hitFlashTimer = 0.25f
        }

        return VoxelDamageReport(
            blockX = x,
            blockY = y,
            blockZ = z,
            initialDurability = initialDurability,
            damageDealt = damageDealt,
            remainingDurability = block.currentDurability,
            wasDestroyed = wasDestroyed,
            previousLevel = previousLevel,
            currentLevel = currentLevel,
            blockType = block.type
        )
    }

    /**
     * Applies radial explosive blast waves across 3D voxel space, damaging blocks
     * proportionately to inverse square distance from the blast origin.
     */
    fun applyExplosiveBlast(
        originX: Float,
        originY: Float,
        originZ: Float,
        blastRadius: Float,
        maxDamage: Float
    ): List<VoxelDamageReport> {
        val reports = mutableListOf<VoxelDamageReport>()

        val minGx = ((originX - blastRadius) / voxelSize).toInt().coerceIn(0, width - 1)
        val maxGx = ((originX + blastRadius) / voxelSize).toInt().coerceIn(0, width - 1)
        val minGy = ((originY - blastRadius) / voxelSize).toInt().coerceIn(0, height - 1)
        val maxGy = ((originY + blastRadius) / voxelSize).toInt().coerceIn(0, height - 1)
        val minGz = ((originZ - blastRadius) / voxelSize).toInt().coerceIn(0, depth - 1)
        val maxGz = ((originZ + blastRadius) / voxelSize).toInt().coerceIn(0, depth - 1)

        for (gx in minGx..maxGx) {
            for (gy in minGy..maxGy) {
                for (gz in minGz..maxGz) {
                    val worldX = (gx + 0.5f) * voxelSize
                    val worldY = (gy + 0.5f) * voxelSize
                    val worldZ = (gz + 0.5f) * voxelSize

                    val dx = worldX - originX
                    val dy = worldY - originY
                    val dz = worldZ - originZ
                    val dist = sqrt(dx * dx + dy * dy + dz * dz)

                    if (dist <= blastRadius) {
                        val falloff = (1.0f - (dist / blastRadius)).coerceIn(0f, 1f)
                        val damage = maxDamage * falloff * falloff
                        val impactAngle = kotlin.math.atan2(dy, dx)

                        val report = applyDamage(
                            x = gx,
                            y = gy,
                            z = gz,
                            rawDamage = damage,
                            damageType = "EXPLOSIVE",
                            impactAngle = impactAngle
                        )
                        if (report != null && report.damageDealt > 0f) {
                            reports.add(report)
                        }
                    }
                }
            }
        }

        // Run structural integrity check to trigger secondary collapses
        calculateStructuralIntegrity()

        return reports
    }

    /**
     * Scans for floating blocks lacking solid structural support beneath them,
     * collapsing unsupported blocks into rubble.
     */
    fun calculateStructuralIntegrity(): Int {
        var collapsedCount = 0

        // Scan from top (depth - 1) down to 1
        for (z in (depth - 1) downTo 1) {
            for (x in 0 until width) {
                for (y in 0 until height) {
                    val block = grid[x][y][z]
                    if (block.isSolid && block.isDestructible) {
                        // Check support directly underneath or adjacent
                        val hasUnderSupport = grid[x][y][z - 1].isSolid
                        val hasNeighborSupport = (
                            (x > 0 && grid[x - 1][y][z].isSolid) ||
                            (x < width - 1 && grid[x + 1][y][z].isSolid) ||
                            (y > 0 && grid[x][y - 1][z].isSolid) ||
                            (y < height - 1 && grid[x][y + 1][z].isSolid)
                        )

                        if (!hasUnderSupport && !hasNeighborSupport) {
                            // Structure collapsed!
                            block.currentDurability = 0f
                            block.isSolid = false
                            block.coverHeight = CoverHeight.NONE
                            block.updateDamageLevel()
                            collapsedCount++
                        }
                    }
                }
            }
        }
        return collapsedCount
    }

    /**
     * Performs a 3D raycast through the voxel grid to find the first solid block intersection.
     */
    fun raycast3D(
        startX: Float,
        startY: Float,
        startZ: Float,
        dirX: Float,
        dirY: Float,
        dirZ: Float,
        maxDistance: Float
    ): VoxelRaycastHit? {
        val len = sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ)
        if (len <= 0f) return null

        val normDx = dirX / len
        val normDy = dirY / len
        val normDz = dirZ / len

        val stepSize = voxelSize * 0.25f
        val steps = (maxDistance / stepSize).toInt()

        var currX = startX
        var currY = startY
        var currZ = startZ

        for (i in 0 until steps) {
            currX += normDx * stepSize
            currY += normDy * stepSize
            currZ += normDz * stepSize

            val gx = (currX / voxelSize).toInt()
            val gy = (currY / voxelSize).toInt()
            val gz = (currZ / voxelSize).toInt()

            if (!isInBounds(gx, gy, gz)) continue

            val block = grid[gx][gy][gz]
            if (block.isSolid && !block.isDisintegrated) {
                val dist = sqrt((currX - startX) * (currX - startX) + (currY - startY) * (currY - startY) + (currZ - startZ) * (currZ - startZ))
                return VoxelRaycastHit(
                    block = block,
                    hitX = currX,
                    hitY = currY,
                    hitZ = currZ,
                    distance = dist,
                    normalX = -normDx,
                    normalY = -normDy,
                    normalZ = -normDz
                )
            }
        }
        return null
    }

    /**
     * Checks if line-of-sight between two 3D points is unobstructed by solid destructible blocks.
     */
    fun hasLineOfSight(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float
    ): Boolean {
        val dx = x2 - x1
        val dy = y2 - y1
        val dz = z2 - z1
        val dist = sqrt(dx * dx + dy * dy + dz * dz)

        val hit = raycast3D(x1, y1, z1, dx, dy, dz, dist)
        return hit == null
    }

    /**
     * Retrieves all solid destructible blocks within a specified 3D sphere radius.
     */
    fun getBlocksInRadius(
        centerX: Float,
        centerY: Float,
        centerZ: Float,
        radius: Float
    ): List<DestructibleVoxelBlock> {
        val result = mutableListOf<DestructibleVoxelBlock>()

        val minGx = ((centerX - radius) / voxelSize).toInt().coerceIn(0, width - 1)
        val maxGx = ((centerX + radius) / voxelSize).toInt().coerceIn(0, width - 1)
        val minGy = ((centerY - radius) / voxelSize).toInt().coerceIn(0, height - 1)
        val maxGy = ((centerY + radius) / voxelSize).toInt().coerceIn(0, height - 1)
        val minGz = ((centerZ - radius) / voxelSize).toInt().coerceIn(0, depth - 1)
        val maxGz = ((centerZ + radius) / voxelSize).toInt().coerceIn(0, depth - 1)

        for (gx in minGx..maxGx) {
            for (gy in minGy..maxGy) {
                for (gz in minGz..maxGz) {
                    val block = grid[gx][gy][gz]
                    if (block.isSolid) {
                        val wx = (gx + 0.5f) * voxelSize
                        val wy = (gy + 0.5f) * voxelSize
                        val wz = (gz + 0.5f) * voxelSize

                        val dist = sqrt((wx - centerX) * (wx - centerX) + (wy - centerY) * (wy - centerY) + (wz - centerZ) * (wz - centerZ))
                        if (dist <= radius) {
                            result.add(block)
                        }
                    }
                }
            }
        }

        return result
    }

    /**
     * Calculates summary metrics for total grid health & destructible block count.
     */
    fun getGridMetrics(): VoxelGridMetrics {
        var totalBlocks = 0
        var intactBlocks = 0
        var damagedBlocks = 0
        var destroyedBlocks = 0
        var sumCurrentDurability = 0f
        var sumMaxDurability = 0f

        for (x in 0 until width) {
            for (y in 0 until height) {
                for (z in 0 until depth) {
                    val b = grid[x][y][z]
                    if (b.isDestructible) {
                        totalBlocks++
                        sumCurrentDurability += b.currentDurability
                        sumMaxDurability += b.maxDurability

                        when (b.updateDamageLevel()) {
                            BlockDamageLevel.INTACT -> intactBlocks++
                            BlockDamageLevel.DISINTEGRATED -> destroyedBlocks++
                            else -> damagedBlocks++
                        }
                    }
                }
            }
        }

        val totalDurabilityPercentage = if (sumMaxDurability > 0f) (sumCurrentDurability / sumMaxDurability) * 100f else 0f

        return VoxelGridMetrics(
            totalDestructibleBlocks = totalBlocks,
            intactBlocks = intactBlocks,
            damagedBlocks = damagedBlocks,
            destroyedBlocks = destroyedBlocks,
            totalDurabilityPercentage = totalDurabilityPercentage
        )
    }
}

/**
 * Summary metrics data class for the voxel grid state.
 */
data class VoxelGridMetrics(
    val totalDestructibleBlocks: Int,
    val intactBlocks: Int,
    val damagedBlocks: Int,
    val destroyedBlocks: Int,
    val totalDurabilityPercentage: Float
)
