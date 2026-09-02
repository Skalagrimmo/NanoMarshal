package com.example.data.model

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

enum class WeaponType {
    PISTOL, PLASMA_RIFLE, SHOTGUN, RAILGUN, SNIPER
}

/**
 * Destructive damage categories calibrated against the voxel landscape materials.
 */
enum class WeaponDamageType(
    val displayName: String,
    val description: String,
    val colorHex: Long,
    val isAreaOfEffect: Boolean = false,
    val heatGenerationFactor: Float = 1.0f
) {
    KINETIC(
        displayName = "Kinetic Impact",
        description = "High-velocity ballistic force effective at fracturing concrete walls and low cover crates.",
        colorHex = 0xFFFFE600,
        isAreaOfEffect = false,
        heatGenerationFactor = 0.8f
    ),
    THERMAL_PLASMA(
        displayName = "Thermal Plasma",
        description = "Superheated energy discharge that vaporizes organic alien biomass and melts energy barriers.",
        colorHex = 0xFF38EF7D,
        isAreaOfEffect = false,
        heatGenerationFactor = 1.4f
    ),
    HIGH_EXPLOSIVE(
        displayName = "High Explosive",
        description = "Concussive radial shockwaves that pulverize destructible pillars and trigger barrel chain reactions.",
        colorHex = 0xFFFF5252,
        isAreaOfEffect = true,
        heatGenerationFactor = 2.0f
    ),
    ELECTROMAGNETIC_BEAM(
        displayName = "EM Hyper-Beam",
        description = "Coherent high-frequency beam that pierces heavy metal plating and disrupts objective nodes.",
        colorHex = 0xFF00E5FF,
        isAreaOfEffect = false,
        heatGenerationFactor = 1.8f
    ),
    CORROSIVE_ACID(
        displayName = "Corrosive Acid",
        description = "Chemical agent that dissolves structural voxel block durability over continuous application.",
        colorHex = 0xFF76FF03,
        isAreaOfEffect = false,
        heatGenerationFactor = 1.1f
    )
}

/**
 * Classifies projectile ballistics, explosive payloads, and structural interaction rules with voxel objects.
 */
enum class ProjectileType(
    val displayName: String,
    val baseArmorPenetration: Float = 0.20f,
    val structuralDamageMultiplier: Float = 1.0f,
    val durabilityShredFactor: Float = 1.0f,
    val isExplosive: Boolean = false,
    val explosionRadius: Float = 0.0f,
    val primaryColorHex: Long = 0xFFFFE600,
    val primaryParticleType: ParticleType = ParticleType.DEBRIS_VOXEL
) {
    BULLET_KINETIC(
        displayName = "Kinetic Ballistic",
        baseArmorPenetration = 0.20f,
        structuralDamageMultiplier = 1.25f,
        durabilityShredFactor = 1.10f,
        isExplosive = false,
        primaryColorHex = 0xFFFFE600,
        primaryParticleType = ParticleType.DEBRIS_VOXEL
    ),
    PLASMA_BOLT(
        displayName = "Thermal Plasma Bolt",
        baseArmorPenetration = 0.45f,
        structuralDamageMultiplier = 1.60f,
        durabilityShredFactor = 1.40f,
        isExplosive = false,
        primaryColorHex = 0xFF38EF7D,
        primaryParticleType = ParticleType.PLASMA_SPARK
    ),
    EXPLOSIVE_ROCKET(
        displayName = "High Explosive Rocket",
        baseArmorPenetration = 0.65f,
        structuralDamageMultiplier = 3.20f,
        durabilityShredFactor = 2.50f,
        isExplosive = true,
        explosionRadius = 140f,
        primaryColorHex = 0xFFFF5252,
        primaryParticleType = ParticleType.EXPLOSION_FLAME
    ),
    RAILGUN_SLUG(
        displayName = "Hyperion Rail Slug",
        baseArmorPenetration = 0.90f,
        structuralDamageMultiplier = 2.40f,
        durabilityShredFactor = 1.80f,
        isExplosive = false,
        primaryColorHex = 0xFF00E5FF,
        primaryParticleType = ParticleType.ELECTRIC_BOLT
    ),
    SCATTER_PELLET(
        displayName = "Scatter Flak Pellet",
        baseArmorPenetration = 0.10f,
        structuralDamageMultiplier = 1.15f,
        durabilityShredFactor = 1.30f,
        isExplosive = false,
        primaryColorHex = 0xFFFF9100,
        primaryParticleType = ParticleType.DEBRIS_VOXEL
    ),
    ENERGY_BEAM(
        displayName = "Coherent Energy Beam",
        baseArmorPenetration = 0.75f,
        structuralDamageMultiplier = 2.00f,
        durabilityShredFactor = 1.60f,
        isExplosive = false,
        primaryColorHex = 0xFF7C4DFF,
        primaryParticleType = ParticleType.PLASMA_WAVE
    ),
    CORROSIVE_ACID(
        displayName = "Virulent Acid Flechette",
        baseArmorPenetration = 0.55f,
        structuralDamageMultiplier = 1.85f,
        durabilityShredFactor = 2.20f,
        isExplosive = false,
        primaryColorHex = 0xFF76FF03,
        primaryParticleType = ParticleType.NANITE_SPORE
    ),
    CRYO_FLECHETTE(
        displayName = "Cryogenic Crystal Flechette",
        baseArmorPenetration = 0.35f,
        structuralDamageMultiplier = 1.30f,
        durabilityShredFactor = 1.20f,
        isExplosive = false,
        primaryColorHex = 0xFF80D8FF,
        primaryParticleType = ParticleType.CRYO_CRYSTAL
    );

    companion object {
        val KINETIC get() = BULLET_KINETIC
        val PLASMA get() = PLASMA_BOLT
        val EXPLOSIVE get() = EXPLOSIVE_ROCKET
        val RAILGUN get() = RAILGUN_SLUG
        val SCATTER get() = SCATTER_PELLET
        val BEAM get() = ENERGY_BEAM
        val ACID get() = CORROSIVE_ACID
        val CRYO get() = CRYO_FLECHETTE

        fun fromDamageType(damageType: WeaponDamageType): ProjectileType = when (damageType) {
            WeaponDamageType.KINETIC -> BULLET_KINETIC
            WeaponDamageType.THERMAL_PLASMA -> PLASMA_BOLT
            WeaponDamageType.HIGH_EXPLOSIVE -> EXPLOSIVE_ROCKET
            WeaponDamageType.ELECTROMAGNETIC_BEAM -> RAILGUN_SLUG
            WeaponDamageType.CORROSIVE_ACID -> CORROSIVE_ACID
        }

        fun fromWeaponType(weaponType: WeaponType): ProjectileType = when (weaponType) {
            WeaponType.PISTOL -> BULLET_KINETIC
            WeaponType.PLASMA_RIFLE -> PLASMA_BOLT
            WeaponType.SHOTGUN -> SCATTER_PELLET
            WeaponType.RAILGUN -> RAILGUN_SLUG
            WeaponType.SNIPER -> ENERGY_BEAM
        }

        fun fallbackWeaponType(proj: ProjectileType): WeaponType = when (proj) {
            BULLET_KINETIC -> WeaponType.PISTOL
            PLASMA_BOLT -> WeaponType.PLASMA_RIFLE
            EXPLOSIVE_ROCKET, SCATTER_PELLET -> WeaponType.SHOTGUN
            RAILGUN_SLUG -> WeaponType.RAILGUN
            ENERGY_BEAM -> WeaponType.SNIPER
            else -> WeaponType.PISTOL
        }
    }
}

