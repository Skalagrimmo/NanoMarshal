package com.example.engine

import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Test

class EnvironmentalHazardSystemTest {

    @Test
    fun testHazardInitializationAndTrigger() {
        val terrain = VoxelTerrain(
            width = 30,
            height = 30,
            tileSize = 64f
        )
        // Add a nanite gas vent block
        terrain.tiles[10][10] = VoxelTile(
            gridX = 10,
            gridY = 10,
            elevationZ = 1,
            type = VoxelType.NANITE_GAS_VENT,
            currentHp = 100f,
            maxHp = 100f,
            coverHeight = CoverHeight.LOW
        )
        // Add an electric conduit block
        terrain.tiles[12][12] = VoxelTile(
            gridX = 12,
            gridY = 12,
            elevationZ = 1,
            type = VoxelType.ELECTRIC_CONDUIT,
            currentHp = 120f,
            maxHp = 120f,
            coverHeight = CoverHeight.LOW
        )

        val hazardSystem = EnvironmentalHazardSystem()
        hazardSystem.initializeFromTerrain(terrain)

        // Verify hazards were registered
        val player = PlayerState(x = 640f, y = 640f)
        val initialResult = hazardSystem.update(
            deltaSec = 0.016f,
            player = player,
            enemies = mutableListOf<Enemy>(),
            squad = emptyList(),
            terrain = terrain,
            bullets = mutableListOf<Bullet>(),
            particles = mutableListOf(),
            dynamicLights = mutableListOf()
        )

        assertEquals(2, initialResult.activeHazards.size)

        // Trigger damage on the gas vent block
        val destroyed = mutableListOf<Pair<Int, Int>>()
        val hitResult = hazardSystem.onVoxelDamaged(
            gx = 10,
            gy = 10,
            damage = 100f,
            damageType = WeaponDamageType.KINETIC,
            isPlayerBullet = true,
            terrain = terrain,
            enemies = mutableListOf<Enemy>(),
            player = player,
            spawnedParticles = mutableListOf(),
            spawnedLights = mutableListOf(),
            destroyedCoords = destroyed
        )

        assertTrue(hitResult)
        // Check that a gas cloud or active hazard status changed
        val updatedResult = hazardSystem.update(
            deltaSec = 0.1f,
            player = player,
            enemies = mutableListOf<Enemy>(),
            squad = emptyList(),
            terrain = terrain,
            bullets = mutableListOf<Bullet>(),
            particles = mutableListOf(),
            dynamicLights = mutableListOf()
        )
        assertTrue(updatedResult.activeGasClouds.isNotEmpty())
    }

    @Test
    fun testHazardManualInteraction() {
        val terrain = VoxelTerrain(
            width = 30,
            height = 30,
            tileSize = 64f
        )
        terrain.tiles[5][5] = VoxelTile(
            gridX = 5,
            gridY = 5,
            elevationZ = 1,
            type = VoxelType.CRYO_PIPE,
            currentHp = 80f,
            maxHp = 80f,
            coverHeight = CoverHeight.LOW
        )

        val hazardSystem = EnvironmentalHazardSystem()
        hazardSystem.initializeFromTerrain(terrain)

        val player = PlayerState(x = 5 * 64f + 32f, y = 5 * 64f + 32f)
        val result = hazardSystem.update(
            deltaSec = 0.016f,
            player = player,
            enemies = mutableListOf<Enemy>(),
            squad = emptyList(),
            terrain = terrain,
            bullets = mutableListOf<Bullet>(),
            particles = mutableListOf(),
            dynamicLights = mutableListOf()
        )

        assertNotNull(result.interactionPrompt)
        val prompt = result.interactionPrompt!!
        assertEquals(HazardType.CRYO_PIPE, prompt.type)

        val destroyed = mutableListOf<Pair<Int, Int>>()
        val interacted = hazardSystem.interactWithHazard(
            hazardId = prompt.hazardId,
            terrain = terrain,
            enemies = mutableListOf<Enemy>(),
            player = player,
            spawnedParticles = mutableListOf(),
            spawnedLights = mutableListOf(),
            destroyedCoords = destroyed
        )
        assertTrue(interacted)
    }
}
