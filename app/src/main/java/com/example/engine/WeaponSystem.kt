package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.data.model.Particle
import com.example.data.model.ParticleType
import com.example.data.model.PlayerStance
import com.example.data.model.ProjectileType
import com.example.data.model.VoxelType
import com.example.data.model.Weapon
import com.example.data.model.WeaponDamageType
import com.example.data.model.WeaponType
import com.example.data.model.VoxelDamageCalibrator
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 3D Point / Vector helper data class.
 */
data class Vector3D(
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * Active physical 3D projectile in flight.
 */
data class Projectile3D(
    val id: String,
    var x: Float,
    var y: Float,
    var z: Float,
    var vx: Float,
    var vy: Float,
    var vz: Float,
    var damage: Float,
    val weapon: Weapon,
    val weaponType: WeaponType,
    val damageType: WeaponDamageType = weapon.damageType,
    val projectileType: ProjectileType = weapon.projectileType,
    val isPlayerOwned: Boolean,
    val pierceCover: Boolean = false,
    val kineticForce: Float = 1.0f,
    val gravity: Float = 0f,
    val drag: Float = 0f,
    val radius: Float = 4f,
    val color: Color = Color(weapon.damageType.colorHex),
    var lifeMs: Long = 2000,
    val maxLifeMs: Long = 2000,
    var ricochetCount: Int = 0,
    val maxRicochets: Int = 0,
    var penetrationPower: Float = 1.0f,
    val isExplosive: Boolean = false,
    val explosionRadius: Float = 0f
) {
    val speed: Float get() = sqrt(vx * vx + vy * vy + vz * vz)
}

/**
 * Recoil calculation result payload containing goal angles, knockback velocity, and screen shake.
 */
data class RecoilResult(
    val deflectedAngleRad: Float,
    val kickVelocityX: Float,
    val kickVelocityY: Float,
    val screenShakeMs: Long
)

/**
 * Details of a projectile impact event against the voxel world or targets.
 */
data class ProjectileImpact(
    val projectileId: String,
    val hitX: Float,
    val hitY: Float,
    val hitZ: Float,
    val gridX: Int,
    val gridY: Int,
    val gridZ: Int,
    val voxelHit: Voxel3DCell?,
    val damageResult: VoxelDamageResult?,
    val didPenetrate: Boolean,
    val didRicochet: Boolean,
    val particlesGenerated: List<Particle>,
    val screenShakeMs: Long,
    val soundEvent: String?
)

/**
 * Summary result payload after updating all active projectiles for a frame.
 */
data class WeaponSystemUpdateResult(
    val activeProjectiles: List<Projectile3D>,
    val impactsThisFrame: List<ProjectileImpact>,
    val particlesGenerated: List<Particle>,
    val totalScreenShakeMs: Long,
    val destroyedVoxelCoords: List<Pair<Int, Int>>
)

/**
 * WeaponSystem handles 3D/2D projectile physics, ballistic trajectories, drag, gravity,
 * damage types, recoil patterns, and calibrated destruction calculations
 * against destructible environment blocks managed by [VoxelWorldManager].
 */
class WeaponSystem(
    val worldManager: VoxelWorldManager
) {
    private val activeProjectiles = mutableListOf<Projectile3D>()
    private var projectileCounter = 0L

    /**
     * Clear all active in-flight projectiles.
     */
    fun clearProjectiles() {
        activeProjectiles.clear()
    }

    /**
     * Get snapshot of all currently active projectiles.
     */
    fun getActiveProjectiles(): List<Projectile3D> = activeProjectiles.toList()

    /**
     * Computes recoil angular deflection and physical pushback force for a fired shot.
     */
    fun calculateShotRecoil(
        weapon: Weapon,
        currentAimAngleRad: Float,
        stance: PlayerStance = PlayerStance.STAND,
        continuousShotCount: Int = 1
    ): RecoilResult {
        val pattern = weapon.recoilPattern
        val damping = when (stance) {
            PlayerStance.STAND -> 1.0f
            PlayerStance.CROUCH -> pattern.crouchDampingFactor
            PlayerStance.PRONE -> pattern.proneDampingFactor
        }

        val verticalKickRad = Math.toRadians((pattern.verticalKickDeg * damping).toDouble()).toFloat()
        val swayRad = Math.toRadians(((Random.nextFloat() * 2f - 1f) * pattern.horizontalSwayDeg * damping).toDouble()).toFloat()

        val spreadBloomRad = Math.toRadians(
            (pattern.spreadGrowthPerShot * continuousShotCount).coerceAtMost(pattern.spreadAngleMaxDeg).toDouble()
        ).toFloat()

        val netAngleDeflection = (verticalKickRad + swayRad + (Random.nextFloat() * spreadBloomRad - spreadBloomRad / 2f))
        val finalAngle = currentAimAngleRad + netAngleDeflection

        val impulse = pattern.recoilImpulseForce * damping * 65f
        val kickVx = -cos(currentAimAngleRad) * impulse
        val kickVy = -sin(currentAimAngleRad) * impulse

        val shake = (pattern.recoilImpulseForce * 38f).toLong().coerceIn(0L, 250L)

        return RecoilResult(
            deflectedAngleRad = finalAngle,
            kickVelocityX = kickVx,
            kickVelocityY = kickVy,
            screenShakeMs = shake
        )
    }

    /**
     * Fires a weapon from specified 3D origin toward a 3D target coordinate,
     * instantiating appropriate projectile dynamics (spread, damage type, velocity, force).
     */
    fun fireWeapon(
        weapon: Weapon,
        originX: Float,
        originY: Float,
        originZ: Float = 1.5f,
        targetX: Float,
        targetY: Float,
        targetZ: Float = 1.5f,
        isPlayerOwned: Boolean,
        customDamage: Float? = null,
        stance: PlayerStance = PlayerStance.STAND,
        continuousShotCount: Int = 1
    ): List<Projectile3D> {
        val firedList = mutableListOf<Projectile3D>()
        val baseDamage = customDamage ?: weapon.effectiveDamage.toFloat()

        // Calculate aim vector
        val dx = targetX - originX
        val dy = targetY - originY
        val dz = targetZ - originZ
        val targetDist = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1.0f)

        val normDx = dx / targetDist
        val normDy = dy / targetDist
        val normDz = dz / targetDist
        val baseAngle = atan2(dy, dx)

        // Compute stance-adjusted recoil angle offset
        val recoil = calculateShotRecoil(weapon, baseAngle, stance, continuousShotCount)
        val firingAngle = recoil.deflectedAngleRad

        when (weapon.type) {
            WeaponType.SHOTGUN -> {
                // Multi-pellet scatter spread
                val pelletCount = 8
                val pelletDamage = baseDamage / 5f
                val baseSpeed = 1150f

                for (i in 0 until pelletCount) {
                    val spreadRad = Math.toRadians((Random.nextFloat() * weapon.recoilPattern.spreadAngleMaxDeg - weapon.recoilPattern.spreadAngleMaxDeg / 2f).toDouble()).toFloat()
                    val pelletAngle = firingAngle + spreadRad
                    val speedVar = baseSpeed * (0.85f + Random.nextFloat() * 0.3f)

                    val vx = cos(pelletAngle) * speedVar
                    val vy = sin(pelletAngle) * speedVar
                    val vz = normDz * speedVar + (Random.nextFloat() * 60f - 30f)

                    val p = Projectile3D(
                        id = "proj_${++projectileCounter}",
                        x = originX,
                        y = originY,
                        z = originZ,
                        vx = vx,
                        vy = vy,
                        vz = vz,
                        damage = pelletDamage,
                        weapon = weapon,
                        weaponType = weapon.type,
                        damageType = weapon.damageType,
                        isPlayerOwned = isPlayerOwned,
                        pierceCover = weapon.pierceCover,
                        kineticForce = 2.2f,
                        gravity = 120f,
                        drag = 0.8f,
                        radius = 3.8f,
                        color = Color(weapon.damageType.colorHex),
                        lifeMs = 950,
                        maxLifeMs = 950,
                        penetrationPower = 0.6f,
                        isExplosive = true,
                        explosionRadius = 18f
                    )
                    activeProjectiles.add(p)
                    firedList.add(p)
                }
            }

            WeaponType.RAILGUN -> {
                // High velocity hyper-penetration slug
                val speed = 2500f
                val p = Projectile3D(
                    id = "proj_${++projectileCounter}",
                    x = originX,
                    y = originY,
                    z = originZ,
                    vx = cos(firingAngle) * speed,
                    vy = sin(firingAngle) * speed,
                    vz = normDz * speed,
                    damage = baseDamage,
                    weapon = weapon,
                    weaponType = weapon.type,
                    damageType = weapon.damageType,
                    isPlayerOwned = isPlayerOwned,
                    pierceCover = true,
                    kineticForce = 4.8f,
                    gravity = 0f,
                    drag = 0.05f,
                    radius = 5.2f,
                    color = Color(weapon.damageType.colorHex),
                    lifeMs = 1800,
                    maxLifeMs = 1800,
                    penetrationPower = 3.8f,
                    isExplosive = true,
                    explosionRadius = 28f
                )
                activeProjectiles.add(p)
                firedList.add(p)
            }

            WeaponType.PLASMA_RIFLE -> {
                // High fire-rate energetic plasma bolt
                val speed = 1480f

                val p = Projectile3D(
                    id = "proj_${++projectileCounter}",
                    x = originX,
                    y = originY,
                    z = originZ,
                    vx = cos(firingAngle) * speed,
                    vy = sin(firingAngle) * speed,
                    vz = normDz * speed,
                    damage = baseDamage,
                    weapon = weapon,
                    weaponType = weapon.type,
                    damageType = weapon.damageType,
                    isPlayerOwned = isPlayerOwned,
                    pierceCover = weapon.pierceCover,
                    kineticForce = 1.3f,
                    gravity = 0f,
                    drag = 0.15f,
                    radius = 4.2f,
                    color = Color(weapon.damageType.colorHex),
                    lifeMs = 1500,
                    maxLifeMs = 1500,
                    penetrationPower = 1.1f,
                    isExplosive = false
                )
                activeProjectiles.add(p)
                firedList.add(p)
            }

            WeaponType.SNIPER -> {
                // Pinpoint high damage sniper beam
                val speed = 2900f
                val p = Projectile3D(
                    id = "proj_${++projectileCounter}",
                    x = originX,
                    y = originY,
                    z = originZ,
                    vx = cos(firingAngle) * speed,
                    vy = sin(firingAngle) * speed,
                    vz = normDz * speed,
                    damage = baseDamage,
                    weapon = weapon,
                    weaponType = weapon.type,
                    damageType = weapon.damageType,
                    isPlayerOwned = isPlayerOwned,
                    pierceCover = weapon.pierceCover,
                    kineticForce = 2.8f,
                    gravity = 0f,
                    drag = 0.02f,
                    radius = 4.6f,
                    color = Color(weapon.damageType.colorHex),
                    lifeMs = 2200,
                    maxLifeMs = 2200,
                    penetrationPower = 2.4f,
                    isExplosive = false
                )
                activeProjectiles.add(p)
                firedList.add(p)
            }

            WeaponType.PISTOL -> {
                // Standard needle suppressed sidearm
                val speed = 1250f
                val p = Projectile3D(
                    id = "proj_${++projectileCounter}",
                    x = originX,
                    y = originY,
                    z = originZ,
                    vx = cos(firingAngle) * speed,
                    vy = sin(firingAngle) * speed,
                    vz = normDz * speed,
                    damage = baseDamage,
                    weapon = weapon,
                    weaponType = weapon.type,
                    damageType = weapon.damageType,
                    isPlayerOwned = isPlayerOwned,
                    pierceCover = false,
                    kineticForce = 0.9f,
                    gravity = 45f,
                    drag = 0.25f,
                    radius = 3.2f,
                    color = Color(weapon.damageType.colorHex),
                    lifeMs = 1200,
                    maxLifeMs = 1200,
                    maxRicochets = 1,
                    penetrationPower = 0.85f,
                    isExplosive = false
                )
                activeProjectiles.add(p)
                firedList.add(p)
            }
        }

        return firedList
    }

    /**
     * Updates all active projectile physics positions, sub-steps trajectories against the 3D voxel grid,
     * calculates damage & deformation impacts calibrated by material damage multipliers,
     * generates debris particles, and manages projectile lifespans.
     */
    fun updateProjectiles(deltaSec: Float): WeaponSystemUpdateResult {
        val remainingProjectiles = mutableListOf<Projectile3D>()
        val impactsThisFrame = mutableListOf<ProjectileImpact>()
        val generatedParticles = mutableListOf<Particle>()
        val destroyedVoxelCoords = mutableListOf<Pair<Int, Int>>()
        var accumulatedScreenShakeMs = 0L

        val deltaMs = (deltaSec * 1000f).toLong()

        for (proj in activeProjectiles) {
            proj.lifeMs -= deltaMs
            if (proj.lifeMs <= 0) continue

            // Apply gravity & drag physics
            if (proj.gravity != 0f) {
                proj.vz -= proj.gravity * deltaSec
            }
            if (proj.drag > 0f) {
                val dragFactor = (1.0f - proj.drag * deltaSec).coerceIn(0.5f, 1.0f)
                proj.vx *= dragFactor
                proj.vy *= dragFactor
            }

            // Calculate step translation
            val stepDx = proj.vx * deltaSec
            val stepDy = proj.vy * deltaSec
            val stepDz = proj.vz * deltaSec
            val stepDistance = sqrt(stepDx * stepDx + stepDy * stepDy + stepDz * stepDz)

            val startX = proj.x
            val startY = proj.y
            val startZ = proj.z

            val targetX = startX + stepDx
            val targetY = startY + stepDy
            val targetZ = startZ + stepDz

            // Perform sub-stepping raycast against 3D voxel world to prevent tunneling
            val hit = worldManager.raycast3D(
                startX = startX,
                startY = startY,
                startZ = startZ,
                dirX = stepDx,
                dirY = stepDy,
                dirZ = stepDz,
                maxDistance = stepDistance.coerceAtLeast(worldManager.tileSize * 0.1f)
            )

            if (hit != null) {
                // Projectile impacted a solid voxel block!
                val gx = hit.gridX
                val gy = hit.gridY
                val gz = hit.gridZ
                val voxelCell = hit.voxel

                val impactAngle = atan2(proj.vy, proj.vx)

                // Calibrate damage using VoxelDamageCalibrator matrix
                val materialMultiplier = VoxelDamageCalibrator.getDamageMultiplier(proj.damageType, voxelCell.type)
                val damageToApply = proj.damage * materialMultiplier

                // Apply point damage & deformation to voxel block
                val damageResult = worldManager.applyVoxelDamage(
                    gx = gx,
                    gy = gy,
                    gz = gz,
                    amount = damageToApply,
                    impactAngle = impactAngle,
                    impactForce = proj.kineticForce
                )

                var penetrated = false
                var ricocheted = false

                // Spawn voxel impact debris & sparks matching damage type
                val impactParticles = generateImpactParticles(
                    hitX = hit.hitX,
                    hitY = hit.hitY,
                    voxel = voxelCell,
                    projectileColor = proj.color,
                    weaponType = proj.weaponType,
                    damageType = proj.damageType,
                    impactForce = proj.kineticForce
                )
                generatedParticles.addAll(impactParticles)

                // Spawn destruction visual effects when voxel health reaches zero
                if (damageResult?.wasDestroyed == true) {
                    val destructionVfx = proj.weapon.createDestructionVisualEffects(
                        voxel = voxelCell,
                        projectileType = proj.projectileType,
                        worldX = hit.hitX,
                        worldY = hit.hitY,
                        voxelType = voxelCell.type
                    )
                    generatedParticles.addAll(destructionVfx)
                }

                // Check explosive payloads
                if (proj.isExplosive && proj.explosionRadius > 0f) {
                    val destroyedList = worldManager.applyExplosion3D(
                        worldX = hit.hitX,
                        worldY = hit.hitY,
                        radiusWorld = proj.explosionRadius,
                        damage = damageToApply * 1.5f
                    )
                    destroyedVoxelCoords.addAll(destroyedList)
                    accumulatedScreenShakeMs = accumulatedScreenShakeMs.coerceAtLeast(360L)
                }

                // Check kinetic penetration vs ricochet vs termination
                if (proj.pierceCover && proj.penetrationPower > 0.5f && damageResult?.wasDestroyed == true) {
                    // Penetrate through destroyed block!
                    penetrated = true
                    proj.penetrationPower -= 0.6f
                    proj.damage *= 0.75f // Reduce remaining bullet energy
                    proj.x = targetX
                    proj.y = targetY
                    proj.z = targetZ
                    remainingProjectiles.add(proj)
                } else if (proj.maxRicochets > proj.ricochetCount && voxelCell.density > 0.8f && abs(stepDz) < 200f) {
                    // Ricochet bounce off dense structure
                    ricocheted = true
                    proj.ricochetCount++
                    proj.vx = -proj.vx * 0.7f
                    proj.vy = -proj.vy * 0.7f
                    proj.x = hit.hitX
                    proj.y = hit.hitY
                    proj.z = hit.hitZ
                    remainingProjectiles.add(proj)
                } else {
                    // Projectile absorbed / terminated
                    if (damageResult?.wasDestroyed == true) {
                        destroyedVoxelCoords.add(Pair(gx, gy))
                        accumulatedScreenShakeMs = accumulatedScreenShakeMs.coerceAtLeast(180L)
                    } else if (damageResult?.state != VoxelDamageState.PRISTINE) {
                        accumulatedScreenShakeMs = accumulatedScreenShakeMs.coerceAtLeast(80L)
                    }
                }

                val impactRecord = ProjectileImpact(
                    projectileId = proj.id,
                    hitX = hit.hitX,
                    hitY = hit.hitY,
                    hitZ = hit.hitZ,
                    gridX = gx,
                    gridY = gy,
                    gridZ = gz,
                    voxelHit = voxelCell,
                    damageResult = damageResult,
                    didPenetrate = penetrated,
                    didRicochet = ricocheted,
                    particlesGenerated = impactParticles,
                    screenShakeMs = if (damageResult?.wasDestroyed == true) 200L else 50L,
                    soundEvent = if (damageResult?.wasDestroyed == true) "voxel_break" else "voxel_impact"
                )
                impactsThisFrame.add(impactRecord)
            } else {
                // No collision, continue flight
                proj.x = targetX
                proj.y = targetY
                proj.z = targetZ

                // Boundary check
                val mapMaxX = worldManager.width * worldManager.tileSize
                val mapMaxY = worldManager.height * worldManager.tileSize
                if (proj.x in 0f..mapMaxX && proj.y in 0f..mapMaxY) {
                    remainingProjectiles.add(proj)
                }
            }
        }

        activeProjectiles.clear()
        activeProjectiles.addAll(remainingProjectiles)

        return WeaponSystemUpdateResult(
            activeProjectiles = activeProjectiles.toList(),
            impactsThisFrame = impactsThisFrame,
            particlesGenerated = generatedParticles,
            totalScreenShakeMs = accumulatedScreenShakeMs,
            destroyedVoxelCoords = destroyedVoxelCoords.distinct()
        )
    }

    /**
     * Compute predictive 3D ballistic trajectory points for tactical aiming / UI rendering.
     */
    fun computeBallisticTrajectory(
        originX: Float,
        originY: Float,
        originZ: Float = 1.5f,
        targetX: Float,
        targetY: Float,
        targetZ: Float = 1.5f,
        speed: Float = 1200f,
        gravity: Float = 0f,
        timeSteps: Int = 25,
        dt: Float = 0.04f
    ): List<Vector3D> {
        val points = mutableListOf<Vector3D>()
        val dx = targetX - originX
        val dy = targetY - originY
        val dz = targetZ - originZ
        val dist = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1f)

        var currX = originX
        var currY = originY
        var currZ = originZ

        var vx = (dx / dist) * speed
        var vy = (dy / dist) * speed
        var vz = (dz / dist) * speed

        for (step in 0 until timeSteps) {
            points.add(Vector3D(currX, currY, currZ))

            if (gravity != 0f) {
                vz -= gravity * dt
            }

            val nextX = currX + vx * dt
            val nextY = currY + vy * dt
            val nextZ = currZ + vz * dt

            val hit = worldManager.raycast3D(
                startX = currX,
                startY = currY,
                startZ = currZ,
                dirX = vx * dt,
                dirY = vy * dt,
                dirZ = vz * dt,
                maxDistance = speed * dt
            )

            if (hit != null) {
                points.add(Vector3D(hit.hitX, hit.hitY, hit.hitZ))
                break
            }

            currX = nextX
            currY = nextY
            currZ = nextZ
        }

        return points
    }

    /**
     * Spawns voxel block particles (debris chunks, plasma sparks, dust) matching voxel material and damage type.
     */
    private fun generateImpactParticles(
        hitX: Float,
        hitY: Float,
        voxel: Voxel3DCell,
        projectileColor: Color,
        weaponType: WeaponType,
        damageType: WeaponDamageType = WeaponDamageType.KINETIC,
        impactForce: Float
    ): List<Particle> {
        val particles = mutableListOf<Particle>()
        val count = (8 * impactForce).toInt().coerceIn(4, 24)

        // Primary particle color based on voxel type
        val baseVoxelColor = when (voxel.type) {
            VoxelType.FLOOR_DIRT -> Color(0xFF8D6E63)
            VoxelType.FLOOR_PLAZA -> Color(0xFF9E9E9E)
            VoxelType.LOW_COVER_CRATE -> Color(0xFFD7CCC8)
            VoxelType.HIGH_COVER_WALL -> Color(0xFF757575)
            VoxelType.EXPLOSIVE_BARREL -> Color(0xFFEF5350)
            VoxelType.ENERGY_BARRIER -> Color(0xFF00E5FF)
            VoxelType.ACID_POOL -> Color(0xFF76FF03)
            VoxelType.OBJECTIVE_NODE -> Color(0xFFE040FB)
            VoxelType.DESTRUCTIBLE_PILLAR -> Color(0xFFB0BEC5)
            VoxelType.REINFORCED_METAL -> Color(0xFF38BDF8)
            VoxelType.CONCRETE_WALL -> Color(0xFF94A3B8)
            VoxelType.ALIEN_BIOMASS -> Color(0xFFA855F7)
            else -> Color(0xFF00F0FF)
        }

        // Debris voxel chunks
        for (i in 0 until count) {
            val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
            val velocity = Random.nextFloat() * 180f * impactForce + 40f
            val isSpark = Random.nextFloat() > 0.5f

            val pType = when {
                isSpark && damageType == WeaponDamageType.THERMAL_PLASMA -> ParticleType.PLASMA_SPARK
                isSpark && damageType == WeaponDamageType.ELECTROMAGNETIC_BEAM -> ParticleType.LASER_TRAIL
                damageType == WeaponDamageType.CORROSIVE_ACID -> ParticleType.ACID_SPLASH
                else -> ParticleType.DEBRIS_VOXEL
            }

            particles.add(
                Particle(
                    x = hitX + (Random.nextFloat() * 8f - 4f),
                    y = hitY + (Random.nextFloat() * 8f - 4f),
                    vx = cos(angle) * velocity,
                    vy = sin(angle) * velocity,
                    color = if (isSpark) projectileColor else baseVoxelColor,
                    size = if (isSpark) Random.nextFloat() * 3f + 2f else Random.nextFloat() * 6f + 4f,
                    life = 1.0f,
                    maxLife = 1.0f,
                    type = pType,
                    rotation = Random.nextFloat() * 360f,
                    vRot = (Random.nextFloat() - 0.5f) * 12f
                )
            )
        }

        // Dust smoke cloud on heavy kinetic or explosive hit
        if (impactForce > 1.2f || damageType == WeaponDamageType.HIGH_EXPLOSIVE) {
            for (i in 0 until 5) {
                val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                val speed = Random.nextFloat() * 50f + 15f
                particles.add(
                    Particle(
                        x = hitX,
                        y = hitY,
                        vx = cos(angle) * speed,
                        vy = sin(angle) * speed,
                        color = Color(0x66B0BEC5),
                        size = Random.nextFloat() * 18f + 10f,
                        life = 1.0f,
                        maxLife = 1.0f,
                        type = ParticleType.SMOKE_NANO,
                        rotation = Random.nextFloat() * 360f,
                        vRot = (Random.nextFloat() - 0.5f) * 4f
                    )
                )
            }
        }

        return particles
    }
}

