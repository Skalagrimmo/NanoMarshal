package com.example.engine

import com.example.data.model.*
import kotlin.math.hypot

data class ObjectiveUpdateResult(
    val objectives: List<MissionObjective>,
    val activeToast: String? = null,
    val isAllPrimaryCompleted: Boolean = false,
    val isAnyPrimaryFailed: Boolean = false
)

/**
 * ObjectiveManager tracks real-time mission goals, evaluates progress triggers
 * (terminal defense countdown, hostile clearance, bounty termination, core sabotage),
 * updates status states, and signals tactical HUD overlays.
 */
class ObjectiveManager {

    private val _objectives = mutableListOf<MissionObjective>()
    val objectives: List<MissionObjective> get() = _objectives.toList()

    private var activeToastMessage: String? = null
    private var toastTimerMs: Long = 0

    fun initializeForMission(mission: Mission, terrain: VoxelTerrain) {
        _objectives.clear()
        val objX = terrain.objectivePointX
        val objY = terrain.objectivePointY

        when (mission.id) {
            DefaultMissions.MISSION_1.id -> {
                // Operation: Neon Outpost
                _objectives.add(
                    MissionObjective(
                        id = "obj_m1_defend",
                        title = "Defend Data Terminal",
                        description = "Hold position inside the terminal grid perimeter for 20s to extract syndicate data.",
                        category = ObjectiveCategory.DEFEND_TERMINAL,
                        isPrimary = true,
                        status = ObjectiveStatus.IN_PROGRESS,
                        currentProgress = 0,
                        requiredProgress = 20,
                        targetWorldX = objX,
                        targetWorldY = objY,
                        targetRadiusWorld = 160f,
                        timerRemainingSec = 20f,
                        maxTimerSec = 20f,
                        terminalHp = 250f,
                        maxTerminalHp = 250f,
                        rewardBonusCredits = 400
                    )
                )
                _objectives.add(
                    MissionObjective(
                        id = "obj_m1_boss",
                        title = "Eliminate Warlord Jax",
                        description = "Neutralize Syndicate Warlord Jax 'The Neon' Vex.",
                        category = ObjectiveCategory.ELIMINATE_BOUNTY,
                        isPrimary = true,
                        status = ObjectiveStatus.IN_PROGRESS,
                        currentProgress = 0,
                        requiredProgress = 1,
                        targetWorldX = objX - 40f,
                        targetWorldY = objY - 40f,
                        rewardBonusCredits = 500
                    )
                )
                _objectives.add(
                    MissionObjective(
                        id = "obj_m1_clear",
                        title = "Clear Sector Guards",
                        description = "Eliminate 5 syndicate perimeter sentries.",
                        category = ObjectiveCategory.CLEAR_SECTOR,
                        isPrimary = false,
                        status = ObjectiveStatus.IN_PROGRESS,
                        currentProgress = 0,
                        requiredProgress = 5,
                        rewardBonusCredits = 250
                    )
                )
            }

            DefaultMissions.MISSION_2.id -> {
                // Sabotage: Core Breach
                _objectives.add(
                    MissionObjective(
                        id = "obj_m2_sabotage",
                        title = "Sabotage Power Core",
                        description = "Destroy or overload the central nanite power core terminal.",
                        category = ObjectiveCategory.SABOTAGE_POWER_CORE,
                        isPrimary = true,
                        status = ObjectiveStatus.IN_PROGRESS,
                        currentProgress = 0,
                        requiredProgress = 1,
                        targetWorldX = objX,
                        targetWorldY = objY,
                        terminalHp = 300f,
                        maxTerminalHp = 300f,
                        rewardBonusCredits = 600
                    )
                )
                _objectives.add(
                    MissionObjective(
                        id = "obj_m2_defend",
                        title = "Defend Core Overload",
                        description = "Hold defense perimeter while nanite core undergoes thermal overload (25s).",
                        category = ObjectiveCategory.DEFEND_TERMINAL,
                        isPrimary = true,
                        status = ObjectiveStatus.NOT_STARTED,
                        currentProgress = 0,
                        requiredProgress = 25,
                        targetWorldX = objX,
                        targetWorldY = objY,
                        targetRadiusWorld = 180f,
                        timerRemainingSec = 25f,
                        maxTimerSec = 25f,
                        terminalHp = 300f,
                        maxTerminalHp = 300f,
                        rewardBonusCredits = 800
                    )
                )
                _objectives.add(
                    MissionObjective(
                        id = "obj_m2_boss",
                        title = "Eliminate Overseer Karr",
                        description = "Eliminate Overseer Karr before core extraction.",
                        category = ObjectiveCategory.ELIMINATE_BOUNTY,
                        isPrimary = false,
                        status = ObjectiveStatus.IN_PROGRESS,
                        currentProgress = 0,
                        requiredProgress = 1,
                        rewardBonusCredits = 450
                    )
                )
            }

            DefaultMissions.MISSION_3.id -> {
                // Ghost Infiltration
                _objectives.add(
                    MissionObjective(
                        id = "obj_m3_defend",
                        title = "Hack Cyber Terminal",
                        description = "Maintain terminal link for 30 seconds without letting guards compromise uplink.",
                        category = ObjectiveCategory.DEFEND_TERMINAL,
                        isPrimary = true,
                        status = ObjectiveStatus.IN_PROGRESS,
                        currentProgress = 0,
                        requiredProgress = 30,
                        targetWorldX = objX,
                        targetWorldY = objY,
                        targetRadiusWorld = 170f,
                        timerRemainingSec = 30f,
                        maxTimerSec = 30f,
                        terminalHp = 200f,
                        maxTerminalHp = 200f,
                        rewardBonusCredits = 1000
                    )
                )
                _objectives.add(
                    MissionObjective(
                        id = "obj_m3_boss",
                        title = "Eliminate Phantom Vex",
                        description = "Locate and assassinate Shadow Sniper Phantom Vex.",
                        category = ObjectiveCategory.ELIMINATE_BOUNTY,
                        isPrimary = true,
                        status = ObjectiveStatus.IN_PROGRESS,
                        currentProgress = 0,
                        requiredProgress = 1,
                        rewardBonusCredits = 1200
                    )
                )
            }

            else -> {
                // Apex Bounty / General
                _objectives.add(
                    MissionObjective(
                        id = "obj_generic_boss",
                        title = "Eliminate Apex Warlord",
                        description = "Neutralize the primary high-value target.",
                        category = ObjectiveCategory.ELIMINATE_BOUNTY,
                        isPrimary = true,
                        status = ObjectiveStatus.IN_PROGRESS,
                        currentProgress = 0,
                        requiredProgress = 1,
                        targetWorldX = objX,
                        targetWorldY = objY,
                        rewardBonusCredits = 1500
                    )
                )
                _objectives.add(
                    MissionObjective(
                        id = "obj_generic_defend",
                        title = "Defend Uplink Terminal",
                        description = "Hold perimeter defense around the terminal for 15s.",
                        category = ObjectiveCategory.DEFEND_TERMINAL,
                        isPrimary = false,
                        status = ObjectiveStatus.IN_PROGRESS,
                        currentProgress = 0,
                        requiredProgress = 15,
                        targetWorldX = objX,
                        targetWorldY = objY,
                        targetRadiusWorld = 160f,
                        timerRemainingSec = 15f,
                        maxTimerSec = 15f,
                        terminalHp = 300f,
                        maxTerminalHp = 300f,
                        rewardBonusCredits = 500
                    )
                )
                _objectives.add(
                    MissionObjective(
                        id = "obj_generic_clear",
                        title = "Clear Hostile Sector",
                        description = "Eliminate all active hostile combatants in sector.",
                        category = ObjectiveCategory.CLEAR_SECTOR,
                        isPrimary = false,
                        status = ObjectiveStatus.IN_PROGRESS,
                        currentProgress = 0,
                        requiredProgress = 6,
                        rewardBonusCredits = 400
                    )
                )
            }
        }
    }

