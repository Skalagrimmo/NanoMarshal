package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.data.model.*
import kotlin.math.*
import kotlin.random.Random

/**
 * Types of procedural environmental hazards present in the battlefield.
 */
enum class HazardType(
    val displayName: String,
    val voxelType: VoxelType,
    val baseColorHex: Long,
    val iconTag: String,
    val actionVerb: String
) {
    NANITE_GAS_VENT(
        displayName = "Nanite Gas Vent",
        voxelType = VoxelType.NANITE_GAS_VENT,
        baseColorHex = 0xFF10B981,
        iconTag = "☣ GAS",
        actionVerb = "VENT NANITE GAS"
    ),
    ELECTRIC_CONDUIT(
        displayName = "Electric Conduit",
        voxelType = VoxelType.ELECTRIC_CONDUIT,
        baseColorHex = 0xFF00F0FF,
        iconTag = "⚡ CONDUIT",
        actionVerb = "OVERLOAD CONDUIT"
    ),
    CRYO_PIPE(
        displayName = "Cryo Manifold",
        voxelType = VoxelType.CRYO_PIPE,
        baseColorHex = 0xFF38BDF8,
        iconTag = "❄ CRYO",
        actionVerb = "RUPTURE CRYO PIPE"
    ),
    PLASMA_GENERATOR(
        displayName = "Plasma Core Generator",
        voxelType = VoxelType.PLASMA_GENERATOR,
        baseColorHex = 0xFFF59E0B,
        iconTag = "☢ REACTOR",
        actionVerb = "TRIGGER MELTDOWN"
    )
}

/**
 * Operational status of an environmental hazard.
 */
enum class HazardStatus {
    DORMANT,
    ACTIVE,
    CHARGING,
    BURSTING,
    IGNITED,
    EXHAUSTED
}

/**
 * Individual instantiated environmental hazard obstacle on the voxel grid.
 */
data class HazardInstance(
    val id: String,
    val gridX: Int,
    val gridY: Int,
    val worldX: Float,
    val worldY: Float,
    val type: HazardType,
    var status: HazardStatus = HazardStatus.DORMANT,
    var currentHp: Float = 60f,
    val maxHp: Float = 60f,
    var activeTimerSec: Float = 0f,
    val maxDurationSec: Float = 12f,
    val radius: Float = 180f,
    val damagePerSec: Float = 40f,
    var pulsePhase: Float = Random.nextFloat() * 6.28f,
    var isInteractable: Boolean = true,
    var triggerCount: Int = 0,
    var chargeCountdownSec: Float = 0f
)

/**
 * Active expanding nanite toxic gas cloud lingering on the battlefield.
 */
data class ActiveGasCloud(
    val id: String,
    var x: Float,
    var y: Float,
    var currentRadius: Float = 35f,
    val maxRadius: Float = 160f,
    var remainingSec: Float = 10f,
    var isIgnited: Boolean = false,
    val damagePerSec: Float = 35f,
    val armorDissolveRate: Float = 0.35f,
    val slowFactor: Float = 0.50f,
    var pulseAnim: Float = 0f
)

/**
 * High-voltage electric arc chaining between conduit, enemies, and metal voxels.
 */
data class ActiveElectricArc(
    val id: String,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    var lifeSec: Float = 0.35f,
    val maxLifeSec: Float = 0.35f,
    val color: Color = Color(0xFF00F0FF),
    val damageDealt: Float = 95f,
    val isStunArc: Boolean = true,
    val targetEnemyId: String? = null
)

/**
 * Subzero cryo frost zone that freezes enemies and crystalizes terrain.
 */
data class ActiveCryoField(
    val id: String,
    val x: Float,
    val y: Float,
    val radius: Float = 140f,
    var remainingSec: Float = 8f,
    val freezeDurationSec: Float = 3.2f
)

/**
 * Thermal shockwave expanding outward from a detonated generator or ignited gas cloud.
 */
data class ActiveHazardShockwave(
    val x: Float,
    val y: Float,
    var currentRadius: Float = 10f,
    val maxRadius: Float = 260f,
    var lifeSec: Float = 0.5f,
    val maxLifeSec: Float = 0.5f,
    val color: Color = Color(0xFFFF9900)
)

/**
 * HUD interaction banner info when player is near a hazard terminal.
 */
data class HazardInteractionPrompt(
    val hazardId: String,
    val title: String,
    val actionName: String,
    val worldX: Float,
    val worldY: Float,
    val type: HazardType,
    val distance: Float
)

/**
 * Result bundle after updating all active hazards for a frame.
 */
