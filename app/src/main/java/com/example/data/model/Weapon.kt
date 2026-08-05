package com.example.data.model

enum class WeaponType {
    PISTOL, PLASMA_RIFLE, SHOTGUN, RAILGUN, SNIPER
}

data class Weapon(
    val id: String,
    val name: String,
    val type: WeaponType,
    val damage: Int,
    val fireRateMs: Long,
    val magSize: Int,
    val reloadTimeMs: Long,
    val coverDamage: Int,
    val isSilenced: Boolean = false,
    val pierceCover: Boolean = false,
    val iconName: String,
    val description: String,
    val cost: Int = 0,
    var isUnlocked: Boolean = false,
    var upgradeLevel: Int = 1
) {
    val effectiveDamage: Int get() = (damage * (1.0 + (upgradeLevel - 1) * 0.25)).toInt()
}

object DefaultWeapons {
    val NEEDLE_PISTOL = Weapon(
        id = "w_needle",
        name = "Needle Sub-Pistol",
        type = WeaponType.PISTOL,
        damage = 28,
        fireRateMs = 280,
        magSize = 12,
        reloadTimeMs = 1200,
        coverDamage = 15,
        isSilenced = true,
        iconName = "ic_needle",
        description = "Sub-sonic suppressed sidearm for silent stealth takedowns without alerting guards.",
        cost = 0,
        isUnlocked = true
    )

    val PLASMA_RIFLE = Weapon(
        id = "w_plasma",
        name = "VORTEX Plasma Rifle",
        type = WeaponType.PLASMA_RIFLE,
        damage = 38,
        fireRateMs = 160,
        magSize = 30,
        reloadTimeMs = 1800,
        coverDamage = 30,
        isSilenced = false,
        iconName = "ic_rifle",
        description = "Standard issue tactical assault rifle firing concentrated plasma bolts with high fire rate.",
        cost = 0,
        isUnlocked = true
    )

    val SCATTERGUN = Weapon(
        id = "w_scatter",
        name = "Nano Scattergun",
        type = WeaponType.SHOTGUN,
        damage = 90,
        fireRateMs = 700,
        magSize = 8,
        reloadTimeMs = 2200,
        coverDamage = 80,
        isSilenced = false,
        iconName = "ic_shotgun",
        description = "Close-range heavy scatter weapon that pulverizes voxel cover and enemies instantly.",
        cost = 1500,
        isUnlocked = false
    )

    val HEAVY_RAILGUN = Weapon(
        id = "w_railgun",
        name = "Hyperion Railgun",
        type = WeaponType.RAILGUN,
        damage = 140,
        fireRateMs = 1100,
        magSize = 5,
        reloadTimeMs = 2600,
        coverDamage = 120,
        isSilenced = false,
        pierceCover = true,
        iconName = "ic_railgun",
        description = "High-energy magnetic accelerator that punches clean through low voxel cover blocks.",
        cost = 3500,
        isUnlocked = false
    )

    val SPECTRUM_SNIPER = Weapon(
        id = "w_sniper",
        name = "Spectrum Beam Sniper",
        type = WeaponType.SNIPER,
        damage = 180,
        fireRateMs = 1400,
        magSize = 4,
        reloadTimeMs = 2800,
        coverDamage = 45,
        isSilenced = true,
        iconName = "ic_sniper",
        description = "Extreme long-range beam weapon with thermal targeting for surgical elimination.",
        cost = 5000,
        isUnlocked = false
    )

    fun getAll() = listOf(NEEDLE_PISTOL, PLASMA_RIFLE, SCATTERGUN, HEAVY_RAILGUN, SPECTRUM_SNIPER)
}
