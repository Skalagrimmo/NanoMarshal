package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WeaponInventoryDao {
    @Query("SELECT * FROM weapon_inventory ORDER BY id ASC")
    fun getInventoryFlow(): Flow<List<WeaponInventoryEntity>>

    @Query("SELECT * FROM weapon_inventory ORDER BY id ASC")
    suspend fun getInventoryList(): List<WeaponInventoryEntity>

    @Query("SELECT * FROM weapon_inventory WHERE id = :id")
    suspend fun getWeaponById(id: String): WeaponInventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(item: WeaponInventoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<WeaponInventoryEntity>)

    @Query("UPDATE weapon_inventory SET currentMagAmmo = :magAmmo, reserveAmmo = :reserveAmmo WHERE id = :id")
    suspend fun updateAmmo(id: String, magAmmo: Int, reserveAmmo: Int)

    @Query("UPDATE weapon_inventory SET reserveAmmo = :reserveAmmo WHERE id = :id")
    suspend fun updateReserveAmmo(id: String, reserveAmmo: Int)

    @Query("UPDATE weapon_inventory SET isUnlocked = 1 WHERE id = :id")
    suspend fun unlockWeapon(id: String)

    @Query("UPDATE weapon_inventory SET upgradeLevel = :level, damage = :damage WHERE id = :id")
    suspend fun upgradeWeapon(id: String, level: Int, damage: Int)

    @Query("SELECT COUNT(*) FROM weapon_inventory")
    suspend fun getCount(): Int
}
