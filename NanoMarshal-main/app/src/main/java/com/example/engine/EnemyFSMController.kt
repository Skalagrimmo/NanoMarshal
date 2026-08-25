package com.example.engine

import androidx.compose.ui.graphics.Color
import com.example.data.model.AIState
import com.example.data.model.CoverHeight
import com.example.data.model.Enemy
import com.example.data.model.EnemyType
import com.example.data.model.Particle
import com.example.data.model.ParticleType
import com.example.data.model.PlayerState
import com.example.data.model.VoxelTile
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Proximity classification relative to player distance in world units.
 */
enum class ProximityCategory {
    CLOSE,  // < 150 units - High urgency, immediate flanking or cover reaction
    MID,    // 150..350 units - Tactical engagements, cover seeking, suppressed fire
    FAR     // > 350 units - Long-range vision/stalker tracking or patrol
}

/**
 * Snapshot of enemy perception evaluated per tick.
 */
data class PerceptionSnapshot(
    val distToPlayer: Float,
    val angleToPlayer: Float,
    val inVisionCone: Boolean,
    val hasLOS: Boolean,
    val canSeePlayer: Boolean,
    val canHearPlayer: Boolean,
    val playerIsBehindCover: Boolean,
    val healthRatio: Float,
    val proximity: ProximityCategory,
    val bestCoverCandidate: CoverSpotCandidate?
)

/**
 * Finite State Machine (FSM) AI Behavior Controller for enemy units.
 * Evaluates state transitions and drives unit behaviors based on player proximity, visibility,
 * health status, and tactical voxel map analysis.
 */
