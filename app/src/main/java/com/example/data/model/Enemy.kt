package com.example.data.model

enum class EnemyType {
    GRUNT, FLANKER, SHIELD_ENFORCER, SNIPER_STALKER, BOUNTY_BOSS
}

enum class AIState {
    PATROL, SUSPICIOUS, INVESTIGATING, SEEKING_COVER, ENGAGED, FLANKING, SUPPRESSING, STUNNED, RETREAT, DEAD
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
    var isFlankingPlayer: Boolean = false,
    var stunTimerMs: Long = 0,
    var bountyReward: Int = 200,
    val weaponName: String = "Plasma Carbine",
    val weaponDamage: Float = 15f
) {
    val isAlive: Boolean get() = health > 0f
}
