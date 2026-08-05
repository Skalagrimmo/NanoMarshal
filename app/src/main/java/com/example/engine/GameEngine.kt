package com.example.engine

import com.example.data.model.*
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*
import kotlin.random.Random

data class Bullet(
    val id: String,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val damage: Float,
    val isPlayerBullet: Boolean,
    val pierceCover: Boolean = false,
    val color: Color = Color(0xFF00F0FF),
    var lifeMs: Long = 1500
)

data class ThrowableItem(
    val id: String,
    var x: Float,
    var y: Float,
    var targetX: Float,
    var targetY: Float,
    var startX: Float,
    var startY: Float,
    var progress: Float = 0f, // 0.0 to 1.0
    val gadget: Gadget
)

data class GameState(
    val player: PlayerState = PlayerState(),
    val enemies: List<Enemy> = emptyList(),
    val bullets: List<Bullet> = emptyList(),
    val particles: List<Particle> = emptyList(),
    val throwables: List<ThrowableItem> = emptyList(),
    val currentMission: Mission = DefaultMissions.MISSION_1,
    val isGameOver: Boolean = false,
    val isVictory: Boolean = false,
    val isPaused: Boolean = false,
    val isTacticalGridOverlayEnabled: Boolean = true,
    val screenShakeMs: Long = 0,
    val missionTimeMs: Long = 0
)

