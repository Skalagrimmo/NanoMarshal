package com.example.engine.behavior

import androidx.compose.ui.graphics.Color
import com.example.data.model.AIState
import com.example.data.model.CoverHeight
import com.example.data.model.Enemy
import com.example.data.model.EnemyType
import com.example.data.model.FlankDirection
import com.example.data.model.FlankManeuverType
import com.example.data.model.Particle
import com.example.data.model.ParticleType
import com.example.data.model.PlayerMovementState
import com.example.data.model.PlayerState
import com.example.engine.Bullet
import com.example.engine.VoxelPathfinder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Real-time analytical evaluation of player's cover and tactical state.
 */
data class PlayerCoverAnalysis(
    val isCoverSnapped: Boolean,
    val isBehindCover: Boolean,
    val movementState: PlayerMovementState,
    val coverHeight: CoverHeight,
    val coverTileX: Int?,
    val coverTileY: Int?,
    val coverNormalX: Float,
    val coverNormalY: Float,
    val isExposedFlankToEnemy: Boolean,
    val angleDiffFromCoverDeg: Float,
    val isVaulting: Boolean,
    val isTraversing: Boolean,
    val isPeeking: Boolean,
    val playerAimAngle: Float,
    val isPlayerAimingAtEnemy: Boolean
)

/**
 * Perception & Cover State Analyzer Node:
 * Inspects player's cover snapping, stance, vaulting, traversal, and peeking vector,
 * and determines if this enemy currently has an exposed flanking angle.
 */
class DetectPlayerCoverNode(
    override val name: String = "DetectPlayerCover"
) : BTNode {
    override fun tick(ctx: BTContext): BTNodeStatus {
        val player = ctx.player
        val enemy = ctx.enemy
        val isSnapped = player.isCoverSnapped
        val isBehind = player.isBehindCover
        val mState = player.movementState
        val cHeight = player.coverHeight

        val dx = enemy.x - player.x
        val dy = enemy.y - player.y
        val angleToEnemy = atan2(dy, dx)

        // Cover defense normal vector (direction cover is protecting against)
        val coverThreatAngle: Float = when {
            isSnapped && (player.coverSnapNormalX != 0f || player.coverSnapNormalY != 0f) -> {
                atan2(player.coverSnapNormalY, player.coverSnapNormalX)
            }
            player.coverTileX != null && player.coverTileY != null -> {
                val cWorldX = (player.coverTileX!! + 0.5f) * ctx.terrain.tileSize
                val cWorldY = (player.coverTileY!! + 0.5f) * ctx.terrain.tileSize
                atan2(player.y - cWorldY, player.x - cWorldX)
            }
            else -> player.facingAngle
        }

        var angleDiffFromThreat = abs(angleToEnemy - coverThreatAngle)
        if (angleDiffFromThreat > PI) {
            angleDiffFromThreat = (2 * PI - angleDiffFromThreat).toFloat()
        }
        val angleDiffDeg = Math.toDegrees(angleDiffFromThreat.toDouble()).toFloat()

        // Entity is flanked if angle exceeds half protection arc (typically ~65..70 deg)
        val isExposedFlank = (isSnapped || isBehind) && angleDiffDeg > 68f

        // Check if player is aiming towards enemy (< 35 deg difference from player aim angle)
        var aimDiff = abs(angleToEnemy - player.aimAngle)
        if (aimDiff > PI) aimDiff = (2 * PI - aimDiff).toFloat()
        val isPlayerAimingAtEnemy = aimDiff < Math.toRadians(35.0)

        val analysis = PlayerCoverAnalysis(
            isCoverSnapped = isSnapped,
            isBehindCover = isBehind,
            movementState = mState,
            coverHeight = cHeight,
            coverTileX = player.coverTileX,
            coverTileY = player.coverTileY,
            coverNormalX = player.coverSnapNormalX,
            coverNormalY = player.coverSnapNormalY,
            isExposedFlankToEnemy = isExposedFlank,
            angleDiffFromCoverDeg = angleDiffDeg,
            isVaulting = mState == PlayerMovementState.COVER_VAULTING,
            isTraversing = mState == PlayerMovementState.COVER_TRAVERSING,
            isPeeking = mState == PlayerMovementState.COVER_PEEKING,
            playerAimAngle = player.aimAngle,
            isPlayerAimingAtEnemy = isPlayerAimingAtEnemy
        )

        ctx.set("PLAYER_COVER_ANALYSIS", analysis)
        enemy.isCoverFlanked = isExposedFlank

        return BTNodeStatus.SUCCESS
    }
}

