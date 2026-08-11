package com.example.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface LevelProgressDao {
    @Query("SELECT * FROM level_progress ORDER BY difficulty ASC, missionId ASC")
    fun getAllLevelProgressFlow(): Flow<List<LevelProgressEntity>>

    @Query("SELECT * FROM level_progress ORDER BY difficulty ASC, missionId ASC")
    suspend fun getAllLevelProgress(): List<LevelProgressEntity>

    @Query("SELECT * FROM level_progress WHERE missionId = :missionId")
    suspend fun getLevelProgressById(missionId: String): LevelProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(level: LevelProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(level: LevelProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(levels: List<LevelProgressEntity>)

    @Update
    suspend fun update(level: LevelProgressEntity)

    @Delete
    suspend fun delete(level: LevelProgressEntity)

    @Query("DELETE FROM level_progress WHERE missionId = :missionId")
    suspend fun deleteById(missionId: String)

    @Query("UPDATE level_progress SET isCompleted = 1, starsEarned = MAX(starsEarned, :stars), highScore = MAX(highScore, :score) WHERE missionId = :missionId")
    suspend fun recordCompletion(missionId: String, stars: Int, score: Int)

    @Query("UPDATE level_progress SET isUnlocked = 1 WHERE missionId = :missionId")
    suspend fun unlockLevel(missionId: String)

    @Query("SELECT COUNT(*) FROM level_progress")
    suspend fun getCount(): Int
}

typealias LevelDataDao = LevelProgressDao