class GameEngine(
    val mission: Mission
) {
    val terrain = VoxelTerrain(width = mission.gridWidth, height = mission.gridHeight, tileSize = 64f)

    private val _gameState = MutableStateFlow(GameState(currentMission = mission))
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private var lastShotTimeMs: Long = 0
    private var lastGadgetTimeMs: Long = 0
    private var lastFrameTimeMs: Long = System.currentTimeMillis()

    init {
        initMission()
    }

    fun initMission() {
        terrain.generateProceduralMap(mission.id, seed = mission.id.hashCode().toLong())

        val p = PlayerState(
            x = terrain.spawnPointX,
            y = terrain.spawnPointY,
            health = 100f,
            maxHealth = 100f,
            nanoShield = 50f,
            maxNanoShield = 50f,
            currentWeapon = DefaultWeapons.PLASMA_RIFLE,
            sidearmWeapon = DefaultWeapons.NEEDLE_PISTOL
        )

        // Spawn Enemies based on mission difficulty
        val enemyList = mutableListOf<Enemy>()
        val rand = Random(mission.id.hashCode())

        val numEnemies = 4 + mission.difficulty * 2
        for (i in 0 until numEnemies) {
            val gx = rand.nextInt(5, terrain.width - 3)
            val gy = rand.nextInt(5, terrain.height - 3)
            val tile = terrain.tiles[gx][gy]
            if (tile.isWalkable) {
                val eType = when (i % 5) {
                    0 -> EnemyType.GRUNT
                    1 -> EnemyType.FLANKER
                    2 -> EnemyType.SHIELD_ENFORCER
                    3 -> EnemyType.SNIPER_STALKER
                    else -> EnemyType.GRUNT
                }

                val hp = when (eType) {
                    EnemyType.GRUNT -> 80f
                    EnemyType.FLANKER -> 60f
                    EnemyType.SHIELD_ENFORCER -> 140f
                    EnemyType.SNIPER_STALKER -> 70f
                    EnemyType.BOUNTY_BOSS -> 350f
                }

                val waypoints = listOf(
                    Pair((gx + 0.5f) * terrain.tileSize, (gy + 0.5f) * terrain.tileSize),
                    Pair((gx + 2.5f).coerceAtMost(terrain.width - 2f) * terrain.tileSize, (gy + 0.5f) * terrain.tileSize)
                )

                enemyList.add(
                    Enemy(
                        id = "enemy_$i",
                        name = "Syndicate ${eType.name}",
                        type = eType,
                        x = (gx + 0.5f) * terrain.tileSize,
                        y = (gy + 0.5f) * terrain.tileSize,
                        health = hp,
                        maxHealth = hp,
                        shieldHp = if (eType == EnemyType.SHIELD_ENFORCER) 80f else 0f,
                        maxShieldHp = if (eType == EnemyType.SHIELD_ENFORCER) 80f else 0f,
                        patrolWaypoints = waypoints,
                        bountyReward = 150 + mission.difficulty * 100
                    )
                )
            }
        }

        // Spawn Boss Bounty Target near objective
        val objGx = terrain.width - 2
        val objGy = terrain.height - 2
        enemyList.add(
            Enemy(
                id = "bounty_boss",
                name = mission.bountyTargetName,
                type = EnemyType.BOUNTY_BOSS,
                x = (objGx - 1f) * terrain.tileSize,
                y = (objGy - 1f) * terrain.tileSize,
                health = 300f + mission.difficulty * 100f,
                maxHealth = 300f + mission.difficulty * 100f,
                shieldHp = 100f,
                maxShieldHp = 100f,
                bountyReward = mission.rewardCredits
            )
        )

        _gameState.value = GameState(
            player = p,
            enemies = enemyList,
            currentMission = mission
        )
    }

    // 60 FPS Engine Tick Loop
    fun update(dtMs: Long) {
        val currState = _gameState.value
        if (currState.isGameOver || currState.isPaused) return

        val now = System.currentTimeMillis()
        val deltaSec = dtMs / 1000f

        val player = currState.player.copy()
        val enemies = currState.enemies.map { it.copy() }.toMutableList()
        val bullets = currState.bullets.map { it.copy() }.toMutableList()
        val particles = currState.particles.map { it.copy() }.toMutableList()
        val throwables = currState.throwables.map { it.copy() }.toMutableList()

        // 1. Update Player position & cover status
        updatePlayerLogic(player, deltaSec, now)

        // 2. Update Bullets & Collisions
        updateBullets(bullets, player, enemies, particles, deltaSec)

        // 3. Update Throwables (Grenades/EMP)
        updateThrowables(throwables, enemies, particles, deltaSec)

        // 4. Update Adaptive Enemy AI
        updateEnemyAI(enemies, player, bullets, particles, now, deltaSec)

        // 5. Update Particles
        updateParticles(particles, deltaSec)

        // 6. Check Win/Loss conditions
        var isVictory = currState.isVictory
        var isGameOver = currState.isGameOver

        if (!player.isAlive) {
            isGameOver = true
        }

        val bountyBoss = enemies.find { it.id == "bounty_boss" }
        if (bountyBoss != null && !bountyBoss.isAlive) {
            isVictory = true
            isGameOver = true
            SoundFX.play(SoundFX.SoundType.MISSION_WIN)
        }

        // Update LOD terrain camera
        terrain.updateLODLevels(player.x, player.y)

        _gameState.value = currState.copy(
            player = player,
            enemies = enemies,
            bullets = bullets,
            particles = particles,
            throwables = throwables,
            isVictory = isVictory,
            isGameOver = isGameOver,
            screenShakeMs = (currState.screenShakeMs - dtMs).coerceAtLeast(0),
            missionTimeMs = currState.missionTimeMs + dtMs
        )
    }

    fun handlePlayerMoveInput(dx: Float, dy: Float) {
        val curr = _gameState.value.player
        val speed = 180f * curr.moveSpeedMultiplier
        val vx = dx * speed
        val vy = dy * speed

        val newFacing = if (abs(dx) > 0.1f || abs(dy) > 0.1f) atan2(dy, dx) else curr.facingAngle
        val noise = if (abs(dx) > 0.1f || abs(dy) > 0.1f) {
            when (curr.stance) {
                PlayerStance.STAND -> 120f
                PlayerStance.CROUCH -> 40f
                PlayerStance.PRONE -> 15f
            }
        } else 0f

        _gameState.value = _gameState.value.copy(
            player = curr.copy(
                vx = vx,
                vy = vy,
                facingAngle = newFacing,
                stealthNoiseRadius = noise
            )
        )
    }

    fun handlePlayerAimInput(angle: Float, isFiring: Boolean) {
        val curr = _gameState.value.player
        _gameState.value = _gameState.value.copy(
            player = curr.copy(
                aimAngle = angle,
                isFiring = isFiring
            )
        )
        if (isFiring) {
            triggerPlayerShot()
        }
    }

    fun triggerPlayerShot() {
        val now = System.currentTimeMillis()
        val state = _gameState.value
        val p = state.player
        val weapon = p.activeWeapon

        if (p.isReloading || p.currentAmmo <= 0 || now - lastShotTimeMs < weapon.fireRateMs) return

        lastShotTimeMs = now
        val newAmmo = p.currentAmmo - 1

        SoundFX.play(if (weapon.type == WeaponType.PLASMA_RIFLE) SoundFX.SoundType.PLASMA_BLAST else SoundFX.SoundType.LASER_SHOT)

        // Bullet velocity vector
        val bulletSpeed = 600f
        val vx = cos(p.aimAngle) * bulletSpeed
        val vy = sin(p.aimAngle) * bulletSpeed

        val bullets = state.bullets.toMutableList()
        bullets.add(
            Bullet(
                id = "b_${now}_${Random.nextInt(1000)}",
                x = p.x + cos(p.aimAngle) * 20f,
                y = p.y + sin(p.aimAngle) * 20f,
                vx = vx,
                vy = vy,
                damage = weapon.effectiveDamage.toFloat(),
                isPlayerBullet = true,
                pierceCover = weapon.pierceCover,
                color = if (weapon.type == WeaponType.RAILGUN) Color(0xFFF59E0B) else Color(0xFF00F0FF)
            )
        )

        // Stealth Noise emission
        val noiseRadius = if (weapon.isSilenced) 60f else 380f

        _gameState.value = state.copy(
            bullets = bullets,
            player = p.copy(
                currentAmmo = newAmmo,
                stealthNoiseRadius = noiseRadius
            ),
            screenShakeMs = if (weapon.type == WeaponType.SHOTGUN || weapon.type == WeaponType.RAILGUN) 120 else 0
        )

        if (newAmmo <= 0) {
            reloadWeapon()
        }
    }

    fun reloadWeapon() {
        val p = _gameState.value.player
        if (p.isReloading) return
        SoundFX.play(SoundFX.SoundType.RELOAD)
        _gameState.value = _gameState.value.copy(
            player = p.copy(
                isReloading = true,
                reloadTimerMs = p.activeWeapon.reloadTimeMs
            )
        )
    }

    fun throwGadget(targetX: Float, targetY: Float) {
        val now = System.currentTimeMillis()
        val p = _gameState.value.player
        if (p.gadgetCount <= 0 || now - lastGadgetTimeMs < p.activeGadget.cooldownMs) return

        lastGadgetTimeMs = now
        val throwables = _gameState.value.throwables.toMutableList()
        throwables.add(
            ThrowableItem(
                id = "t_$now",
                x = p.x,
                y = p.y,
                startX = p.x,
                startY = p.y,
                targetX = targetX,
                targetY = targetY,
                gadget = p.activeGadget
            )
        )

        _gameState.value = _gameState.value.copy(
            throwables = throwables,
            player = p.copy(gadgetCount = p.gadgetCount - 1)
        )
    }

    fun toggleStance() {
        val p = _gameState.value.player
        val nextStance = when (p.stance) {
            PlayerStance.STAND -> PlayerStance.CROUCH
            PlayerStance.CROUCH -> PlayerStance.PRONE
            PlayerStance.PRONE -> PlayerStance.STAND
        }
        _gameState.value = _gameState.value.copy(player = p.copy(stance = nextStance))
    }

    fun toggleTacticalOverlay() {
        _gameState.value = _gameState.value.copy(
            isTacticalGridOverlayEnabled = !_gameState.value.isTacticalGridOverlayEnabled
        )
    }

    fun swapWeapon() {
        val p = _gameState.value.player
        val nextSlot = if (p.activeWeaponSlot == 1) 2 else 1
        _gameState.value = _gameState.value.copy(
            player = p.copy(
                activeWeaponSlot = nextSlot,
                currentAmmo = if (nextSlot == 1) p.currentWeapon.magSize else p.sidearmWeapon.magSize,
                isReloading = false
            )
        )
    }

    private fun updatePlayerLogic(player: PlayerState, deltaSec: Float, now: Long) {
        // Handle Reloading timer
        if (player.isReloading) {
            player.reloadTimerMs -= (deltaSec * 1000).toLong()
            if (player.reloadTimerMs <= 0) {
                player.isReloading = false
                player.currentAmmo = player.activeWeapon.magSize
            }
        }

        // Apply Movement
        val nextX = player.x + player.vx * deltaSec
        val nextY = player.y + player.vy * deltaSec

        // Check Voxel Collisions
        val currentTile = terrain.getTileAtWorld(nextX, nextY)
        if (currentTile == null || currentTile.isWalkable) {
            player.x = nextX.coerceIn(terrain.tileSize, (terrain.width - 1) * terrain.tileSize)
            player.y = nextY.coerceIn(terrain.tileSize, (terrain.height - 1) * terrain.tileSize)
        }

        // Check adjacent Cover status
        val gx = (player.x / terrain.tileSize).toInt()
        val gy = (player.y / terrain.tileSize).toInt()
        var foundCover = false
        var maxCoverHeight = CoverHeight.NONE

        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val neighbor = terrain.tiles.getOrNull(gx + dx)?.getOrNull(gy + dy)
                if (neighbor != null && neighbor.coverHeight != CoverHeight.NONE && !neighbor.isDisintegrated) {
                    foundCover = true
                    if (neighbor.coverHeight.ordinal > maxCoverHeight.ordinal) {
                        maxCoverHeight = neighbor.coverHeight
                    }
                }
            }
        }

        player.isBehindCover = foundCover
        player.coverHeight = if (foundCover) maxCoverHeight else CoverHeight.NONE

        // Shield recharge timer
        if (player.nanoShield < player.maxNanoShield) {
            player.shieldRechargeDelayMs -= (deltaSec * 1000).toLong()
            if (player.shieldRechargeDelayMs <= 0) {
                player.nanoShield = (player.nanoShield + 15f * deltaSec).coerceAtMost(player.maxNanoShield)
            }
        }
    }

    private fun updateBullets(
        bullets: MutableList<Bullet>,
        player: PlayerState,
        enemies: MutableList<Enemy>,
        particles: MutableList<Particle>,
        deltaSec: Float
    ) {
        val iterator = bullets.iterator()
        while (iterator.hasNext()) {
            val b = iterator.next()
            b.x += b.vx * deltaSec
            b.y += b.vy * deltaSec
            b.lifeMs -= (deltaSec * 1000).toLong()

            if (b.lifeMs <= 0) {
                iterator.remove()
                continue
            }

            // Check Voxel Tile collision
            val tile = terrain.getTileAtWorld(b.x, b.y)
            if (tile != null && tile.coverHeight != CoverHeight.NONE && !tile.isDisintegrated) {
                if (!b.pierceCover) {
                    // Damage voxel cover
                    val destroyed = terrain.applyDamageToTile(tile.gridX, tile.gridY, b.damage)
                    if (destroyed) {
                        spawnDebrisParticles(particles, (tile.gridX + 0.5f) * terrain.tileSize, (tile.gridY + 0.5f) * terrain.tileSize)
                        SoundFX.play(SoundFX.SoundType.EXPLOSION)
                    }
                    iterator.remove()
                    continue
                }
            }

            // Check Enemy hits if player bullet
            if (b.isPlayerBullet) {
                for (e in enemies) {
                    if (!e.isAlive) continue
                    val dist = sqrt((b.x - e.x) * (b.x - e.x) + (b.y - e.y) * (b.y - e.y))
                    if (dist < 26f) {
                        // Flanking & Cover damage calculation
                        val hitAngle = atan2(b.y - e.y, b.x - e.x)
                        val angleDiff = abs(hitAngle - e.facingAngle)
                        val isFlanked = angleDiff > Math.toRadians(90.0)

                        var finalDmg = b.damage
                        if (e.isBehindCover && !isFlanked) {
                            finalDmg *= 0.3f // Cover mitigation
                        } else if (isFlanked) {
                            finalDmg *= 1.8f // Flanking critical bonus!
                        }

                        // Apply to enemy shield first then health
                        if (e.shieldHp > 0) {
                            e.shieldHp -= finalDmg
                            if (e.shieldHp < 0) {
                                e.health += e.shieldHp
                                e.shieldHp = 0f
                            }
                        } else {
                            e.health -= finalDmg
                        }

                        e.state = AIState.ENGAGED
                        e.lastKnownPlayerX = player.x
                        e.lastKnownPlayerY = player.y

                        // Spawn Floating Damage Text Particle
                        particles.add(
                            Particle(
                                x = e.x, y = e.y - 15f, vx = 0f, vy = -30f,
                                color = if (isFlanked) Color(0xFFF59E0B) else Color(0xFF00F0FF),
                                size = 16f,
                                type = ParticleType.HIT_NUMBER,
                                text = if (isFlanked) "CRIT ${finalDmg.toInt()}" else "${finalDmg.toInt()}"
                            )
                        )

                        if (!e.isAlive) {
                            player.killsCount++
                            player.credits += e.bountyReward
                            spawnDebrisParticles(particles, e.x, e.y)
                        }

                        iterator.remove()
                        break
                    }
                }
            } else {
                // Enemy bullet hits player
                val dist = sqrt((b.x - player.x) * (b.x - player.x) + (b.y - player.y) * (b.y - player.y))
                if (dist < 24f) {
                    var dmg = b.damage
                    if (player.isBehindCover) {
                        dmg *= when (player.coverHeight) {
                            CoverHeight.HIGH -> 0.15f
                            CoverHeight.LOW -> 0.45f
                            else -> 1.0f
                        }
                    }

                    if (player.nanoShield > 0) {
                        player.nanoShield -= dmg
                        if (player.nanoShield < 0) {
                            player.health += player.nanoShield
                            player.nanoShield = 0f
                        }
                    } else {
                        player.health -= dmg
                    }
                    player.shieldRechargeDelayMs = 3500

                    iterator.remove()
                }
            }
        }
    }

    private fun updateThrowables(
        throwables: MutableList<ThrowableItem>,
        enemies: MutableList<Enemy>,
        particles: MutableList<Particle>,
        deltaSec: Float
    ) {
        val iterator = throwables.iterator()
        while (iterator.hasNext()) {
            val t = iterator.next()
            t.progress += deltaSec * 1.8f
            t.x = t.startX + (t.targetX - t.startX) * t.progress
            t.y = t.startY + (t.targetY - t.startY) * t.progress

            if (t.progress >= 1.0f) {
                // Detonate Grenade / EMP
                SoundFX.play(SoundFX.SoundType.EXPLOSION)
                spawnDebrisParticles(particles, t.targetX, t.targetY, count = 25)

                // Damage surrounding enemies & destroy cover
                for (e in enemies) {
                    if (!e.isAlive) continue
                    val dist = sqrt((e.x - t.targetX) * (e.x - t.targetX) + (e.y - t.targetY) * (e.y - t.targetY))
                    if (dist < 180f) {
                        val blastDmg = 120f * (1.0f - dist / 180f)
                        e.health -= blastDmg
                        e.state = AIState.ENGAGED
                        if (t.gadget.type == GadgetType.EMP_MINE) {
                            e.shieldHp = 0f
                            e.stunTimerMs = 3000
                        }
                    }
                }

                // Destroy adjacent Voxel tiles
                val gx = (t.targetX / terrain.tileSize).toInt()
                val gy = (t.targetY / terrain.tileSize).toInt()
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        terrain.applyDamageToTile(gx + dx, gy + dy, 150f)
                    }
                }

                iterator.remove()
            }
        }
    }

    private fun updateEnemyAI(
        enemies: MutableList<Enemy>,
        player: PlayerState,
        bullets: MutableList<Bullet>,
        particles: MutableList<Particle>,
        now: Long,
        deltaSec: Float
    ) {
        for (e in enemies) {
            if (!e.isAlive) continue

            if (e.stunTimerMs > 0) {
                e.stunTimerMs -= (deltaSec * 1000).toLong()
                continue
            }

            val distToPlayer = sqrt((player.x - e.x) * (player.x - e.x) + (player.y - e.y) * (player.y - e.y))
            val angleToPlayer = atan2(player.y - e.y, player.x - e.x)

            // Line of sight vision cone check
            val angleDiff = abs(angleToPlayer - e.facingAngle)
            val canSeePlayer = distToPlayer < e.visionRange && angleDiff < (e.visionAngleRad / 2f)

            // Hear player noise
            val canHearPlayer = distToPlayer < player.stealthNoiseRadius

            if (canSeePlayer || canHearPlayer) {
                e.state = if (e.type == EnemyType.FLANKER) AIState.FLANKING else AIState.ENGAGED
                e.lastKnownPlayerX = player.x
                e.lastKnownPlayerY = player.y
                e.facingAngle = angleToPlayer
            }

            // AI State Machine Execution
            when (e.state) {
                AIState.PATROL -> {
                    if (e.patrolWaypoints.isNotEmpty()) {
                        val target = e.patrolWaypoints[e.currentWaypointIndex]
                        val dx = target.first - e.x
                        val dy = target.second - e.y
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist < 10f) {
                            e.currentWaypointIndex = (e.currentWaypointIndex + 1) % e.patrolWaypoints.size
                        } else {
                            e.facingAngle = atan2(dy, dx)
                            e.x += cos(e.facingAngle) * e.moveSpeed * 12f * deltaSec
                            e.y += sin(e.facingAngle) * e.moveSpeed * 12f * deltaSec
                        }
                    }
                }

                AIState.ENGAGED -> {
                    // Fire at player
                    if (now - e.shootCooldownMs > 1200) {
                        e.shootCooldownMs = now
                        val bulletVx = cos(e.facingAngle) * 450f
                        val bulletVy = sin(e.facingAngle) * 450f
                        bullets.add(
                            Bullet(
                                id = "eb_${now}_${Random.nextInt(1000)}",
                                x = e.x,
                                y = e.y,
                                vx = bulletVx,
                                vy = bulletVy,
                                damage = e.weaponDamage,
                                isPlayerBullet = false,
                                color = Color(0xFFEF4444)
                            )
                        )
                        SoundFX.play(SoundFX.SoundType.LASER_SHOT)
                    }

                    // Seek cover if low HP
                    if (e.health < e.maxHealth * 0.4f) {
                        val coverTile = terrain.findBestCoverNear(e.x, e.y, player.x, player.y)
                        if (coverTile != null) {
                            e.state = AIState.SEEKING_COVER
                            e.targetCoverX = coverTile.gridX
                            e.targetCoverY = coverTile.gridY
                        }
                    }
                }

                AIState.FLANKING -> {
                    // Run around player to flank from sides!
                    val flankAngle = player.facingAngle + Math.PI.toFloat() / 2f
                    val flankX = player.x + cos(flankAngle) * 160f
                    val flankY = player.y + sin(flankAngle) * 160f

                    val dx = flankX - e.x
                    val dy = flankY - e.y
                    e.facingAngle = atan2(dy, dx)
                    e.x += cos(e.facingAngle) * e.moveSpeed * 18f * deltaSec
                    e.y += sin(e.facingAngle) * e.moveSpeed * 18f * deltaSec

                    if (now - e.shootCooldownMs > 900) {
                        e.shootCooldownMs = now
                        bullets.add(
                            Bullet(
                                id = "eb_${now}_${Random.nextInt(1000)}",
                                x = e.x,
                                y = e.y,
                                vx = cos(angleToPlayer) * 500f,
                                vy = sin(angleToPlayer) * 500f,
                                damage = e.weaponDamage * 1.2f,
                                isPlayerBullet = false,
                                color = Color(0xFFEC4899)
                            )
                        )
                    }
                }

                AIState.SEEKING_COVER -> {
                    if (e.targetCoverX != null && e.targetCoverY != null) {
                        val targetWorldX = (e.targetCoverX!! + 0.5f) * terrain.tileSize
                        val targetWorldY = (e.targetCoverY!! + 0.5f) * terrain.tileSize
                        val dx = targetWorldX - e.x
                        val dy = targetWorldY - e.y
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist < 15f) {
                            e.isBehindCover = true
                            e.state = AIState.ENGAGED
                        } else {
                            e.facingAngle = atan2(dy, dx)
                            e.x += cos(e.facingAngle) * e.moveSpeed * 15f * deltaSec
                            e.y += sin(e.facingAngle) * e.moveSpeed * 15f * deltaSec
                        }
                    } else {
                        e.state = AIState.ENGAGED
                    }
                }

                else -> {}
            }
        }
    }

    private fun updateParticles(particles: MutableList<Particle>, deltaSec: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx * deltaSec
            p.y += p.vy * deltaSec
            p.life -= deltaSec * 1.5f
            if (p.life <= 0f) {
                iterator.remove()
            }
        }
    }

    private fun spawnDebrisParticles(particles: MutableList<Particle>, x: Float, y: Float, count: Int = 12) {
        val rand = Random.Default
        for (i in 0 until count) {
            val angle = rand.nextFloat() * 2f * Math.PI.toFloat()
            val speed = rand.nextFloat() * 180f + 40f
            particles.add(
                Particle(
                    x = x,
                    y = y,
                    vx = cos(angle) * speed,
                    vy = sin(angle) * speed,
                    color = when (i % 3) {
                        0 -> Color(0xFF00F0FF)
                        1 -> Color(0xFFF59E0B)
                        else -> Color(0xFFA855F7)
                    },
                    size = rand.nextFloat() * 6f + 3f,
                    type = ParticleType.DEBRIS_VOXEL
                )
            )
        }
    }
}