    fun update(
        player: PlayerState,
        enemies: List<Enemy>,
        terrain: VoxelTerrain,
        worldManager: VoxelWorldManager,
        deltaSec: Float
    ): ObjectiveUpdateResult {
        var toastToShow: String? = null

        val deadEnemiesCount = enemies.count { !it.isAlive }
        val bossEnemy = enemies.find { it.type == EnemyType.BOUNTY_BOSS }
        val isBossDead = bossEnemy != null && !bossEnemy.isAlive

        // Check central objective tile status
        val objTile = terrain.getTileAtWorld(terrain.objectivePointX, terrain.objectivePointY)
        val isCoreTileDestroyed = objTile == null || objTile.type != VoxelType.OBJECTIVE_NODE || objTile.currentHp <= 0

        for (i in _objectives.indices) {
            val obj = _objectives[i]
            if (obj.status == ObjectiveStatus.COMPLETED || obj.status == ObjectiveStatus.FAILED) continue

            when (obj.category) {
                ObjectiveCategory.DEFEND_TERMINAL -> {
                    if (obj.status == ObjectiveStatus.NOT_STARTED) {
                        // Check if Sabotage Core was completed first if prerequisite
                        val sabotageObj = _objectives.find { it.category == ObjectiveCategory.SABOTAGE_POWER_CORE }
                        if (sabotageObj == null || sabotageObj.status == ObjectiveStatus.COMPLETED) {
                            _objectives[i] = obj.copy(status = ObjectiveStatus.IN_PROGRESS)
                            toastToShow = "NEW OBJECTIVE: ${obj.title}"
                        }
                        continue
                    }

                    val tx = obj.targetWorldX ?: terrain.objectivePointX
                    val ty = obj.targetWorldY ?: terrain.objectivePointY
                    val distToPlayer = hypot(player.x - tx, player.y - ty)

                    // Check if enemies are attacking terminal
                    var totalEnemyAttackDamage = 0f
                    for (enemy in enemies) {
                        if (enemy.isAlive && hypot(enemy.x - tx, enemy.y - ty) < 100f) {
                            totalEnemyAttackDamage += 12f * deltaSec
                        }
                    }

                    // Update Terminal HP
                    val currentHp = obj.terminalHp ?: 250f
                    val newHp = (currentHp - totalEnemyAttackDamage).coerceAtLeast(0f)
                    val remainingTimer = obj.timerRemainingSec ?: 20f

                    if (newHp <= 0) {
                        _objectives[i] = obj.copy(
                            status = ObjectiveStatus.FAILED,
                            terminalHp = 0f
                        )
                        toastToShow = "OBJECTIVE FAILED: Terminal Destroyed!"
                    } else if (distToPlayer <= obj.targetRadiusWorld) {
                        // Player inside defense zone -> countdown timer
                        val newTimer = (remainingTimer - deltaSec).coerceAtLeast(0f)
                        val elapsedSec = ((obj.maxTimerSec ?: 20f) - newTimer).toInt()

                        if (newTimer <= 0) {
                            _objectives[i] = obj.copy(
                                status = ObjectiveStatus.COMPLETED,
                                currentProgress = obj.requiredProgress,
                                timerRemainingSec = 0f,
                                terminalHp = newHp
                            )
                            toastToShow = "OBJECTIVE COMPLETE: ${obj.title} (+${obj.rewardBonusCredits} CR)"
                        } else {
                            _objectives[i] = obj.copy(
                                currentProgress = elapsedSec,
                                timerRemainingSec = newTimer,
                                terminalHp = newHp
                            )
                        }
                    } else {
                        // Player outside zone -> update terminal HP only
                        _objectives[i] = obj.copy(terminalHp = newHp)
                    }
                }

                ObjectiveCategory.CLEAR_SECTOR -> {
                    val currentProg = deadEnemiesCount.coerceAtMost(obj.requiredProgress)
                    if (currentProg >= obj.requiredProgress) {
                        _objectives[i] = obj.copy(
                            status = ObjectiveStatus.COMPLETED,
                            currentProgress = obj.requiredProgress
                        )
                        toastToShow = "OBJECTIVE COMPLETE: ${obj.title} (+${obj.rewardBonusCredits} CR)"
                    } else {
                        _objectives[i] = obj.copy(currentProgress = currentProg)
                    }
                }

                ObjectiveCategory.ELIMINATE_BOUNTY -> {
                    if (isBossDead) {
                        _objectives[i] = obj.copy(
                            status = ObjectiveStatus.COMPLETED,
                            currentProgress = 1
                        )
                        toastToShow = "BOUNTY NEUTRALIZED: ${obj.title} (+${obj.rewardBonusCredits} CR)"
                    } else if (bossEnemy != null) {
                        // Update position for target tracking
                        _objectives[i] = obj.copy(
                            targetWorldX = bossEnemy.x,
                            targetWorldY = bossEnemy.y
                        )
                    }
                }

                ObjectiveCategory.SABOTAGE_POWER_CORE -> {
                    val tx = obj.targetWorldX ?: terrain.objectivePointX
                    val ty = obj.targetWorldY ?: terrain.objectivePointY
                    val distToPlayer = hypot(player.x - tx, player.y - ty)

                    // Core tile destroyed or interacted
                    if (isCoreTileDestroyed || distToPlayer < 60f) {
                        _objectives[i] = obj.copy(
                            status = ObjectiveStatus.COMPLETED,
                            currentProgress = 1
                        )
                        toastToShow = "CORE SABOTAGED: ${obj.title} (+${obj.rewardBonusCredits} CR)"
                    }
                }

                ObjectiveCategory.STEALTH_INFILTRATION -> {
                    // Completed if reach objective area without alert or after stealth time
                    val tx = obj.targetWorldX ?: terrain.objectivePointX
                    val ty = obj.targetWorldY ?: terrain.objectivePointY
                    if (hypot(player.x - tx, player.y - ty) < obj.targetRadiusWorld) {
                        _objectives[i] = obj.copy(
                            status = ObjectiveStatus.COMPLETED,
                            currentProgress = 1
                        )
                        toastToShow = "INFILTRATION COMPLETE: ${obj.title} (+${obj.rewardBonusCredits} CR)"
                    }
                }
            }
        }

        val primaryList = _objectives.filter { it.isPrimary }
        val isAllPrimaryCompleted = primaryList.isNotEmpty() && primaryList.all { it.status == ObjectiveStatus.COMPLETED }
        val isAnyPrimaryFailed = primaryList.any { it.status == ObjectiveStatus.FAILED }

        return ObjectiveUpdateResult(
            objectives = _objectives.toList(),
            activeToast = toastToShow,
            isAllPrimaryCompleted = isAllPrimaryCompleted,
            isAnyPrimaryFailed = isAnyPrimaryFailed
        )
    }
}
