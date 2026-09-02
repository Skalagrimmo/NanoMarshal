package com.example.data.model

import org.junit.Assert.*
import org.junit.Test

class VoxelTileTest {

    @Test
    fun testVoxelTileHealthAndDurabilityInitialization() {
        val tile = VoxelTile(
            gridX = 5,
            gridY = 8,
            elevationZ = 2,
            type = VoxelType.CONCRETE_WALL,
            health = 220f,
            durability = 180f,
            maxHealth = 220f,
            maxDurability = 180f,
            isDestructible = true,
            coverHeight = CoverHeight.HIGH
        )

        assertEquals(220f, tile.health, 0.001f)
        assertEquals(180f, tile.durability, 0.001f)
        assertEquals(220f, tile.maxHealth, 0.001f)
        assertEquals(180f, tile.maxDurability, 0.001f)
        assertEquals(220f, tile.currentHp, 0.001f)
        assertEquals(220f, tile.maxHp, 0.001f)
        assertEquals(1.0f, tile.healthPercentage, 0.001f)
        assertEquals(1.0f, tile.durabilityPercentage, 0.001f)
        assertFalse(tile.isDestroyed)
        assertFalse(tile.isWalkable)
    }

    @Test
    fun testVoxelTileDestructionMechanicsWithDurabilityMitigation() {
        val tile = VoxelTile(
            gridX = 2,
            gridY = 3,
            elevationZ = 1,
            type = VoxelType.LOW_COVER_CRATE,
            health = 100f,
            durability = 60f,
            maxHealth = 100f,
            maxDurability = 60f,
            isDestructible = true,
            coverHeight = CoverHeight.LOW
        )

        // Apply 50 damage with 0 penetration - durability mitigates portion of damage
        val damageDealt = tile.takeDamage(amount = 50f, armorPenetration = 0f)

        // Verify durability reduced
        assertTrue(tile.durability < 60f)
        // Verify health reduced
        assertTrue(tile.health < 100f)
        assertEquals(tile.health, tile.currentHp, 0.001f)
        assertFalse(tile.isDestroyed)

        // Deal fatal damage
        tile.takeDamage(amount = 500f, armorPenetration = 1.0f)
        assertEquals(0f, tile.health, 0.001f)
        assertTrue(tile.isDestroyed)
        assertTrue(tile.isWalkable)
    }

    @Test
    fun testVoxelTileBackwardCompatibilityAliases() {
        val tile = VoxelTile(
            gridX = 1,
            gridY = 1,
            type = VoxelType.REINFORCED_METAL,
            currentHp = 300f,
            maxHp = 300f
        )

        assertEquals(300f, tile.health, 0.001f)
        assertEquals(300f, tile.currentHp, 0.001f)
        assertEquals(300f, tile.durability, 0.001f)

        // Mutating currentHp updates health
        tile.currentHp = 150f
        assertEquals(150f, tile.health, 0.001f)

        // Mutating health updates currentHp
        tile.health = 80f
        assertEquals(80f, tile.currentHp, 0.001f)
    }
}
