package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GadgetInventoryDao {
    @Query("SELECT * FROM gadget_inventory ORDER BY id ASC")
    fun getGadgetsFlow(): Flow<List<GadgetInventoryEntity>>

    @Query("SELECT * FROM gadget_inventory ORDER BY id ASC")
    suspend fun getGadgetsList(): List<GadgetInventoryEntity>

    @Query("SELECT * FROM gadget_inventory WHERE id = :id")
    suspend fun getGadgetById(id: String): GadgetInventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(gadget: GadgetInventoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(gadgets: List<GadgetInventoryEntity>)

    @Query("UPDATE gadget_inventory SET isUnlocked = 1 WHERE id = :id")
    suspend fun unlockGadget(id: String)

    @Query("SELECT COUNT(*) FROM gadget_inventory")
    suspend fun getCount(): Int
}
