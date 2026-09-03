package com.example.engine.behavior

import com.example.data.model.EnemyType
import com.example.data.model.PlayerMovementState

/**
 * Builds specialized tactical Behavior Trees for enemy archetypes
 * that detect player cover state and execute coordinated flanking maneuvers.
 */
object EnemyBehaviorTreeBuilder {

    /**
     * Builds and returns a complete Behavior Tree for the given [EnemyType].
     */
    fun buildTreeFor(enemyType: EnemyType): BTNode {
        return when (enemyType) {
            EnemyType.FLANKER -> buildFlankerTree()
            EnemyType.SHIELD_ENFORCER -> buildShieldEnforcerTree()
            EnemyType.GRUNT -> buildGruntTree()
            EnemyType.SNIPER_STALKER -> buildSniperTree()
            EnemyType.BOUNTY_BOSS -> buildBountyBossTree()
        }
    }

    /**
     * FLANKER: Highly agile stealth/flank specialist.
     * Prioritizes wide cover-skirting maneuvers, blind-side ambushes, and vault interception.
     */
    private fun buildFlankerTree(): BTNode {
        return BTSelector(
            name = "FlankerRoot",
            children = listOf(
                // 1. Critical Health Retreat
                BTCondition(
                    name = "FlankerLowHPCover",
                    predicate = { ctx -> ctx.enemy.health / ctx.enemy.maxHealth < 0.20f && ctx.bestCoverSpot != null },
                    child = SeekDefensiveCoverNode()
                ),

                // 2. Cover-Aware Flanking Pipeline
                BTSequence(
                    name = "FlankerTacticalSequence",
                    children = listOf(
                        DetectPlayerCoverNode(),
                        BTCondition(
                            name = "CanExecuteFlank",
                            predicate = { ctx ->
                                val p = ctx.player
                                val canPerceive = ctx.perception?.canSeePlayer == true || ctx.perception?.canHearPlayer == true || ctx.enemy.alertLevel > 20f
                                canPerceive && (p.isCoverSnapped || p.isBehindCover || p.movementState == PlayerMovementState.COVER_VAULTING || p.movementState == PlayerMovementState.COVER_TRAVERSING || p.movementState == PlayerMovementState.COVER_PEEKING || ctx.enemy.isFlankingPlayer)
                            },
                            child = BTSequence(
                                name = "FlankExecutionSubtree",
                                children = listOf(
                                    EvaluateFlankStrategyNode(),
                                    BTSelector(
                                        name = "FlankActionSelector",
                                        children = listOf(
                                            TacticalFlankAttackNode(), // Fires immediately if at flank angle or intercepting vault
                                            ExecuteFlankPathNode()     // Navigates along calculated flank path
                                        )
                                    )
                                )
                            )
                        )
                    )
                ),

                // 3. Direct Engagement if player is caught in the open
                BTCondition(
                    name = "FlankerDirectEngageCheck",
                    predicate = { ctx -> ctx.perception?.canSeePlayer == true },
                    child = DirectEngageAttackNode()
                ),

                // 4. Fallback Patrol / Investigate
                PatrolOrInvestigateNode()
            )
        )
    }

    /**
     * SHIELD_ENFORCER: Heavy frontline gunner.
     * Specializes in pinning down player cover with sustained suppression fire to disintegrate voxels
     * while squad members flank.
     */
    private fun buildShieldEnforcerTree(): BTNode {
        return BTSelector(
            name = "ShieldEnforcerRoot",
            children = listOf(
                // 1. Perception & Cover Analysis
                BTSequence(
                    name = "EnforcerTacticalSequence",
                    children = listOf(
                        DetectPlayerCoverNode(),
                        BTSelector(
                            name = "EnforcerActionSelector",
                            children = listOf(
                                // Cover Suppression Fire if player is turtled
                                BTCondition(
                                    name = "ShouldSuppressPlayerCover",
                                    predicate = { ctx ->
                                        val a = ctx.get<PlayerCoverAnalysis>("PLAYER_COVER_ANALYSIS")
                                        val canSee = ctx.perception?.canSeePlayer == true
                                        a != null && (a.isCoverSnapped || a.isBehindCover) && a.coverTileX != null && (canSee || ctx.enemy.alertLevel > 40f)
                                    },
                                    child = CoverSuppressionNode()
                                ),
                                // Direct Engagement if in open view
                                BTCondition(
                                    name = "EnforcerDirectEngageCheck",
                                    predicate = { ctx -> ctx.perception?.canSeePlayer == true },
                                    child = DirectEngageAttackNode()
                                )
                            )
                        )
                    )
                ),

                // 2. Patrol
                PatrolOrInvestigateNode()
            )
        )
    }

