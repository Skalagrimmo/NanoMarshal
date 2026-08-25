package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerStatsDao {
    @Query("SELECT * FROM player_stats WHERE id = 1")
    fun getPlayerStatsFlow(): Flow<PlayerStatsEntity?>

    @Query("SELECT * FROM player_stats WHERE id = 1")
    suspend fun getPlayerStats(): PlayerStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(stats: PlayerStatsEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(stats: PlayerStatsEntity)

    @Update
    suspend fun update(stats: PlayerStatsEntity)

    @Delete
    suspend fun delete(stats: PlayerStatsEntity)

    @Query("DELETE FROM player_stats")
    suspend fun deleteAll()

    @Query("UPDATE player_stats SET credits = :credits, naniteCores = :cores WHERE id = 1")
    suspend fun updateCurrencies(credits: Int, cores: Int)

    @Query("UPDATE player_stats SET primaryWeaponId = :primaryId, secondaryWeaponId = :secondaryId, activeGadgetId = :gadgetId WHERE id = 1")
    suspend fun updateEquippedLoadout(primaryId: String, secondaryId: String, gadgetId: String)

    @Query("UPDATE player_stats SET kills = kills + :addKills, stealthKills = stealthKills + :addStealthKills WHERE id = 1")
    suspend fun recordKills(addKills: Int, addStealthKills: Int)

    @Query("SELECT COUNT(*) FROM player_stats")
    suspend fun getCount(): Int
}