class EnemyFSMController(
    val worldManager: VoxelWorldManager,
    val terrain: VoxelTerrain
) {

    /**
     * Evaluates perception inputs (Proximity, Vision Cone, 3D Voxel Raycast LOS, Noise Hearing, Cover Opportunities).
     */
    fun evaluatePerception(
        enemy: Enemy,
        player: PlayerState,
        bestCover: CoverSpotCandidate?
    ): PerceptionSnapshot {
        val dx = player.x - enemy.x
        val dy = player.y - enemy.y
        val distToPlayer = sqrt(dx * dx + dy * dy)
        val angleToPlayer = atan2(dy, dx)

        // Vision Cone Check
        val angleDiff = abs(angleToPlayer - enemy.facingAngle)
        val inVisionCone = distToPlayer < enemy.visionRange && angleDiff < (enemy.visionAngleRad / 2f)

        // 3D Voxel Raycast Line-Of-Sight Check
        val hasLOS = if (inVisionCone) {
            hasLineOfSight(enemy.x, enemy.y, 1.2f, player.x, player.y, 1.2f)
        } else {
            false
        }

        val canSeePlayer = inVisionCone && hasLOS
        val canHearPlayer = distToPlayer < player.stealthNoiseRadius

        val proximity = when {
            distToPlayer < 150f -> ProximityCategory.CLOSE
            distToPlayer < 350f -> ProximityCategory.MID
            else -> ProximityCategory.FAR
        }

        val healthRatio = (enemy.health / enemy.maxHealth).coerceIn(0f, 1f)

        return PerceptionSnapshot(
            distToPlayer = distToPlayer,
            angleToPlayer = angleToPlayer,
            inVisionCone = inVisionCone,
            hasLOS = hasLOS,
            canSeePlayer = canSeePlayer,
            canHearPlayer = canHearPlayer,
            playerIsBehindCover = player.isBehindCover,
            healthRatio = healthRatio,
            proximity = proximity,
            bestCoverCandidate = bestCover
        )
    }

    /**
     * Finite State Machine Transition Evaluator.
     * Determines next [AIState] based on current state and [PerceptionSnapshot].
     */
    fun evaluateNextState(
        enemy: Enemy,
        perception: PerceptionSnapshot,
        deltaSec: Float
    ): AIState {
        // Stun or Dead states override state machine transitions
        if (enemy.stunTimerMs > 0) return AIState.STUNNED
        if (enemy.health <= 0f) return AIState.DEAD

        val currentState = enemy.state

        // Update Perception & Alert Meter
        if (perception.canSeePlayer || perception.canHearPlayer) {
            enemy.alertLevel = (enemy.alertLevel + deltaSec * 90f).coerceAtMost(100f)
        } else if (enemy.alertLevel > 0f) {
            enemy.alertLevel = (enemy.alertLevel - deltaSec * 15f).coerceAtLeast(0f)
        }

        return when (currentState) {
            AIState.PATROL -> {
                when {
                    perception.canSeePlayer -> {
                        // High visibility -> Seek Cover if low HP, else Flank or Engage
                        if (perception.healthRatio < 0.6f && perception.bestCoverCandidate != null) {
                            AIState.SEEKING_COVER
                        } else if (perception.playerIsBehindCover || enemy.type == EnemyType.FLANKER) {
                            AIState.FLANKING
                        } else {
                            AIState.ENGAGED
                        }
                    }
                    perception.canHearPlayer || enemy.alertLevel > 20f -> AIState.SUSPICIOUS
                    else -> AIState.PATROL
                }
            }

            AIState.SUSPICIOUS, AIState.INVESTIGATING -> {
                when {
                    perception.canSeePlayer -> {
                        if (perception.playerIsBehindCover) AIState.FLANKING else AIState.ENGAGED
                    }
                    enemy.alertLevel < 5f -> AIState.PATROL
                    else -> AIState.INVESTIGATING
                }
            }

            AIState.ENGAGED -> {
                when {
                    // Low Health Trigger -> Seek Cover or Retreat
                    perception.healthRatio < 0.35f -> {
                        if (perception.bestCoverCandidate != null) AIState.SEEKING_COVER else AIState.RETREAT
                    }
                    // Player behind cover -> Flank or Suppress
                    perception.playerIsBehindCover -> {
                        if (enemy.type == EnemyType.SHIELD_ENFORCER || Random.nextFloat() < 0.35f) {
                            AIState.SUPPRESSING
                        } else {
                            AIState.FLANKING
                        }
                    }
                    // Close Proximity -> Flank to avoid static gunfights
                    perception.proximity == ProximityCategory.CLOSE && enemy.type != EnemyType.SNIPER_STALKER -> {
                        AIState.FLANKING
                    }
                    // Lost Sight & Far -> Investigate
                    !perception.canSeePlayer && perception.proximity == ProximityCategory.FAR -> {
                        AIState.INVESTIGATING
                    }
                    else -> AIState.ENGAGED
                }
            }

            AIState.SEEKING_COVER -> {
                when {
                    enemy.isBehindCover -> AIState.ENGAGED
                    !perception.canSeePlayer && perception.proximity == ProximityCategory.FAR -> AIState.INVESTIGATING
                    else -> AIState.SEEKING_COVER
                }
            }

            AIState.FLANKING -> {
                when {
                    // Critical Health -> Seek Cover
                    perception.healthRatio < 0.25f && perception.bestCoverCandidate != null -> AIState.SEEKING_COVER
                    // Player left cover and is visible at close range -> Engaged
                    !perception.playerIsBehindCover && perception.canSeePlayer && perception.proximity == ProximityCategory.CLOSE -> AIState.ENGAGED
                    else -> AIState.FLANKING
                }
            }

            AIState.SUPPRESSING -> {
                when {
                    // Target cover destroyed or player exposed -> Transition to Flanking or Engaged
                    !perception.playerIsBehindCover -> AIState.ENGAGED
                    Random.nextFloat() < 0.015f -> AIState.FLANKING
                    else -> AIState.SUPPRESSING
                }
            }

            AIState.RETREAT -> {
                when {
                    perception.healthRatio > 0.5f -> AIState.ENGAGED
                    else -> AIState.RETREAT
                }
            }

            AIState.STUNNED, AIState.DEAD -> currentState
        }
    }

    /**
     * Checks 3D Voxel Raycast LOS against terrain and voxel blocks.
     */
    fun hasLineOfSight(
        startX: Float, startY: Float, startZ: Float,
        targetX: Float, targetY: Float, targetZ: Float
    ): Boolean {
        val dx = targetX - startX
        val dy = targetY - startY
        val dz = targetZ - startZ
        val dist = sqrt(dx * dx + dy * dy + dz * dz)
        if (dist < 10f) return true

        val hit = worldManager.raycast3D(
            startX = startX, startY = startY, startZ = startZ,
            dirX = dx, dirY = dy, dirZ = dz,
            maxDistance = (dist - 12f).coerceAtLeast(10f)
        )

        if (hit != null && hit.voxel.isSolid) {
            return false
        }

        // 2D Voxel Tile grid check
        val steps = (dist / (terrain.tileSize * 0.4f)).toInt().coerceIn(4, 60)
        val stepX = dx / steps
        val stepY = dy / steps

        var currX = startX
        var currY = startY

        for (i in 0 until steps) {
            currX += stepX
            currY += stepY
            val tile = terrain.getTileAtWorld(currX, currY)
            if (tile != null && !tile.isDisintegrated) {
                if (tile.coverHeight == CoverHeight.HIGH) {
                    return false
                }
            }
        }

        return true
    }
}