data class HazardUpdateResult(
    val activeHazards: List<HazardInstance>,
    val activeGasClouds: List<ActiveGasCloud>,
    val activeElectricArcs: List<ActiveElectricArc>,
    val activeCryoFields: List<ActiveCryoField>,
    val activeShockwaves: List<ActiveHazardShockwave>,
    val interactionPrompt: HazardInteractionPrompt?,
    val spawnedParticles: List<Particle>,
    val spawnedLights: List<DynamicLight>,
    val screenShakeMs: Long = 0L,
    val destroyedVoxelCoords: List<Pair<Int, Int>>
)

/**
 * System governing procedural generation, physics simulation, real-time activation,
 * chain reactions, and battlefield layout modification of environmental hazards.
 */
class EnvironmentalHazardSystem {

    private val hazards = mutableListOf<HazardInstance>()
    private val gasClouds = mutableListOf<ActiveGasCloud>()
    private val electricArcs = mutableListOf<ActiveElectricArc>()
    private val cryoFields = mutableListOf<ActiveCryoField>()
    private val shockwaves = mutableListOf<ActiveHazardShockwave>()
    private var hazardSequence = 0L

    fun clear() {
        hazards.clear()
        gasClouds.clear()
        electricArcs.clear()
        cryoFields.clear()
        shockwaves.clear()
    }

    /**
     * Procedurally generate and register hazards across the terrain map.
     */
    fun initializeFromTerrain(terrain: VoxelTerrain) {
        clear()
        for (x in 0 until terrain.width) {
            for (y in 0 until terrain.height) {
                val tile = terrain.tiles[x][y]
                val hazardType = when (tile.type) {
                    VoxelType.NANITE_GAS_VENT -> HazardType.NANITE_GAS_VENT
                    VoxelType.ELECTRIC_CONDUIT -> HazardType.ELECTRIC_CONDUIT
                    VoxelType.CRYO_PIPE -> HazardType.CRYO_PIPE
                    VoxelType.PLASMA_GENERATOR -> HazardType.PLASMA_GENERATOR
                    else -> null
                }

                if (hazardType != null && !tile.isDisintegrated) {
                    val worldX = (x + 0.5f) * terrain.tileSize
                    val worldY = (y + 0.5f) * terrain.tileSize
                    hazards.add(
                        HazardInstance(
                            id = "hazard_${hazardType.name}_${x}_${y}_${++hazardSequence}",
                            gridX = x,
                            gridY = y,
                            worldX = worldX,
                            worldY = worldY,
                            type = hazardType,
                            status = HazardStatus.DORMANT,
                            currentHp = if (hazardType == HazardType.PLASMA_GENERATOR) 120f else 60f,
                            maxHp = if (hazardType == HazardType.PLASMA_GENERATOR) 120f else 60f,
                            radius = when (hazardType) {
                                HazardType.PLASMA_GENERATOR -> 280f
                                HazardType.ELECTRIC_CONDUIT -> 240f
                                HazardType.NANITE_GAS_VENT -> 180f
                                HazardType.CRYO_PIPE -> 160f
                            },
                            damagePerSec = when (hazardType) {
                                HazardType.PLASMA_GENERATOR -> 150f
                                HazardType.ELECTRIC_CONDUIT -> 80f
                                HazardType.NANITE_GAS_VENT -> 35f
                                HazardType.CRYO_PIPE -> 25f
                            }
                        )
                    )
                }
            }
        }
    }

    /**
     * Called when a projectile or blast impacts a voxel tile.
     */
    fun onVoxelDamaged(
        gx: Int,
        gy: Int,
        damage: Float,
        damageType: WeaponDamageType?,
        isPlayerBullet: Boolean,
        terrain: VoxelTerrain,
        enemies: MutableList<Enemy>,
        player: PlayerState,
        spawnedParticles: MutableList<Particle>,
        spawnedLights: MutableList<DynamicLight>,
        destroyedCoords: MutableList<Pair<Int, Int>>
    ): Boolean {
        val hazard = hazards.find { it.gridX == gx && it.gridY == gy && it.status != HazardStatus.EXHAUSTED } ?: return false

        hazard.currentHp -= damage
        if (hazard.currentHp <= 0f && hazard.status == HazardStatus.DORMANT) {
            triggerHazardActivation(
                hazard = hazard,
                triggerSource = if (isPlayerBullet) "PLAYER_WEAPON" else "ENEMY_FIRE",
                terrain = terrain,
                enemies = enemies,
                player = player,
                spawnedParticles = spawnedParticles,
                spawnedLights = spawnedLights,
                destroyedCoords = destroyedCoords
            )
            return true
        }
        return false
    }