/**
 * Result data payload returned when a Weapon inflicts damage upon a Voxel object.
 */
data class WeaponVoxelDamageResult(
    val voxel: DestructibleVoxel,
    val initialHealth: Float,
    val initialDurability: Float,
    val damageDealt: Float,
    val healthDamage: Float,
    val durabilityDamage: Float,
    val remainingHealth: Float,
    val remainingDurability: Float,
    val wasDestroyed: Boolean,
    val destructionEffects: List<Particle> = emptyList()
)

typealias VoxelAttackResult = WeaponVoxelDamageResult

/**
 * Firing behavior profiles governing rate-of-fire cycles and burst mechanics.
 */
enum class FiringMode(
    val displayName: String,
    val isAutomatic: Boolean,
    val burstCount: Int = 1,
    val cyclicRateRpm: Int = 300,
    val heatPerShot: Float = 5.0f
) {
    SINGLE_SEMI(
        displayName = "Semi-Automatic",
        isAutomatic = false,
        burstCount = 1,
        cyclicRateRpm = 220,
        heatPerShot = 3.0f
    ),
    FULL_AUTO(
        displayName = "Full Automatic",
        isAutomatic = true,
        burstCount = 1,
        cyclicRateRpm = 650,
        heatPerShot = 4.5f
    ),
    BURST_3(
        displayName = "3-Round Burst",
        isAutomatic = false,
        burstCount = 3,
        cyclicRateRpm = 800,
        heatPerShot = 4.0f
    ),
    PULSE_CHARGE(
        displayName = "Pulse Charge",
        isAutomatic = false,
        burstCount = 1,
        cyclicRateRpm = 55,
        heatPerShot = 25.0f
    ),
    SPREAD_SCATTER(
        displayName = "Spread Scatter",
        isAutomatic = false,
        burstCount = 8,
        cyclicRateRpm = 85,
        heatPerShot = 12.0f
    )
}

/**
 * Physical recoil trajectory parameters governing weapon kick, accuracy bloom, and stance damping.
 */
data class RecoilPattern(
    val verticalKickDeg: Float,        // Upward aim angle deflection per shot in degrees
    val horizontalSwayDeg: Float,      // Maximum left/right random aim sway per shot
    val recoveryRate: Float,           // Speed at which aim returns to center (units/sec)
    val recoilImpulseForce: Float,     // Physical backward force applied to firing entity
    val spreadAngleMaxDeg: Float,      // Cone of fire spread ceiling
    val spreadGrowthPerShot: Float,    // Increase in spread angle per fired projectile
    val crouchDampingFactor: Float = 0.60f, // Recoil reduction when crouching (40% less recoil)
    val proneDampingFactor: Float = 0.35f   // Recoil reduction when prone (65% less recoil)
)

