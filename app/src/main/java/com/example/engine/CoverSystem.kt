package com.example.engine

import com.example.data.model.CoverHeight
import com.example.data.model.Enemy
import com.example.data.model.PlayerStance
import com.example.data.model.PlayerState
import com.example.data.model.VoxelTile
import com.example.data.model.VoxelType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Defensive characteristics and buff parameters associated with specific voxel terrain types.
 */
data class VoxelCoverTypeInfo(
    val voxelType: VoxelType,
    val coverHeight: CoverHeight,
    val baseDamageMitigation: Float,       // Fraction of incoming projectile damage absorbed (e.g. 0.85 = 85% absorbed)
    val crouchBonusMitigation: Float,      // Extra damage absorption when crouching/prone
    val shieldRechargeBonusMultiplier: Float, // Multiplier to active shield recharge rate (e.g. 1.5 = +50%)
    val accuracyStabilityBonus: Float,     // Aim recoil and spread stabilization bonus
    val stealthNoiseDamping: Float,        // Sound occlusion factor (e.g. 0.4 = 60% noise reduction)
    val protectionArcDegrees: Float,       // Angle arc of defensive coverage in degrees
    val buffTitle: String,
    val buffDescription: String,
    val isHazardousCover: Boolean = false,
    val hazardWarning: String? = null
)

/**
 * Real-time tactical evaluation of an entity's cover state and active defensive buffs.
 */
data class CoverBuffEvaluation(
    val isCovered: Boolean,
    val coverTile: VoxelTile?,
    val coverInfo: VoxelCoverTypeInfo?,
    val damageMitigationFraction: Float,   // e.g. 0.85 means entity takes only 15% damage
    val shieldRechargeMultiplier: Float,   // Multiplier to shield regen
    val accuracyBonusMultiplier: Float,    // Multiplier to weapon accuracy/range
    val stealthNoiseMultiplier: Float,     // Multiplier to noise radius
    val isFlanked: Boolean,
    val threatAngleDeg: Float = 0f,
    val coverNormalAngleDeg: Float = 0f,
    val buffBadgeTitle: String = "",
    val buffBadgeSubtitle: String = ""
)

/**
 * Logic system to identify cover voxels in the terrain, compute line-of-sight & directional protection,
 * and provide defensive buffs (damage reduction, nano-shield acceleration, recoil stabilization, stealth dampening)
 * to both player and enemies positioned behind specific voxel obstacles.
 */
class CoverSystem {