    /**
     * Trigger hazard activation directly (e.g. from player console hack, remote drone ping, or bullet damage).
     */
    fun triggerHazardActivation(
        hazard: HazardInstance,
        triggerSource: String,
        terrain: VoxelTerrain,
        enemies: MutableList<Enemy>,
        player: PlayerState,
        spawnedParticles: MutableList<Particle>,
        spawnedLights: MutableList<DynamicLight>,
        destroyedCoords: MutableList<Pair<Int, Int>>
    ) {
        if (hazard.status == HazardStatus.EXHAUSTED) return

        hazard.triggerCount++

        when (hazard.type) {
            HazardType.NANITE_GAS_VENT -> {
                hazard.status = HazardStatus.ACTIVE
                hazard.activeTimerSec = hazard.maxDurationSec

                // Spawn expanding toxic nanite gas cloud
                gasClouds.add(
                    ActiveGasCloud(
                        id = "gas_${hazard.id}_${System.currentTimeMillis()}",
                        x = hazard.worldX,
                        y = hazard.worldY,
                        currentRadius = 40f,
                        maxRadius = hazard.radius,
                        remainingSec = hazard.maxDurationSec,
                        damagePerSec = hazard.damagePerSec
                    )
                )

                // Disintegrate organic biomass voxels in immediate radius to carve new pathway
                modifyBattlefieldLayout(
                    centerX = hazard.worldX,
                    centerY = hazard.worldY,
                    radius = 90f,
                    terrain = terrain,
                    destroyedCoords = destroyedCoords,
                    spawnedParticles = spawnedParticles,
                    turnHighToLowCover = true
                )

                // Spawn initial green gas burst particles
                spawnGasBurstParticles(hazard.worldX, hazard.worldY, spawnedParticles, count = 28)

                spawnedLights.add(
                    DynamicLight(
                        id = "light_gas_${System.nanoTime()}",
                        x = hazard.worldX,
                        y = hazard.worldY,
                        radius = 220f,
                        color = Color(0xFF10B981),
                        intensity = 1.8f,
                        type = DynamicLightType.ENVIRONMENTAL_EMITTER,
                        decayRate = 0.5f
                    )
                )

                SoundFX.play(SoundFX.SoundType.RELOAD)
            }

            HazardType.ELECTRIC_CONDUIT -> {
                hazard.status = HazardStatus.BURSTING
                hazard.activeTimerSec = 4.0f

                // Chain lightning arcing to up to 5 nearby enemies and metal obstacles
                val hitTargets = findConductiveTargets(hazard.worldX, hazard.worldY, hazard.radius, enemies, terrain)

                for (target in hitTargets) {
                    electricArcs.add(
                        ActiveElectricArc(
                            id = "arc_${System.nanoTime()}",
                            startX = hazard.worldX,
                            startY = hazard.worldY,
                            endX = target.x,
                            endY = target.y,
                            lifeSec = 0.45f,
                            color = Color(0xFF00F0FF),
                            damageDealt = 110f,
                            targetEnemyId = target.enemyId
                        )
                    )

                    // If target is enemy, stun them and strip shields
                    target.enemy?.let { enemy ->
                        enemy.health -= 110f
                        enemy.shieldHp = 0f
                        enemy.stunTimerMs = 3000 // 3-second stun lock
                        enemy.state = AIState.RETREAT
                    }

                    // Spawn electric spark particles at hit location
                    spawnElectricSparks(target.x, target.y, spawnedParticles, count = 18)
                }

                // Shockwave and disintegrate adjacent Energy Barriers or Reinforced Metal walls
                modifyBattlefieldLayout(
                    centerX = hazard.worldX,
                    centerY = hazard.worldY,
                    radius = 120f,
                    terrain = terrain,
                    destroyedCoords = destroyedCoords,
                    spawnedParticles = spawnedParticles,
                    targetMetalAndEnergyOnly = true
                )

                spawnedLights.add(
                    DynamicLight(
                        id = "light_elec_${System.nanoTime()}",
                        x = hazard.worldX,
                        y = hazard.worldY,
                        radius = 320f,
                        color = Color(0xFF00F0FF),
                        intensity = 2.8f,
                        type = DynamicLightType.EXPLOSION_BURST,
                        decayRate = 3.0f
                    )
                )

                SoundFX.play(SoundFX.SoundType.LASER_SHOT)
            }

            HazardType.CRYO_PIPE -> {
                hazard.status = HazardStatus.BURSTING
                hazard.activeTimerSec = 6.0f

                // Create subzero cryo field
                cryoFields.add(
                    ActiveCryoField(
                        id = "cryo_${hazard.id}_${System.currentTimeMillis()}",
                        x = hazard.worldX,
                        y = hazard.worldY,
                        radius = hazard.radius,
                        remainingSec = 7.5f,
                        freezeDurationSec = 3.5f
                    )
                )

                // Freeze all enemies in radius
                for (enemy in enemies) {
                    if (!enemy.isAlive) continue
                    val dist = hypot(enemy.x - hazard.worldX, enemy.y - hazard.worldY)
                    if (dist <= hazard.radius) {
                        enemy.health -= 60f
                        enemy.stunTimerMs = 3500 // Flash-frozen in ice
                    }
                }

                // Spawn cryo frost particles
                spawnCryoParticles(hazard.worldX, hazard.worldY, spawnedParticles, count = 30)

                spawnedLights.add(
                    DynamicLight(
                        id = "light_cryo_${System.nanoTime()}",
                        x = hazard.worldX,
                        y = hazard.worldY,
                        radius = 260f,
                        color = Color(0xFF38BDF8),
                        intensity = 2.0f,
                        type = DynamicLightType.EXPLOSION_BURST,
                        decayRate = 1.5f
                    )
                )

                SoundFX.play(SoundFX.SoundType.RELOAD)
            }

            HazardType.PLASMA_GENERATOR -> {
                hazard.status = HazardStatus.CHARGING
                hazard.chargeCountdownSec = 1.2f // 1.2s delay with warning ring before catastrophic thermal blast

                spawnedParticles.add(
                    Particle(
                        x = hazard.worldX,
                        y = hazard.worldY,
                        vx = 0f,
                        vy = -25f,
                        color = Color(0xFFF59E0B),
                        size = 14f,
                        type = ParticleType.HIT_NUMBER,
                        text = "REACTOR OVERLOAD DETECTED!"
                    )
                )

                SoundFX.play(SoundFX.SoundType.RELOAD)
            }
        }
    }