/**
 * Evaluates and plans dynamic flanking maneuvers around player cover:
 * - If vaulting: triggers INTERCEPT_VAULT
 * - If traversing: triggers CUT_OFF_CORNER
 * - If peeking: triggers BLIND_SIDE_FLANK
 * - If high cover snapped: triggers WIDE_ARC_FLANK
 * - Balances left/right pincer movements with squad allies.
 */
class EvaluateFlankStrategyNode(
    override val name: String = "EvaluateFlankStrategy"
) : BTNode {
    override fun tick(ctx: BTContext): BTNodeStatus {
        val analysis = ctx.get<PlayerCoverAnalysis>("PLAYER_COVER_ANALYSIS") ?: return BTNodeStatus.FAILURE
        val enemy = ctx.enemy
        val player = ctx.player
        val terrain = ctx.terrain

        // Calculate tactical flank waypoint adapting to player cover state and squad
        val flankTarget = VoxelPathfinder.calculateTacticalFlankTarget(
            terrain = terrain,
            enemy = enemy,
            player = player,
            allEnemies = ctx.allEnemies
        )

        enemy.flankDirection = flankTarget.direction
        enemy.flankManeuverType = flankTarget.maneuverType
        enemy.isFlankingPlayer = true
        enemy.state = AIState.FLANKING

        // Visual Tactical Maneuver Label
        enemy.tacticalManeuverLabel = when (flankTarget.maneuverType) {
            FlankManeuverType.INTERCEPT_VAULT -> "VAULT-INTERCEPT"
            FlankManeuverType.CUT_OFF_CORNER -> "CUT-OFF"
            FlankManeuverType.BLIND_SIDE_FLANK -> "BLIND-AMBUSH"
            FlankManeuverType.WIDE_ARC_FLANK -> if (flankTarget.direction == FlankDirection.LEFT) "FLANK-L" else "FLANK-R"
            FlankManeuverType.TIGHT_COVER_FLANK -> if (flankTarget.direction == FlankDirection.LEFT) "FLANK-L" else "FLANK-R"
            FlankManeuverType.SUPPRESS_AND_CHIP -> "PIN/SUPPRESS"
            FlankManeuverType.ENCIRCLE -> "ENCIRCLE"
            FlankManeuverType.NONE -> "FLANK"
        }

        val egx = (enemy.x / terrain.tileSize).toInt()
        val egy = (enemy.y / terrain.tileSize).toInt()

        // Replan path if expired or unassigned
        enemy.pathUpdateTimerMs += (ctx.deltaSec * 1000).toLong()
        if (enemy.pathUpdateTimerMs > 450 || enemy.activePath.isEmpty() || enemy.activePathIndex >= enemy.activePath.size) {
            enemy.pathUpdateTimerMs = 0
            enemy.activePath = VoxelPathfinder.findPath(
                terrain = terrain,
                startGx = egx,
                startGy = egy,
                targetGx = flankTarget.targetGx,
                targetGy = flankTarget.targetGy,
                isFlanking = true,
                playerState = player
            )
            enemy.activePathIndex = 0
        }

        ctx.set("TACTICAL_FLANK_TARGET", flankTarget)
        return BTNodeStatus.SUCCESS
    }
}

/**
 * Steers the enemy along the calculated tactical flanking path.
 */
class ExecuteFlankPathNode(
    override val name: String = "ExecuteFlankPath"
) : BTNode {
    override fun tick(ctx: BTContext): BTNodeStatus {
        val enemy = ctx.enemy
        val deltaSec = ctx.deltaSec

        if (enemy.activePath.isEmpty() || enemy.activePathIndex >= enemy.activePath.size) {
            return BTNodeStatus.SUCCESS // Reached destination
        }

        val target = enemy.activePath[enemy.activePathIndex]
        val dx = target.first - enemy.x
        val dy = target.second - enemy.y
        val dist = sqrt(dx * dx + dy * dy)

        // Agile movement speed boost when flanking
        val speedMult = when (enemy.type) {
            EnemyType.FLANKER -> 1.25f
            EnemyType.BOUNTY_BOSS -> 1.15f
            else -> 1.05f
        }

        if (dist < 10f) {
            enemy.activePathIndex++
            if (enemy.activePathIndex >= enemy.activePath.size) {
                return BTNodeStatus.SUCCESS
            }
        } else {
            enemy.facingAngle = atan2(dy, dx)
            enemy.x += cos(enemy.facingAngle) * enemy.moveSpeed * 15f * speedMult * deltaSec
            enemy.y += sin(enemy.facingAngle) * enemy.moveSpeed * 15f * speedMult * deltaSec
        }

        return BTNodeStatus.RUNNING
    }
}

