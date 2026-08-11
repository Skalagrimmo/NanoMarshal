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
    var lifeMs: Long = 1500,
    var ricochetCount: Int = 0,
    val maxRicochets: Int = 2,
    var lastHitTileKey: String? = null
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
    val objectives: List<MissionObjective> = emptyList(),
    val activeObjectiveToast: String? = null,
    val activeObjectiveToastMs: Long = 0,
    val isGameOver: Boolean = false,
    val isVictory: Boolean = false,
    val isPaused: Boolean = false,
    val isTacticalGridOverlayEnabled: Boolean = true,
    val ricochetTrajectoryPoints: List<Pair<Float, Float>> = emptyList(),
    val screenShakeMs: Long = 0,
    val missionTimeMs: Long = 0,
    val svdagCompressionRatio: Float = 0f,
    val uniqueDagNodes: Int = 0,
    val totalDagNodes: Int = 0,
    val lod0Count: Int = 0,
    val lod1Count: Int = 0,
    val lod2Count: Int = 0,
    val dynamicLights: List<DynamicLight> = emptyList()
)

class GameEngine(
    val mission: Mission
) {
    val worldManager = VoxelWorldManager(width = mission.gridWidth, height = mission.gridHeight, maxDepth = 5, tileSize = 64f)
    val weaponSystem = WeaponSystem(worldManager)
    val terrain get() = worldManager.terrain
    val svdagEngine get() = worldManager.svdagEngine
    val enemyAI = EnemyAI(worldManager, terrain)
    val objectiveManager = ObjectiveManager()

    private val _gameState = MutableStateFlow(GameState(currentMission = mission))
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private var lastShotTimeMs: Long = 0
    private var lastGadgetTimeMs: Long = 0
    private var lastFrameTimeMs: Long = System.currentTimeMillis()

    init {
        initMission()
    }

    fun initMission() {
        worldManager.initializeWorld(mission.id, worldSeed = mission.id.hashCode().toLong())

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

        objectiveManager.initializeForMission(mission, terrain)

        _gameState.value = GameState(
            player = p,
            enemies = enemyList,
            currentMission = mission,
            objectives = objectiveManager.objectives
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
        updatePlayerLogic(player, particles, deltaSec, now)

        // 2. Update Bullets & Collisions
        updateBullets(bullets, player, enemies, particles, deltaSec)

        // 3. Update Throwables (Grenades/EMP)
        updateThrowables(throwables, enemies, particles, deltaSec)

        // 4. Update Adaptive Enemy AI
        updateEnemyAI(enemies, player, bullets, particles, now, deltaSec)

        // 5. Update Particles
        updateParticles(particles, deltaSec)

        // 6. Update Mission Objectives
        val objResult = objectiveManager.update(
            player = player,
            enemies = enemies,
            terrain = terrain,
            worldManager = worldManager,
            deltaSec = deltaSec
        )

        var activeToast = currState.activeObjectiveToast
        var activeToastMs = currState.activeObjectiveToastMs

        if (objResult.activeToast != null) {
            activeToast = objResult.activeToast
            activeToastMs = 3000L
        } else if (activeToastMs > 0) {
            activeToastMs = (activeToastMs - dtMs).coerceAtLeast(0)
            if (activeToastMs == 0L) activeToast = null
        }

        // 7. Check Win/Loss conditions
        var isVictory = currState.isVictory
        var isGameOver = currState.isGameOver

        if (!player.isAlive || objResult.isAnyPrimaryFailed) {
            isGameOver = true
        }

        if (objResult.isAllPrimaryCompleted && !isVictory) {
            isVictory = true
            isGameOver = true
            SoundFX.play(SoundFX.SoundType.MISSION_WIN)
        }

        // Update Sparse Voxel DAG structure and multi-tier LOD hierarchy
        worldManager.rebuildWorldSvdag()
        worldManager.updateWorldLOD(player.x, player.y)

        // Update Dynamic Lighting
        val activeLights = updateDynamicLights(bullets, player, enemies, deltaSec)

        // Compute predictive ricochet trajectory
        val trajectoryPoints = computeRicochetTrajectory(player, maxBounces = player.activeWeapon.maxRicochets)

        _gameState.value = currState.copy(
            player = player,
            enemies = enemies,
            bullets = bullets,
            particles = particles,
            throwables = throwables,
            objectives = objResult.objectives,
            activeObjectiveToast = activeToast,
            activeObjectiveToastMs = activeToastMs,
            dynamicLights = activeLights,
            ricochetTrajectoryPoints = trajectoryPoints,
            isVictory = isVictory,
            isGameOver = isGameOver,
            screenShakeMs = (currState.screenShakeMs - dtMs).coerceAtLeast(0),
            missionTimeMs = currState.missionTimeMs + dtMs,
            svdagCompressionRatio = svdagEngine.compressionRatio,
            uniqueDagNodes = svdagEngine.uniqueDagNodesCount,
            totalDagNodes = svdagEngine.totalUncompressedNodes,
            lod0Count = svdagEngine.lod0Count,
            lod1Count = svdagEngine.lod1Count,
            lod2Count = svdagEngine.lod2Count
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

    fun toggleAutoAimMode() {
        val curr = _gameState.value.player
        val nextMode = when (curr.autoAimMode) {
            AutoAimMode.SMART -> AutoAimMode.PRECISE
            AutoAimMode.PRECISE -> AutoAimMode.OFF
            AutoAimMode.OFF -> AutoAimMode.SMART
        }
        _gameState.value = _gameState.value.copy(
            player = curr.copy(
                autoAimMode = nextMode,
                isAutoAimEnabled = (nextMode != AutoAimMode.OFF)
            )
        )
    }

    private fun normalizeAngle(rad: Float): Float {
        var a = rad % (2 * Math.PI.toFloat())
        if (a > Math.PI.toFloat()) a -= 2 * Math.PI.toFloat()
        else if (a < -Math.PI.toFloat()) a += 2 * Math.PI.toFloat()
        return a
    }

    private fun blendAngles(a1: Float, a2: Float, t: Float): Float {
        val diff = normalizeAngle(a2 - a1)
        return a1 + diff * t
    }

    fun processAutoAimForAngle(player: PlayerState, rawAngle: Float): PlayerState {
        player.rawInputAngle = rawAngle
        if (player.autoAimMode == AutoAimMode.OFF || !player.isAutoAimEnabled) {
            return player.copy(
                aimAngle = rawAngle,
                isAutoAimLocked = false,
                autoAimTargetEnemyId = null,
                autoAimTargetPos = null,
                autoAimLockProgress = 0f
            )
        }

        val aliveEnemies = _gameState.value.enemies.filter { it.isAlive }
        if (aliveEnemies.isEmpty()) {
            return player.copy(
                aimAngle = rawAngle,
                isAutoAimLocked = false,
                autoAimTargetEnemyId = null,
                autoAimTargetPos = null,
                autoAimLockProgress = 0f
            )
        }

        val maxRange = if (player.autoAimMode == AutoAimMode.SMART) 520f else 680f
        val maxConeRad = if (player.autoAimMode == AutoAimMode.SMART) Math.toRadians(65.0).toFloat() else Math.toRadians(35.0).toFloat()

        var bestEnemy: Enemy? = null
        var bestScore = Float.MAX_VALUE

        for (e in aliveEnemies) {
            val dx = e.x - player.x
            val dy = e.y - player.y
            val dist = hypot(dx, dy)
            if (dist > maxRange) continue

            val angleToEnemy = atan2(dy, dx)
            val angleDiff = abs(normalizeAngle(angleToEnemy - rawAngle))

            if (angleDiff <= maxConeRad) {
                val score = angleDiff * 300f + dist * 0.7f
                if (score < bestScore) {
                    bestScore = score
                    bestEnemy = e
                }
            }
        }

        return if (bestEnemy != null) {
            val targetAngle = atan2(bestEnemy.y - player.y, bestEnemy.x - player.x)
            val magnetism = if (player.autoAimMode == AutoAimMode.SMART) 0.82f else 0.95f
            val magnetizedAngle = blendAngles(rawAngle, targetAngle, magnetism)
            val lockProg = (player.autoAimLockProgress + 0.2f).coerceAtMost(1f)

            player.copy(
                aimAngle = magnetizedAngle,
                rawInputAngle = rawAngle,
                isAutoAimLocked = true,
                autoAimTargetEnemyId = bestEnemy.id,
                autoAimTargetPos = Pair(bestEnemy.x, bestEnemy.y),
                autoAimLockProgress = lockProg
            )
        } else {
            player.copy(
                aimAngle = rawAngle,
                rawInputAngle = rawAngle,
                isAutoAimLocked = false,
                autoAimTargetEnemyId = null,
                autoAimTargetPos = null,
                autoAimLockProgress = 0f
            )
        }
    }

    fun handlePlayerAimInput(angle: Float, isFiring: Boolean) {
        val curr = _gameState.value.player
        val processedPlayer = processAutoAimForAngle(curr, angle)
        _gameState.value = _gameState.value.copy(
            player = processedPlayer.copy(
                isFiring = isFiring
            )
        )
        if (isFiring) {
            triggerPlayerShot()
        }
    }

    fun computeRicochetTrajectory(player: PlayerState, maxBounces: Int = 2): List<Pair<Float, Float>> {
        val points = mutableListOf<Pair<Float, Float>>()
        points.add(Pair(player.x, player.y))

        var currX = player.x
        var currY = player.y
        var dirX = cos(player.aimAngle)
        var dirY = sin(player.aimAngle)

        val stepSize = 18f
        val maxSteps = 28

        var currentBounces = 0
        var lastTileKey: String? = null

        while (currentBounces <= maxBounces && points.size < 50) {
            var segmentHit = false

            for (step in 0 until maxSteps) {
                val nextX = currX + dirX * stepSize
                val nextY = currY + dirY * stepSize

                val tile = terrain.getTileAtWorld(nextX, nextY)
                if (tile != null && tile.coverHeight != CoverHeight.NONE && !tile.isDisintegrated) {
                    val tileKey = "${tile.gridX}_${tile.gridY}"
                    if (tileKey != lastTileKey) {
                        points.add(Pair(nextX, nextY))

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
                points.add(Pair(currX, currY))
                break
            }
        }

        return points
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

        // Calculate recoil deflection and physical pushback kick
        val recoil = weaponSystem.calculateShotRecoil(
            weapon = weapon,
            currentAimAngleRad = p.aimAngle,
            stance = p.stance
        )

        val bulletColor = Color(weapon.damageType.colorHex)

        // Bullet velocity vector using deflected recoil aim angle
        val bulletSpeed = 650f
        val vx = cos(recoil.deflectedAngleRad) * bulletSpeed
        val vy = sin(recoil.deflectedAngleRad) * bulletSpeed

        val bullets = state.bullets.toMutableList()
        bullets.add(
            Bullet(
                id = "b_${now}_${Random.nextInt(1000)}",
                x = p.x + cos(recoil.deflectedAngleRad) * 20f,
                y = p.y + sin(recoil.deflectedAngleRad) * 20f,
                vx = vx,
                vy = vy,
                damage = weapon.effectiveDamage.toFloat(),
                isPlayerBullet = true,
                pierceCover = weapon.pierceCover,
                color = bulletColor,
                ricochetCount = 0,
                maxRicochets = p.maxRicochetsOverride ?: weapon.maxRicochets
            )
        )

        spawnMuzzleFlashLight(
            x = p.x + cos(recoil.deflectedAngleRad) * 20f,
            y = p.y + sin(recoil.deflectedAngleRad) * 20f,
            color = bulletColor
        )

        // Also trigger 3D projectile in WeaponSystem
        val targetX = p.x + cos(recoil.deflectedAngleRad) * 800f
        val targetY = p.y + sin(recoil.deflectedAngleRad) * 800f
        weaponSystem.fireWeapon(
            weapon = weapon,
            originX = p.x,
            originY = p.y,
            originZ = 1.5f,
            targetX = targetX,
            targetY = targetY,
            targetZ = 1.5f,
            isPlayerOwned = true,
            stance = p.stance
        )

        // Stealth Noise emission
        val noiseRadius = if (weapon.isSilenced) 60f else 380f

        _gameState.value = state.copy(
            bullets = bullets,
            player = p.copy(
                vx = p.vx + recoil.kickVelocityX * 0.3f,
                vy = p.vy + recoil.kickVelocityY * 0.3f,
                aimAngle = recoil.deflectedAngleRad,
                currentAmmo = newAmmo,
                stealthNoiseRadius = noiseRadius
            ),
            screenShakeMs = recoil.screenShakeMs
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

    fun snapPlayerToCover(): Boolean {
        val p = _gameState.value.player
        val gx = (p.x / terrain.tileSize).toInt()
        val gy = (p.y / terrain.tileSize).toInt()

        var bestTile: VoxelTile? = null
        var minDistance = Float.MAX_VALUE

        // Scan 3x3 neighbor grid for nearest destructible voxel obstacle
        for (dx in -1..1) {
            for (dy in -1..1) {
                if (dx == 0 && dy == 0) continue
                val tile = terrain.tiles.getOrNull(gx + dx)?.getOrNull(gy + dy)
                if (tile != null && tile.coverHeight != CoverHeight.NONE && !tile.isDisintegrated) {
                    val tileCenterX = (tile.gridX + 0.5f) * terrain.tileSize
                    val tileCenterY = (tile.gridY + 0.5f) * terrain.tileSize
                    val dist = sqrt((p.x - tileCenterX) * (p.x - tileCenterX) + (p.y - tileCenterY) * (p.y - tileCenterY))
                    if (dist < minDistance && dist <= terrain.tileSize * 1.8f) {
                        minDistance = dist
                        bestTile = tile
                    }
                }
            }
        }

        if (bestTile != null) {
            val tileCenterX = (bestTile.gridX + 0.5f) * terrain.tileSize
            val tileCenterY = (bestTile.gridY + 0.5f) * terrain.tileSize
            val angleToTile = atan2(tileCenterY - p.y, tileCenterX - p.x)

            // Snap player to offset edge of voxel obstacle
            val snapDist = terrain.tileSize * 0.72f
            val snappedX = (tileCenterX - cos(angleToTile) * snapDist).coerceIn(terrain.tileSize, (terrain.width - 1) * terrain.tileSize)
            val snappedY = (tileCenterY - sin(angleToTile) * snapDist).coerceIn(terrain.tileSize, (terrain.height - 1) * terrain.tileSize)

            _gameState.value = _gameState.value.copy(
                player = p.copy(
                    x = snappedX,
                    y = snappedY,
                    facingAngle = angleToTile, // Face towards cover
                    stance = if (bestTile.coverHeight == CoverHeight.HIGH) PlayerStance.STAND else PlayerStance.CROUCH,
                    isBehindCover = true,
                    coverTileX = bestTile.gridX,
                    coverTileY = bestTile.gridY,
                    coverHeight = bestTile.coverHeight
                )
            )

            // Spawn snap spark particle
            val particles = _gameState.value.particles.toMutableList()
            particles.add(
                Particle(
                    x = snappedX, y = snappedY, vx = 0f, vy = -15f,
                    color = Color(0xFF00F0FF), size = 12f,
                    type = ParticleType.HIT_NUMBER, text = "COVER SNAP!"
                )
            )
            _gameState.value = _gameState.value.copy(particles = particles)
            SoundFX.play(SoundFX.SoundType.RELOAD)
            return true
        }
        return false
    }

    fun toggleStance() {
        val p = _gameState.value.player
        // Try snapping to cover first if standing or crouching near voxel obstacle
        if (!p.isBehindCover && snapPlayerToCover()) {
            return
        }

        val nextStance = when (p.stance) {
            PlayerStance.STAND -> PlayerStance.CROUCH
            PlayerStance.CROUCH -> PlayerStance.PRONE
            PlayerStance.PRONE -> PlayerStance.STAND
        }
        _gameState.value = _gameState.value.copy(
            player = p.copy(
                stance = nextStance,
                isBehindCover = if (nextStance == PlayerStance.STAND && p.coverHeight == CoverHeight.LOW) false else p.isBehindCover
            )
        )
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

    private fun updatePlayerLogic(player: PlayerState, particles: MutableList<Particle>, deltaSec: Float, now: Long) {
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

        // Process Automatic Cover Snap system against adjacent voxel obstacles
        processCoverSnapForPlayer(player, particles, deltaSec)

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
                    val bulletAngle = atan2(b.vy, b.vx)
                    val tileKey = "${tile.gridX}_${tile.gridY}"
                    val canRicochet = b.ricochetCount < b.maxRicochets && b.lastHitTileKey != tileKey

                    // Damage voxel cover with mesh deformation (half damage if ricocheting off)
                    val damageToTile = if (canRicochet) b.damage * 0.45f else b.damage
                    val destroyed = terrain.applyDamageToTile(tile.gridX, tile.gridY, damageToTile, bulletAngle)
                    val impactX = b.x
                    val impactY = b.y
                    val isBarrel = tile.type == VoxelType.EXPLOSIVE_BARREL

                    spawnImpactLight(impactX, impactY, color = if (isBarrel) Color(0xFFFFB703) else Color(0xFF00F0FF), radius = 135f)
                    spawnDebrisParticles(
                        particles = particles,
                        x = impactX,
                        y = impactY,
                        count = if (destroyed) 22 else 12,
                        impactAngleRad = bulletAngle,
                        tileType = tile.type
                    )

                    if (destroyed) {
                        SoundFX.play(SoundFX.SoundType.EXPLOSION)
                        spawnExplosionLight(impactX, impactY, radius = if (isBarrel) 420f else 240f, intensity = 2.5f, color = if (isBarrel) Color(0xFFFF4500) else Color(0xFFFFA500))
                        iterator.remove()
                        continue
                    }

                    if (canRicochet) {
                        // Calculate surface reflection normal
                        val tileCenterX = (tile.gridX + 0.5f) * terrain.tileSize
                        val tileCenterY = (tile.gridY + 0.5f) * terrain.tileSize
                        val dx = b.x - tileCenterX
                        val dy = b.y - tileCenterY

                        val nx = if (abs(dx) > abs(dy)) (if (dx > 0) 1f else -1f) else 0f
                        val ny = if (abs(dx) <= abs(dy)) (if (dy > 0) 1f else -1f) else 0f

                        val dot = b.vx * nx + b.vy * ny
                        var rx = b.vx - 2f * dot * nx
                        var ry = b.vy - 2f * dot * ny

                        // Smart Target Seeking Ricochet: bend reflection toward nearest living enemy
                        var bestEnemyTarget: Enemy? = null
                        var bestEnemyDist = Float.MAX_VALUE
                        for (e in enemies) {
                            if (!e.isAlive) continue
                            val edist = hypot(e.x - impactX, e.y - impactY)
                            if (edist < 380f && edist < bestEnemyDist) {
                                bestEnemyDist = edist
                                bestEnemyTarget = e
                            }
                        }

                        if (bestEnemyTarget != null) {
                            val seekDx = bestEnemyTarget.x - impactX
                            val seekDy = bestEnemyTarget.y - impactY
                            val seekDist = sqrt(seekDx * seekDx + seekDy * seekDy).coerceAtLeast(1f)
                            val currentSpeed = sqrt(rx * rx + ry * ry)

                            rx = rx * 0.35f + (seekDx / seekDist * currentSpeed) * 0.65f
                            ry = ry * 0.35f + (seekDy / seekDist * currentSpeed) * 0.65f
                        }

                        b.vx = rx * 0.88f
                        b.vy = ry * 0.88f
                        b.x += nx * 6f
                        b.y += ny * 6f
                        b.ricochetCount++
                        b.lastHitTileKey = tileKey

                        SoundFX.play(SoundFX.SoundType.RICOCHET)

                        // Spawn ricochet spark effect
                        for (i in 0 until 5) {
                            val sparkAngle = atan2(ry, rx) + (Random.nextFloat() * 1.2f - 0.6f)
                            val speed = Random.nextFloat() * 150f + 50f
                            particles.add(
                                Particle(
                                    x = impactX,
                                    y = impactY,
                                    vx = cos(sparkAngle) * speed,
                                    vy = sin(sparkAngle) * speed,
                                    color = Color(0xFF00F0FF),
                                    size = Random.nextFloat() * 4f + 3f,
                                    life = 0.5f,
                                    maxLife = 0.5f,
                                    type = ParticleType.PLASMA_SPARK
                                )
                            )
                        }

                        // Floating text indicator
                        particles.add(
                            Particle(
                                x = impactX, y = impactY - 12f, vx = 0f, vy = -20f,
                                color = Color(0xFF00F0FF),
                                size = 12f,
                                type = ParticleType.HIT_NUMBER,
                                text = "RICOCHET!"
                            )
                        )
                    } else {
                        SoundFX.play(SoundFX.SoundType.HIT_WALL)
                        iterator.remove()
                        continue
                    }
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

                        spawnImpactLight(e.x, e.y, color = Color(0xFF00F0FF), radius = 120f)

                        iterator.remove()
                        break
                    }
                }
            } else {
                // Enemy bullet hits player
                val dist = sqrt((b.x - player.x) * (b.x - player.x) + (b.y - player.y) * (b.y - player.y))
                if (dist < 24f) {
                    var dmg = b.damage
                    var wasMitigatedByCover = false

                    if (player.isBehindCover) {
                        // Check if bullet came from front or rear (flank) relative to cover obstacle
                        val cx = player.coverTileX
                        val cy = player.coverTileY
                        if (cx != null && cy != null) {
                            val tileCenterX = (cx + 0.5f) * terrain.tileSize
                            val tileCenterY = (cy + 0.5f) * terrain.tileSize
                            val bulletAngle = atan2(b.vy, b.vx)
                            val coverAngle = atan2(tileCenterY - player.y, tileCenterX - player.x)
                            val angleDiff = abs(bulletAngle - coverAngle)

                            // If bullet is traveling towards cover face (front angle), cover absorbs damage!
                            if (angleDiff > Math.toRadians(80.0)) {
                                wasMitigatedByCover = true
                                // Voxel obstacle absorbs impact
                                val coverTile = terrain.tiles.getOrNull(cx)?.getOrNull(cy)
                                val destroyed = terrain.applyDamageToTile(cx, cy, b.damage, bulletAngle)
                                spawnDebrisParticles(
                                    particles = particles,
                                    x = tileCenterX,
                                    y = tileCenterY,
                                    count = if (destroyed) 20 else 10,
                                    impactAngleRad = bulletAngle,
                                    tileType = coverTile?.type
                                )
                                if (destroyed) {
                                    SoundFX.play(SoundFX.SoundType.EXPLOSION)
                                }

                                // Apply heavy damage mitigation to player based on cover height & stance
                                dmg *= when {
                                    player.stance == PlayerStance.PRONE -> 0.10f
                                    player.coverHeight == CoverHeight.HIGH -> 0.15f
                                    player.coverHeight == CoverHeight.LOW -> 0.38f
                                    else -> 0.50f
                                }
                            } else {
                                // Flanked! Bullet bypassed cover from behind
                                dmg *= 1.4f
                                particles.add(
                                    Particle(
                                        x = player.x, y = player.y - 15f, vx = 0f, vy = -20f,
                                        color = Color(0xFFEF4444), size = 14f,
                                        type = ParticleType.HIT_NUMBER, text = "FLANKED! 1.4x"
                                    )
                                )
                            }
                        } else {
                            // General proximity cover mitigation
                            dmg *= when (player.coverHeight) {
                                CoverHeight.HIGH -> 0.20f
                                CoverHeight.LOW -> 0.45f
                                else -> 1.0f
                            }
                        }
                    }

                    if (wasMitigatedByCover) {
                        particles.add(
                            Particle(
                                x = player.x, y = player.y - 10f, vx = 0f, vy = -15f,
                                color = Color(0xFF00F0FF), size = 12f,
                                type = ParticleType.HIT_NUMBER, text = "-${dmg.toInt()} (COVER)"
                            )
                        )
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
                spawnExplosionLight(
                    x = t.targetX,
                    y = t.targetY,
                    radius = 440f,
                    intensity = 2.8f,
                    color = if (t.gadget.type == GadgetType.EMP_MINE) Color(0xFF00F0FF) else Color(0xFFFF9900)
                )

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

                // Destroy/Deform adjacent Voxel tiles
                val gx = (t.targetX / terrain.tileSize).toInt()
                val gy = (t.targetY / terrain.tileSize).toInt()
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        val tx = gx + dx
                        val ty = gy + dy
                        if (tx in 0 until terrain.width && ty in 0 until terrain.height) {
                            val tile = terrain.tiles[tx][ty]
                            if (tile.coverHeight != CoverHeight.NONE && !tile.isDisintegrated) {
                                val tileWorldX = (tx + 0.5f) * terrain.tileSize
                                val tileWorldY = (ty + 0.5f) * terrain.tileSize
                                val blastAngle = atan2(tileWorldY - t.targetY, tileWorldX - t.targetX)
                                val destroyed = terrain.applyDamageToTile(tx, ty, 150f, blastAngle)
                                spawnDebrisParticles(
                                    particles = particles,
                                    x = tileWorldX,
                                    y = tileWorldY,
                                    count = if (destroyed) 16 else 8,
                                    impactAngleRad = blastAngle,
                                    tileType = tile.type
                                )
                            }
                        }
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
        val actions = enemyAI.updateEnemyAI(enemies, player, bullets, particles, now, deltaSec)
        for (flash in actions.muzzleFlashes) {
            spawnMuzzleFlashLight(flash.first, flash.second, color = Color(0xFFEF4444))
        }
        for (s in actions.soundsToPlay) {
            if (s == "laser_shot") {
                SoundFX.play(SoundFX.SoundType.LASER_SHOT)
            }
        }
    }

    private fun updateParticles(particles: MutableList<Particle>, deltaSec: Float) {
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.x += p.vx * deltaSec
            p.y += p.vy * deltaSec

            when (p.type) {
                ParticleType.DEBRIS_VOXEL -> {
                    p.vx *= 0.93f
                    p.vy *= 0.93f
                    p.rotation += p.vRot * deltaSec * 50f
                }
                ParticleType.PLASMA_SPARK -> {
                    p.vx *= 0.88f
                    p.vy *= 0.88f
                }
                ParticleType.SMOKE_NANO -> {
                    p.vx *= 0.96f
                    p.vy *= 0.96f
                    p.vy -= 12f * deltaSec
                    p.size += 14f * deltaSec
                }
                ParticleType.EXPLOSION_FLAME -> {
                    p.vx *= 0.90f
                    p.vy *= 0.90f
                    p.size += 8f * deltaSec
                }
                else -> {}
            }

            p.life -= deltaSec * (1.0f / p.maxLife)
            if (p.life <= 0f) {
                iterator.remove()
            }
        }

        // Decay voxel tile hit flash overlays
        for (x in 0 until terrain.width) {
            for (y in 0 until terrain.height) {
                val tile = terrain.tiles[x][y]
                if (tile.hitFlashTimer > 0f) {
                    tile.hitFlashTimer = (tile.hitFlashTimer - deltaSec * 3.5f).coerceAtLeast(0f)
                }
            }
        }
    }

    private fun spawnDebrisParticles(
        particles: MutableList<Particle>,
        x: Float,
        y: Float,
        count: Int = 14,
        impactAngleRad: Float? = null,
        tileType: VoxelType? = null
    ) {
        val rand = Random.Default
        val baseAngle = impactAngleRad ?: (rand.nextFloat() * 2f * Math.PI.toFloat())

        // 1. Spawning flying angular voxel debris chunks (DEBRIS_VOXEL)
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

        // 2. High-speed plasma impact sparks (PLASMA_SPARK)
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
                    color = if (rand.nextBoolean()) Color(0xFFF59E0B) else Color(0xFF00F0FF),
                    size = rand.nextFloat() * 3f + 2f,
                    life = 1.0f,
                    maxLife = 0.25f,
                    type = ParticleType.PLASMA_SPARK
                )
            )
        }

        // 3. Expanding particulate smoke/dust clouds (SMOKE_NANO)
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
    }

    private fun processCoverSnapForPlayer(player: PlayerState, particles: MutableList<Particle>, deltaSec: Float) {
        val charRadius = 18f
        val tileSize = terrain.tileSize
        val gx = (player.x / tileSize).toInt().coerceIn(0, terrain.width - 1)
        val gy = (player.y / tileSize).toInt().coerceIn(0, terrain.height - 1)

        val directions = listOf(
            Pair(1, 0),   // Right neighbor
            Pair(-1, 0),  // Left neighbor
            Pair(0, 1),   // Bottom neighbor
            Pair(0, -1)   // Top neighbor
        )

        var nearestObstacleTile: VoxelTile? = null
        var minFaceDist = Float.MAX_VALUE
        var snapNx = 0f
        var snapNy = 0f
        var targetSnapX = player.x
        var targetSnapY = player.y

        for ((dx, dy) in directions) {
            val nx = gx + dx
            val ny = gy + dy
            val tile = terrain.tiles.getOrNull(nx)?.getOrNull(ny)
            if (tile != null && tile.coverHeight != CoverHeight.NONE && !tile.isDisintegrated) {
                var faceDist = Float.MAX_VALUE
                var normalX = 0f
                var normalY = 0f
                var calcX = player.x
                var calcY = player.y

                if (dx == 1) { // Right neighbor obstacle
                    val faceX = nx * tileSize
                    faceDist = faceX - player.x
                    normalX = -1f
                    calcX = faceX - charRadius
                } else if (dx == -1) { // Left neighbor obstacle
                    val faceX = gx * tileSize
                    faceDist = player.x - faceX
                    normalX = 1f
                    calcX = faceX + charRadius
                } else if (dy == 1) { // Bottom neighbor obstacle
                    val faceY = ny * tileSize
                    faceDist = faceY - player.y
                    normalY = -1f
                    calcY = faceY - charRadius
                } else if (dy == -1) { // Top neighbor obstacle
                    val faceY = gy * tileSize
                    faceDist = player.y - faceY
                    normalY = 1f
                    calcY = faceY + charRadius
                }

                val pressingToward = (player.vx * (-normalX) + player.vy * (-normalY)) > 10f

                if (faceDist in -10f..36f && (pressingToward || player.isCoverSnapped)) {
                    if (faceDist < minFaceDist) {
                        minFaceDist = faceDist
                        nearestObstacleTile = tile
                        snapNx = normalX
                        snapNy = normalY
                        targetSnapX = calcX
                        targetSnapY = calcY
                    }
                }
            }
        }

        if (nearestObstacleTile != null) {
            val isPushingAway = (player.vx * snapNx + player.vy * snapNy) > 60f

            if (isPushingAway) {
                if (player.isCoverSnapped) {
                    player.isCoverSnapped = false
                    player.isBehindCover = false
                    player.coverTileX = null
                    player.coverTileY = null
                }
            } else {
                val wasSnapped = player.isCoverSnapped
                player.isCoverSnapped = true
                player.isBehindCover = true
                player.coverTileX = nearestObstacleTile.gridX
                player.coverTileY = nearestObstacleTile.gridY
                player.coverHeight = nearestObstacleTile.coverHeight
                player.coverSnapNormalX = snapNx
                player.coverSnapNormalY = snapNy

                if (snapNx != 0f) player.x = targetSnapX
                if (snapNy != 0f) player.y = targetSnapY

                if (nearestObstacleTile.coverHeight == CoverHeight.LOW) {
                    player.stance = PlayerStance.CROUCH
                } else if (nearestObstacleTile.coverHeight == CoverHeight.HIGH) {
                    player.stance = PlayerStance.STAND
                }

                player.coverAnimPulse = (player.coverAnimPulse + deltaSec * 8f) % (2f * Math.PI.toFloat())

                if (!wasSnapped) {
                    SoundFX.play(SoundFX.SoundType.HIT_SHIELD)
                    particles.add(
                        Particle(
                            x = player.x,
                            y = player.y - 28f,
                            vx = 0f,
                            vy = -30f,
                            color = Color(0xFF00F0FF),
                            size = 13f,
                            life = 1.0f,
                            maxLife = 0.8f,
                            type = ParticleType.HIT_NUMBER,
                            text = "COVER LOCKED!"
                        )
                    )
                    for (i in 0..5) {
                        particles.add(
                            Particle(
                                x = player.x + (Random.nextFloat() * 12f - 6f),
                                y = player.y + (Random.nextFloat() * 12f - 6f),
                                vx = snapNx * (Random.nextFloat() * 60f + 20f),
                                vy = snapNy * (Random.nextFloat() * 60f + 20f),
                                color = Color(0xFF00F0FF),
                                size = 3.5f,
                                life = 0.45f,
                                maxLife = 0.45f,
                                type = ParticleType.PLASMA_SPARK
                            )
                        )
                    }
                }
            }
        } else {
            if (player.isCoverSnapped) {
                player.isCoverSnapped = false
                player.isBehindCover = false
                player.coverTileX = null
                player.coverTileY = null
            } else {
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
            }
        }
    }

    private val transientDynamicLights = mutableListOf<DynamicLight>()

    fun spawnMuzzleFlashLight(x: Float, y: Float, color: Color = Color(0xFF00F0FF)) {
        transientDynamicLights.add(
            DynamicLight(
                x = x, y = y,
                color = color,
                radius = 160f,
                maxRadius = 160f,
                intensity = 1.8f,
                maxIntensity = 1.8f,
                life = 1.0f,
                decayRate = 12.0f,
                type = DynamicLightType.MUZZLE_FLASH
            )
        )
    }

    fun spawnImpactLight(x: Float, y: Float, color: Color = Color(0xFF00F0FF), radius: Float = 140f) {
        transientDynamicLights.add(
            DynamicLight(
                x = x, y = y,
                color = color,
                radius = radius,
                maxRadius = radius * 1.3f,
                intensity = 2.0f,
                maxIntensity = 2.0f,
                life = 1.0f,
                decayRate = 5.0f,
                expansionRate = 120f,
                type = DynamicLightType.IMPACT_FLASH
            )
        )
    }

    fun spawnExplosionLight(x: Float, y: Float, radius: Float = 380f, intensity: Float = 2.5f, color: Color = Color(0xFFFFB703)) {
        transientDynamicLights.add(
            DynamicLight(
                x = x, y = y,
                color = color,
                radius = 90f,
                maxRadius = radius,
                intensity = intensity,
                maxIntensity = intensity,
                life = 1.0f,
                decayRate = 1.6f,
                expansionRate = radius / 0.35f,
                type = DynamicLightType.EXPLOSION_BURST
            )
        )
    }

    private fun updateDynamicLights(
        bullets: List<Bullet>,
        player: PlayerState,
        enemies: List<Enemy>,
        deltaSec: Float
    ): List<DynamicLight> {
        val activeLights = mutableListOf<DynamicLight>()

        for (b in bullets) {
            val radius = if (b.damage > 50f) 180f else 130f
            activeLights.add(
                DynamicLight(
                    id = "b_light_${b.id}",
                    x = b.x,
                    y = b.y,
                    color = b.color,
                    radius = radius,
                    intensity = 1.3f,
                    life = 1.0f,
                    type = DynamicLightType.PROJECTILE_BULLET
                )
            )
        }

        val timeFactor = System.currentTimeMillis() / 250f
        val startGx = ((player.x - 700f) / terrain.tileSize).toInt().coerceIn(0, terrain.width - 1)
        val endGx = ((player.x + 700f) / terrain.tileSize).toInt().coerceIn(0, terrain.width - 1)
        val startGy = ((player.y - 700f) / terrain.tileSize).toInt().coerceIn(0, terrain.height - 1)
        val endGy = ((player.y + 700f) / terrain.tileSize).toInt().coerceIn(0, terrain.height - 1)

        for (gx in startGx..endGx) {
            for (gy in startGy..endGy) {
                val tile = terrain.tiles[gx][gy]
                if (tile.isDisintegrated) continue
                val tileX = (gx + 0.5f) * terrain.tileSize
                val tileY = (gy + 0.5f) * terrain.tileSize

                when (tile.type) {
                    VoxelType.EXPLOSIVE_BARREL -> {
                        val pulse = 0.6f + 0.25f * sin((timeFactor + gx * 3 + gy * 7).toDouble()).toFloat()
                        activeLights.add(
                            DynamicLight(
                                id = "barrel_${gx}_${gy}",
                                x = tileX, y = tileY,
                                color = Color(0xFFF59E0B),
                                radius = 95f,
                                intensity = pulse,
                                life = 1.0f,
                                type = DynamicLightType.ENVIRONMENTAL_EMITTER
                            )
                        )
                    }
                    VoxelType.ACID_POOL -> {
                        val pulse = 0.5f + 0.2f * sin((timeFactor * 0.8f + gx * 5 + gy * 2).toDouble()).toFloat()
                        activeLights.add(
                            DynamicLight(
                                id = "acid_${gx}_${gy}",
                                x = tileX, y = tileY,
                                color = Color(0xFF10B981),
                                radius = 110f,
                                intensity = pulse,
                                life = 1.0f,
                                type = DynamicLightType.ENVIRONMENTAL_EMITTER
                            )
                        )
                    }
                    VoxelType.ENERGY_BARRIER -> {
                        val pulse = 0.7f + 0.3f * sin((timeFactor * 1.5f + gx + gy).toDouble()).toFloat()
                        activeLights.add(
                            DynamicLight(
                                id = "barrier_${gx}_${gy}",
                                x = tileX, y = tileY,
                                color = Color(0xFF00F0FF),
                                radius = 120f,
                                intensity = pulse,
                                life = 1.0f,
                                type = DynamicLightType.ENVIRONMENTAL_EMITTER
                            )
                        )
                    }
                    VoxelType.OBJECTIVE_NODE -> {
                        val pulse = 0.8f + 0.35f * sin((timeFactor * 2f).toDouble()).toFloat()
                        activeLights.add(
                            DynamicLight(
                                id = "obj_${gx}_${gy}",
                                x = tileX, y = tileY,
                                color = Color(0xFFEC4899),
                                radius = 150f,
                                intensity = pulse,
                                life = 1.0f,
                                type = DynamicLightType.ENVIRONMENTAL_EMITTER
                            )
                        )
                    }
                    else -> {}
                }
            }
        }

        val iterator = transientDynamicLights.iterator()
        while (iterator.hasNext()) {
            val light = iterator.next()
            light.life -= deltaSec * light.decayRate
            light.radius = (light.radius + light.expansionRate * deltaSec).coerceAtMost(light.maxRadius)
            light.intensity = (light.maxIntensity * light.life).coerceAtLeast(0f)

            if (light.life <= 0f) {
                iterator.remove()
            } else {
                activeLights.add(light.copy())
            }
        }

        return activeLights
    }
}
