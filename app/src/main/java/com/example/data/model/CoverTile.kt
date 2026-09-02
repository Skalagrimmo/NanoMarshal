package com.example.data.model

enum class VoxelType {
    FLOOR_DIRT,
    FLOOR_PLAZA,
    LOW_COVER_CRATE,
    HIGH_COVER_WALL,
    EXPLOSIVE_BARREL,
    ENERGY_BARRIER,
    ACID_POOL,
    OBJECTIVE_NODE,
    DESTRUCTIBLE_PILLAR,
    REINFORCED_METAL,
    CONCRETE_WALL,
    ALIEN_BIOMASS,
    NANITE_GAS_VENT,
    ELECTRIC_CONDUIT,
    CRYO_PIPE,
    PLASMA_GENERATOR
}

enum class CoverHeight {
    NONE,       // 0% cover
    LOW,        // 50% damage reduction when crouching/behind
    HIGH        // 90% damage reduction from front angle
}

/**
 * Interface representing a destructible tactical voxel with health and durability.
 */
interface DestructibleVoxel {
    var health: Float
    var maxHealth: Float
    var durability: Float
    var maxDurability: Float
    val isDestructible: Boolean
    val isDestroyed: Boolean
    fun takeDamage(amount: Float, armorPenetration: Float = 0f): Float
}

/**
 * Convenient alias for DestructibleVoxel.
 */
typealias Voxel = DestructibleVoxel

/**
 * Concrete base/standalone Voxel entity implementing DestructibleVoxel.
 */
open class VoxelObject(
    var type: VoxelType = VoxelType.CONCRETE_WALL,
    override var health: Float = 100f,
    override var maxHealth: Float = 100f,
    override var durability: Float = 100f,
    override var maxDurability: Float = 100f,
    override var isDestructible: Boolean = true,
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
) : DestructibleVoxel {
    override val isDestroyed: Boolean
        get() = health <= 0f

    override fun takeDamage(amount: Float, armorPenetration: Float): Float {
        if (!isDestructible || isDestroyed) return 0f
        val effectiveDur = (durability * (1.0f - armorPenetration.coerceIn(0f, 1f))).coerceAtLeast(0f)
        val mitigation = (effectiveDur / (effectiveDur + 55f)).coerceIn(0f, 0.72f)
        val healthDmg = amount * (1.0f - mitigation)
        val durDmg = amount * (0.35f + mitigation * 0.65f)
        durability = (durability - durDmg).coerceAtLeast(0f)
        health = (health - healthDmg).coerceAtLeast(0f)
        return healthDmg
    }
}

/**
 * Tactical Voxel Tile data structure supporting destructible environments
 * with real-time health, structural durability, deformation, and cover mechanics.
 */
data class VoxelTile(
    val gridX: Int,
    val gridY: Int,
    var elevationZ: Int = 0, // 0 = ground, 1 = low cover, 2 = wall/pillar, 3 = high structure
    var type: VoxelType,
    override var health: Float = 100f,
    override var durability: Float = 100f,
    override var maxHealth: Float = 100f,
    override var maxDurability: Float = 100f,
    override var isDestructible: Boolean = true,
    var coverHeight: CoverHeight = CoverHeight.NONE,
    var lodLevel: Int = 0,
    var isDisintegrated: Boolean = false,
    var deformationX: Float = 0f,
    var deformationY: Float = 0f,
    var meshScaleX: Float = 1.0f,
    var meshScaleY: Float = 1.0f,
    var rotationAngle: Float = 0f,
    var damageCracksCount: Int = 0,
    var hitFlashTimer: Float = 0f
) : DestructibleVoxel {

    // Backward-compatibility aliases for currentHp and maxHp
    var currentHp: Float
        get() = health
        set(value) {
            health = value
        }

    var maxHp: Float
        get() = maxHealth
        set(value) {
            maxHealth = value
        }

    override val isDestroyed: Boolean
        get() = (health <= 0f && durability <= 0f) || isDisintegrated

    val healthPercentage: Float
        get() = if (maxHealth > 0f) (health / maxHealth).coerceIn(0f, 1f) else 0f

    val durabilityPercentage: Float
        get() = if (maxDurability > 0f) (durability / maxDurability).coerceIn(0f, 1f) else 0f

    /**
     * Applies damage to this voxel tile taking into account durability and armor penetration.
     * Durability acts as structural resistance that mitigates incoming damage.
     * As durability is chipped away, incoming damage increasingly affects core health.
     * Returns the actual effective damage dealt to health.
     */
    override fun takeDamage(amount: Float, armorPenetration: Float): Float {
        if (!isDestructible || isDisintegrated) return 0f

        // Durability absorption ratio (up to 70% mitigation, bypassed by penetration)
        val effectiveDurability = (durability * (1.0f - armorPenetration.coerceIn(0f, 1f))).coerceAtLeast(0f)
        val mitigationFactor = (effectiveDurability / (effectiveDurability + 60f)).coerceIn(0f, 0.70f)
        val healthDamage = amount * (1.0f - mitigationFactor)

        // Durability degrades under stress
        val durabilityDamage = amount * (0.35f + mitigationFactor * 0.5f)
        durability = (durability - durabilityDamage).coerceAtLeast(0f)
        health = (health - healthDamage).coerceAtLeast(0f)

        if (health <= 0f) {
            health = 0f
            if (elevationZ <= 1) {
                isDisintegrated = true
                coverHeight = CoverHeight.NONE
            }
        }
        return healthDamage
    }

    /**
     * Secondary constructor providing backward compatibility for callers specifying currentHp/maxHp.
     */
    constructor(
        gridX: Int,
        gridY: Int,
        elevationZ: Int = 0,
        type: VoxelType,
        currentHp: Float,
        maxHp: Float = currentHp,
        isDestructible: Boolean = true,
        coverHeight: CoverHeight = CoverHeight.NONE,
        lodLevel: Int = 0,
        isDisintegrated: Boolean = false,
        deformationX: Float = 0f,
        deformationY: Float = 0f,
        meshScaleX: Float = 1.0f,
        meshScaleY: Float = 1.0f,
        rotationAngle: Float = 0f,
        damageCracksCount: Int = 0,
        hitFlashTimer: Float = 0f,
        durability: Float = maxHp,
        maxDurability: Float = durability
    ) : this(
        gridX = gridX,
        gridY = gridY,
        elevationZ = elevationZ,
        type = type,
        health = currentHp,
        durability = durability,
        maxHealth = maxHp,
        maxDurability = maxDurability,
        isDestructible = isDestructible,
        coverHeight = coverHeight,
        lodLevel = lodLevel,
        isDisintegrated = isDisintegrated,
        deformationX = deformationX,
        deformationY = deformationY,
        meshScaleX = meshScaleX,
        meshScaleY = meshScaleY,
        rotationAngle = rotationAngle,
        damageCracksCount = damageCracksCount,
        hitFlashTimer = hitFlashTimer
    )

    val isWalkable: Boolean get() = (coverHeight == CoverHeight.NONE && type != VoxelType.ACID_POOL) || isDisintegrated
    val isHazard: Boolean get() = (type == VoxelType.ACID_POOL || type == VoxelType.NANITE_GAS_VENT || type == VoxelType.ELECTRIC_CONDUIT || type == VoxelType.CRYO_PIPE || type == VoxelType.PLASMA_GENERATOR) && !isDisintegrated
}