/**
 * Fires high-impact flanking ambush bursts at player from unprotected angles.
 * Triggers instant opportunistic fire when player is vaulting or exposed.
 */
class TacticalFlankAttackNode(
    override val name: String = "TacticalFlankAttack"
) : BTNode {
    override fun tick(ctx: BTContext): BTNodeStatus {
        val analysis = ctx.get<PlayerCoverAnalysis>("PLAYER_COVER_ANALYSIS") ?: return BTNodeStatus.FAILURE
        val enemy = ctx.enemy
        val player = ctx.player
        val now = ctx.now

        val dx = player.x - enemy.x
        val dy = player.y - enemy.y
        val angleToPlayer = atan2(dy, dx)
        enemy.facingAngle = angleToPlayer

        val isVaultVulnerable = analysis.isVaulting
        val isFlankExposed = analysis.isExposedFlankToEnemy

        // Flank attack trigger: if angle is exposed OR player is vaulting OR peeking away
        if (!isFlankExposed && !isVaultVulnerable && analysis.isBehindCover) {
            return BTNodeStatus.FAILURE // Still shielded by cover, don't waste flank burst
        }

        val baseCooldown = when (enemy.type) {
            EnemyType.FLANKER -> 720L
            EnemyType.BOUNTY_BOSS -> 600L
            EnemyType.SNIPER_STALKER -> 1800L
            else -> 950L
        }

        // Instant reaction if player is vaulting over cover
        val effectiveCooldown = if (isVaultVulnerable) 300L else baseCooldown

        if (now - enemy.shootCooldownMs > effectiveCooldown) {
            enemy.shootCooldownMs = now

            // Flank critical damage bonus (+30%)
            val damageMultiplier = if (isFlankExposed) 1.30f else 1.10f
            val bulletColor = when (enemy.type) {
                EnemyType.FLANKER -> Color(0xFFF43F5E) // Hot Rose Flanker Plasma
                EnemyType.SNIPER_STALKER -> Color(0xFFF59E0B) // Amber Stalker
                EnemyType.BOUNTY_BOSS -> Color(0xFFA855F7) // Purple Boss
                else -> Color(0xFFEF4444)
            }

            val bullet = Bullet(
                id = "flank_${now}_${Random.nextInt(1000)}",
                x = enemy.x,
                y = enemy.y,
                vx = cos(angleToPlayer) * 540f,
                vy = sin(angleToPlayer) * 540f,
                damage = enemy.weaponDamage * damageMultiplier,
                isPlayerBullet = false,
                color = bulletColor
            )

            ctx.bullets.add(bullet)
            ctx.spawnedBullets.add(bullet)
            ctx.soundList.add("laser_shot")
            ctx.muzzleFlashes.add(Pair(enemy.x, enemy.y))

            // Flank muzzle burst particles
            for (i in 0 until 3) {
                ctx.particles.add(
                    Particle(
                        x = enemy.x + cos(angleToPlayer) * 12f,
                        y = enemy.y + sin(angleToPlayer) * 12f,
                        vx = cos(angleToPlayer + (Random.nextFloat() * 0.4f - 0.2f)) * (Random.nextFloat() * 80f + 40f),
                        vy = sin(angleToPlayer + (Random.nextFloat() * 0.4f - 0.2f)) * (Random.nextFloat() * 80f + 40f),
                        color = bulletColor,
                        size = Random.nextFloat() * 4f + 2f,
                        life = 0.3f,
                        maxLife = 0.3f,
                        type = ParticleType.PLASMA_SPARK
                    )
                )
            }

            return BTNodeStatus.SUCCESS
        }

        return BTNodeStatus.RUNNING
    }
}

/**
 * Pours heavy suppression fire into the player's cover voxel tile:
 * - Damages the voxel obstacle directly (erodes block HP and disintegrates it).
 * - Generates impact spark debris.
 * - Pins the player down while teammates flank around the sides.
 */
