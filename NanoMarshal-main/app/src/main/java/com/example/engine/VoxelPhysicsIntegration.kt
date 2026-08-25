package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.data.model.CoverHeight
import com.example.data.model.Particle
import com.example.data.model.ParticleType
import com.example.data.model.VoxelType
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Result payload returned when a projectile collision is processed by the physics layer.
 */
data class ProjectileCollisionResult(
    val hasHit: Boolean,
    val hitBlock: DestructibleVoxelBlock? = null,
    val impactX: Float = 0f,
    val impactY: Float = 0f,
    val impactZ: Float = 1.2f,
    val normalX: Float = 0f,
    val normalY: Float = 0f,
    val normalZ: Float = 0f,
    val damageReport: VoxelDamageReport? = null,
    val spawnedParticles: List<Particle> = emptyList(),
    val shouldDestroyBullet: Boolean = true,
    val residualDamage: Float = 0f,
    val deflectedVx: Float = 0f,
    val deflectedVy: Float = 0f
)

/**
 * Result payload returned after executing an explosive kinetic blast wave.
 */
data class ExplosivePhysicsResult(
    val totalBlocksDamaged: Int,
    val totalBlocksDestroyed: Int,
    val collapsedBlocksCount: Int,
    val spawnedParticles: List<Particle>,
    val damageReports: List<VoxelDamageReport>
)

/**
 * VoxelPhysicsIntegration connects [VoxelManager]'s 3D destructible block grid with
 * continuous collision detection (CCD) for moving projectiles, material impact dynamics,
 * explosive kinetic shockwaves, and particle debris simulation.
 */
