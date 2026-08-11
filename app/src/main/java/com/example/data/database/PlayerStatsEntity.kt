package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "player_stats")
data class PlayerStatsEntity(
    @PrimaryKey val id: Int = 1, // Single-row entity for main player profile
    val credits: Int = 2000,
    val naniteCores: Int = 8,
    val kills: Int = 0,
    val stealthKills: Int = 0,
    val totalMissionsCompleted: Int = 0,
    val primaryWeaponId: String = "w_plasma",
    val secondaryWeaponId: String = "w_needle",
    val activeGadgetId: String = "g_grenade",
    val maxHealth: Float = 100f,
    val maxShield: Float = 50f,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
)

typealias PlayerStats = PlayerStatsEntity
