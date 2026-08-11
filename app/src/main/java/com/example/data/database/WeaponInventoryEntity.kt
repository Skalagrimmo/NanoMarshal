package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weapon_inventory")
data class WeaponInventoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val weaponType: String,
    val damage: Int,
    val fireRateMs: Long,
    val magSize: Int,
    val currentMagAmmo: Int,
    val reserveAmmo: Int,
    val maxReserveAmmo: Int,
    val isUnlocked: Boolean,
    val upgradeLevel: Int = 1,
    val cost: Int = 0
) {
    val totalAmmo: Int get() = currentMagAmmo + reserveAmmo
}

typealias WeaponEntity = WeaponInventoryEntity