    /**
     * Detonate a charged plasma generator reactor, causing massive structural reshaping.
     */
    private fun detonatePlasmaGenerator(
        hazard: HazardInstance,
        terrain: VoxelTerrain,
        enemies: MutableList<Enemy>,
        player: PlayerState,
        spawnedParticles: MutableList<Particle>,
        spawnedLights: MutableList<DynamicLight>,
        destroyedCoords: MutableList<Pair<Int, Int>>
    ) {
        hazard.status = HazardStatus.EXHAUSTED

        // Thermal blast shockwave
        shockwaves.add(
            ActiveHazardShockwave(
                x = hazard.worldX,
                y = hazard.worldY,
                currentRadius = 20f,
                maxRadius = hazard.radius,
                lifeSec = 0.6f,
                color = Color(0xFFF59E0B)
            )
        )

        // Damage enemies in large radius
        for (enemy in enemies) {
            if (!enemy.isAlive) continue
            val dist = hypot(enemy.x - hazard.worldX, enemy.y - hazard.worldY)
            if (dist <= hazard.radius) {
                val dmg = 180f * (1.0f - dist / hazard.radius).coerceAtLeast(0.3f)
                enemy.health -= dmg
                enemy.shieldHp = 0f
                enemy.state = AIState.RETREAT
            }
        }

        // Damage player if caught in blast
        val pDist = hypot(player.x - hazard.worldX, player.y - hazard.worldY)
        if (pDist <= hazard.radius) {
            val pDmg = 80f * (1.0f - pDist / hazard.radius).coerceAtLeast(0.2f)
            if (player.nanoShield > 0f) {
                player.nanoShield -= pDmg
                if (player.nanoShield < 0f) {
                    player.health += player.nanoShield
                    player.nanoShield = 0f
                }
            } else {
                player.health -= pDmg
            }
            player.shieldRechargeDelayMs = 4000
        }

        // Massive battlefield restructuring: demolish high walls, pillars, crates, and alien biomass
        modifyBattlefieldLayout(
            centerX = hazard.worldX,
            centerY = hazard.worldY,
            radius = hazard.radius,
            terrain = terrain,
            destroyedCoords = destroyedCoords,
            spawnedParticles = spawnedParticles,
            obliterateAllCover = true
        )

        // Spawn explosive fireball particles
        spawnExplosionFire(hazard.worldX, hazard.worldY, spawnedParticles, count = 35)

        spawnedLights.add(
            DynamicLight(
                id = "light_plasma_det_${System.nanoTime()}",
                x = hazard.worldX,
                y = hazard.worldY,
                radius = 450f,
                color = Color(0xFFFFB703),
                intensity = 3.5f,
                type = DynamicLightType.EXPLOSION_BURST,
                decayRate = 2.0f
            )
        )

        SoundFX.play(SoundFX.SoundType.EXPLOSION)
    }