    companion object {
        // Detailed tactical cover profiles for each voxel material type
        val COVER_PROFILES = mapOf(
            VoxelType.REINFORCED_METAL to VoxelCoverTypeInfo(
                voxelType = VoxelType.REINFORCED_METAL,
                coverHeight = CoverHeight.HIGH,
                baseDamageMitigation = 0.88f,
                crouchBonusMitigation = 0.07f,
                shieldRechargeBonusMultiplier = 1.25f,
                accuracyStabilityBonus = 1.30f,
                stealthNoiseDamping = 0.35f,
                protectionArcDegrees = 150f,
                buffTitle = "REINFORCED PLATING",
                buffDescription = "-88% DMG // +30% STABILITY"
            ),
            VoxelType.CONCRETE_WALL to VoxelCoverTypeInfo(
                voxelType = VoxelType.CONCRETE_WALL,
                coverHeight = CoverHeight.HIGH,
                baseDamageMitigation = 0.82f,
                crouchBonusMitigation = 0.08f,
                shieldRechargeBonusMultiplier = 1.15f,
                accuracyStabilityBonus = 1.25f,
                stealthNoiseDamping = 0.40f,
                protectionArcDegrees = 140f,
                buffTitle = "CONCRETE BASTION",
                buffDescription = "-82% DMG // +25% STABILITY"
            ),
            VoxelType.HIGH_COVER_WALL to VoxelCoverTypeInfo(
                voxelType = VoxelType.HIGH_COVER_WALL,
                coverHeight = CoverHeight.HIGH,
                baseDamageMitigation = 0.80f,
                crouchBonusMitigation = 0.10f,
                shieldRechargeBonusMultiplier = 1.10f,
                accuracyStabilityBonus = 1.20f,
                stealthNoiseDamping = 0.45f,
                protectionArcDegrees = 140f,
                buffTitle = "TACTICAL HIGH WALL",
                buffDescription = "-80% DMG // SOLID COVER"
            ),
            VoxelType.ENERGY_BARRIER to VoxelCoverTypeInfo(
                voxelType = VoxelType.ENERGY_BARRIER,
                coverHeight = CoverHeight.HIGH,
                baseDamageMitigation = 0.75f,
                crouchBonusMitigation = 0.05f,
                shieldRechargeBonusMultiplier = 1.80f, // Greatly accelerates nano-shield regeneration
                accuracyStabilityBonus = 1.15f,
                stealthNoiseDamping = 0.80f,
                protectionArcDegrees = 160f,
                buffTitle = "ENERGY FIELD",
                buffDescription = "-75% DMG // +80% SHIELD REGEN"
            ),
            VoxelType.DESTRUCTIBLE_PILLAR to VoxelCoverTypeInfo(
                voxelType = VoxelType.DESTRUCTIBLE_PILLAR,
                coverHeight = CoverHeight.HIGH,
                baseDamageMitigation = 0.72f,
                crouchBonusMitigation = 0.12f,
                shieldRechargeBonusMultiplier = 1.10f,
                accuracyStabilityBonus = 1.15f,
                stealthNoiseDamping = 0.50f,
                protectionArcDegrees = 110f,
                buffTitle = "STRUCTURAL PILLAR",
                buffDescription = "-72% DMG // NARROW ARC"
            ),
            VoxelType.LOW_COVER_CRATE to VoxelCoverTypeInfo(
                voxelType = VoxelType.LOW_COVER_CRATE,
                coverHeight = CoverHeight.LOW,
                baseDamageMitigation = 0.55f,
                crouchBonusMitigation = 0.25f, // Major benefit when crouching
                shieldRechargeBonusMultiplier = 1.05f,
                accuracyStabilityBonus = 1.20f,
                stealthNoiseDamping = 0.65f,
                protectionArcDegrees = 120f,
                buffTitle = "CARGO CRATE",
                buffDescription = "CROUCH FOR -80% DMG"
            ),
            VoxelType.ALIEN_BIOMASS to VoxelCoverTypeInfo(
                voxelType = VoxelType.ALIEN_BIOMASS,
                coverHeight = CoverHeight.LOW,
                baseDamageMitigation = 0.40f,
                crouchBonusMitigation = 0.20f,
                shieldRechargeBonusMultiplier = 0.90f, // Spores slightly hinder tech shields
                accuracyStabilityBonus = 1.05f,
                stealthNoiseDamping = 0.30f, // Organic biomass heavily dampens footsteps
                protectionArcDegrees = 100f,
                buffTitle = "ORGANIC BIOMASS",
                buffDescription = "-40% DMG // SILENT STEALTH"
            ),
            VoxelType.EXPLOSIVE_BARREL to VoxelCoverTypeInfo(
                voxelType = VoxelType.EXPLOSIVE_BARREL,
                coverHeight = CoverHeight.LOW,
                baseDamageMitigation = 0.25f,
                crouchBonusMitigation = 0.05f,
                shieldRechargeBonusMultiplier = 1.00f,
                accuracyStabilityBonus = 1.00f,
                stealthNoiseDamping = 1.00f,
                protectionArcDegrees = 80f,
                buffTitle = "VOLATILE BARREL",
                buffDescription = "HAZARD: COMBUSTIBLE!",
                isHazardousCover = true,
                hazardWarning = "EXPLOSION RISK"
            ),
            VoxelType.NANITE_GAS_VENT to VoxelCoverTypeInfo(
                voxelType = VoxelType.NANITE_GAS_VENT,
                coverHeight = CoverHeight.LOW,
                baseDamageMitigation = 0.35f,
                crouchBonusMitigation = 0.10f,
                shieldRechargeBonusMultiplier = 0.95f,
                accuracyStabilityBonus = 1.05f,
                stealthNoiseDamping = 0.60f,
                protectionArcDegrees = 90f,
                buffTitle = "GAS VENT CASING",
                buffDescription = "HAZARD: NANITE LEAK RISK",
                isHazardousCover = true,
                hazardWarning = "CORROSIVE GAS VALVE"
            ),
            VoxelType.ELECTRIC_CONDUIT to VoxelCoverTypeInfo(
                voxelType = VoxelType.ELECTRIC_CONDUIT,
                coverHeight = CoverHeight.HIGH,
                baseDamageMitigation = 0.65f,
                crouchBonusMitigation = 0.10f,
                shieldRechargeBonusMultiplier = 1.40f,
                accuracyStabilityBonus = 1.10f,
                stealthNoiseDamping = 0.70f,
                protectionArcDegrees = 110f,
                buffTitle = "POWER CONDUIT",
                buffDescription = "HAZARD: ARC DISCHARGE",
                isHazardousCover = true,
                hazardWarning = "HIGH VOLTAGE"
            ),
            VoxelType.CRYO_PIPE to VoxelCoverTypeInfo(
                voxelType = VoxelType.CRYO_PIPE,
                coverHeight = CoverHeight.LOW,
                baseDamageMitigation = 0.40f,
                crouchBonusMitigation = 0.15f,
                shieldRechargeBonusMultiplier = 1.00f,
                accuracyStabilityBonus = 1.15f,
                stealthNoiseDamping = 0.50f,
                protectionArcDegrees = 100f,
                buffTitle = "CRYO MANIFOLD",
                buffDescription = "HAZARD: FREON LEAK",
                isHazardousCover = true,
                hazardWarning = "FROST HAZARD"
            ),
            VoxelType.PLASMA_GENERATOR to VoxelCoverTypeInfo(
                voxelType = VoxelType.PLASMA_GENERATOR,
                coverHeight = CoverHeight.HIGH,
                baseDamageMitigation = 0.70f,
                crouchBonusMitigation = 0.10f,
                shieldRechargeBonusMultiplier = 1.50f,
                accuracyStabilityBonus = 1.20f,
                stealthNoiseDamping = 0.80f,
                protectionArcDegrees = 130f,
                buffTitle = "REACTOR CORE",
                buffDescription = "HAZARD: CRITICAL MELTDOWN",
                isHazardousCover = true,
                hazardWarning = "THERMONUCLEAR RISK"
            )
        )

        val DEFAULT_NONE_PROFILE = VoxelCoverTypeInfo(
            voxelType = VoxelType.FLOOR_DIRT,
            coverHeight = CoverHeight.NONE,
            baseDamageMitigation = 0f,
            crouchBonusMitigation = 0f,
            shieldRechargeBonusMultiplier = 1.0f,
            accuracyStabilityBonus = 1.0f,
            stealthNoiseDamping = 1.0f,
            protectionArcDegrees = 0f,
            buffTitle = "EXPOSED",
            buffDescription = "NO COVER DEFENSE"
        )
    }