class CoverSuppressionNode(
    override val name: String = "CoverSuppression"
) : BTNode {
    override fun tick(ctx: BTContext): BTNodeStatus {
        val analysis = ctx.get<PlayerCoverAnalysis>("PLAYER_COVER_ANALYSIS") ?: return BTNodeStatus.FAILURE
        val coverGx = analysis.coverTileX ?: return BTNodeStatus.FAILURE
        val coverGy = analysis.coverTileY ?: return BTNodeStatus.FAILURE
        val enemy = ctx.enemy
        val now = ctx.now

        val coverWorldX = (coverGx + 0.5f) * ctx.terrain.tileSize
        val coverWorldY = (coverGy + 0.5f) * ctx.terrain.tileSize

        val angleToCover = atan2(coverWorldY - enemy.y, coverWorldX - enemy.x)
        enemy.facingAngle = angleToCover
        enemy.state = AIState.SUPPRESSING
        enemy.tacticalManeuverLabel = "PIN/SUPPRESS"
        enemy.suppressionTargetGx = coverGx
        enemy.suppressionTargetGy = coverGy

        if (now - enemy.shootCooldownMs > 420L) {
            enemy.shootCooldownMs = now

            // Spread shot focused on cover tile
            val spread = (Random.nextFloat() * 0.12f - 0.06f)
            val bullet = Bullet(
                id = "sup_${now}_${Random.nextInt(1000)}",
                x = enemy.x,
                y = enemy.y,
                vx = cos(angleToCover + spread) * 480f,
                vy = sin(angleToCover + spread) * 480f,
                damage = enemy.weaponDamage * 1.15f,
                isPlayerBullet = false,
                color = Color(0xFFF59E0B) // Hazard Amber suppression fire
            )

            ctx.bullets.add(bullet)
            ctx.spawnedBullets.add(bullet)
            ctx.soundList.add("laser_shot")
            ctx.muzzleFlashes.add(Pair(enemy.x, enemy.y))

            // Erode the cover block directly!
            ctx.terrain.applyDamageToTile(coverGx, coverGy, 10f)

            // Spawn chipping debris particles at cover position
            for (p in 0 until 2) {
                ctx.particles.add(
                    Particle(
                        x = coverWorldX + (Random.nextFloat() * 16f - 8f),
                        y = coverWorldY + (Random.nextFloat() * 16f - 8f),
                        vx = (Random.nextFloat() * 60f - 30f),
                        vy = (Random.nextFloat() * 60f - 30f),
                        color = Color(0xFFE2E8F0),
                        size = Random.nextFloat() * 5f + 2f,
                        life = 0.5f,
                        maxLife = 0.5f,
                        type = ParticleType.DEBRIS_VOXEL
                    )
                )
            }
        }

        return BTNodeStatus.RUNNING
    }
}

/**
 * Standard frontal engagement when player is not in cover or when direct LOS is open.
 */
class DirectEngageAttackNode(
    override val name: String = "DirectEngageAttack"
) : BTNode {
    override fun tick(ctx: BTContext): BTNodeStatus {
        val enemy = ctx.enemy
        val player = ctx.player
        val now = ctx.now

        val dx = player.x - enemy.x
        val dy = player.y - enemy.y
        val angleToPlayer = atan2(dy, dx)
        enemy.facingAngle = angleToPlayer
        enemy.state = AIState.ENGAGED
        enemy.tacticalManeuverLabel = "ENGAGE"

        val cooldown = when (enemy.type) {
            EnemyType.SNIPER_STALKER -> 2000L
            EnemyType.FLANKER -> 850L
            EnemyType.BOUNTY_BOSS -> 650L
            else -> 1100L
        }

        if (now - enemy.shootCooldownMs > cooldown) {
            enemy.shootCooldownMs = now
            val bullet = Bullet(
                id = "eng_${now}_${Random.nextInt(1000)}",
                x = enemy.x,
                y = enemy.y,
                vx = cos(angleToPlayer) * 460f,
                vy = sin(angleToPlayer) * 460f,
                damage = enemy.weaponDamage,
                isPlayerBullet = false,
                color = Color(0xFFEF4444)
            )

            ctx.bullets.add(bullet)
            ctx.spawnedBullets.add(bullet)
            ctx.soundList.add("laser_shot")
            ctx.muzzleFlashes.add(Pair(enemy.x, enemy.y))
            return BTNodeStatus.SUCCESS
        }

        return BTNodeStatus.RUNNING
    }
}