    /**
     * Modifies the physical terrain layout by blowing holes through walls, disintegrating obstacles,
     * or converting high blocking walls into low rubble to open up brand new tactical corridors.
     */
    private fun modifyBattlefieldLayout(
        centerX: Float,
        centerY: Float,
        radius: Float,
        terrain: VoxelTerrain,
        destroyedCoords: MutableList<Pair<Int, Int>>,
        spawnedParticles: MutableList<Particle>,
        turnHighToLowCover: Boolean = false,
        targetMetalAndEnergyOnly: Boolean = false,
        obliterateAllCover: Boolean = false
    ) {
        val minGx = ((centerX - radius) / terrain.tileSize).toInt().coerceIn(1, terrain.width - 2)
        val maxGx = ((centerX + radius) / terrain.tileSize).toInt().coerceIn(1, terrain.width - 2)
        val minGy = ((centerY - radius) / terrain.tileSize).toInt().coerceIn(1, terrain.height - 2)
        val maxGy = ((centerY + radius) / terrain.tileSize).toInt().coerceIn(1, terrain.height - 2)

        for (gx in minGx..maxGx) {
            for (gy in minGy..maxGy) {
                val tile = terrain.tiles[gx][gy]
                if (tile.isDisintegrated) continue

                val tileWorldX = (gx + 0.5f) * terrain.tileSize
                val tileWorldY = (gy + 0.5f) * terrain.tileSize
                val dist = hypot(tileWorldX - centerX, tileWorldY - centerY)

                if (dist <= radius) {
                    if (targetMetalAndEnergyOnly && tile.type != VoxelType.REINFORCED_METAL && tile.type != VoxelType.ENERGY_BARRIER) {
                        continue
                    }

                    if (tile.coverHeight != CoverHeight.NONE) {
                        if (obliterateAllCover || tile.type == VoxelType.LOW_COVER_CRATE || tile.type == VoxelType.ENERGY_BARRIER || tile.type == VoxelType.ALIEN_BIOMASS) {
                            tile.isDisintegrated = true
                            tile.coverHeight = CoverHeight.NONE
                            tile.elevationZ = 0
                            tile.type = VoxelType.FLOOR_PLAZA
                            destroyedCoords.add(Pair(gx, gy))

                            spawnDebrisParticles(tileWorldX, tileWorldY, spawnedParticles, count = 12, tileType = tile.type)
                        } else if (turnHighToLowCover && tile.coverHeight == CoverHeight.HIGH) {
                            // Turn high concrete/metal walls into low rubble crates
                            tile.coverHeight = CoverHeight.LOW
                            tile.elevationZ = 1
                            tile.type = VoxelType.LOW_COVER_CRATE
                            tile.currentHp = 40f
                            tile.maxHp = 40f
                            destroyedCoords.add(Pair(gx, gy))

                            spawnDebrisParticles(tileWorldX, tileWorldY, spawnedParticles, count = 10, tileType = tile.type)
                        }
                    }
                }
            }
        }
    }