    /**
     * Retrieves cover type metadata for a specific voxel type.
     */
    fun getCoverInfo(type: VoxelType): VoxelCoverTypeInfo {
        return COVER_PROFILES[type] ?: DEFAULT_NONE_PROFILE
    }

    /**
     * Identifies if a voxel grid cell is a valid defensive cover block.
     */
    fun identifyCoverVoxel(terrain: VoxelTerrain, gx: Int, gy: Int): VoxelCoverTypeInfo? {
        val tile = terrain.tiles.getOrNull(gx)?.getOrNull(gy) ?: return null
        if (tile.isDisintegrated || tile.coverHeight == CoverHeight.NONE) return null
        return getCoverInfo(tile.type)
    }

    /**
     * Evaluates whether an entity is positioned behind cover relative to a threat position or aim vector,
     * and calculates the resulting defensive buffs.
     */
    fun evaluateCoverBuff(
        entityX: Float,
        entityY: Float,
        facingAngle: Float,
        threatX: Float?,
        threatY: Float?,
        stance: PlayerStance,
        terrain: VoxelTerrain,
        coverTileX: Int? = null,
        coverTileY: Int? = null
    ): CoverBuffEvaluation {
        val tileSize = terrain.tileSize
        val egx = (entityX / tileSize).toInt().coerceIn(0, terrain.width - 1)
        val egy = (entityY / tileSize).toInt().coerceIn(0, terrain.height - 1)

        // Find cover tile: either from explicit coordinate or search adjacent 3x3 tiles
        var coverTile: VoxelTile? = null
        if (coverTileX != null && coverTileY != null) {
            val candidate = terrain.tiles.getOrNull(coverTileX)?.getOrNull(coverTileY)
            if (candidate != null && !candidate.isDisintegrated && candidate.coverHeight != CoverHeight.NONE) {
                coverTile = candidate
            }
        }

        if (coverTile == null) {
            var minDistance = Float.MAX_VALUE
            for (dx in -1..1) {
                for (dy in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = egx + dx
                    val ny = egy + dy
                    val tile = terrain.tiles.getOrNull(nx)?.getOrNull(ny)
                    if (tile != null && tile.coverHeight != CoverHeight.NONE && !tile.isDisintegrated) {
                        val tileCenterX = (nx + 0.5f) * tileSize
                        val tileCenterY = (ny + 0.5f) * tileSize
                        val dist = sqrt((entityX - tileCenterX) * (entityX - tileCenterX) + (entityY - tileCenterY) * (entityY - tileCenterY))
                        if (dist < minDistance && dist <= tileSize * 1.6f) {
                            minDistance = dist
                            coverTile = tile
                        }
                    }
                }
            }
        }

        if (coverTile == null) {
            return CoverBuffEvaluation(
                isCovered = false,
                coverTile = null,
                coverInfo = null,
                damageMitigationFraction = when (stance) {
                    PlayerStance.CROUCH -> 0.15f // 15% natural crouch ducking
                    PlayerStance.PRONE -> 0.30f  // 30% natural prone profile
                    PlayerStance.STAND -> 0.0f
                },
                shieldRechargeMultiplier = 1.0f,
                accuracyBonusMultiplier = when (stance) {
                    PlayerStance.CROUCH -> 1.15f
                    PlayerStance.PRONE -> 1.35f
                    PlayerStance.STAND -> 1.0f
                },
                stealthNoiseMultiplier = when (stance) {
                    PlayerStance.CROUCH -> 0.40f
                    PlayerStance.PRONE -> 0.15f
                    PlayerStance.STAND -> 1.0f
                },
                isFlanked = false
            )
        }

        val coverInfo = getCoverInfo(coverTile.type)
        val tileCenterX = (coverTile.gridX + 0.5f) * tileSize
        val tileCenterY = (coverTile.gridY + 0.5f) * tileSize

        // Direction from entity to cover obstacle
        val angleToCover = atan2(tileCenterY - entityY, tileCenterX - entityX)
        val coverNormalAngleDeg = Math.toDegrees(angleToCover.toDouble()).toFloat()

        var isFlanked = false
        var threatAngleDeg = 0f

        if (threatX != null && threatY != null) {
            val angleToThreat = atan2(threatY - entityY, threatX - entityX)
            threatAngleDeg = Math.toDegrees(angleToThreat.toDouble()).toFloat()

            // Calculate angular difference between threat direction and cover direction
            var angleDiff = abs(angleToThreat - angleToCover)
            if (angleDiff > Math.PI) {
                angleDiff = (2 * Math.PI - angleDiff).toFloat()
            }
            val angleDiffDeg = Math.toDegrees(angleDiff.toDouble()).toFloat()

            // If angle difference exceeds half of the protection arc, entity is flanked
            val halfArc = coverInfo.protectionArcDegrees / 2f
            isFlanked = angleDiffDeg > halfArc
        }

        // Calculate dynamic damage mitigation based on stance and block health
        val hpRatio = (coverTile.currentHp / coverTile.maxHp).coerceIn(0.2f, 1.0f)
        var totalMitigation = coverInfo.baseDamageMitigation * hpRatio

        if (stance == PlayerStance.CROUCH || stance == PlayerStance.PRONE) {
            totalMitigation += coverInfo.crouchBonusMitigation
        }
        if (stance == PlayerStance.PRONE) {
            totalMitigation += 0.05f
        }
        totalMitigation = totalMitigation.coerceIn(0.0f, 0.95f)

        if (isFlanked) {
            totalMitigation = 0f // No cover mitigation if flanked
        }

        // Final accuracy stability and stealth noise buffs
        val finalAccuracy = if (isFlanked) 1.0f else coverInfo.accuracyStabilityBonus
        val finalShieldRecharge = if (isFlanked) 1.0f else coverInfo.shieldRechargeBonusMultiplier
        val finalNoiseMult = coverInfo.stealthNoiseDamping

        val badgeTitle = if (isFlanked) "FLANKED!" else coverInfo.buffTitle
        val badgeSubtitle = if (isFlanked) {
            "UNPROTECTED ANGLE"
        } else {
            "-${(totalMitigation * 100).toInt()}% DMG // ${coverInfo.buffDescription}"
        }

        return CoverBuffEvaluation(
            isCovered = true,
            coverTile = coverTile,
            coverInfo = coverInfo,
            damageMitigationFraction = totalMitigation,
            shieldRechargeMultiplier = finalShieldRecharge,
            accuracyBonusMultiplier = finalAccuracy,
            stealthNoiseMultiplier = finalNoiseMult,
            isFlanked = isFlanked,
            threatAngleDeg = threatAngleDeg,
            coverNormalAngleDeg = coverNormalAngleDeg,
            buffBadgeTitle = badgeTitle,
            buffBadgeSubtitle = badgeSubtitle
        )
    }