/**
 * Retreats to safe defensive voxel cover when enemy health is low.
 */
class SeekDefensiveCoverNode(
    override val name: String = "SeekDefensiveCover"
) : BTNode {
    override fun tick(ctx: BTContext): BTNodeStatus {
        val enemy = ctx.enemy
        val bestCover = ctx.bestCoverSpot ?: return BTNodeStatus.FAILURE

        enemy.state = AIState.SEEKING_COVER
        enemy.tacticalManeuverLabel = "COVER"
        enemy.targetCoverX = bestCover.gridX
        enemy.targetCoverY = bestCover.gridY

        val egx = (enemy.x / ctx.terrain.tileSize).toInt()
        val egy = (enemy.y / ctx.terrain.tileSize).toInt()

        enemy.pathUpdateTimerMs += (ctx.deltaSec * 1000).toLong()
        if (enemy.pathUpdateTimerMs > 750 || enemy.activePath.isEmpty()) {
            enemy.pathUpdateTimerMs = 0
            enemy.activePath = VoxelPathfinder.findPath(
                terrain = ctx.terrain,
                startGx = egx,
                startGy = egy,
                targetGx = bestCover.gridX,
                targetGy = bestCover.gridY,
                isFlanking = false
            )
            enemy.activePathIndex = 0
        }

        if (enemy.activePath.isNotEmpty() && enemy.activePathIndex < enemy.activePath.size) {
            val target = enemy.activePath[enemy.activePathIndex]
            val dx = target.first - enemy.x
            val dy = target.second - enemy.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < 10f) {
                enemy.activePathIndex++
                if (enemy.activePathIndex >= enemy.activePath.size) {
                    enemy.isBehindCover = true
                    return BTNodeStatus.SUCCESS
                }
            } else {
                enemy.facingAngle = atan2(dy, dx)
                enemy.x += cos(enemy.facingAngle) * enemy.moveSpeed * 14f * ctx.deltaSec
                enemy.y += sin(enemy.facingAngle) * enemy.moveSpeed * 14f * ctx.deltaSec
            }
        }

        return BTNodeStatus.RUNNING
    }
}

/**
 * Default patrol waypoint cycling or investigating noise alerts.
 */
class PatrolOrInvestigateNode(
    override val name: String = "PatrolOrInvestigate"
) : BTNode {
    override fun tick(ctx: BTContext): BTNodeStatus {
        val enemy = ctx.enemy
        val deltaSec = ctx.deltaSec

        if (enemy.alertLevel > 15f && enemy.lastKnownPlayerX != null && enemy.lastKnownPlayerY != null) {
            enemy.state = AIState.INVESTIGATING
            enemy.tacticalManeuverLabel = "ALERT"
            val dx = enemy.lastKnownPlayerX!! - enemy.x
            val dy = enemy.lastKnownPlayerY!! - enemy.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < 18f) {
                enemy.alertLevel = 0f
                enemy.lastKnownPlayerX = null
                enemy.lastKnownPlayerY = null
                enemy.state = AIState.PATROL
            } else {
                enemy.facingAngle = atan2(dy, dx)
                enemy.x += cos(enemy.facingAngle) * enemy.moveSpeed * 12f * deltaSec
                enemy.y += sin(enemy.facingAngle) * enemy.moveSpeed * 12f * deltaSec
            }
            return BTNodeStatus.RUNNING
        }

        // Standard Patrol
        enemy.state = AIState.PATROL
        enemy.tacticalManeuverLabel = null
        if (enemy.patrolWaypoints.isNotEmpty()) {
            val target = enemy.patrolWaypoints[enemy.currentWaypointIndex]
            val dx = target.first - enemy.x
            val dy = target.second - enemy.y
            val dist = sqrt(dx * dx + dy * dy)
            if (dist < 12f) {
                enemy.currentWaypointIndex = (enemy.currentWaypointIndex + 1) % enemy.patrolWaypoints.size
            } else {
                enemy.facingAngle = atan2(dy, dx)
                enemy.x += cos(enemy.facingAngle) * enemy.moveSpeed * 11f * deltaSec
                enemy.y += sin(enemy.facingAngle) * enemy.moveSpeed * 11f * deltaSec
            }
        } else {
            enemy.facingAngle += (sin(ctx.now * 0.002) * 0.015).toFloat()
        }

        return BTNodeStatus.SUCCESS
    }
}
