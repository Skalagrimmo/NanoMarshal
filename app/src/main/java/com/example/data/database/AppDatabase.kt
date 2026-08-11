package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        WeaponInventoryEntity::class,
        PlayerStatsEntity::class,
        LevelProgressEntity::class,
        GadgetInventoryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weaponInventoryDao(): WeaponInventoryDao
    fun weaponDao(): WeaponInventoryDao = weaponInventoryDao()

    abstract fun playerStatsDao(): PlayerStatsDao

    abstract fun levelProgressDao(): LevelProgressDao
    fun levelDataDao(): LevelProgressDao = levelProgressDao()

    abstract fun gadgetInventoryDao(): GadgetInventoryDao

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

        val DEFAULT_PLAYER_STATS = PlayerStatsEntity(
            id = 1,
            credits = 2000,
            naniteCores = 8,
            kills = 0,
            stealthKills = 0,
            totalMissionsCompleted = 0,
            primaryWeaponId = "w_plasma",
            secondaryWeaponId = "w_needle",
            activeGadgetId = "g_grenade",
            maxHealth = 100f,
            maxShield = 50f
        )

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

        val DEFAULT_LEVEL_PROGRESS = listOf(
            LevelProgressEntity(
                missionId = "m_outpost9",
                sectorName = "SECTOR 01 // CRATER MARGINS",
                title = "OUTPOST 9 RECON",
                bountyTargetName = "Warlord Kael",
                difficulty = 1,
                isUnlocked = true,
                isCompleted = false,
                rewardCredits = 1000,
                rewardCores = 3
            ),
            LevelProgressEntity(
                missionId = "m_nanovault",
                sectorName = "SECTOR 02 // SUB-VOID CORE",
                title = "VAULT EXTRACTION",
                bountyTargetName = "Archon Vex",
                difficulty = 2,
                isUnlocked = false,
                isCompleted = false,
                rewardCredits = 1800,
                rewardCores = 5
            ),
            LevelProgressEntity(
                missionId = "m_titanfort",
                sectorName = "SECTOR 03 // TITAN CITADEL",
                title = "CITADEL SIEGE",
                bountyTargetName = "Grand Overseer Malakor",
                difficulty = 3,
                isUnlocked = false,
                isCompleted = false,
                rewardCredits = 3000,
                rewardCores = 8
            )
        )

        val DEFAULT_GADGETS = listOf(
            GadgetInventoryEntity(
                id = "g_grenade",
                name = "Nano Disruption Grenade",
                isUnlocked = true,
                count = 3,
                maxCount = 5,
                cost = 0
            ),
            GadgetInventoryEntity(
                id = "g_smoke",
                name = "Sub-Space Smoke Screen",
                isUnlocked = false,
                count = 2,
                maxCount = 4,
                cost = 600
            ),
            GadgetInventoryEntity(
                id = "g_turret",
                name = "Deployable Defense Turret",
                isUnlocked = false,
                count = 1,
                maxCount = 2,
                cost = 1200
            )
        )
    }
}