    /**
     * Primary tick: updates active gas clouds, electric arcs, cryo fields, and countdown timers.
     */
    fun update(
        deltaSec: Float,
        player: PlayerState,
        enemies: MutableList<Enemy>,
        squad: List<SquadMember>,
        terrain: VoxelTerrain,
        bullets: MutableList<Bullet>,
        particles: MutableList<Particle>,
        dynamicLights: MutableList<DynamicLight>
    ): HazardUpdateResult {
        val destroyedCoords = mutableListOf<Pair<Int, Int>>()
        var screenShakeMs = 0L

        // 1. Update Hazard Charging & Countdown timers
        for (hazard in hazards) {
            hazard.pulsePhase += deltaSec * 3f

            if (hazard.status == HazardStatus.CHARGING) {
                hazard.chargeCountdownSec -= deltaSec
                if (hazard.chargeCountdownSec <= 0f) {
                    detonatePlasmaGenerator(
                        hazard = hazard,
                        terrain = terrain,
                        enemies = enemies,
                        player = player,
                        spawnedParticles = particles,
                        spawnedLights = dynamicLights,
                        destroyedCoords = destroyedCoords
                    )
                    screenShakeMs = screenShakeMs.coerceAtLeast(450L)
                }
            } else if (hazard.status == HazardStatus.BURSTING || hazard.status == HazardStatus.ACTIVE) {
                hazard.activeTimerSec -= deltaSec
                if (hazard.activeTimerSec <= 0f) {
                    hazard.status = HazardStatus.EXHAUSTED
                }
            }
        }

        // 2. Update Active Gas Clouds (Expansion, damage tick, and bullet ignition check)
        val gasIterator = gasClouds.iterator()
        while (gasIterator.hasNext()) {
            val gas = gasIterator.next()
            gas.remainingSec -= deltaSec
            gas.pulseAnim += deltaSec * 4f

            // Gas cloud slowly expands to max radius
            if (gas.currentRadius < gas.maxRadius) {
                gas.currentRadius = (gas.currentRadius + 30f * deltaSec).coerceAtMost(gas.maxRadius)
            }

            // Check if any plasma / fire bullet flies into the gas cloud -> Triggers DEFLAGRATION BLAST!
            val ignitingBullet = bullets.find { bullet ->
                val bDist = hypot(bullet.x - gas.x, bullet.y - gas.y)
                bDist <= gas.currentRadius && (bullet.color == Color(0xFFFF9900) || bullet.color == Color(0xFF00F0FF) || bullet.damage >= 45f)
            }

            if (ignitingBullet != null && !gas.isIgnited) {
                gas.isIgnited = true
                gas.remainingSec = 0.2f // Instantly finish gas into explosion

                // Deflagration shockwave
                shockwaves.add(
                    ActiveHazardShockwave(
                        x = gas.x,
                        y = gas.y,
                        currentRadius = 15f,
                        maxRadius = gas.currentRadius * 1.3f,
                        lifeSec = 0.55f,
                        color = Color(0xFFFF5500)
                    )
                )

                // Blast damage to all nearby enemies
                for (enemy in enemies) {
                    if (!enemy.isAlive) continue
                    val eDist = hypot(enemy.x - gas.x, enemy.y - gas.y)
                    if (eDist <= gas.currentRadius * 1.3f) {
                        enemy.health -= 130f
                        enemy.state = AIState.RETREAT
                    }
                }

                // Disintegrate surrounding low crates into flat plaza
                modifyBattlefieldLayout(
                    centerX = gas.x,
                    centerY = gas.y,
                    radius = gas.currentRadius,
                    terrain = terrain,
                    destroyedCoords = destroyedCoords,
                    spawnedParticles = particles,
                    turnHighToLowCover = true
                )

                spawnExplosionFire(gas.x, gas.y, particles, count = 26)
                SoundFX.play(SoundFX.SoundType.EXPLOSION)
                screenShakeMs = screenShakeMs.coerceAtLeast(300L)
            }

            // Apply corrosive DoT and movement debuff to enemies inside cloud
            for (enemy in enemies) {
                if (!enemy.isAlive) continue
                val eDist = hypot(enemy.x - gas.x, enemy.y - gas.y)
                if (eDist <= gas.currentRadius) {
                    val dmg = gas.damagePerSec * deltaSec
                    enemy.health -= dmg
                    // Dissolve armor & slow enemy
                    if (enemy.shieldHp > 0f) {
                        enemy.shieldHp = (enemy.shieldHp - dmg * 1.5f).coerceAtLeast(0f)
                    }
                    if (Random.nextFloat() < 0.25f) {
                        particles.add(
                            Particle(
                                x = enemy.x + (Random.nextFloat() - 0.5f) * 20f,
                                y = enemy.y + (Random.nextFloat() - 0.5f) * 20f,
                                vx = (Random.nextFloat() - 0.5f) * 30f,
                                vy = -20f,
                                color = Color(0xFF10B981),
                                size = 8f,
                                type = ParticleType.NANITE_SPORE
                            )
                        )
                    }
                }
            }

            // Spawn ambient nanite spores drifting around cloud
            if (Random.nextFloat() < 0.4f) {
                val angle = Random.nextFloat() * 6.28f
                val rad = Random.nextFloat() * gas.currentRadius
                particles.add(
                    Particle(
                        x = gas.x + cos(angle) * rad,
                        y = gas.y + sin(angle) * rad,
                        vx = (Random.nextFloat() - 0.5f) * 20f,
                        vy = (Random.nextFloat() - 0.5f) * 20f - 10f,
                        color = Color(0xFF10B981),
                        size = Random.nextFloat() * 12f + 6f,
                        type = ParticleType.NANITE_SPORE,
                        life = 1.2f,
                        maxLife = 1.2f
                    )
                )
            }

            if (gas.remainingSec <= 0f) {
                gasIterator.remove()
            }
        }

        // 3. Update Electric Arcs
        val arcIterator = electricArcs.iterator()
        while (arcIterator.hasNext()) {
            val arc = arcIterator.next()
            arc.lifeSec -= deltaSec
            if (arc.lifeSec <= 0f) {
                arcIterator.remove()
            }
        }

        // 4. Update Cryo Fields
        val cryoIterator = cryoFields.iterator()
        while (cryoIterator.hasNext()) {
            val cryo = cryoIterator.next()
            cryo.remainingSec -= deltaSec

            // Freeze enemies inside cryo field
            for (enemy in enemies) {
                if (!enemy.isAlive) continue
                val eDist = hypot(enemy.x - cryo.x, enemy.y - cryo.y)
                if (eDist <= cryo.radius) {
                    enemy.stunTimerMs = 2500
                    if (Random.nextFloat() < 0.2f) {
                        particles.add(
                            Particle(
                                x = enemy.x + (Random.nextFloat() - 0.5f) * 25f,
                                y = enemy.y + (Random.nextFloat() - 0.5f) * 25f,
                                vx = 0f,
                                vy = -15f,
                                color = Color(0xFF38BDF8),
                                size = 9f,
                                type = ParticleType.CRYO_CRYSTAL
                            )
                        )
                    }
                }
            }

            if (cryo.remainingSec <= 0f) {
                cryoIterator.remove()
            }
        }

        // 5. Update Shockwave rings
        val swIterator = shockwaves.iterator()
        while (swIterator.hasNext()) {
            val sw = swIterator.next()
            sw.lifeSec -= deltaSec
            val progress = 1.0f - (sw.lifeSec / sw.maxLifeSec)
            sw.currentRadius = 15f + (sw.maxRadius - 15f) * progress

            if (sw.lifeSec <= 0f) {
                swIterator.remove()
            }
        }

        // 6. Check Player Proximity for Contextual Interaction Prompt
        val prompt = getNearestInteractableHazard(player.x, player.y)

        return HazardUpdateResult(
            activeHazards = hazards.toList(),
            activeGasClouds = gasClouds.toList(),
            activeElectricArcs = electricArcs.toList(),
            activeCryoFields = cryoFields.toList(),
            activeShockwaves = shockwaves.toList(),
            interactionPrompt = prompt,
            spawnedParticles = emptyList(),
            spawnedLights = emptyList(),
            screenShakeMs = screenShakeMs,
            destroyedVoxelCoords = destroyedCoords
        )
    }

