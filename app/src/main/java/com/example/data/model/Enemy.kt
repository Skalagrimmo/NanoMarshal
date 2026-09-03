package com.example.data.model

enum class EnemyType {
    GRUNT, FLANKER, SHIELD_ENFORCER, SNIPER_STALKER, BOUNTY_BOSS
}

enum class AIState {
    PATROL, SUSPICIOUS, INVESTIGATING, SEEKING_COVER, ENGAGED, FLANKING, SUPPRESSING, STUNNED, RETREAT, DEAD
}

enum class FlankDirection {
    NONE, LEFT, RIGHT, BLIND_REAR, CUT_OFF
}

enum class FlankManeuverType {
    NONE,
    WIDE_ARC_FLANK,
    BLIND_SIDE_FLANK,
    CUT_OFF_CORNER,
    INTERCEPT_VAULT,
    SUPPRESS_AND_CHIP,
    TIGHT_COVER_FLANK,
    ENCIRCLE
}

data class Enemy(
    val id: String,
    val name: String,
    val type: EnemyType,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var facingAngle: Float = 0f, // Radians
    var health: Float,
    val maxHealth: Float,
    var shieldHp: Float = 0f,
    val maxShieldHp: Float = 0f,
    var state: AIState = AIState.PATROL,
    var lastKnownPlayerX: Float? = null,
    var lastKnownPlayerY: Float? = null,
    var visionRange: Float = 320f,
    var visionAngleRad: Float = Math.toRadians(75.0).toFloat(),
    var alertLevel: Float = 0f, // 0 = Calm, 100 = Fully Alerted
    var patrolWaypoints: List<Pair<Float, Float>> = emptyList(),
    var currentWaypointIndex: Int = 0,
    var shootCooldownMs: Long = 0,
    var moveSpeed: Float = 3.2f,
    var targetCoverX: Int? = null,
    var targetCoverY: Int? = null,
    var isBehindCover: Boolean = false,
    var isCoverSnapped: Boolean = false,
    var coverSnapNormalX: Float = 0f,
    var coverSnapNormalY: Float = 0f,
    var coverAnimPulse: Float = 0f,
    var activeCoverType: VoxelType? = null,
    var activeCoverDamageMitigation: Float = 0f,
    var isCoverFlanked: Boolean = false,
    var isFlankingPlayer: Boolean = false,
    var flankDirection: FlankDirection = FlankDirection.NONE,
    var flankManeuverType: FlankManeuverType = FlankManeuverType.NONE,
    var tacticalManeuverLabel: String? = null,
    var suppressionTargetGx: Int? = null,
    var suppressionTargetGy: Int? = null,
    var activePath: List<Pair<Float, Float>> = emptyList(),
    var activePathIndex: Int = 0,
    var pathUpdateTimerMs: Long = 0,
    var stunTimerMs: Long = 0,
    var isVisibleInFog: Boolean = false,
    var radarPingAlpha: Float = 0f,
    var audioTremorDetected: Boolean = false,
    var hasDirectLineOfSightToPlayer: Boolean = false,
    var bountyReward: Int = 200,
    val weaponName: String = "Plasma Carbine",
    val weaponDamage: Float = 15f
) {
    val isAlive: Boolean get() = health > 0f
}
