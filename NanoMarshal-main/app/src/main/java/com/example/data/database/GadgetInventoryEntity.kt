package com.example.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gadget_inventory")
data class GadgetInventoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isUnlocked: Boolean,
    val count: Int,
    val maxCount: Int,
    val cost: Int = 0
)