    /**
     * Checks if a projectile trajectory coming towards an entity is intercepted and mitigated by cover.
     */
    fun isCoverProtectingAgainstBullet(
        bulletVx: Float,
        bulletVy: Float,
        entityX: Float,
        entityY: Float,
        coverTile: VoxelTile,
        tileSize: Float
    ): Boolean {
        val tileCenterX = (coverTile.gridX + 0.5f) * tileSize
        val tileCenterY = (coverTile.gridY + 0.5f) * tileSize

        val bulletAngle = atan2(bulletVy, bulletVx)
        val coverAngle = atan2(tileCenterY - entityY, tileCenterX - entityX)

        var angleDiff = abs(bulletAngle - coverAngle)
        if (angleDiff > Math.PI) {
            angleDiff = (2 * Math.PI - angleDiff).toFloat()
        }

        // Bullet is incoming from the front of the cover obstacle (opposite travel vector)
        return angleDiff > Math.toRadians(75.0)
    }

    /**
     * Computes the mitigated final damage applied to an entity based on its active cover evaluation.
     */
    fun calculateMitigatedDamage(
        rawDamage: Float,
        coverEval: CoverBuffEvaluation,
        isCritFlank: Boolean = false
    ): Float {
        if (isCritFlank || coverEval.isFlanked) {
            return rawDamage * 1.50f // 50% extra critical flanking damage
        }
        if (!coverEval.isCovered) {
            return rawDamage * (1.0f - coverEval.damageMitigationFraction)
        }
        val remainingFraction = (1.0f - coverEval.damageMitigationFraction).coerceIn(0.05f, 1.0f)
        return rawDamage * remainingFraction
    }
}