class VoxelPhysicsIntegration(
    val voxelManager: VoxelManager,
    val gravity: Float = 600f,
    val airDrag: Float = 0.85f,
    val groundFriction: Float = 0.70f,
    val bounceRestitution: Float = 0.45f
) {

    /**
     * Performs continuous collision detection (CCD) for a moving projectile against
     * the 3D voxel grid in [VoxelManager].
     *
     * Handles:
     * 1. Sub-step raycasting along bullet velocity vector to prevent tunneling through thin walls.
     * 2. Material-specific damage calculations & armor reduction via [VoxelManager.applyDamage].
     * 3. Debris particle generation with material-specific colors and scatter physics.
     * 4. Penetration logic for armor-piercing bullets or disintegrated blocks.
     */
    fun processProjectileStep(
        bullet: Bullet,
        deltaSec: Float,
        currentZ: Float = 1.2f
    ): ProjectileCollisionResult {
        val nextX = bullet.x + bullet.vx * deltaSec
        val nextY = bullet.y + bullet.vy * deltaSec
        val dx = nextX - bullet.x
        val dy = nextY - bullet.y
        val moveDist = sqrt(dx * dx + dy * dy)

        if (moveDist <= 0.001f) {
            return ProjectileCollisionResult(hasHit = false)
        }

        // Raycast against solid destructible voxel blocks
        val rayHit = voxelManager.raycast3D(
            startX = bullet.x,
            startY = bullet.y,
            startZ = currentZ * voxelManager.voxelSize,
            dirX = bullet.vx,
            dirY = bullet.vy,
            dirZ = 0f,
            maxDistance = moveDist
        )

        if (rayHit == null || !rayHit.block.isSolid) {
            return ProjectileCollisionResult(hasHit = false)
        }

        val hitBlock = rayHit.block
        val impactAngle = atan2(bullet.vy, bullet.vx)

        // Apply point damage to voxel block via VoxelManager
        val damageType = if (bullet.pierceCover) "PLASMA" else "KINETIC"
        val damageReport = voxelManager.applyDamage(
            x = hitBlock.x,
            y = hitBlock.y,
            z = hitBlock.z,
            rawDamage = bullet.damage,
            damageType = damageType,
            impactAngle = impactAngle
        )

        val spawnedParticles = mutableListOf<Particle>()

        // Generate debris, sparks, and impact flash particles
        val debris = createDebrisParticlesForImpact(
            block = hitBlock,
            impactX = rayHit.hitX,
            impactY = rayHit.hitY,
            normalX = rayHit.normalX,
            normalY = rayHit.normalY,
            impactForce = bullet.damage,
            wasDestroyed = damageReport?.wasDestroyed == true
        )
        spawnedParticles.addAll(debris)

        // Spawn floating damage text if block took damage
        val damageDealt = damageReport?.damageDealt ?: 0f
        if (damageDealt > 0f) {
            spawnedParticles.add(
                Particle(
                    x = rayHit.hitX,
                    y = rayHit.hitY - 12f,
                    vx = (Random.nextFloat() * 30f - 15f),
                    vy = -40f,
                    color = getMaterialImpactColor(hitBlock.type),
                    size = 10f,
                    life = 0.5f,
                    maxLife = 0.5f,
                    type = ParticleType.HIT_NUMBER,
                    text = damageDealt.toInt().toString()
                )
            )
        }

        // Determine if bullet is destroyed or over-penetrates
        var destroyBullet = true
        var residualDamage = 0f
        var deflectVx = 0f
        var deflectVy = 0f

        if (damageReport?.wasDestroyed == true) {
            // Block was destroyed on impact -> residual bullet energy continues
            destroyBullet = !bullet.pierceCover && (bullet.damage < hitBlock.maxDurability * 1.5f)
            residualDamage = max(0f, bullet.damage - hitBlock.maxDurability)
        } else if (bullet.pierceCover) {
            // Armor-piercing projectile punches through with 35% damage loss
            destroyBullet = false
            residualDamage = bullet.damage * 0.65f
        } else {
            // Check for glancing deflection / bounce angle
            val dot = (bullet.vx * rayHit.normalX + bullet.vy * rayHit.normalY)
            if (dot < 0f) {
                // Calculate reflection vector: R = V - 2*(V . N)*N
                deflectVx = bullet.vx - 2f * dot * rayHit.normalX
                deflectVy = bullet.vy - 2f * dot * rayHit.normalY
            }
        }

        return ProjectileCollisionResult(
            hasHit = true,
            hitBlock = hitBlock,
            impactX = rayHit.hitX,
            impactY = rayHit.hitY,
            impactZ = rayHit.hitZ,
            normalX = rayHit.normalX,
            normalY = rayHit.normalY,
            normalZ = rayHit.normalZ,
            damageReport = damageReport,
            spawnedParticles = spawnedParticles,
            shouldDestroyBullet = destroyBullet,
            residualDamage = residualDamage,
            deflectedVx = deflectVx,
            deflectedVy = deflectVy
        )
    }

    /**
     * Triggers a radial explosive blast shockwave on the voxel grid.
     * Damaging blocks, applying outward kinetic impulse to debris, and checking structural collapses.
     */
    fun triggerExplosivePhysics(
        originX: Float,
        originY: Float,
        originZ: Float = 1.2f * voxelManager.voxelSize,
        blastRadius: Float = 120f,
        maxDamage: Float = 250f,
        damageType: String = "EXPLOSIVE"
    ): ExplosivePhysicsResult {
        // Execute 3D radial voxel blast
        val damageReports = voxelManager.applyExplosiveBlast(
            originX = originX,
            originY = originY,
            originZ = originZ,
            blastRadius = blastRadius,
            maxDamage = maxDamage
        )

        val spawnedParticles = mutableListOf<Particle>()
        var destroyedCount = 0

        for (report in damageReports) {
            if (report.wasDestroyed) {
                destroyedCount++
            }

            val worldX = (report.blockX + 0.5f) * voxelManager.voxelSize
            val worldY = (report.blockY + 0.5f) * voxelManager.voxelSize

            val dx = worldX - originX
            val dy = worldY - originY
            val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
            val normX = dx / dist
            val normY = dy / dist

            val block = voxelManager.getBlock(report.blockX, report.blockY, report.blockZ)
            if (block != null) {
                val blastDebris = createDebrisParticlesForImpact(
                    block = block,
                    impactX = worldX,
                    impactY = worldY,
                    normalX = normX,
                    normalY = normY,
                    impactForce = report.damageDealt,
                    wasDestroyed = report.wasDestroyed
                )
                spawnedParticles.addAll(blastDebris)
            }
        }

        // Spawn central shockwave expansion particles
        for (i in 0 until 16) {
            val angle = (i.toFloat() / 16f) * 2f * Math.PI.toFloat()
            val speed = Random.nextFloat() * 220f + 140f
            spawnedParticles.add(
                Particle(
                    x = originX,
                    y = originY,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = Color(0xFFF97316),
                    size = Random.nextFloat() * 12f + 6f,
                    life = 0.6f,
                    maxLife = 0.6f,
                    type = ParticleType.EXPLOSION_FLAME
                )
            )
        }

        // Secondary structural collapse check for floating blocks
        val collapsedCount = voxelManager.calculateStructuralIntegrity()
        if (collapsedCount > 0) {
            // Spawn falling rubble particles
            for (c in 0 until collapsedCount * 3) {
                spawnedParticles.add(
                    Particle(
                        x = originX + (Random.nextFloat() * 80f - 40f),
                        y = originY + (Random.nextFloat() * 80f - 40f),
                        vx = Random.nextFloat() * 60f - 30f,
                        vy = Random.nextFloat() * 40f + 20f,
                        color = Color(0xFF64748B),
                        size = Random.nextFloat() * 8f + 4f,
                        life = 0.8f,
                        maxLife = 0.8f,
                        type = ParticleType.DEBRIS_VOXEL
                    )
                )
            }
        }

        return ExplosivePhysicsResult(
            totalBlocksDamaged = damageReports.size,
            totalBlocksDestroyed = destroyedCount,
            collapsedBlocksCount = collapsedCount,
            spawnedParticles = spawnedParticles,
            damageReports = damageReports
        )
    }

    /**
     * Updates physics for all active debris particles in the simulation loop.
     * Applies gravity acceleration, drag damping, rotation, and elastic bounce collisions
     * against surrounding solid voxel blocks in [VoxelManager].
     */
    fun updateDebrisPhysics(
        particles: MutableList<Particle>,
        deltaSec: Float
    ) {
        val iterator = particles.iterator()

        while (iterator.hasNext()) {
            val p = iterator.next()

            // Update particle lifespan
            p.life -= deltaSec
            if (p.life <= 0f) {
                iterator.remove()
                continue
            }

            when (p.type) {
                ParticleType.DEBRIS_VOXEL, ParticleType.ACID_SPLASH -> {
                    // Apply air drag damping
                    p.vx *= (1.0f - airDrag * deltaSec).coerceIn(0.1f, 1.0f)
                    p.vy *= (1.0f - airDrag * deltaSec).coerceIn(0.1f, 1.0f)

                    // Apply gravity
                    p.vy += gravity * deltaSec

                    // Angular spin velocity
                    p.rotation += p.vRot * deltaSec

                    // Predict next position
                    val nextX = p.x + p.vx * deltaSec
                    val nextY = p.y + p.vy * deltaSec

                    val gx = (nextX / voxelManager.voxelSize).toInt()
                    val gy = (nextY / voxelManager.voxelSize).toInt()

                    val block = voxelManager.getBlock(gx, gy, 1)
                    if (block != null && block.isSolid && !block.isDisintegrated) {
                        // Elastic bounce collision against block surface
                        val blockCenterX = (gx + 0.5f) * voxelManager.voxelSize
                        val blockCenterY = (gy + 0.5f) * voxelManager.voxelSize

                        val penX = nextX - blockCenterX
                        val penY = nextY - blockCenterY

                        if (kotlin.math.abs(penX) > kotlin.math.abs(penY)) {
                            p.vx = -p.vx * bounceRestitution
                            p.vy *= groundFriction
                        } else {
                            p.vy = -p.vy * bounceRestitution
                            p.vx *= groundFriction
                        }
                        p.vRot *= -0.5f
                    } else {
                        p.x = nextX
                        p.y = nextY
                    }
                }

                ParticleType.PLASMA_SPARK, ParticleType.LASER_TRAIL -> {
                    p.vx *= (1.0f - 1.2f * deltaSec).coerceIn(0.1f, 1.0f)
                    p.vy *= (1.0f - 1.2f * deltaSec).coerceIn(0.1f, 1.0f)
                    p.x += p.vx * deltaSec
                    p.y += p.vy * deltaSec
                }

                ParticleType.SMOKE_NANO, ParticleType.STEALTH_PULSE -> {
                    p.x += p.vx * deltaSec
                    p.y += p.vy * deltaSec
                    p.size += deltaSec * 8f // Expanding smoke cloud
                }

                ParticleType.EXPLOSION_FLAME -> {
                    p.x += p.vx * deltaSec
                    p.y += p.vy * deltaSec
                    p.vx *= (1.0f - 2.0f * deltaSec).coerceIn(0.0f, 1.0f)
                    p.vy *= (1.0f - 2.0f * deltaSec).coerceIn(0.0f, 1.0f)
                }

                ParticleType.HIT_NUMBER -> {
                    p.y += p.vy * deltaSec
                    p.x += p.vx * deltaSec
                    p.vy += 20f * deltaSec // Light float upward drag
                }
            }
        }
    }

    /**
     * Factory function generating material-specific debris fragment particles
     * upon projectile or blast impact against a voxel block.
     */
    fun createDebrisParticlesForImpact(
        block: DestructibleVoxelBlock,
        impactX: Float,
        impactY: Float,
        normalX: Float,
        normalY: Float,
        impactForce: Float,
        wasDestroyed: Boolean
    ): List<Particle> {
        val particles = mutableListOf<Particle>()
        val baseColor = getMaterialImpactColor(block.type)

        val count = if (wasDestroyed) {
            (impactForce * 0.12f).toInt().coerceIn(8, 22)
        } else {
            (impactForce * 0.05f).toInt().coerceIn(3, 8)
        }

        val baseAngle = atan2(normalY, normalX)

        for (i in 0 until count) {
            val angleSpread = baseAngle + (Random.nextFloat() * 1.6f - 0.8f)
            val speed = Random.nextFloat() * (impactForce * 2.5f) + 60f

            // Color variation for realistic texture scatter
            val colorTint = when (Random.nextInt(3)) {
                0 -> baseColor
                1 -> Color(
                    red = (baseColor.red * 0.85f).coerceAtLeast(0f),
                    green = (baseColor.green * 0.85f).coerceAtLeast(0f),
                    blue = (baseColor.blue * 0.85f).coerceAtLeast(0f),
                    alpha = 1.0f
                )
                else -> Color(0xFFE2E8F0) // Bright spark/stone highlight
            }

            particles.add(
                Particle(
                    x = impactX + (Random.nextFloat() * 6f - 3f),
                    y = impactY + (Random.nextFloat() * 6f - 3f),
                    vx = cos(angleSpread) * speed,
                    vy = sin(angleSpread) * speed,
                    color = colorTint,
                    size = Random.nextFloat() * 6f + 3f,
                    life = Random.nextFloat() * 0.5f + 0.4f,
                    maxLife = 0.9f,
                    type = ParticleType.DEBRIS_VOXEL,
                    rotation = Random.nextFloat() * 360f,
                    vRot = Random.nextFloat() * 720f - 360f
                )
            )
        }

        // Add energetic plasma sparks for metallic or high-tech blocks
        if (block.type == VoxelType.REINFORCED_METAL ||
            block.type == VoxelType.ENERGY_BARRIER ||
            block.type == VoxelType.OBJECTIVE_NODE ||
            block.type == VoxelType.EXPLOSIVE_BARREL
        ) {
            val sparkCount = if (wasDestroyed) 10 else 4
            for (s in 0 until sparkCount) {
                val sparkAngle = baseAngle + (Random.nextFloat() * 2.4f - 1.2f)
                val sparkSpeed = Random.nextFloat() * 320f + 120f
                particles.add(
                    Particle(
                        x = impactX,
                        y = impactY,
                        vx = cos(sparkAngle) * sparkSpeed,
                        vy = sin(sparkAngle) * sparkSpeed,
                        color = if (block.type == VoxelType.ENERGY_BARRIER) Color(0xFF00F0FF) else Color(0xFFF59E0B),
                        size = Random.nextFloat() * 4f + 2f,
                        life = Random.nextFloat() * 0.25f + 0.1f,
                        maxLife = 0.35f,
                        type = ParticleType.PLASMA_SPARK
                    )
                )
            }
        }

        return particles
    }

    /**
     * Maps voxel material types to primary visual theme colors for debris particles.
     */
    private fun getMaterialImpactColor(type: VoxelType): Color {
        return when (type) {
            VoxelType.REINFORCED_METAL -> Color(0xFF94A3B8)
            VoxelType.CONCRETE_WALL, VoxelType.HIGH_COVER_WALL -> Color(0xFF64748B)
            VoxelType.DESTRUCTIBLE_PILLAR -> Color(0xFF475569)
            VoxelType.LOW_COVER_CRATE -> Color(0xFFD97706)
            VoxelType.ALIEN_BIOMASS -> Color(0xFFA855F7)
            VoxelType.ENERGY_BARRIER -> Color(0xFF00F0FF)
            VoxelType.EXPLOSIVE_BARREL -> Color(0xFFEF4444)
            VoxelType.ACID_POOL -> Color(0xFF22C55E)
            VoxelType.OBJECTIVE_NODE -> Color(0xFF3B82F6)
            VoxelType.FLOOR_PLAZA -> Color(0xFFCBD5E1)
            VoxelType.FLOOR_DIRT -> Color(0xFF78350F)
        }
    }
}
