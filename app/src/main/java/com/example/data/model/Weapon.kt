package com.example.data.model

import androidx.compose.ui.graphics.Color

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
    val id: String,
    val name: String,
    val type: WeaponType,
    val damage: Int,
    val fireRateMs: Long,
    val magSize: Int,
    val reloadTimeMs: Long,
    val coverDamage: Int,
    val isSilenced: Boolean = false,
    val pierceCover: Boolean = false,
    val iconName: String,
    val description: String,
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
    val coolingRatePerSec: Float = 25.0f
) {
    val effectiveDamage: Int get() = (damage * (1.0 + (upgradeLevel - 1) * 0.25)).toInt()
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