/**
 * Matrix calibrating damage effectiveness multipliers between weapon damage types and voxel material types.
 */
object VoxelDamageCalibrator {
    fun getDamageMultiplier(damageType: WeaponDamageType, voxelType: VoxelType): Float {
        return when (damageType) {
            WeaponDamageType.KINETIC -> when (voxelType) {
                VoxelType.CONCRETE_WALL -> 1.35f
                VoxelType.DESTRUCTIBLE_PILLAR -> 1.45f
                VoxelType.LOW_COVER_CRATE -> 1.25f
                VoxelType.REINFORCED_METAL -> 0.80f
                VoxelType.ALIEN_BIOMASS -> 0.90f
                VoxelType.ENERGY_BARRIER -> 0.70f
                else -> 1.00f
            }
            WeaponDamageType.THERMAL_PLASMA -> when (voxelType) {
                VoxelType.ALIEN_BIOMASS -> 2.50f
                VoxelType.ENERGY_BARRIER -> 2.20f
                VoxelType.LOW_COVER_CRATE -> 1.50f
                VoxelType.ACID_POOL -> 1.40f
                VoxelType.REINFORCED_METAL -> 0.65f
                else -> 1.10f
            }
            WeaponDamageType.HIGH_EXPLOSIVE -> when (voxelType) {
                VoxelType.CONCRETE_WALL -> 2.80f
                VoxelType.DESTRUCTIBLE_PILLAR -> 3.40f
                VoxelType.EXPLOSIVE_BARREL -> 4.50f
                VoxelType.HIGH_COVER_WALL -> 2.60f
                VoxelType.REINFORCED_METAL -> 1.60f
                else -> 2.00f
            }
            WeaponDamageType.ELECTROMAGNETIC_BEAM -> when (voxelType) {
                VoxelType.REINFORCED_METAL -> 2.30f
                VoxelType.OBJECTIVE_NODE -> 2.60f
                VoxelType.ENERGY_BARRIER -> 2.10f
                VoxelType.HIGH_COVER_WALL -> 1.70f
                else -> 1.20f
            }
            WeaponDamageType.CORROSIVE_ACID -> when (voxelType) {
                VoxelType.REINFORCED_METAL -> 2.10f
                VoxelType.CONCRETE_WALL -> 1.85f
                VoxelType.LOW_COVER_CRATE -> 1.95f
                VoxelType.ALIEN_BIOMASS -> 0.50f
                else -> 1.30f
            }
        }
    }
}