    /**
     * Find nearest interactable hazard terminal within activation distance (115px).
     */
    fun getNearestInteractableHazard(playerX: Float, playerY: Float, maxDist: Float = 115f): HazardInteractionPrompt? {
        var nearestHazard: HazardInstance? = null
        var minDist = Float.MAX_VALUE

        for (hazard in hazards) {
            if (hazard.status == HazardStatus.EXHAUSTED || hazard.status == HazardStatus.CHARGING) continue
            val dist = hypot(hazard.worldX - playerX, hazard.worldY - playerY)
            if (dist < minDist && dist <= maxDist) {
                minDist = dist
                nearestHazard = hazard
            }
        }

        return nearestHazard?.let {
            HazardInteractionPrompt(
                hazardId = it.id,
                title = it.type.displayName,
                actionName = it.type.actionVerb,
                worldX = it.worldX,
                worldY = it.worldY,
                type = it.type,
                distance = minDist
            )
        }
    }

    /**
     * Trigger the nearest hazard by ID via player interaction action.
     */
    fun interactWithHazard(
        hazardId: String,
        terrain: VoxelTerrain,
        enemies: MutableList<Enemy>,
        player: PlayerState,
        spawnedParticles: MutableList<Particle>,
        spawnedLights: MutableList<DynamicLight>,
        destroyedCoords: MutableList<Pair<Int, Int>>
    ): Boolean {
        val hazard = hazards.find { it.id == hazardId && it.status != HazardStatus.EXHAUSTED } ?: return false
        triggerHazardActivation(
            hazard = hazard,
            triggerSource = "PLAYER_CONSOLE_OVERLOAD",
            terrain = terrain,
            enemies = enemies,
            player = player,
            spawnedParticles = spawnedParticles,
            spawnedLights = spawnedLights,
            destroyedCoords = destroyedCoords
        )
        return true
    }

    // --- Helper math & particle functions ---

    private data class ConductiveTarget(val x: Float, val y: Float, val enemy: Enemy? = null, val enemyId: String? = null)

