package com.example.engine.behavior

import com.example.data.model.Enemy
import com.example.data.model.Particle
import com.example.data.model.PlayerState
import com.example.engine.Bullet
import com.example.engine.CoverSpotCandidate
import com.example.engine.CoverSystem
import com.example.engine.PerceptionSnapshot
import com.example.engine.VoxelTerrain
import com.example.engine.VoxelWorldManager

/**
 * Execution status for Behavior Tree nodes.
 */
enum class BTNodeStatus {
    SUCCESS,
    FAILURE,
    RUNNING
}

/**
 * Shared context passed to behavior tree nodes on each tick.
 */
data class BTContext(
    val enemy: Enemy,
    val player: PlayerState,
    val allEnemies: List<Enemy>,
    val terrain: VoxelTerrain,
    val worldManager: VoxelWorldManager,
    val coverSystem: CoverSystem,
    val bullets: MutableList<Bullet>,
    val spawnedBullets: MutableList<Bullet>,
    val particles: MutableList<Particle>,
    val soundList: MutableList<String>,
    val muzzleFlashes: MutableList<Pair<Float, Float>>,
    val now: Long,
    val deltaSec: Float,
    var perception: PerceptionSnapshot? = null,
    var bestCoverSpot: CoverSpotCandidate? = null,
    val blackboard: MutableMap<String, Any> = mutableMapOf()
) {
    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = blackboard[key] as? T

    fun set(key: String, value: Any) {
        blackboard[key] = value
    }

    fun has(key: String): Boolean = blackboard.containsKey(key)
}

/**
 * Base interface for all Behavior Tree nodes.
 */
interface BTNode {
    val name: String
    fun tick(ctx: BTContext): BTNodeStatus
}

/**
 * Selector (Fallback): Executes children in order until one returns SUCCESS or RUNNING.
 * Returns FAILURE if all children fail.
 */
class BTSelector(
    override val name: String,
    val children: List<BTNode>
) : BTNode {
    override fun tick(ctx: BTContext): BTNodeStatus {
        for (child in children) {
            val status = child.tick(ctx)
            if (status != BTNodeStatus.FAILURE) {
                return status
            }
        }
        return BTNodeStatus.FAILURE
    }
}

/**
 * Sequence: Executes children in order until one returns FAILURE or RUNNING.
 * Returns SUCCESS only if all children succeed.
 */
class BTSequence(
    override val name: String,
    val children: List<BTNode>
) : BTNode {
    override fun tick(ctx: BTContext): BTNodeStatus {
        for (child in children) {
            val status = child.tick(ctx)
            if (status != BTNodeStatus.SUCCESS) {
                return status
            }
        }
        return BTNodeStatus.SUCCESS
    }
}

/**
 * Condition Decorator: Evaluates a predicate before executing child node.
 */
class BTCondition(
    override val name: String,
    val predicate: (BTContext) -> Boolean,
    val child: BTNode
) : BTNode {
    override fun tick(ctx: BTContext): BTNodeStatus {
        return if (predicate(ctx)) {
            child.tick(ctx)
        } else {
            BTNodeStatus.FAILURE
        }
    }
}

/**
 * Inverter Decorator: Inverts SUCCESS and FAILURE, preserves RUNNING.
 */
class BTInverter(
    override val name: String = "Inverter",
    val child: BTNode
) : BTNode {
    override fun tick(ctx: BTContext): BTNodeStatus {
        return when (child.tick(ctx)) {
            BTNodeStatus.SUCCESS -> BTNodeStatus.FAILURE
            BTNodeStatus.FAILURE -> BTNodeStatus.SUCCESS
            BTNodeStatus.RUNNING -> BTNodeStatus.RUNNING
        }
    }
}

/**
 * Cooldown Decorator: Enforces minimum time elapsed between child executions.
 */
class BTCooldown(
    override val name: String = "Cooldown",
    val cooldownMs: Long,
    val child: BTNode
) : BTNode {
    private var lastRunMs: Long = 0L

    override fun tick(ctx: BTContext): BTNodeStatus {
        if (ctx.now - lastRunMs < cooldownMs) {
            return BTNodeStatus.FAILURE
        }
        val status = child.tick(ctx)
        if (status != BTNodeStatus.FAILURE) {
            lastRunMs = ctx.now
        }
        return status
    }
}
