package com.example.engine

import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Test

class FogOfWarSystemTest {

    @Test
    fun testDestructibleVoxelOcclusionAndDestruction() {
        val width = 20
        val height = 20
        val tileSize = 64f
        val terrain = VoxelTerrain(width = width, height = height, tileSize = tileSize)

        // Set up an opaque destructible high cover wall in between (5, 5) and (5, 10)
        terrain.tiles[5][7] = VoxelTile(
            gridX = 5,
            gridY = 7,
            elevationZ = 2,
            type = VoxelType.CONCRETE_WALL,
            currentHp = 250f,
            maxHp = 250f,
            coverHeight = CoverHeight.HIGH
        )

        val fog = FogOfWarSystem(width = width, height = height, tileSize = tileSize)

        val startX = (5 + 0.5f) * tileSize
        val startY = (5 + 0.5f) * tileSize
        val targetX = (5 + 0.5f) * tileSize
        val targetY = (10 + 0.5f) * tileSize

        // Line of sight must be blocked by the intact concrete wall
        val hasLoSBlocked = fog.hasLineOfSight(startX, startY, targetX, targetY, terrain)
        assertFalse("Concrete wall should obstruct line-of-sight", hasLoSBlocked)

        // Now destroy the voxel (simulate projectile/explosive damage)
        terrain.tiles[5][7].currentHp = 0f
        terrain.tiles[5][7].isDisintegrated = true

        // Line of sight must now be restored through the destroyed voxel
        val hasLoSRestored = fog.hasLineOfSight(startX, startY, targetX, targetY, terrain)
        assertTrue("Destroyed voxel must restore line-of-sight", hasLoSRestored)
    }

    @Test
    fun testLowCoverStealthOcclusionForCrouchingUnits() {
        val width = 20
        val height = 20
        val tileSize = 64f
        val terrain = VoxelTerrain(width = width, height = height, tileSize = tileSize)

        // Place a low cover crate
        terrain.tiles[8][8] = VoxelTile(
            gridX = 8,
            gridY = 8,
            elevationZ = 1,
            type = VoxelType.LOW_COVER_CRATE,
            currentHp = 100f,
            maxHp = 100f,
            coverHeight = CoverHeight.LOW
        )

        val fog = FogOfWarSystem(width = width, height = height, tileSize = tileSize)

        val enemyX = (8 + 0.5f) * tileSize
        val enemyY = (4 + 0.5f) * tileSize
        val playerX = (8 + 0.5f) * tileSize
        val playerY = (9 + 0.5f) * tileSize // Player is immediately behind crate at tile (8, 8)

        // When player is STANDING, enemy has line-of-sight over low cover
        val losStanding = fog.hasLineOfSight(
            startX = enemyX,
            startY = enemyY,
            endX = playerX,
            endY = playerY,
            terrain = terrain,
            viewerStance = PlayerStance.STAND,
            targetCover = CoverHeight.NONE
        )
        assertTrue("Standing target should be visible over low cover", losStanding)

        // When player is CROUCHING behind the low cover crate, line-of-sight is obstructed
        val losCrouching = fog.hasLineOfSight(
            startX = enemyX,
            startY = enemyY,
            endX = playerX,
            endY = playerY,
            terrain = terrain,
            viewerStance = PlayerStance.STAND,
            targetCover = CoverHeight.LOW
        )
        assertFalse("Crouching target behind low cover crate must be concealed", losCrouching)
    }

    @Test
    fun testRadarPingRevealsHostilesInFog() {
        val width = 30
        val height = 30
        val tileSize = 64f
        val terrain = VoxelTerrain(width = width, height = height, tileSize = tileSize)
        val fog = FogOfWarSystem(width = width, height = height, tileSize = tileSize)

        val player = PlayerState(x = 100f, y = 100f, stance = PlayerStance.STAND)
        val concealedEnemy = Enemy(
            id = "test_stalker",
            name = "Test Stalker",
            type = EnemyType.SNIPER_STALKER,
            x = 350f,
            y = 100f,
            health = 100f,
            maxHealth = 100f,
            isVisibleInFog = false,
            radarPingAlpha = 0f
        )

        // Trigger radar ping from player position with 600px range
        fog.triggerRadarPing(player.x, player.y, maxRadius = 600f)
        assertEquals(1, fog.activeRadarPings.size)

        // Simulate ping expansion over time
        val ping = fog.activeRadarPings[0]
        ping.currentRadius = 250f // Ping wave reaches enemy distance (250px away)

        fog.updateVisibility(
            player = player,
            squad = emptyList(),
            enemies = listOf(concealedEnemy),
            terrain = terrain,
            deltaSec = 0.016f
        )

        // Verify radar ping highlighted the enemy
        assertTrue("Hostile in radar wave path must be pinged", concealedEnemy.radarPingAlpha > 0f)
    }

    @Test
    fun testTacticalStealthAmbushEvaluation() {
        val width = 25
        val height = 25
        val tileSize = 64f
        val terrain = VoxelTerrain(width = width, height = height, tileSize = tileSize)
        val fog = FogOfWarSystem(width = width, height = height, tileSize = tileSize)

        // High wall blocking sight between enemy and player
        terrain.tiles[10][10] = VoxelTile(
            gridX = 10,
            gridY = 10,
            type = VoxelType.HIGH_COVER_WALL,
            currentHp = 300f,
            maxHp = 300f,
            coverHeight = CoverHeight.HIGH
        )

        val player = PlayerState(
            x = (10 + 0.5f) * tileSize,
            y = (12 + 0.5f) * tileSize,
            stance = PlayerStance.CROUCH,
            stealthNoiseRadius = 20f
        )
        val enemy = Enemy(
            id = "patrol_grunt",
            name = "Patrol Grunt",
            type = EnemyType.GRUNT,
            x = (10 + 0.5f) * tileSize,
            y = (8 + 0.5f) * tileSize,
            health = 100f,
            maxHealth = 100f,
            state = AIState.PATROL,
            facingAngle = Math.PI.toFloat() / 2f // Facing down towards player, but blocked by wall
        )

        fog.updateVisibility(
            player = player,
            squad = emptyList(),
            enemies = listOf(enemy),
            terrain = terrain,
            deltaSec = 0.016f
        )

        val eval = fog.stealthEvaluation
        assertEquals("Player hidden behind wall must have HIDDEN stealth status", StealthStatus.HIDDEN, eval.status)
        assertTrue("Ambush critical hit should be ready when hidden", eval.isAmbushReady)
        assertEquals(0, eval.detectingEnemiesCount)
        assertFalse("Enemy should not have direct line of sight", enemy.hasDirectLineOfSightToPlayer)
    }
}
