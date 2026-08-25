package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "level_progress")
data class LevelProgressEntity(
    @PrimaryKey val missionId: String,
    val sectorName: String,
    val title: String,
    val bountyTargetName: String,
    val difficulty: Int,
    val isUnlocked: Boolean,
    val isCompleted: Boolean,
    val starsEarned: Int = 0,
    val highScore: Int = 0,
    val bestClearTimeSeconds: Long = 0,
    val rewardCredits: Int = 1000,
    val rewardCores: Int = 3
)

typealias LevelData = LevelProgressEntity
