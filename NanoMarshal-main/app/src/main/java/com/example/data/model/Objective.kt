package com.example.data.model

enum class ObjectiveStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

enum class ObjectiveCategory {
    DEFEND_TERMINAL,
    CLEAR_SECTOR,
    ELIMINATE_BOUNTY,
    SABOTAGE_POWER_CORE,
    STEALTH_INFILTRATION
}

/**
 * Data model for primary and secondary mission objectives in the voxel tactical environment.
 */
data class MissionObjective(
    val id: String,
    val title: String,
    val description: String,
    val category: ObjectiveCategory,
    val isPrimary: Boolean = true,
    var status: ObjectiveStatus = ObjectiveStatus.IN_PROGRESS,
    var currentProgress: Int = 0,
    var requiredProgress: Int = 1,
    val targetWorldX: Float? = null,
    val targetWorldY: Float? = null,
    val targetRadiusWorld: Float = 160f,
    var timerRemainingSec: Float? = null,
    val maxTimerSec: Float? = null,
    var terminalHp: Float? = null,
    val maxTerminalHp: Float? = null,
    val rewardBonusCredits: Int = 300,
    val rewardBonusCores: Int = 2
) {
    val progressRatio: Float
        get() = if (requiredProgress > 0) (currentProgress.toFloat() / requiredProgress.toFloat()).coerceIn(0f, 1f) else 1f

    val timerProgressRatio: Float
        get() = if (maxTimerSec != null && maxTimerSec > 0 && timerRemainingSec != null) {
            (timerRemainingSec!! / maxTimerSec).coerceIn(0f, 1f)
        } else 1f

    val terminalHpRatio: Float
        get() = if (maxTerminalHp != null && maxTerminalHp > 0 && terminalHp != null) {
            (terminalHp!! / maxTerminalHp).coerceIn(0f, 1f)
        } else 1f
}
