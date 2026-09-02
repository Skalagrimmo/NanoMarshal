package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.data.model.AIState
import com.example.data.model.CoverHeight
import com.example.data.model.DefaultWeapons
import com.example.data.model.DynamicLight
import com.example.data.model.DynamicLightType
import com.example.data.model.Enemy
import com.example.data.model.Particle
import com.example.data.model.ParticleType
import com.example.data.model.PlayerStance
import com.example.data.model.PlayerState
import com.example.data.model.ProjectileType
import com.example.data.model.VoxelDamageCalibrator
import com.example.data.model.VoxelTile
import com.example.data.model.VoxelType
import com.example.data.model.Weapon
import com.example.data.model.WeaponDamageType
import com.example.data.model.WeaponType
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Result payload returned from an individual projectile impact on the voxel grid or entities.
 */
data class ProjectileImpactEvent(
    val bulletId: String,
    val hitX: Float,
    val hitY: Float,
    val isVoxelHit: Boolean,
    val gridX: Int? = null,
    val gridY: Int? = null,
    val voxelType: VoxelType? = null,
    val wasVoxelDestroyed: Boolean = false,
    val wasRicochet: Boolean = false,
    val wasPenetration: Boolean = false,
    val targetEnemyId: String? = null,
    val didHitPlayer: Boolean = false,
    val damageDealt: Float = 0f,
    val isCriticalFlank: Boolean = false,
    val wasCoverMitigated: Boolean = false,
    val soundEffect: SoundFX.SoundType? = null
)

/**
 * Trajectory simulation waypoint in 2D/3D space.
 */
data class TrajectoryPoint(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val isBouncePoint: Boolean = false,
    val isTerminalPoint: Boolean = false
)

/**
 * Comprehensive result payload after advancing all active projectile trajectories for a frame.
 */
data class ProjectileUpdateResult(
    val activeBullets: List<Bullet>,
    val impactEvents: List<ProjectileImpactEvent>,
    val particlesSpawned: List<Particle>,
    val dynamicLightsSpawned: List<DynamicLight>,
    val destroyedVoxelCoords: List<Pair<Int, Int>>,
    val screenShakeMs: Long = 0L,
    val playerDamageTaken: Float = 0f,
    val enemiesKilled: List<Enemy> = emptyList(),
    val totalCreditsEarned: Int = 0
)

/**
 * ProjectileManager handles all bullet physics trajectories, continuous collision detection (CCD)
 * against the 2D/3D voxel grid, calibrated material destruction, ricochet reflections,
 * cover penetration, entity combat resolution, and particle/lighting generation.
 */
