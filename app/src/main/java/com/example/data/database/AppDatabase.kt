package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [WeaponInventoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weaponInventoryDao(): WeaponInventoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "nanomarshal_inventory.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }

        val DEFAULT_INVENTORY = listOf(
            WeaponInventoryEntity(
                id = "w_needle",
                name = "Needle Sub-Pistol",
                weaponType = "PISTOL",
                damage = 28,
                fireRateMs = 280,
                magSize = 12,
                currentMagAmmo = 12,
                reserveAmmo = 120,
                maxReserveAmmo = 240,
                isUnlocked = true,
                upgradeLevel = 1,
                cost = 0
            ),
            WeaponInventoryEntity(
                id = "w_plasma",
                name = "VORTEX Plasma Rifle",
                weaponType = "PLASMA_RIFLE",
                damage = 38,
                fireRateMs = 160,
                magSize = 30,
                currentMagAmmo = 30,
                reserveAmmo = 180,
                maxReserveAmmo = 360,
                isUnlocked = true,
                upgradeLevel = 1,
                cost = 0
            ),
            WeaponInventoryEntity(
                id = "w_scatter",
                name = "Nano Scattergun",
                weaponType = "SHOTGUN",
                damage = 90,
                fireRateMs = 700,
                magSize = 8,
                currentMagAmmo = 8,
                reserveAmmo = 48,
                maxReserveAmmo = 96,
                isUnlocked = false,
                upgradeLevel = 1,
                cost = 1500
            ),
            WeaponInventoryEntity(
                id = "w_railgun",
                name = "Hyperion Railgun",
                weaponType = "RAILGUN",
                damage = 140,
                fireRateMs = 1100,
                magSize = 5,
                currentMagAmmo = 5,
                reserveAmmo = 25,
                maxReserveAmmo = 50,
                isUnlocked = false,
                upgradeLevel = 1,
                cost = 3500
            ),
            WeaponInventoryEntity(
                id = "w_sniper",
                name = "Spectrum Beam Sniper",
                weaponType = "SNIPER",
                damage = 180,
                fireRateMs = 1400,
                magSize = 4,
                currentMagAmmo = 4,
                reserveAmmo = 20,
                maxReserveAmmo = 40,
                isUnlocked = false,
                upgradeLevel = 1,
                cost = 5000
            )
        )
    }
}