    /**
     * GRUNT: Adaptable infantry.
     * Analyzes player cover state: if player is vaulting or traversing, attempts cut-off/intercept.
     * If ally is already suppressing, grunts flank; otherwise they suppress or engage.
     */
    private fun buildGruntTree(): BTNode {
        return BTSelector(
            name = "GruntRoot",
            children = listOf(
                // 1. Seek Cover if low HP
                BTCondition(
                    name = "GruntLowHPCover",
                    predicate = { ctx -> ctx.enemy.health / ctx.enemy.maxHealth < 0.30f && ctx.bestCoverSpot != null },
                    child = SeekDefensiveCoverNode()
                ),

                // 2. Tactical Cover Pipeline
                BTSequence(
                    name = "GruntTacticalSeq",
                    children = listOf(
                        DetectPlayerCoverNode(),
                        BTSelector(
                            name = "GruntTacticalChoice",
                            children = listOf(
                                // If player is vaulting or traversing, prioritize immediate intercept
                                BTCondition(
                                    name = "GruntVaultOrTraverseIntercept",
                                    predicate = { ctx ->
                                        val a = ctx.get<PlayerCoverAnalysis>("PLAYER_COVER_ANALYSIS")
                                        a != null && (a.isVaulting || a.isTraversing)
                                    },
                                    child = BTSequence(
                                        name = "GruntInterceptSeq",
                                        children = listOf(
                                            EvaluateFlankStrategyNode(),
                                            BTSelector(
                                                name = "GruntInterceptAction",
                                                children = listOf(
                                                    TacticalFlankAttackNode(),
                                                    ExecuteFlankPathNode()
                                                )
                                            )
                                        )
                                    )
                                ),

                                // If player is behind cover, coordinate flank or suppression
                                BTCondition(
                                    name = "GruntFlankWhenCovered",
                                    predicate = { ctx ->
                                        val a = ctx.get<PlayerCoverAnalysis>("PLAYER_COVER_ANALYSIS")
                                        a != null && (a.isCoverSnapped || a.isBehindCover)
                                    },
                                    child = BTSelector(
                                        name = "GruntCoverReaction",
                                        children = listOf(
                                            // Flank if another ally is suppressing
                                            BTCondition(
                                                name = "FlankIfAllySuppressing",
                                                predicate = { ctx ->
                                                    ctx.allEnemies.any { it.id != ctx.enemy.id && it.state == com.example.data.model.AIState.SUPPRESSING }
                                                },
                                                child = BTSequence(
                                                    name = "GruntFlankSubtree",
                                                    children = listOf(
                                                        EvaluateFlankStrategyNode(),
                                                        BTSelector(
                                                            name = "GruntFlankMoveOrShoot",
                                                            children = listOf(
                                                                TacticalFlankAttackNode(),
                                                                ExecuteFlankPathNode()
                                                            )
                                                        )
                                                    )
                                                )
                                            ),
                                            // Otherwise, chip away at cover
                                            CoverSuppressionNode()
                                        )
                                    )
                                )
                            )
                        )
                    )
                ),

                // 3. Direct Engagement
                BTCondition(
                    name = "GruntDirectEngageCheck",
                    predicate = { ctx -> ctx.perception?.canSeePlayer == true },
                    child = DirectEngageAttackNode()
                ),

                // 4. Patrol
                PatrolOrInvestigateNode()
            )
        )
    }

    /**
     * SNIPER_STALKER: Long-range precision marksman.
     * Holds long angles and repositions to high-visibility vantage points when player seeks cover.
     */
    private fun buildSniperTree(): BTNode {
        return BTSelector(
            name = "SniperRoot",
            children = listOf(
                DetectPlayerCoverNode(),
                BTCondition(
                    name = "SniperRepositionWhenBlocked",
                    predicate = { ctx ->
                        val a = ctx.get<PlayerCoverAnalysis>("PLAYER_COVER_ANALYSIS")
                        a != null && a.isBehindCover && !a.isExposedFlankToEnemy
                    },
                    child = BTSequence(
                        name = "SniperRepositionSeq",
                        children = listOf(
                            EvaluateFlankStrategyNode(),
                            ExecuteFlankPathNode()
                        )
                    )
                ),
                BTCondition(
                    name = "SniperFireLOS",
                    predicate = { ctx -> ctx.perception?.canSeePlayer == true },
                    child = TacticalFlankAttackNode()
                ),
                PatrolOrInvestigateNode()
            )
        )
    }

    /**
     * BOUNTY_BOSS: Master tactician with multi-phase aggression.
     * Transitions fluidly between devastating cover suppression, rapid flank rushes, and vault punishments.
     */
    private fun buildBountyBossTree(): BTNode {
        return BTSelector(
            name = "BossRoot",
            children = listOf(
                DetectPlayerCoverNode(),
                // Vault Intercept
                BTCondition(
                    name = "BossVaultPunish",
                    predicate = { ctx ->
                        val a = ctx.get<PlayerCoverAnalysis>("PLAYER_COVER_ANALYSIS")
                        a != null && a.isVaulting
                    },
                    child = BTSequence(
                        name = "BossVaultSeq",
                        children = listOf(
                            EvaluateFlankStrategyNode(),
                            TacticalFlankAttackNode()
                        )
                    )
                ),
                // Heavy Flank Maneuver
                BTCondition(
                    name = "BossFlankManeuver",
                    predicate = { ctx ->
                        val a = ctx.get<PlayerCoverAnalysis>("PLAYER_COVER_ANALYSIS")
                        a != null && (a.isCoverSnapped || a.isBehindCover)
                    },
                    child = BTSequence(
                        name = "BossFlankSeq",
                        children = listOf(
                            EvaluateFlankStrategyNode(),
                            BTSelector(
                                name = "BossFlankMoveOrStrike",
                                children = listOf(
                                    TacticalFlankAttackNode(),
                                    ExecuteFlankPathNode(),
                                    CoverSuppressionNode()
                                )
                            )
                        )
                    )
                ),
                // Direct Fire
                BTCondition(
                    name = "BossDirectFire",
                    predicate = { ctx -> ctx.perception?.canSeePlayer == true },
                    child = DirectEngageAttackNode()
                ),
                PatrolOrInvestigateNode()
            )
        )
    }
}