    private fun findConductiveTargets(
        originX: Float,
        originY: Float,
        radius: Float,
        enemies: List<Enemy>,
        terrain: VoxelTerrain
    ): List<ConductiveTarget> {
        val results = mutableListOf<ConductiveTarget>()

        // 1. Find live enemies within radius
        for (e in enemies) {
            if (!e.isAlive) continue
            val dist = hypot(e.x - originX, e.y - originY)
            if (dist <= radius) {
                results.add(ConductiveTarget(e.x, e.y, e, e.id))
            }
        }

        // 2. Find adjacent metallic voxels to create electric arcs along walls
        val minGx = ((originX - radius) / terrain.tileSize).toInt().coerceIn(0, terrain.width - 1)
        val maxGx = ((originX + radius) / terrain.tileSize).toInt().coerceIn(0, terrain.width - 1)
        val minGy = ((originY - radius) / terrain.tileSize).toInt().coerceIn(0, terrain.height - 1)
        val maxGy = ((originY + radius) / terrain.tileSize).toInt().coerceIn(0, terrain.height - 1)

        for (gx in minGx..maxGx) {
            for (gy in minGy..maxGy) {
                val tile = terrain.tiles[gx][gy]
                if (!tile.isDisintegrated && (tile.type == VoxelType.REINFORCED_METAL || tile.type == VoxelType.ENERGY_BARRIER)) {
                    val wx = (gx + 0.5f) * terrain.tileSize
                    val wy = (gy + 0.5f) * terrain.tileSize
                    val dist = hypot(wx - originX, wy - originY)
                    if (dist <= radius && results.size < 6) {
                        results.add(ConductiveTarget(wx, wy))
                    }
                }
            }
        }

        return results
    }

    private fun spawnGasBurstParticles(x: Float, y: Float, particles: MutableList<Particle>, count: Int) {
        val rand = Random.Default
        for (i in 0 until count) {
            val angle = rand.nextFloat() * 6.28f
            val speed = rand.nextFloat() * 120f + 30f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = Color(0xFF10B981),
                    size = rand.nextFloat() * 16f + 10f,
                    type = ParticleType.NANITE_SPORE,
                    life = 1.4f,
                    maxLife = 1.4f
                )
            )
        }
    }

    private fun spawnElectricSparks(x: Float, y: Float, particles: MutableList<Particle>, count: Int) {
        val rand = Random.Default
        for (i in 0 until count) {
            val angle = rand.nextFloat() * 6.28f
            val speed = rand.nextFloat() * 180f + 60f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = Color(0xFF00F0FF),
                    size = rand.nextFloat() * 6f + 3f,
                    type = ParticleType.ELECTRIC_BOLT,
                    life = 0.5f,
                    maxLife = 0.5f
                )
            )
        }
    }

    private fun spawnCryoParticles(x: Float, y: Float, particles: MutableList<Particle>, count: Int) {
        val rand = Random.Default
        for (i in 0 until count) {
            val angle = rand.nextFloat() * 6.28f
            val speed = rand.nextFloat() * 140f + 40f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = Color(0xFF38BDF8),
                    size = rand.nextFloat() * 10f + 6f,
                    type = ParticleType.CRYO_CRYSTAL,
                    life = 1.0f,
                    maxLife = 1.0f
                )
            )
        }
    }

    private fun spawnExplosionFire(x: Float, y: Float, particles: MutableList<Particle>, count: Int) {
        val rand = Random.Default
        for (i in 0 until count) {
            val angle = rand.nextFloat() * 6.28f
            val speed = rand.nextFloat() * 240f + 80f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = if (i % 2 == 0) Color(0xFFFF9900) else Color(0xFFFF0055),
                    size = rand.nextFloat() * 18f + 10f,
                    type = ParticleType.EXPLOSION_FLAME,
                    life = 0.8f,
                    maxLife = 0.8f
                )
            )
        }
    }

    private fun spawnDebrisParticles(x: Float, y: Float, particles: MutableList<Particle>, count: Int, tileType: VoxelType) {
        val rand = Random.Default
        for (i in 0 until count) {
            val angle = rand.nextFloat() * 6.28f
            val speed = rand.nextFloat() * 160f + 50f
            val col = when (tileType) {
                VoxelType.REINFORCED_METAL -> Color(0xFF38BDF8)
                VoxelType.CONCRETE_WALL, VoxelType.HIGH_COVER_WALL -> Color(0xFF94A3B8)
                VoxelType.ALIEN_BIOMASS -> Color(0xFFA855F7)
                VoxelType.ENERGY_BARRIER -> Color(0xFF00F0FF)
                else -> Color(0xFFD97706)
            }
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = col,
                    size = rand.nextFloat() * 7f + 3f,
                    type = ParticleType.DEBRIS_VOXEL,
                    life = 0.7f,
                    maxLife = 0.7f,
                    rotation = rand.nextFloat() * 360f,
                    vRot = rand.nextFloat() * 10f - 5f
                )
            )
        }
    }
}