data class Weapon(
    val id: String = "w_weapon",
    val name: String = "Tactical Weapon",
    val type: WeaponType = WeaponType.PISTOL,
    val damage: Int = 35,
    val fireRateMs: Long = 250,
    val magSize: Int = 15,
    val reloadTimeMs: Long = 1500,
    val coverDamage: Int = 20,
    val isSilenced: Boolean = false,
    val pierceCover: Boolean = false,
    val iconName: String = "ic_weapon",
    val description: String = "Tactical combat weapon.",
    val cost: Int = 0,
    var isUnlocked: Boolean = false,
    var upgradeLevel: Int = 1,
    val damageType: WeaponDamageType = WeaponDamageType.KINETIC,
    val firingMode: FiringMode = FiringMode.SINGLE_SEMI,
    val recoilPattern: RecoilPattern = RecoilPattern(
        verticalKickDeg = 2.0f,
        horizontalSwayDeg = 0.8f,
        recoveryRate = 10.0f,
        recoilImpulseForce = 1.0f,
        spreadAngleMaxDeg = 5.0f,
        spreadGrowthPerShot = 0.5f
    ),
    val maxHeatCapacity: Float = 100.0f,
    val coolingRatePerSec: Float = 25.0f,
    val maxRicochets: Int = 2,
    val projectileType: ProjectileType = ProjectileType.fromDamageType(damageType)
) {
    val effectiveDamage: Int get() = (damage * (1.0 + (upgradeLevel - 1) * 0.25)).toInt()

    var onDestructionListener: ((voxel: DestructibleVoxel, effects: List<Particle>) -> Unit)? = null

    /**
     * Convenience secondary constructor allowing simple instantiation with name, damage, and projectile type.
     */
    constructor(
        name: String,
        damage: Int,
        projectileType: ProjectileType = ProjectileType.BULLET_KINETIC
    ) : this(
        id = "w_${name.lowercase().replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }}",
        name = name,
        type = ProjectileType.fallbackWeaponType(projectileType),
        damage = damage,
        fireRateMs = 250L,
        magSize = 15,
        reloadTimeMs = 1500L,
        coverDamage = (damage * 0.75f).toInt(),
        iconName = "ic_weapon",
        description = "$name firing ${projectileType.displayName} rounds.",
        damageType = when (projectileType) {
            ProjectileType.BULLET_KINETIC -> WeaponDamageType.KINETIC
            ProjectileType.PLASMA_BOLT -> WeaponDamageType.THERMAL_PLASMA
            ProjectileType.EXPLOSIVE_ROCKET, ProjectileType.SCATTER_PELLET -> WeaponDamageType.HIGH_EXPLOSIVE
            ProjectileType.RAILGUN_SLUG, ProjectileType.ENERGY_BEAM -> WeaponDamageType.ELECTROMAGNETIC_BEAM
            ProjectileType.CORROSIVE_ACID -> WeaponDamageType.CORROSIVE_ACID
            ProjectileType.CRYO_FLECHETTE -> WeaponDamageType.KINETIC
        },
        projectileType = projectileType
    )

    /**
     * Calculates damage to a Voxel object based on projectile type, weapon power, and voxel material.
     */
    fun calculateDamage(
        voxel: DestructibleVoxel,
        projectileType: ProjectileType = this.projectileType,
        voxelType: VoxelType? = (voxel as? VoxelTile)?.type
    ): Float {
        val base = effectiveDamage.toFloat().coerceAtLeast(damage.toFloat())
        val projMultiplier = projectileType.structuralDamageMultiplier

        val resolvedType = voxelType ?: (voxel as? VoxelTile)?.type
        val materialMultiplier = if (resolvedType != null) {
            getMaterialDamageMultiplier(projectileType, resolvedType)
        } else {
            1.0f
        }

        val coverBonus = (coverDamage.toFloat() / 35.0f).coerceIn(0.6f, 2.5f)
        val upgradeBonus = 1.0f + (upgradeLevel - 1) * 0.15f

        return (base * projMultiplier * materialMultiplier * coverBonus * upgradeBonus).coerceAtLeast(1.0f)
    }

    /**
     * Overload for calculating damage to a VoxelTile.
     */
    fun calculateDamage(
        tile: VoxelTile,
        projectileType: ProjectileType = this.projectileType
    ): Float = calculateDamage(tile, projectileType, tile.type)

    /**
     * Calculates material damage interaction between a projectile type and a voxel material.
     */
    fun getMaterialDamageMultiplier(projectileType: ProjectileType, voxelType: VoxelType): Float {
        return when (projectileType) {
            ProjectileType.BULLET_KINETIC -> when (voxelType) {
                VoxelType.CONCRETE_WALL -> 1.35f
                VoxelType.DESTRUCTIBLE_PILLAR -> 1.45f
                VoxelType.LOW_COVER_CRATE -> 1.30f
                VoxelType.REINFORCED_METAL -> 0.75f
                VoxelType.ALIEN_BIOMASS -> 0.90f
                VoxelType.ENERGY_BARRIER -> 0.65f
                else -> 1.0f
            }
            ProjectileType.PLASMA_BOLT -> when (voxelType) {
                VoxelType.ALIEN_BIOMASS -> 2.60f
                VoxelType.ENERGY_BARRIER -> 2.30f
                VoxelType.LOW_COVER_CRATE -> 1.50f
                VoxelType.ACID_POOL -> 1.40f
                VoxelType.REINFORCED_METAL -> 0.70f
                else -> 1.15f
            }
            ProjectileType.EXPLOSIVE_ROCKET -> when (voxelType) {
                VoxelType.EXPLOSIVE_BARREL -> 4.80f
                VoxelType.DESTRUCTIBLE_PILLAR -> 3.50f
                VoxelType.CONCRETE_WALL -> 2.90f
                VoxelType.HIGH_COVER_WALL -> 2.70f
                VoxelType.LOW_COVER_CRATE -> 2.50f
                VoxelType.REINFORCED_METAL -> 1.80f
                else -> 2.10f
            }
            ProjectileType.RAILGUN_SLUG -> when (voxelType) {
                VoxelType.REINFORCED_METAL -> 2.50f
                VoxelType.OBJECTIVE_NODE -> 2.70f
                VoxelType.ENERGY_BARRIER -> 2.20f
                VoxelType.HIGH_COVER_WALL -> 1.80f
                VoxelType.CONCRETE_WALL -> 1.60f
                else -> 1.30f
            }
            ProjectileType.SCATTER_PELLET -> when (voxelType) {
                VoxelType.LOW_COVER_CRATE -> 1.75f
                VoxelType.DESTRUCTIBLE_PILLAR -> 1.40f
                VoxelType.ALIEN_BIOMASS -> 1.30f
                VoxelType.CONCRETE_WALL -> 1.10f
                VoxelType.REINFORCED_METAL -> 0.60f
                else -> 1.0f
            }
            ProjectileType.ENERGY_BEAM -> when (voxelType) {
                VoxelType.REINFORCED_METAL -> 2.20f
                VoxelType.ENERGY_BARRIER -> 2.40f
                VoxelType.OBJECTIVE_NODE -> 2.50f
                VoxelType.HIGH_COVER_WALL -> 1.75f
                else -> 1.25f
            }
            ProjectileType.CORROSIVE_ACID -> when (voxelType) {
                VoxelType.REINFORCED_METAL -> 2.25f
                VoxelType.CONCRETE_WALL -> 1.90f
                VoxelType.LOW_COVER_CRATE -> 2.00f
                VoxelType.ALIEN_BIOMASS -> 0.60f
                else -> 1.35f
            }
            ProjectileType.CRYO_FLECHETTE -> when (voxelType) {
                VoxelType.PLASMA_GENERATOR -> 2.50f
                VoxelType.ELECTRIC_CONDUIT -> 2.00f
                VoxelType.ENERGY_BARRIER -> 1.80f
                VoxelType.CRYO_PIPE -> 1.60f
                else -> 1.10f
            }
        }
    }

    /**
     * Calculates damage, updates voxel health and durability, and triggers destruction visual effects
     * when the voxel's health reaches zero.
     */
    fun damageVoxel(
        voxel: DestructibleVoxel,
        projectileType: ProjectileType = this.projectileType,
        voxelType: VoxelType? = (voxel as? VoxelTile)?.type,
        hitWorldX: Float = 0f,
        hitWorldY: Float = 0f,
        spawnedParticles: MutableList<Particle>? = null,
        onDestruction: ((DestructibleVoxel, List<Particle>) -> Unit)? = null
    ): WeaponVoxelDamageResult {
        val initialHealth = voxel.health
        val initialDurability = voxel.durability

        if (!voxel.isDestructible || voxel.isDestroyed) {
            return WeaponVoxelDamageResult(
                voxel = voxel,
                initialHealth = initialHealth,
                initialDurability = initialDurability,
                damageDealt = 0f,
                healthDamage = 0f,
                durabilityDamage = 0f,
                remainingHealth = voxel.health,
                remainingDurability = voxel.durability,
                wasDestroyed = voxel.isDestroyed,
                destructionEffects = emptyList()
            )
        }

        val rawDamage = calculateDamage(voxel, projectileType, voxelType)

        // Durability mitigation vs Armor penetration
        val armorPen = projectileType.baseArmorPenetration.coerceIn(0f, 1f)
        val effectiveDurability = (voxel.durability * (1.0f - armorPen)).coerceAtLeast(0f)
        val mitigationFactor = (effectiveDurability / (effectiveDurability + 55.0f)).coerceIn(0f, 0.72f)
        val healthDamage = rawDamage * (1.0f - mitigationFactor)
        val durabilityDamage = rawDamage * (0.35f + mitigationFactor * 0.65f) * projectileType.durabilityShredFactor

        // Update durability and health on the voxel object
        val newDurability = (voxel.durability - durabilityDamage).coerceAtLeast(0f)
        val newHealth = (voxel.health - healthDamage).coerceAtLeast(0f)
        voxel.durability = newDurability
        voxel.health = newHealth

        val wasDestroyed = newHealth <= 0f || voxel.isDestroyed
        if (newHealth <= 0f) {
            voxel.health = 0f
            if (voxel is VoxelTile) {
                voxel.isDisintegrated = true
                voxel.coverHeight = CoverHeight.NONE
            }
        }

        // Trigger destruction visual effects when health reaches zero
        val effects = if (wasDestroyed) {
            val resolvedX = if (hitWorldX != 0f) hitWorldX else ((voxel as? VoxelTile)?.let { (it.gridX + 0.5f) * 32f } ?: 0f)
            val resolvedY = if (hitWorldY != 0f) hitWorldY else ((voxel as? VoxelTile)?.let { (it.gridY + 0.5f) * 32f } ?: 0f)
            createDestructionVisualEffects(
                voxel = voxel,
                projectileType = projectileType,
                worldX = resolvedX,
                worldY = resolvedY,
                voxelType = voxelType ?: (voxel as? VoxelTile)?.type
            )
        } else {
            emptyList()
        }

        if (wasDestroyed && effects.isNotEmpty()) {
            spawnedParticles?.addAll(effects)
            onDestruction?.invoke(voxel, effects)
            onDestructionListener?.invoke(voxel, effects)
        }

        return WeaponVoxelDamageResult(
            voxel = voxel,
            initialHealth = initialHealth,
            initialDurability = initialDurability,
            damageDealt = rawDamage,
            healthDamage = healthDamage,
            durabilityDamage = durabilityDamage,
            remainingHealth = voxel.health,
            remainingDurability = voxel.durability,
            wasDestroyed = wasDestroyed,
            destructionEffects = effects
        )
    }

    /**
     * Overload for directly damaging a VoxelTile.
     */
    fun damageVoxel(
        tile: VoxelTile,
        projectileType: ProjectileType = this.projectileType,
        spawnedParticles: MutableList<Particle>? = null,
        onDestruction: ((DestructibleVoxel, List<Particle>) -> Unit)? = null
    ): WeaponVoxelDamageResult {
        val wx = (tile.gridX + 0.5f) * 32f
        val wy = (tile.gridY + 0.5f) * 32f
        return damageVoxel(tile, projectileType, tile.type, wx, wy, spawnedParticles, onDestruction)
    }

    /**
     * Applies damage to a voxel object with full projectile simulation parameters.
     */
    fun applyDamage(
        voxel: DestructibleVoxel,
        projectileType: ProjectileType = this.projectileType,
        voxelType: VoxelType? = (voxel as? VoxelTile)?.type,
        hitWorldX: Float = 0f,
        hitWorldY: Float = 0f,
        spawnedParticles: MutableList<Particle>? = null,
        onDestruction: ((DestructibleVoxel, List<Particle>) -> Unit)? = null
    ): WeaponVoxelDamageResult = damageVoxel(voxel, projectileType, voxelType, hitWorldX, hitWorldY, spawnedParticles, onDestruction)

    /**
     * Updates health and durability of the voxel based on projectile damage calculations.
     */
    fun updateHealthAndDurability(
        voxel: DestructibleVoxel,
        projectileType: ProjectileType = this.projectileType
    ): WeaponVoxelDamageResult = damageVoxel(voxel, projectileType)

    /**
     * Generates responsive destruction visual effect particles tailored to projectile type and voxel material.
     */
    fun createDestructionVisualEffects(
        voxel: DestructibleVoxel,
        projectileType: ProjectileType = this.projectileType,
        worldX: Float = 0f,
        worldY: Float = 0f,
        voxelType: VoxelType? = (voxel as? VoxelTile)?.type
    ): List<Particle> {
        val particles = mutableListOf<Particle>()
        val rand = Random(System.nanoTime())

        val baseColor = when (voxelType) {
            VoxelType.CONCRETE_WALL -> Color(0xFFB0BEC5)
            VoxelType.DESTRUCTIBLE_PILLAR -> Color(0xFF90A4AE)
            VoxelType.LOW_COVER_CRATE -> Color(0xFFD7CCC8)
            VoxelType.REINFORCED_METAL -> Color(0xFF78909C)
            VoxelType.ALIEN_BIOMASS -> Color(0xFF00E676)
            VoxelType.ENERGY_BARRIER -> Color(0xFF00E5FF)
            VoxelType.EXPLOSIVE_BARREL -> Color(0xFFFFB703)
            VoxelType.ACID_POOL -> Color(0xFF76FF03)
            VoxelType.NANITE_GAS_VENT -> Color(0xFFAB47BC)
            VoxelType.CRYO_PIPE -> Color(0xFF80D8FF)
            VoxelType.ELECTRIC_CONDUIT -> Color(0xFFFFD600)
            VoxelType.PLASMA_GENERATOR -> Color(0xFFFF4081)
            else -> Color(projectileType.primaryColorHex)
        }

        // 1. Primary tumbling voxel debris chunks
        val debrisCount = when (projectileType) {
            ProjectileType.EXPLOSIVE_ROCKET -> 24
            ProjectileType.RAILGUN_SLUG -> 18
            ProjectileType.PLASMA_BOLT -> 16
            ProjectileType.SCATTER_PELLET -> 20
            else -> 14
        }

        for (i in 0 until debrisCount) {
            val angle = rand.nextFloat() * 2f * Math.PI.toFloat()
            val speed = 60f + rand.nextFloat() * 160f
            val vx = cos(angle) * speed
            val vy = sin(angle) * speed
            val size = 4f + rand.nextFloat() * 6f
            val life = 0.4f + rand.nextFloat() * 0.5f

            particles.add(
                Particle(
                    x = worldX,
                    y = worldY,
                    vx = vx,
                    vy = vy,
                    color = baseColor,
                    size = size,
                    life = life,
                    maxLife = life,
                    type = ParticleType.DEBRIS_VOXEL,
                    rotation = rand.nextFloat() * 360f,
                    vRot = (rand.nextFloat() - 0.5f) * 720f,
                    aspectRatio = 0.7f + rand.nextFloat() * 0.6f
                )
            )
        }

        // 2. Projectile-specific payload effects
        when (projectileType) {
            ProjectileType.EXPLOSIVE_ROCKET -> {
                for (i in 0 until 16) {
                    val a = rand.nextFloat() * 2f * Math.PI.toFloat()
                    val spd = 40f + rand.nextFloat() * 120f
                    particles.add(
                        Particle(
                            x = worldX + (rand.nextFloat() - 0.5f) * 16f,
                            y = worldY + (rand.nextFloat() - 0.5f) * 16f,
                            vx = cos(a) * spd,
                            vy = sin(a) * spd,
                            color = if (rand.nextBoolean()) Color(0xFFFF5722) else Color(0xFFFFC107),
                            size = 8f + rand.nextFloat() * 10f,
                            life = 0.5f + rand.nextFloat() * 0.4f,
                            type = ParticleType.EXPLOSION_FLAME
                        )
                    )
                }
                particles.add(
                    Particle(
                        x = worldX,
                        y = worldY,
                        vx = 0f,
                        vy = 0f,
                        color = Color(0xFFFF9800),
                        size = 70f,
                        life = 0.4f,
                        maxLife = 0.4f,
                        type = ParticleType.PLASMA_WAVE
                    )
                )
                for (i in 0 until 8) {
                    val a = rand.nextFloat() * 2f * Math.PI.toFloat()
                    particles.add(
                        Particle(
                            x = worldX,
                            y = worldY,
                            vx = cos(a) * 30f,
                            vy = sin(a) * 30f,
                            color = Color(0xFF616161),
                            size = 14f + rand.nextFloat() * 12f,
                            life = 0.7f + rand.nextFloat() * 0.4f,
                            type = ParticleType.SMOKE_NANO
                        )
                    )
                }
            }
            ProjectileType.PLASMA_BOLT -> {
                for (i in 0 until 14) {
                    val a = rand.nextFloat() * 2f * Math.PI.toFloat()
                    val spd = 80f + rand.nextFloat() * 160f
                    particles.add(
                        Particle(
                            x = worldX,
                            y = worldY,
                            vx = cos(a) * spd,
                            vy = sin(a) * spd,
                            color = Color(0xFF38EF7D),
                            size = 5f + rand.nextFloat() * 4f,
                            life = 0.45f + rand.nextFloat() * 0.35f,
                            type = ParticleType.PLASMA_SPARK
                        )
                    )
                }
                particles.add(
                    Particle(
                        x = worldX,
                        y = worldY,
                        vx = 0f,
                        vy = 0f,
                        color = Color(0xFF00E676),
                        size = 50f,
                        life = 0.35f,
                        maxLife = 0.35f,
                        type = ParticleType.PLASMA_WAVE
                    )
                )
            }
            ProjectileType.RAILGUN_SLUG, ProjectileType.ENERGY_BEAM -> {
                for (i in 0 until 10) {
                    val a = rand.nextFloat() * 2f * Math.PI.toFloat()
                    val spd = 70f + rand.nextFloat() * 140f
                    particles.add(
                        Particle(
                            x = worldX,
                            y = worldY,
                            vx = cos(a) * spd,
                            vy = sin(a) * spd,
                            color = Color(0xFF00E5FF),
                            size = 4f + rand.nextFloat() * 4f,
                            life = 0.35f + rand.nextFloat() * 0.3f,
                            type = ParticleType.ELECTRIC_BOLT
                        )
                    )
                }
                particles.add(
                    Particle(
                        x = worldX,
                        y = worldY,
                        vx = 0f,
                        vy = 0f,
                        color = Color(0xFF7C4DFF),
                        size = 55f,
                        life = 0.3f,
                        maxLife = 0.3f,
                        type = ParticleType.PLASMA_WAVE
                    )
                )
            }
            ProjectileType.CORROSIVE_ACID -> {
                for (i in 0 until 12) {
                    val a = rand.nextFloat() * 2f * Math.PI.toFloat()
                    val spd = 50f + rand.nextFloat() * 100f
                    particles.add(
                        Particle(
                            x = worldX,
                            y = worldY,
                            vx = cos(a) * spd,
                            vy = sin(a) * spd,
                            color = Color(0xFF76FF03),
                            size = 6f + rand.nextFloat() * 5f,
                            life = 0.6f + rand.nextFloat() * 0.4f,
                            type = ParticleType.NANITE_SPORE
                        )
                    )
                }
            }
            ProjectileType.CRYO_FLECHETTE -> {
                for (i in 0 until 12) {
                    val a = rand.nextFloat() * 2f * Math.PI.toFloat()
                    val spd = 50f + rand.nextFloat() * 110f
                    particles.add(
                        Particle(
                            x = worldX,
                            y = worldY,
                            vx = cos(a) * spd,
                            vy = sin(a) * spd,
                            color = Color(0xFF80D8FF),
                            size = 6f + rand.nextFloat() * 5f,
                            life = 0.5f + rand.nextFloat() * 0.4f,
                            type = ParticleType.CRYO_CRYSTAL
                        )
                    )
                }
            }
            else -> {
                for (i in 0 until 6) {
                    val a = rand.nextFloat() * 2f * Math.PI.toFloat()
                    particles.add(
                        Particle(
                            x = worldX,
                            y = worldY,
                            vx = cos(a) * 40f,
                            vy = sin(a) * 40f,
                            color = Color(0xFF8D6E63),
                            size = 8f + rand.nextFloat() * 8f,
                            life = 0.5f,
                            type = ParticleType.SMOKE_NANO
                        )
                    )
                }
            }
        }

        // 3. Floating destruction indicator text
        particles.add(
            Particle(
                x = worldX - 25f,
                y = worldY - 10f,
                vx = 0f,
                vy = -35f,
                color = Color(0xFFFF5252),
                size = 14f,
                life = 0.8f,
                maxLife = 0.8f,
                type = ParticleType.HIT_NUMBER,
                text = "DESTROYED"
            )
        )

        return particles
    }
}

