package com.example.data.repository

import android.content.Context
import com.example.data.database.*
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class PlayerProfile(
    val credits: Int = 2000,
    val naniteCores: Int = 8,
    val unlockedWeaponIds: Set<String> = setOf("w_needle", "w_plasma"),
    val unlockedGadgetIds: Set<String> = setOf("g_grenade"),
    val primaryWeaponId: String = "w_plasma",
    val secondaryWeaponId: String = "w_needle",
    val activeGadgetId: String = "g_grenade",
    val completedMissionIds: Set<String> = emptySet(),
    val missionStars: Map<String, Int> = emptyMap()
)

class GameRepository(context: Context) {
    private val database = AppDatabase.getInstance(context)
    private val inventoryDao = database.weaponInventoryDao()
    private val playerStatsDao = database.playerStatsDao()
    private val levelProgressDao = database.levelProgressDao()
    private val gadgetInventoryDao = database.gadgetInventoryDao()

    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    val weaponInventoryFlow: Flow<List<WeaponInventoryEntity>> = inventoryDao.getInventoryFlow()
    val playerStatsFlow: Flow<PlayerStatsEntity?> = playerStatsDao.getPlayerStatsFlow()
    val levelProgressFlow: Flow<List<LevelProgressEntity>> = levelProgressDao.getAllLevelProgressFlow()
    val gadgetInventoryFlow: Flow<List<GadgetInventoryEntity>> = gadgetInventoryDao.getGadgetsFlow()

    // Direct Entity API for ViewModels
    val allWeaponsFlow: Flow<List<WeaponEntity>> = inventoryDao.getInventoryFlow()
    val allLevelsFlow: Flow<List<LevelData>> = levelProgressDao.getAllLevelProgressFlow()

    suspend fun getPlayerStats(): PlayerStats? = playerStatsDao.getPlayerStats()
    suspend fun updatePlayerStats(stats: PlayerStats) = playerStatsDao.insertOrUpdate(stats)

    suspend fun getAllWeapons(): List<WeaponEntity> = inventoryDao.getInventoryList()
    suspend fun getWeaponById(id: String): WeaponEntity? = inventoryDao.getWeaponById(id)
    suspend fun insertWeapon(weapon: WeaponEntity) = inventoryDao.insertOrUpdate(weapon)
    suspend fun updateWeapon(weapon: WeaponEntity) = inventoryDao.update(weapon)
    suspend fun deleteWeapon(weapon: WeaponEntity) = inventoryDao.delete(weapon)

    suspend fun getAllLevels(): List<LevelData> = levelProgressDao.getAllLevelProgress()
    suspend fun getLevelById(missionId: String): LevelData? = levelProgressDao.getLevelProgressById(missionId)
    suspend fun insertLevel(level: LevelData) = levelProgressDao.insertOrUpdate(level)
    suspend fun updateLevel(level: LevelData) = levelProgressDao.update(level)
    suspend fun deleteLevel(level: LevelData) = levelProgressDao.delete(level)

    init {
        repositoryScope.launch {
            if (inventoryDao.getCount() == 0) {
                inventoryDao.insertAll(AppDatabase.DEFAULT_INVENTORY)
            }
            if (playerStatsDao.getCount() == 0) {
                playerStatsDao.insertOrUpdate(AppDatabase.DEFAULT_PLAYER_STATS)
            }
            if (levelProgressDao.getCount() == 0) {
                levelProgressDao.insertAll(AppDatabase.DEFAULT_LEVEL_PROGRESS)
            }
            if (gadgetInventoryDao.getCount() == 0) {
                gadgetInventoryDao.insertAll(AppDatabase.DEFAULT_GADGETS)
            }
        }
    }

    // Reactive StateFlow combining all Room DAOs for full UI compatibility
    val profile: StateFlow<PlayerProfile> = combine(
        playerStatsDao.getPlayerStatsFlow(),
        inventoryDao.getInventoryFlow(),
        gadgetInventoryDao.getGadgetsFlow(),
        levelProgressDao.getAllLevelProgressFlow()
    ) { stats, weapons, gadgets, levels ->
        val currentStats = stats ?: AppDatabase.DEFAULT_PLAYER_STATS
        val unlockedWeapons = weapons.filter { it.isUnlocked }.map { it.id }.toSet()
            .ifEmpty { setOf("w_needle", "w_plasma") }
        val unlockedGadgets = gadgets.filter { it.isUnlocked }.map { it.id }.toSet()
            .ifEmpty { setOf("g_grenade") }
        val completedMissions = levels.filter { it.isCompleted }.map { it.missionId }.toSet()
        val starsMap = levels.associate { it.missionId to it.starsEarned }

        PlayerProfile(
            credits = currentStats.credits,
            naniteCores = currentStats.naniteCores,
            unlockedWeaponIds = unlockedWeapons,
            unlockedGadgetIds = unlockedGadgets,
            primaryWeaponId = currentStats.primaryWeaponId,
            secondaryWeaponId = currentStats.secondaryWeaponId,
            activeGadgetId = currentStats.activeGadgetId,
            completedMissionIds = completedMissions,
            missionStars = starsMap
        )
    }.stateIn(
        scope = repositoryScope,
        started = SharingStarted.Eagerly,
        initialValue = PlayerProfile()
    )

