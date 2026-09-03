package com.example.data.model

enum class PlayerStance {
    STAND, CROUCH, PRONE
}

enum class PlayerMovementState {
    IDLE,              // Stationary
    WALKING,           // Standard tactical movement
    SPRINTING,         // High-speed run
    COVER_SNAPPED,     // Anchored against voxel cover object
    COVER_TRAVERSING,  // Sliding along voxel cover face
    COVER_PEEKING,     // Leaning/aiming past cover edge or over low barrier
    COVER_VAULTING     // Fluidly vaulting over low obstacle
}

enum class AutoAimMode {
    SMART,   // Magnetic lock-on within tactical cone
    PRECISE, // High precision lock-on to closest high threat target
    OFF      // Manual free aiming
}

data class PlayerState(
    var x: Float = 400f,
    var y: Float = 400f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var facingAngle: Float = 0f,
    var aimAngle: Float = 0f,
    var rawInputAngle: Float = 0f, // Unmodified joystick aim angle before autoaim
    var isAutoAimEnabled: Boolean = true,
    var autoAimMode: AutoAimMode = AutoAimMode.SMART,
    var autoAimTargetEnemyId: String? = null,
    var autoAimTargetPos: Pair<Float, Float>? = null,
    var isAutoAimLocked: Boolean = false,
    var autoAimLockProgress: Float = 0f, // 0.0 to 1.0 for lock-on animation
    var maxRicochetsOverride: Int? = null,
    var health: Float = 100f,
    var maxHealth: Float = 100f,
    var nanoShield: Float = 50f,
    var maxNanoShield: Float = 50f,
    var shieldRechargeDelayMs: Long = 0,
    var stance: PlayerStance = PlayerStance.STAND,
    var movementState: PlayerMovementState = PlayerMovementState.IDLE,
    var isBehindCover: Boolean = false,
    var isCoverSnapped: Boolean = false,
    var coverSnapNormalX: Float = 0f,
    var coverSnapNormalY: Float = 0f,
    var coverAnimPulse: Float = 0f,
    var coverTileX: Int? = null,
    var coverTileY: Int? = null,
    var coverHeight: CoverHeight = CoverHeight.NONE,
    var activeCoverType: VoxelType? = null,
    var activeCoverBuffTitle: String? = null,
    var activeCoverBuffSubtitle: String? = null,
    var activeCoverDamageMitigation: Float = 0f,
    var coverHitProbabilityReduction: Float = 0f, // 0.0 to 1.0 (e.g. 0.85 = 85% reduced hit probability)
    var incomingHitProbability: Float = 0.92f,    // 0.0 to 1.0 (e.g. 0.10 = only 10% hit chance for enemies)
    var isVaulting: Boolean = false,
    var vaultProgress: Float = 0f,
    var vaultStartX: Float = 0f,
    var vaultStartY: Float = 0f,
    var vaultTargetX: Float = 0f,
    var vaultTargetY: Float = 0f,
    var coverPeekFactor: Float = 0f,
    var activeCoverShieldBonus: Float = 1.0f,
    var activeCoverAccuracyBonus: Float = 1.0f,
    var isCoverFlanked: Boolean = false,
    var isFiring: Boolean = false,
    var currentWeapon: Weapon = DefaultWeapons.PLASMA_RIFLE,
    var sidearmWeapon: Weapon = DefaultWeapons.NEEDLE_PISTOL,
    var activeWeaponSlot: Int = 1, // 1 = Primary, 2 = Sidearm
    var currentAmmo: Int = 30,
    var isReloading: Boolean = false,
    var reloadTimerMs: Long = 0,
    var activeGadget: Gadget = DefaultGadgets.NANO_GRENADE,
    var gadgetCount: Int = 3,
    var gadgetCooldownTimerMs: Long = 0,
    var stealthNoiseRadius: Float = 0f, // Radial sound circle generated when moving/firing
    var detectedByEnemiesCount: Int = 0,
    var killsCount: Int = 0,
    var stealthKillsCount: Int = 0,
    var credits: Int = 1200,
    var naniteCores: Int = 5,
    var isNaniteOverdriveActive: Boolean = false,
    var overdriveTimerMs: Long = 0
) {
    val activeWeapon: Weapon get() = if (activeWeaponSlot == 1) currentWeapon else sidearmWeapon
    val isAlive: Boolean get() = health > 0f
    
    val moveSpeedMultiplier: Float get() = when (movementState) {
        PlayerMovementState.SPRINTING -> 1.55f
        PlayerMovementState.COVER_SNAPPED -> 0f
        PlayerMovementState.COVER_TRAVERSING -> 0.60f
        PlayerMovementState.COVER_PEEKING -> 0.30f
        PlayerMovementState.COVER_VAULTING -> 1.25f
        PlayerMovementState.IDLE -> 1.0f
        PlayerMovementState.WALKING -> when (stance) {
            PlayerStance.STAND -> 1.0f
            PlayerStance.CROUCH -> 0.65f
            PlayerStance.PRONE -> 0.35f
        }
    }
}