object DefaultWeapons {
    val NEEDLE_PISTOL = Weapon(
        id = "w_needle",
        name = "Needle Sub-Pistol",
        type = WeaponType.PISTOL,
        damage = 28,
        fireRateMs = 280,
        magSize = 12,
        reloadTimeMs = 1200,
        coverDamage = 15,
        isSilenced = true,
        iconName = "ic_needle",
        description = "Sub-sonic suppressed sidearm delivering rapid kinetic needle rounds with near-zero acoustic footprint.",
        cost = 0,
        isUnlocked = true,
        damageType = WeaponDamageType.KINETIC,
        firingMode = FiringMode.SINGLE_SEMI,
        projectileType = ProjectileType.BULLET_KINETIC,
        recoilPattern = RecoilPattern(
            verticalKickDeg = 1.2f,
            horizontalSwayDeg = 0.3f,
            recoveryRate = 14.0f,
            recoilImpulseForce = 0.4f,
            spreadAngleMaxDeg = 3.0f,
            spreadGrowthPerShot = 0.3f
        )
    )

    val PLASMA_RIFLE = Weapon(
        id = "w_plasma",
        name = "VORTEX Plasma Rifle",
        type = WeaponType.PLASMA_RIFLE,
        damage = 38,
        fireRateMs = 160,
        magSize = 30,
        reloadTimeMs = 1800,
        coverDamage = 30,
        isSilenced = false,
        iconName = "ic_rifle",
        description = "Standard issue tactical assault rifle firing concentrated thermal plasma bolts with high rate-of-fire.",
        cost = 0,
        isUnlocked = true,
        damageType = WeaponDamageType.THERMAL_PLASMA,
        firingMode = FiringMode.FULL_AUTO,
        projectileType = ProjectileType.PLASMA_BOLT,
        recoilPattern = RecoilPattern(
            verticalKickDeg = 2.6f,
            horizontalSwayDeg = 1.2f,
            recoveryRate = 8.5f,
            recoilImpulseForce = 1.1f,
            spreadAngleMaxDeg = 8.5f,
            spreadGrowthPerShot = 0.6f
        )
    )