class ProjectileManager(
    val worldManager: VoxelWorldManager,
    val terrain: VoxelTerrain = worldManager.terrain,
    val spatialGrid: SpatialGrid? = null,
    val coverSystem: CoverSystem = CoverSystem()
) {
    private val activeBullets = mutableListOf<Bullet>()
    private var projectileSequence = 0L

    /**
     * Clear all in-flight projectiles.
     */
    fun clear() {
        activeBullets.clear()
    }

    /**
     * Get immutable snapshot of current in-flight bullets.
     */
    fun getActiveBullets(): List<Bullet> = activeBullets.toList()

    /**
     * Spawns a projectile from player weapon discharge with recoil deflection and weapon attributes.
     */
    fun firePlayerWeapon(
        player: PlayerState,
        weapon: Weapon,
        recoilDeflectedAngleRad: Float,
        speedMultiplier: Float = 1.0f
    ): Bullet {
        val bulletId = "b_p_${++projectileSequence}_${Random.nextInt(1000)}"
        val baseSpeed = when (weapon.type) {
            WeaponType.SNIPER -> 1200f
            WeaponType.RAILGUN -> 1100f
            WeaponType.PLASMA_RIFLE -> 750f
            WeaponType.SHOTGUN -> 700f
            WeaponType.PISTOL -> 650f
        } * speedMultiplier

        val vx = cos(recoilDeflectedAngleRad) * baseSpeed
        val vy = sin(recoilDeflectedAngleRad) * baseSpeed

        val spawnOffsetX = cos(recoilDeflectedAngleRad) * 22f
        val spawnOffsetY = sin(recoilDeflectedAngleRad) * 22f

        val bullet = Bullet(
            id = bulletId,
            x = player.x + spawnOffsetX,
            y = player.y + spawnOffsetY,
            vx = vx,
            vy = vy,
            damage = weapon.effectiveDamage.toFloat(),
            isPlayerBullet = true,
            pierceCover = weapon.pierceCover,
            color = Color(weapon.damageType.colorHex),
            lifeMs = when (weapon.type) {
                WeaponType.SNIPER -> 2200L
                WeaponType.RAILGUN -> 1800L
                WeaponType.SHOTGUN -> 900L
                else -> 1500L
            },
            ricochetCount = 0,
            maxRicochets = player.maxRicochetsOverride ?: weapon.maxRicochets
        )

        activeBullets.add(bullet)
        return bullet
    }

    /**
     * Spawns an enemy projectile fired toward player or target position.
     */
    fun fireEnemyProjectile(
        originX: Float,
        originY: Float,
        aimAngleRad: Float,
        damage: Float,
        bulletSpeed: Float = 480f,
        color: Color = Color(0xFFEF4444),
        lifeMs: Long = 1600L,
        pierceCover: Boolean = false,
        maxRicochets: Int = 0
    ): Bullet {
        val bulletId = "b_e_${++projectileSequence}_${Random.nextInt(1000)}"
        val vx = cos(aimAngleRad) * bulletSpeed
        val vy = sin(aimAngleRad) * bulletSpeed

        val bullet = Bullet(
            id = bulletId,
            x = originX + cos(aimAngleRad) * 16f,
            y = originY + sin(aimAngleRad) * 16f,
            vx = vx,
            vy = vy,
            damage = damage,
            isPlayerBullet = false,
            pierceCover = pierceCover,
            color = color,
            lifeMs = lifeMs,
            ricochetCount = 0,
            maxRicochets = maxRicochets
        )

        activeBullets.add(bullet)
        return bullet
    }

    /**
     * Directly inserts an existing bullet instance into the projectile manager.
     */
    fun addBullet(bullet: Bullet) {
        activeBullets.add(bullet)
    }

    /**
     * Replaces the internal active bullet collection.
     */
    fun setBullets(bullets: List<Bullet>) {
        activeBullets.clear()
        activeBullets.addAll(bullets)
    }

    /**
     * Primary tick: advances all bullet trajectories, resolves voxel collisions, triggers calibrated
     * destruction and deformation, handles ricochets and penetrations, and resolves entity combat damage.
     */
    fun update(
        deltaSec: Float,
        player: PlayerState,
        enemies: MutableList<Enemy>
    ): ProjectileUpdateResult {
        val deltaMs = (deltaSec * 1000f).toLong()
        val remainingBullets = mutableListOf<Bullet>()
        val impactEvents = mutableListOf<ProjectileImpactEvent>()
        val spawnedParticles = mutableListOf<Particle>()
        val spawnedLights = mutableListOf<DynamicLight>()
        val destroyedVoxelCoords = mutableListOf<Pair<Int, Int>>()
        val killedEnemies = mutableListOf<Enemy>()
        var screenShakeAcc = 0L
        var totalPlayerDamage = 0f
        var totalCredits = 0

        val mapMaxX = terrain.width * terrain.tileSize
        val mapMaxY = terrain.height * terrain.tileSize

        for (bullet in activeBullets) {
            bullet.lifeMs -= deltaMs
            if (bullet.lifeMs <= 0) continue

            val stepDx = bullet.vx * deltaSec
            val stepDy = bullet.vy * deltaSec
            val stepDist = hypot(stepDx, stepDy)

            // Sub-stepping to prevent tunneling through thin voxel walls (max step 16px)
            val subSteps = (stepDist / 16f).toInt().coerceAtLeast(1)
            val subDx = stepDx / subSteps
            val subDy = stepDy / subSteps

            var bulletTerminated = false
            var currentX = bullet.x
            var currentY = bullet.y

            for (step in 1..subSteps) {
                val nextX = currentX + subDx
                val nextY = currentY + subDy

                // Out of map bounds check
                if (nextX < 0f || nextX > mapMaxX || nextY < 0f || nextY > mapMaxY) {
                    bulletTerminated = true
                    break
                }

                // 1. VOXEL GRID COLLISION DETECTION
                val tile = terrain.getTileAtWorld(nextX, nextY)
                if (tile != null && tile.coverHeight != CoverHeight.NONE && !tile.isDisintegrated) {
                    val bulletAngle = atan2(bullet.vy, bullet.vx)
                    val tileKey = "${tile.gridX}_${tile.gridY}"
                    val canRicochet = bullet.ricochetCount < bullet.maxRicochets && bullet.lastHitTileKey != tileKey

                    // Calibrated damage applied to voxel structure
                    val damageType = inferDamageTypeFromColor(bullet.color)
                    val damageMultiplier = VoxelDamageCalibrator.getDamageMultiplier(damageType, tile.type)
                    val rawDamage = if (canRicochet) bullet.damage * 0.45f else bullet.damage
                    val effectiveDamage = rawDamage * damageMultiplier

                    // Trigger voxel damage, deformation & destruction on hit
                    val wasDestroyed = terrain.applyDamageToTile(
                        gx = tile.gridX,
                        gy = tile.gridY,
                        damage = effectiveDamage,
                        hitAngleRad = bulletAngle
                    )

                    // Also sync damage into 3D voxel grid
                    val hitElevation = tile.elevationZ.coerceIn(1, worldManager.maxDepth - 1)
                    worldManager.applyVoxelDamage(
                        gx = tile.gridX,
                        gy = tile.gridY,
                        gz = hitElevation,
                        amount = effectiveDamage,
                        impactAngle = bulletAngle,
                        impactForce = if (bullet.pierceCover) 2.5f else 1.0f
                    )

                    val isBarrel = tile.type == VoxelType.EXPLOSIVE_BARREL

                    // Spawn impact debris particles
                    val debris = createVoxelDebrisParticles(
                        x = nextX,
                        y = nextY,
                        count = if (wasDestroyed) 22 else 12,
                        impactAngleRad = bulletAngle,
                        tileType = tile.type,
                        bulletColor = bullet.color
                    )
                    spawnedParticles.addAll(debris)

                    // Spawn impact dynamic light
                    spawnedLights.add(
                        DynamicLight(
                            id = "light_imp_${System.nanoTime()}",
                            x = nextX,
                            y = nextY,
                            radius = if (isBarrel) 280f else 135f,
                            color = if (isBarrel) Color(0xFFFFB703) else bullet.color,
                            intensity = if (isBarrel) 2.4f else 1.6f,
                            type = if (isBarrel) DynamicLightType.EXPLOSION_BURST else DynamicLightType.IMPACT_FLASH,
                            decayRate = if (isBarrel) 3.0f else 6.0f
                        )
                    )

                    if (wasDestroyed) {
                        destroyedVoxelCoords.add(Pair(tile.gridX, tile.gridY))
                        screenShakeAcc = screenShakeAcc.coerceAtLeast(if (isBarrel) 350L else 180L)

                        val projType = ProjectileType.fromDamageType(damageType)
                        val destructionVfx = DefaultWeapons.NEEDLE_PISTOL.createDestructionVisualEffects(
                            voxel = tile,
                            projectileType = projType,
                            worldX = nextX,
                            worldY = nextY,
                            voxelType = tile.type
                        )
                        spawnedParticles.addAll(destructionVfx)

                        // If explosive barrel, trigger radial explosive destruction
                        if (isBarrel) {
                            val chainDestroyed = triggerExplosiveBarrelBlast(
                                worldX = (tile.gridX + 0.5f) * terrain.tileSize,
                                worldY = (tile.gridY + 0.5f) * terrain.tileSize,
                                blastRadius = 160f,
                                blastDamage = 180f,
                                enemies = enemies,
                                spawnedParticles = spawnedParticles,
                                spawnedLights = spawnedLights
                            )
                            destroyedVoxelCoords.addAll(chainDestroyed)
                        }

                        impactEvents.add(
                            ProjectileImpactEvent(
                                bulletId = bullet.id,
                                hitX = nextX,
                                hitY = nextY,
                                isVoxelHit = true,
                                gridX = tile.gridX,
                                gridY = tile.gridY,
                                voxelType = tile.type,
                                wasVoxelDestroyed = true,
                                damageDealt = effectiveDamage,
                                soundEffect = SoundFX.SoundType.EXPLOSION
                            )
                        )

                        // Penetration: piercing bullets can continue through destroyed voxel!
                        if (bullet.pierceCover) {
                            bullet.x = nextX
                            bullet.y = nextY
                            bullet.lastHitTileKey = tileKey
                            bulletTerminated = false
                            break
                        } else {
                            bulletTerminated = true
                            break
                        }
                    }

                    if (canRicochet) {
                        // Reflect trajectory across voxel normal
                        val tileCenterX = (tile.gridX + 0.5f) * terrain.tileSize
                        val tileCenterY = (tile.gridY + 0.5f) * terrain.tileSize
                        val dx = nextX - tileCenterX
                        val dy = nextY - tileCenterY

                        val nx = if (abs(dx) > abs(dy)) (if (dx > 0) 1f else -1f) else 0f
                        val ny = if (abs(dx) <= abs(dy)) (if (dy > 0) 1f else -1f) else 0f

                        val dot = bullet.vx * nx + bullet.vy * ny
                        var rx = bullet.vx - 2f * dot * nx
                        var ry = bullet.vy - 2f * dot * ny

                        // Smart Target Seeking Ricochet (bend reflection toward nearest living enemy)
                        if (bullet.isPlayerBullet) {
                            var bestEnemy: Enemy? = null
                            var bestDistSq = Float.MAX_VALUE
                            for (e in enemies) {
                                if (!e.isAlive) continue
                                val edSq = (e.x - nextX) * (e.x - nextX) + (e.y - nextY) * (e.y - nextY)
                                if (edSq < 400f * 400f && edSq < bestDistSq) {
                                    bestDistSq = edSq
                                    bestEnemy = e
                                }
                            }

                            if (bestEnemy != null) {
                                val seekDx = bestEnemy.x - nextX
                                val seekDy = bestEnemy.y - nextY
                                val seekDist = sqrt(seekDx * seekDx + seekDy * seekDy).coerceAtLeast(1f)
                                val currentSpeed = hypot(rx, ry)
                                rx = rx * 0.35f + (seekDx / seekDist * currentSpeed) * 0.65f
                                ry = ry * 0.35f + (seekDy / seekDist * currentSpeed) * 0.65f
                            }
                        }

                        bullet.vx = rx * 0.88f
                        bullet.vy = ry * 0.88f
                        bullet.x = nextX + nx * 6f
                        bullet.y = nextY + ny * 6f
                        bullet.ricochetCount++
                        bullet.lastHitTileKey = tileKey

                        // Spawn ricochet sparks & floating indicator
                        val ricochetSparks = createRicochetSparkParticles(nextX, nextY, rx, ry, bullet.color)
                        spawnedParticles.addAll(ricochetSparks)

                        impactEvents.add(
                            ProjectileImpactEvent(
                                bulletId = bullet.id,
                                hitX = nextX,
                                hitY = nextY,
                                isVoxelHit = true,
                                gridX = tile.gridX,
                                gridY = tile.gridY,
                                voxelType = tile.type,
                                wasVoxelDestroyed = false,
                                wasRicochet = true,
                                damageDealt = effectiveDamage,
                                soundEffect = SoundFX.SoundType.RICOCHET
                            )
                        )
                        break
                    } else if (bullet.pierceCover && tile.currentHp < 50f) {
                        // Punch through weakened cover
                        bullet.x = nextX
                        bullet.y = nextY
                        bullet.lastHitTileKey = tileKey
                        bulletTerminated = false

                        impactEvents.add(
                            ProjectileImpactEvent(
                                bulletId = bullet.id,
                                hitX = nextX,
                                hitY = nextY,
                                isVoxelHit = true,
                                gridX = tile.gridX,
                                gridY = tile.gridY,
                                voxelType = tile.type,
                                wasPenetration = true,
                                damageDealt = effectiveDamage
                            )
                        )
                        break
                    } else {
                        // Projectile stopped and absorbed
                        bulletTerminated = true
                        impactEvents.add(
                            ProjectileImpactEvent(
                                bulletId = bullet.id,
                                hitX = nextX,
                                hitY = nextY,
                                isVoxelHit = true,
                                gridX = tile.gridX,
                                gridY = tile.gridY,
                                voxelType = tile.type,
                                wasVoxelDestroyed = false,
                                damageDealt = effectiveDamage,
                                soundEffect = SoundFX.SoundType.HIT_WALL
                            )
                        )
                        break
                    }
                }

                // 2. ENEMY ENTITY COLLISION DETECTION (Player Bullets)
                if (bullet.isPlayerBullet) {
                    val potentialHits = spatialGrid?.queryRadius(nextX, nextY, 26f) ?: enemies
                    var hitEnemy: Enemy? = null

                    for (enemy in potentialHits) {
                        if (!enemy.isAlive) continue
                        val distSq = (nextX - enemy.x) * (nextX - enemy.x) + (nextY - enemy.y) * (nextY - enemy.y)
                        if (distSq <= 24f * 24f) {
                            hitEnemy = enemy
                            break
                        }
                    }

                    if (hitEnemy != null) {
                        val hitAngle = atan2(nextY - hitEnemy.y, nextX - hitEnemy.x)
                        val angleDiff = abs(hitAngle - hitEnemy.facingAngle)
                        val isFlanked = angleDiff > Math.toRadians(90.0)

                        val enemyCoverEval = coverSystem.evaluateCoverBuff(
                            entityX = hitEnemy.x,
                            entityY = hitEnemy.y,
                            facingAngle = hitEnemy.facingAngle,
                            threatX = player.x,
                            threatY = player.y,
                            stance = PlayerStance.STAND,
                            terrain = terrain,
                            coverTileX = hitEnemy.targetCoverX,
                            coverTileY = hitEnemy.targetCoverY
                        )

                        val isAmbush = bullet.isPlayerBullet && !hitEnemy.hasDirectLineOfSightToPlayer &&
                                (hitEnemy.state == AIState.PATROL || hitEnemy.state == AIState.SUSPICIOUS)
                        val ambushMultiplier = if (isAmbush) 1.75f else 1.0f

                        val baseMitigated = coverSystem.calculateMitigatedDamage(
                            rawDamage = bullet.damage,
                            coverEval = enemyCoverEval,
                            isCritFlank = isFlanked
                        )
                        val finalDamage = baseMitigated * ambushMultiplier

                        // Apply to enemy shield then health
                        if (hitEnemy.shieldHp > 0f) {
                            hitEnemy.shieldHp -= finalDamage
                            if (hitEnemy.shieldHp < 0f) {
                                hitEnemy.health += hitEnemy.shieldHp
                                hitEnemy.shieldHp = 0f
                            }
                        } else {
                            hitEnemy.health -= finalDamage
                        }

                        hitEnemy.state = AIState.ENGAGED
                        hitEnemy.lastKnownPlayerX = player.x
                        hitEnemy.lastKnownPlayerY = player.y
                        hitEnemy.activeCoverDamageMitigation = enemyCoverEval.damageMitigationFraction
                        hitEnemy.isCoverFlanked = isFlanked

                        // Spawn damage number particle
                        val hitColor = when {
                            isAmbush -> Color(0xFF10B981) // Emerald for ambush
                            isFlanked -> Color(0xFFF59E0B)
                            enemyCoverEval.isCovered -> Color(0xFF38BDF8)
                            else -> Color(0xFF00F0FF)
                        }
                        val hitText = when {
                            isAmbush -> "AMBUSH ${finalDamage.toInt()}!"
                            isFlanked -> "CRIT ${finalDamage.toInt()}"
                            enemyCoverEval.isCovered -> "-${finalDamage.toInt()} (DEF)"
                            else -> "${finalDamage.toInt()}"
                        }
                        spawnedParticles.add(
                            Particle(
                                x = hitEnemy.x,
                                y = hitEnemy.y - 15f,
                                vx = 0f,
                                vy = -30f,
                                color = hitColor,
                                size = if (isAmbush || isFlanked) 17f else 14f,
                                type = ParticleType.HIT_NUMBER,
                                text = hitText
                            )
                        )

                        // Enemy death handling
                        if (!hitEnemy.isAlive) {
                            killedEnemies.add(hitEnemy)
                            totalCredits += hitEnemy.bountyReward
                            if (isAmbush) {
                                player.stealthKillsCount++
                                spawnedParticles.add(
                                    Particle(
                                        x = hitEnemy.x,
                                        y = hitEnemy.y - 32f,
                                        vx = 0f,
                                        vy = -18f,
                                        color = Color(0xFF10B981),
                                        size = 14f,
                                        type = ParticleType.HIT_NUMBER,
                                        text = "STEALTH TAKEDOWN!"
                                    )
                                )
                            }
                            spawnedParticles.addAll(
                                createVoxelDebrisParticles(
                                    x = hitEnemy.x,
                                    y = hitEnemy.y,
                                    count = 18,
                                    tileType = VoxelType.ALIEN_BIOMASS,
                                    bulletColor = bullet.color
                                )
                            )
                        }

                        spawnedLights.add(
                            DynamicLight(
                                id = "light_hit_${System.nanoTime()}",
                                x = hitEnemy.x,
                                y = hitEnemy.y,
                                radius = 120f,
                                color = Color(0xFF00F0FF),
                                intensity = 1.4f,
                                type = DynamicLightType.IMPACT_FLASH,
                                decayRate = 8.0f
                            )
                        )

                        impactEvents.add(
                            ProjectileImpactEvent(
                                bulletId = bullet.id,
                                hitX = nextX,
                                hitY = nextY,
                                isVoxelHit = false,
                                targetEnemyId = hitEnemy.id,
                                damageDealt = finalDamage,
                                isCriticalFlank = isFlanked,
                                soundEffect = SoundFX.SoundType.LASER_SHOT
                            )
                        )

                        bulletTerminated = true
                        break
                    }
                }

                // 3. PLAYER ENTITY COLLISION DETECTION (Enemy Bullets)
                if (!bullet.isPlayerBullet && player.isAlive) {
                    val distToPlayerSq = (nextX - player.x) * (nextX - player.x) + (nextY - player.y) * (nextY - player.y)
                    if (distToPlayerSq <= 22f * 22f) {
                        var dmg = bullet.damage
                        var wasCoverMitigated = false
                        var isFlanked = false

                        val coverEval = coverSystem.evaluateCoverBuff(
                            entityX = player.x,
                            entityY = player.y,
                            facingAngle = player.facingAngle,
                            threatX = bullet.x - bullet.vx,
                            threatY = bullet.y - bullet.vy,
                            stance = player.stance,
                            terrain = terrain,
                            coverTileX = player.coverTileX,
                            coverTileY = player.coverTileY
                        )

                        if (coverEval.isCovered && coverEval.coverTile != null) {
                            val cx = coverEval.coverTile.gridX
                            val cy = coverEval.coverTile.gridY
                            val bAngle = atan2(bullet.vy, bullet.vx)
                            val isProtected = coverSystem.isCoverProtectingAgainstBullet(
                                bulletVx = bullet.vx,
                                bulletVy = bullet.vy,
                                entityX = player.x,
                                entityY = player.y,
                                coverTile = coverEval.coverTile,
                                tileSize = terrain.tileSize
                            )

                            if (isProtected) {
                                wasCoverMitigated = true
                                // Voxel obstacle absorbs impact
                                terrain.applyDamageToTile(cx, cy, bullet.damage, bAngle)
                                dmg = coverSystem.calculateMitigatedDamage(bullet.damage, coverEval, isCritFlank = false)
                            } else {
                                isFlanked = true
                                dmg = bullet.damage * 1.45f
                                spawnedParticles.add(
                                    Particle(
                                        x = player.x,
                                        y = player.y - 15f,
                                        vx = 0f,
                                        vy = -20f,
                                        color = Color(0xFFEF4444),
                                        size = 14f,
                                        type = ParticleType.HIT_NUMBER,
                                        text = "FLANKED! 1.45x"
                                    )
                                )
                            }
                        } else {
                            dmg = coverSystem.calculateMitigatedDamage(bullet.damage, coverEval, isCritFlank = false)
                        }

                        if (wasCoverMitigated) {
                            val buffName = coverEval.coverInfo?.buffTitle ?: "COVER"
                            spawnedParticles.add(
                                Particle(
                                    x = player.x,
                                    y = player.y - 10f,
                                    vx = 0f,
                                    vy = -15f,
                                    color = Color(0xFF00F0FF),
                                    size = 12f,
                                    type = ParticleType.HIT_NUMBER,
                                    text = "-${dmg.toInt()} ($buffName)"
                                )
                            )
                        }

                        totalPlayerDamage += dmg
                        screenShakeAcc = screenShakeAcc.coerceAtLeast(150L)

                        impactEvents.add(
                            ProjectileImpactEvent(
                                bulletId = bullet.id,
                                hitX = nextX,
                                hitY = nextY,
                                isVoxelHit = false,
                                didHitPlayer = true,
                                damageDealt = dmg,
                                wasCoverMitigated = wasCoverMitigated,
                                soundEffect = SoundFX.SoundType.HIT_WALL
                            )
                        )

                        bulletTerminated = true
                        break
                    }
                }

                currentX = nextX
                currentY = nextY
            }

            if (!bulletTerminated) {
                bullet.x = currentX
                bullet.y = currentY
                remainingBullets.add(bullet)
            }
        }

        activeBullets.clear()
        activeBullets.addAll(remainingBullets)

        return ProjectileUpdateResult(
            activeBullets = activeBullets.toList(),
            impactEvents = impactEvents,
            particlesSpawned = spawnedParticles,
            dynamicLightsSpawned = spawnedLights,
            destroyedVoxelCoords = destroyedVoxelCoords.distinct(),
            screenShakeMs = screenShakeAcc,
            playerDamageTaken = totalPlayerDamage,
            enemiesKilled = killedEnemies,
            totalCreditsEarned = totalCredits
        )
    }

    /**
     * Predicts ballistic trajectory paths with ricochet bounces for tactical HUD and laser aiming.
     */
    fun computeTrajectory(
        originX: Float,
        originY: Float,
        aimAngleRad: Float,
        maxBounces: Int = 2,
        stepSize: Float = 18f,
        maxStepsPerSegment: Int = 28
    ): List<TrajectoryPoint> {
        val points = mutableListOf<TrajectoryPoint>()
        points.add(TrajectoryPoint(x = originX, y = originY, isBouncePoint = false))

        var currX = originX
        var currY = originY
        var dirX = cos(aimAngleRad)
        var dirY = sin(aimAngleRad)

        var currentBounces = 0
        var lastTileKey: String? = null

        while (currentBounces <= maxBounces && points.size < 50) {
            var segmentHit = false

            for (step in 0 until maxStepsPerSegment) {
                val nextX = currX + dirX * stepSize
                val nextY = currY + dirY * stepSize

                val tile = terrain.getTileAtWorld(nextX, nextY)
                if (tile != null && tile.coverHeight != CoverHeight.NONE && !tile.isDisintegrated) {
                    val tileKey = "${tile.gridX}_${tile.gridY}"
                    if (tileKey != lastTileKey) {
                        points.add(TrajectoryPoint(x = nextX, y = nextY, isBouncePoint = true))

                        val tileCenterX = (tile.gridX + 0.5f) * terrain.tileSize
                        val tileCenterY = (tile.gridY + 0.5f) * terrain.tileSize
                        val dx = nextX - tileCenterX
                        val dy = nextY - tileCenterY

                        val nx = if (abs(dx) > abs(dy)) (if (dx > 0) 1f else -1f) else 0f
                        val ny = if (abs(dx) <= abs(dy)) (if (dy > 0) 1f else -1f) else 0f

                        val dot = dirX * nx + dirY * ny
                        dirX = dirX - 2f * dot * nx
                        dirY = dirY - 2f * dot * ny

                        currX = nextX + nx * 5f
                        currY = nextY + ny * 5f
                        lastTileKey = tileKey
                        currentBounces++
                        segmentHit = true
                        break
                    }
                }

                currX = nextX
                currY = nextY
            }

            if (!segmentHit) {
                points.add(TrajectoryPoint(x = currX, y = currY, isTerminalPoint = true))
                break
            }
        }

        return points
    }

    /**
     * Converts trajectory points into (x, y) coordinates for rendering.
     */
    fun computeRicochetTrajectoryPoints(
        player: PlayerState,
        maxBounces: Int = 2
    ): List<Pair<Float, Float>> {
        return computeTrajectory(
            originX = player.x,
            originY = player.y,
            aimAngleRad = player.aimAngle,
            maxBounces = maxBounces
        ).map { Pair(it.x, it.y) }
    }

    /**
     * Handles radial explosive barrel detonation that tears through surrounding voxels and damages entities.
     */
    private fun triggerExplosiveBarrelBlast(
        worldX: Float,
        worldY: Float,
        blastRadius: Float,
        blastDamage: Float,
        enemies: MutableList<Enemy>,
        spawnedParticles: MutableList<Particle>,
        spawnedLights: MutableList<DynamicLight>
    ): List<Pair<Int, Int>> {
        val destroyedVoxels = mutableListOf<Pair<Int, Int>>()
        SoundFX.play(SoundFX.SoundType.EXPLOSION)

        // Spawn massive explosion light
        spawnedLights.add(
            DynamicLight(
                id = "light_exp_${System.nanoTime()}",
                x = worldX,
                y = worldY,
                radius = blastRadius * 2.2f,
                color = Color(0xFFFF5252),
                intensity = 3.0f,
                type = DynamicLightType.EXPLOSION_BURST,
                decayRate = 2.5f
            )
        )

        // Damage nearby enemies
        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            val dist = hypot(enemy.x - worldX, enemy.y - worldY)
            if (dist <= blastRadius) {
                val falloff = (1.0f - dist / blastRadius).coerceIn(0.2f, 1.0f)
                val dmg = blastDamage * falloff
                enemy.health -= dmg
                enemy.state = AIState.ENGAGED

                spawnedParticles.add(
                    Particle(
                        x = enemy.x,
                        y = enemy.y - 15f,
                        vx = 0f,
                        vy = -25f,
                        color = Color(0xFFFF5252),
                        size = 16f,
                        type = ParticleType.HIT_NUMBER,
                        text = "BLAST ${dmg.toInt()}"
                    )
                )
            }
        }

        // Damage surrounding voxel tiles
        val centerGx = (worldX / terrain.tileSize).toInt()
        val centerGy = (worldY / terrain.tileSize).toInt()
        val tileRadius = (blastRadius / terrain.tileSize).toInt().coerceAtLeast(1)

        for (gx in (centerGx - tileRadius)..(centerGx + tileRadius)) {
            for (gy in (centerGy - tileRadius)..(centerGy + tileRadius)) {
                if (gx in 0 until terrain.width && gy in 0 until terrain.height) {
                    val tile = terrain.tiles[gx][gy]
                    if (tile.coverHeight != CoverHeight.NONE && !tile.isDisintegrated) {
                        val tileWorldX = (gx + 0.5f) * terrain.tileSize
                        val tileWorldY = (gy + 0.5f) * terrain.tileSize
                        val dist = hypot(tileWorldX - worldX, tileWorldY - worldY)
                        if (dist <= blastRadius) {
                            val falloff = (1.0f - dist / blastRadius).coerceIn(0.25f, 1.0f)
                            val blastAngle = atan2(tileWorldY - worldY, tileWorldX - worldX)
                            val destroyed = terrain.applyDamageToTile(gx, gy, blastDamage * falloff, blastAngle)
                            if (destroyed) {
                                destroyedVoxels.add(Pair(gx, gy))
                            }
                            spawnedParticles.addAll(
                                createVoxelDebrisParticles(
                                    x = tileWorldX,
                                    y = tileWorldY,
                                    count = if (destroyed) 16 else 8,
                                    impactAngleRad = blastAngle,
                                    tileType = tile.type
                                )
                            )
                        }
                    }
                }
            }
        }

        return destroyedVoxels
    }

    /**
     * Infers damage type from bullet rendering color.
     */
    private fun inferDamageTypeFromColor(color: Color): WeaponDamageType {
        val hex = color.value.toLong()
        return when {
            hex == WeaponDamageType.THERMAL_PLASMA.colorHex -> WeaponDamageType.THERMAL_PLASMA
            hex == WeaponDamageType.HIGH_EXPLOSIVE.colorHex -> WeaponDamageType.HIGH_EXPLOSIVE
            hex == WeaponDamageType.ELECTROMAGNETIC_BEAM.colorHex -> WeaponDamageType.ELECTROMAGNETIC_BEAM
            hex == WeaponDamageType.CORROSIVE_ACID.colorHex -> WeaponDamageType.CORROSIVE_ACID
            else -> WeaponDamageType.KINETIC
        }
    }

    /**
     * Spawns flying tumbling voxel block debris chunks, sparks, and smoke.
     */
    private fun createVoxelDebrisParticles(
        x: Float,
        y: Float,
        count: Int = 14,
        impactAngleRad: Float? = null,
        tileType: VoxelType? = null,
        bulletColor: Color = Color(0xFF00F0FF)
    ): List<Particle> {
        val particles = mutableListOf<Particle>()
        val rand = Random.Default
        val baseAngle = impactAngleRad ?: (rand.nextFloat() * 2f * Math.PI.toFloat())

        val debrisCount = (count * 0.5f).toInt().coerceAtLeast(4)
        for (i in 0 until debrisCount) {
            val spread = (rand.nextFloat() - 0.5f) * Math.PI.toFloat() * 0.9f
            val debrisAngle = baseAngle + Math.PI.toFloat() + spread
            val speed = rand.nextFloat() * 220f + 60f

            val debrisColor = when (tileType) {
                VoxelType.REINFORCED_METAL -> Color(0xFF38BDF8)
                VoxelType.CONCRETE_WALL, VoxelType.HIGH_COVER_WALL -> Color(0xFF94A3B8)
                VoxelType.ALIEN_BIOMASS -> Color(0xFFA855F7)
                VoxelType.EXPLOSIVE_BARREL -> Color(0xFFEF4444)
                VoxelType.ENERGY_BARRIER -> Color(0xFF0284C7)
                VoxelType.DESTRUCTIBLE_PILLAR -> Color(0xFF475569)
                VoxelType.LOW_COVER_CRATE -> Color(0xFFD97706)
                else -> if (i % 2 == 0) Color(0xFF00F0FF) else Color(0xFFA855F7)
            }

            particles.add(
                Particle(
                    x = x + (rand.nextFloat() * 12f - 6f),
                    y = y + (rand.nextFloat() * 12f - 6f),
                    vx = cos(debrisAngle) * speed,
                    vy = sin(debrisAngle) * speed,
                    color = debrisColor,
                    size = rand.nextFloat() * 7f + 4f,
                    life = 1.0f,
                    maxLife = rand.nextFloat() * 0.6f + 0.4f,
                    type = ParticleType.DEBRIS_VOXEL,
                    rotation = rand.nextFloat() * 360f,
                    vRot = (rand.nextFloat() - 0.5f) * 12f,
                    aspectRatio = rand.nextFloat() * 0.6f + 0.7f
                )
            )
        }

        val sparkCount = (count * 0.35f).toInt().coerceAtLeast(3)
        for (i in 0 until sparkCount) {
            val sparkAngle = baseAngle + (rand.nextFloat() - 0.5f) * 1.4f
            val speed = rand.nextFloat() * 320f + 100f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(sparkAngle) * speed,
                    vy = sin(sparkAngle) * speed,
                    color = if (rand.nextBoolean()) Color(0xFFF59E0B) else bulletColor,
                    size = rand.nextFloat() * 3f + 2f,
                    life = 1.0f,
                    maxLife = 0.25f,
                    type = ParticleType.PLASMA_SPARK
                )
            )
        }

        val smokeCount = (count * 0.25f).toInt().coerceAtLeast(2)
        for (i in 0 until smokeCount) {
            val smokeAngle = rand.nextFloat() * 2f * Math.PI.toFloat()
            val speed = rand.nextFloat() * 40f + 10f
            particles.add(
                Particle(
                    x = x + (rand.nextFloat() * 8f - 4f),
                    y = y + (rand.nextFloat() * 8f - 4f),
                    vx = cos(smokeAngle) * speed,
                    vy = sin(smokeAngle) * speed,
                    color = Color(0xFF94A3B8).copy(alpha = 0.5f),
                    size = rand.nextFloat() * 6f + 4f,
                    life = 1.0f,
                    maxLife = 0.7f,
                    type = ParticleType.SMOKE_NANO
                )
            )
        }

        return particles
    }

    /**
     * Spawns high-velocity spark flares and floating indicator on ricochet bounce.
     */
    private fun createRicochetSparkParticles(
        impactX: Float,
        impactY: Float,
        rx: Float,
        ry: Float,
        color: Color
    ): List<Particle> {
        val particles = mutableListOf<Particle>()
        for (i in 0 until 5) {
            val sparkAngle = atan2(ry, rx) + (Random.nextFloat() * 1.2f - 0.6f)
            val speed = Random.nextFloat() * 150f + 50f
            particles.add(
                Particle(
                    x = impactX,
                    y = impactY,
                    vx = cos(sparkAngle) * speed,
                    vy = sin(sparkAngle) * speed,
                    color = color,
                    size = Random.nextFloat() * 4f + 3f,
                    life = 0.5f,
                    maxLife = 0.5f,
                    type = ParticleType.PLASMA_SPARK
                )
            )
        }

        particles.add(
            Particle(
                x = impactX,
                y = impactY - 12f,
                vx = 0f,
                vy = -20f,
                color = color,
                size = 12f,
                type = ParticleType.HIT_NUMBER,
                text = "RICOCHET!"
            )
        )

        return particles
    }
}