    fun saveProfile(profile: PlayerProfile) {
        repositoryScope.launch {
            val stats = playerStatsDao.getPlayerStats() ?: AppDatabase.DEFAULT_PLAYER_STATS
            playerStatsDao.insertOrUpdate(
                stats.copy(
                    credits = profile.credits,
                    naniteCores = profile.naniteCores,
                    primaryWeaponId = profile.primaryWeaponId,
                    secondaryWeaponId = profile.secondaryWeaponId,
                    activeGadgetId = profile.activeGadgetId,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            )
            for (weaponId in profile.unlockedWeaponIds) {
                inventoryDao.unlockWeapon(weaponId)
            }
            for (gadgetId in profile.unlockedGadgetIds) {
                gadgetInventoryDao.unlockGadget(gadgetId)
            }
        }
    }

    fun buyWeapon(weapon: Weapon): Boolean {
        val curr = profile.value
        if (curr.credits >= weapon.cost && !curr.unlockedWeaponIds.contains(weapon.id)) {
            repositoryScope.launch {
                val stats = playerStatsDao.getPlayerStats() ?: AppDatabase.DEFAULT_PLAYER_STATS
                playerStatsDao.insertOrUpdate(stats.copy(credits = stats.credits - weapon.cost))
                inventoryDao.unlockWeapon(weapon.id)
            }
            return true
        }
        return false
    }

    fun buyGadget(gadget: Gadget): Boolean {
        val curr = profile.value
        if (curr.credits >= gadget.cost && !curr.unlockedGadgetIds.contains(gadget.id)) {
            repositoryScope.launch {
                val stats = playerStatsDao.getPlayerStats() ?: AppDatabase.DEFAULT_PLAYER_STATS
                playerStatsDao.insertOrUpdate(stats.copy(credits = stats.credits - gadget.cost))
                gadgetInventoryDao.unlockGadget(gadget.id)
            }
            return true
        }
        return false
    }

    fun buyAmmoRefill(weaponId: String, costCredits: Int): Boolean {
        val curr = profile.value
        if (curr.credits >= costCredits) {
            repositoryScope.launch {
                val stats = playerStatsDao.getPlayerStats() ?: AppDatabase.DEFAULT_PLAYER_STATS
                playerStatsDao.insertOrUpdate(stats.copy(credits = stats.credits - costCredits))
                val item = inventoryDao.getWeaponById(weaponId)
                if (item != null) {
                    val newReserve = (item.reserveAmmo + item.maxReserveAmmo / 2).coerceAtMost(item.maxReserveAmmo)
                    inventoryDao.updateReserveAmmo(weaponId, newReserve)
                }
            }
            return true
        }
        return false
    }

    fun upgradeWeaponLevel(weaponId: String, costCores: Int): Boolean {
        val curr = profile.value
        if (curr.naniteCores >= costCores) {
            repositoryScope.launch {
                val stats = playerStatsDao.getPlayerStats() ?: AppDatabase.DEFAULT_PLAYER_STATS
                playerStatsDao.insertOrUpdate(stats.copy(naniteCores = stats.naniteCores - costCores))
                val item = inventoryDao.getWeaponById(weaponId)
                if (item != null) {
                    val nextLevel = item.upgradeLevel + 1
                    val newDamage = (item.damage * 1.25f).toInt()
                    inventoryDao.upgradeWeapon(weaponId, nextLevel, newDamage)
                }
            }
            return true
        }
        return false
    }

    fun upgradeWeaponWithCredits(weaponId: String, costCredits: Int): Boolean {
        val curr = profile.value
        if (curr.credits >= costCredits) {
            repositoryScope.launch {
                val stats = playerStatsDao.getPlayerStats() ?: AppDatabase.DEFAULT_PLAYER_STATS
                playerStatsDao.insertOrUpdate(stats.copy(credits = stats.credits - costCredits))
                val item = inventoryDao.getWeaponById(weaponId)
                if (item != null) {
                    val nextLevel = item.upgradeLevel + 1
                    val newDamage = (item.damage * 1.25f).toInt()
                    inventoryDao.upgradeWeapon(weaponId, nextLevel, newDamage)
                }
            }
            return true
        }
        return false
    }

    fun updateWeaponAmmoState(weaponId: String, currentMag: Int, reserveAmmo: Int) {
        repositoryScope.launch {
            inventoryDao.updateAmmo(weaponId, currentMag, reserveAmmo)
        }
    }

    suspend fun getInventoryList(): List<WeaponInventoryEntity> {
        return inventoryDao.getInventoryList()
    }

    fun recordMissionVictory(missionId: String, stars: Int, creditsEarned: Int, coresEarned: Int) {
        repositoryScope.launch {
            val stats = playerStatsDao.getPlayerStats() ?: AppDatabase.DEFAULT_PLAYER_STATS
            playerStatsDao.insertOrUpdate(
                stats.copy(
                    credits = stats.credits + creditsEarned,
                    naniteCores = stats.naniteCores + coresEarned,
                    totalMissionsCompleted = stats.totalMissionsCompleted + 1,
                    lastUpdatedTimestamp = System.currentTimeMillis()
                )
            )

            // Mark mission completed in Room level_progress DB
            levelProgressDao.recordCompletion(
                missionId = missionId,
                stars = stars,
                score = creditsEarned * 10
            )

            // Unlock next level if present
            val allLevels = levelProgressDao.getAllLevelProgress()
            val currentIndex = allLevels.indexOfFirst { it.missionId == missionId }
            if (currentIndex != -1 && currentIndex + 1 < allLevels.size) {
                levelProgressDao.unlockLevel(allLevels[currentIndex + 1].missionId)
            }

            // Bonus ammo refill for all unlocked weapons upon mission victory
            val items = inventoryDao.getInventoryList()
            for (item in items) {
                if (item.isUnlocked) {
                    val restored = (item.reserveAmmo + item.maxReserveAmmo / 2).coerceAtMost(item.maxReserveAmmo)
                    inventoryDao.updateReserveAmmo(item.id, restored)
                }
            }
        }
    }
}