    val SCATTERGUN = Weapon(
        id = "w_scatter",
        name = "Nano Scattergun",
        type = WeaponType.SHOTGUN,
        damage = 90,
        fireRateMs = 700,
        magSize = 8,
        reloadTimeMs = 2200,
        coverDamage = 80,
        isSilenced = false,
        iconName = "ic_shotgun",
        description = "Close-range heavy scatter weapon delivering explosive cone discharges that obliterate voxel structures.",
        cost = 1500,
        isUnlocked = false,
        damageType = WeaponDamageType.HIGH_EXPLOSIVE,
        firingMode = FiringMode.SPREAD_SCATTER,
        projectileType = ProjectileType.SCATTER_PELLET,
        recoilPattern = RecoilPattern(
            verticalKickDeg = 8.0f,
            horizontalSwayDeg = 2.5f,
            recoveryRate = 4.2f,
            recoilImpulseForce = 3.5f,
            spreadAngleMaxDeg = 16.0f,
            spreadGrowthPerShot = 2.8f
        )
    )

    val HEAVY_RAILGUN = Weapon(
        id = "w_railgun",
        name = "Hyperion Railgun",
        type = WeaponType.RAILGUN,
        damage = 140,
        fireRateMs = 1100,
        magSize = 5,
        reloadTimeMs = 2600,
        coverDamage = 120,
        isSilenced = false,
        pierceCover = true,
        iconName = "ic_railgun",
        description = "High-energy electromagnetic accelerator punching hyper-penetrative slugs through multiple voxel cover layers.",
        cost = 3500,
        isUnlocked = false,
        damageType = WeaponDamageType.ELECTROMAGNETIC_BEAM,
        firingMode = FiringMode.PULSE_CHARGE,
        projectileType = ProjectileType.RAILGUN_SLUG,
        recoilPattern = RecoilPattern(
            verticalKickDeg = 12.5f,
            horizontalSwayDeg = 3.2f,
            recoveryRate = 3.2f,
            recoilImpulseForce = 5.2f,
            spreadAngleMaxDeg = 2.0f,
            spreadGrowthPerShot = 0.1f
        )
    )

    val SPECTRUM_SNIPER = Weapon(
        id = "w_sniper",
        name = "Spectrum Beam Sniper",
        type = WeaponType.SNIPER,
        damage = 180,
        fireRateMs = 1400,
        magSize = 4,
        reloadTimeMs = 2800,
        coverDamage = 45,
        isSilenced = true,
        iconName = "ic_sniper",
        description = "Extreme long-range EM beam weapon with pinpoint thermal guidance for surgical voxel armor elimination.",
        cost = 5000,
        isUnlocked = false,
        damageType = WeaponDamageType.ELECTROMAGNETIC_BEAM,
        firingMode = FiringMode.SINGLE_SEMI,
        projectileType = ProjectileType.ENERGY_BEAM,
        recoilPattern = RecoilPattern(
            verticalKickDeg = 9.5f,
            horizontalSwayDeg = 1.2f,
            recoveryRate = 3.8f,
            recoilImpulseForce = 2.8f,
            spreadAngleMaxDeg = 1.2f,
            spreadGrowthPerShot = 0.2f
        )
    )

    fun getAll() = listOf(NEEDLE_PISTOL, PLASMA_RIFLE, SCATTERGUN, HEAVY_RAILGUN, SPECTRUM_SNIPER)
}

