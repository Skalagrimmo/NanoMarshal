package com.example.data.model

enum class PlayerStance {
    STAND, CROUCH, PRONE
}

data class PlayerState(
    var x: Float = 400f,
    var y: Float = 400f,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var facingAngle: Float = 0f,
    var aimAngle: Float = 0f,
    var health: Float = 100f,
    var maxHealth: Float = 100f,
    var nanoShield: Float = 50f,
    var maxNanoShield: Float = 50f,
    var shieldRechargeDelayMs: Long = 0,
    var stance: PlayerStance = PlayerStance.STAND,
    var isBehindCover: Boolean = false,
    var coverTileX: Int? = null,
    var coverTileY: Int? = null,
    var coverHeight: CoverHeight = CoverHeight.NONE,
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
    
    val moveSpeedMultiplier: Float get() = when (stance) {
        PlayerStance.STAND -> 1.0f
        PlayerStance.CROUCH -> 0.65f
        PlayerStance.PRONE -> 0.35f
    }
}
