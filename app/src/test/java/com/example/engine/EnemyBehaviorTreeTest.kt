package com.example.engine

import com.example.data.model.AIState
import com.example.data.model.CoverHeight
import com.example.data.model.Enemy
import com.example.data.model.EnemyType
import com.example.data.model.FlankDirection
import com.example.data.model.FlankManeuverType
import com.example.data.model.Particle
import com.example.data.model.PlayerMovementState
import com.example.data.model.PlayerState
import com.example.data.model.VoxelTile
import com.example.data.model.VoxelType
import com.example.engine.behavior.BTContext
import com.example.engine.behavior.BTNodeStatus
import com.example.engine.behavior.CoverSuppressionNode
import com.example.engine.behavior.DetectPlayerCoverNode
import com.example.engine.behavior.EnemyBehaviorTreeBuilder
import com.example.engine.behavior.EvaluateFlankStrategyNode
import com.example.engine.behavior.PlayerCoverAnalysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EnemyBehaviorTreeTest {

    private lateinit var terrain: VoxelTerrain
    private lateinit var worldManager: VoxelWorldManager
    private lateinit var coverSystem: CoverSystem

    @Before
    fun setup() {
        terrain = VoxelTerrain(width = 30, height = 30, tileSize = 32f)
        worldManager = VoxelWorldManager(width = 30, height = 30, maxDepth = 5, tileSize = 32f)
        coverSystem = CoverSystem()
    }

    @Test
    fun testDetectPlayerCoverNodeCalculatesFlankAngle() {
        // Player at (300, 300) snapped to cover facing Right (+X, normal = 1f, 0f)
        val player = PlayerState(
            x = 300f,
            y = 300f,
            isCoverSnapped = true,
            isBehindCover = true,
            coverTileX = 9,
            coverTileY = 9,
            coverSnapNormalX = 1f,
            coverSnapNormalY = 0f,
            coverHeight = CoverHeight.HIGH
        )

        // Enemy 1 directly in front of cover defense face (450, 300) -> NOT flanked
        val enemyFront = Enemy(
            id = "e_front",
            name = "Front",
            type = EnemyType.GRUNT,
            x = 450f,
            y = 300f,
            health = 100f,
            maxHealth = 100f
        )

        val ctxFront = BTContext(
            enemy = enemyFront,
            player = player,
            allEnemies = listOf(enemyFront),
            terrain = terrain,
            worldManager = worldManager,
            coverSystem = coverSystem,
            bullets = mutableListOf(),
            spawnedBullets = mutableListOf(),
            particles = mutableListOf(),
            soundList = mutableListOf(),
            muzzleFlashes = mutableListOf(),
            now = 1000L,
            deltaSec = 0.016f
        )

        val detectNode = DetectPlayerCoverNode()
        val statusFront = detectNode.tick(ctxFront)
        assertEquals(BTNodeStatus.SUCCESS, statusFront)

        val analysisFront = ctxFront.get<PlayerCoverAnalysis>("PLAYER_COVER_ANALYSIS")
        assertNotNull(analysisFront)
        assertFalse("Enemy in front of cover normal should not count as flanking", analysisFront!!.isExposedFlankToEnemy)
        assertFalse(enemyFront.isCoverFlanked)

        // Enemy 2 located at 90 degrees to cover normal (300, 480) -> EXPOSED FLANK!
        val enemyFlank = Enemy(
            id = "e_flank",
            name = "Flank",
            type = EnemyType.FLANKER,
            x = 300f,
            y = 480f,
            health = 100f,
            maxHealth = 100f
        )

        val ctxFlank = BTContext(
            enemy = enemyFlank,
            player = player,
            allEnemies = listOf(enemyFlank),
            terrain = terrain,
            worldManager = worldManager,
            coverSystem = coverSystem,
            bullets = mutableListOf(),
            spawnedBullets = mutableListOf(),
            particles = mutableListOf(),
            soundList = mutableListOf(),
            muzzleFlashes = mutableListOf(),
            now = 1000L,
            deltaSec = 0.016f
        )

        val statusFlank = detectNode.tick(ctxFlank)
        assertEquals(BTNodeStatus.SUCCESS, statusFlank)

        val analysisFlank = ctxFlank.get<PlayerCoverAnalysis>("PLAYER_COVER_ANALYSIS")
        assertNotNull(analysisFlank)
        assertTrue("Enemy at 90 degrees from cover normal should count as exposed flank", analysisFlank!!.isExposedFlankToEnemy)
        assertTrue(enemyFlank.isCoverFlanked)
    }

    @Test
    fun testFlankManeuverAdaptsToPlayerMovementStates() {
        val enemy = Enemy(
            id = "flanker1",
            name = "Flanker",
            type = EnemyType.FLANKER,
            x = 200f,
            y = 200f,
            health = 100f,
            maxHealth = 100f
        )

        // 1. Player is VAULTING -> should select INTERCEPT_VAULT
        val vaultingPlayer = PlayerState(
            x = 300f,
            y = 300f,
            movementState = PlayerMovementState.COVER_VAULTING,
            vaultProgress = 0.5f
        )
        val vaultTarget = VoxelPathfinder.calculateTacticalFlankTarget(terrain, enemy, vaultingPlayer)
        assertEquals(FlankManeuverType.INTERCEPT_VAULT, vaultTarget.maneuverType)

        // 2. Player is TRAVERSING along cover wall -> should select CUT_OFF_CORNER
        val traversingPlayer = PlayerState(
            x = 300f,
            y = 300f,
            vx = 2f,
            vy = 0f,
            movementState = PlayerMovementState.COVER_TRAVERSING,
            isCoverSnapped = true,
            coverSnapNormalX = 0f,
            coverSnapNormalY = -1f
        )
        val traverseTarget = VoxelPathfinder.calculateTacticalFlankTarget(terrain, enemy, traversingPlayer)
        assertEquals(FlankManeuverType.CUT_OFF_CORNER, traverseTarget.maneuverType)

        // 3. Player is PEEKING -> should select BLIND_SIDE_FLANK
        val peekingPlayer = PlayerState(
            x = 300f,
            y = 300f,
            movementState = PlayerMovementState.COVER_PEEKING,
            aimAngle = 0f,
            isCoverSnapped = true
        )
        val peekTarget = VoxelPathfinder.calculateTacticalFlankTarget(terrain, enemy, peekingPlayer)
        assertEquals(FlankManeuverType.BLIND_SIDE_FLANK, peekTarget.maneuverType)

        // 4. Player in High Cover -> WIDE_ARC_FLANK
        val highCoverPlayer = PlayerState(
            x = 300f,
            y = 300f,
            movementState = PlayerMovementState.COVER_SNAPPED,
            isCoverSnapped = true,
            coverHeight = CoverHeight.HIGH,
            coverSnapNormalX = 1f,
            coverSnapNormalY = 0f
        )
        val highCoverTarget = VoxelPathfinder.calculateTacticalFlankTarget(terrain, enemy, highCoverPlayer)
        assertEquals(FlankManeuverType.WIDE_ARC_FLANK, highCoverTarget.maneuverType)
    }

    @Test
    fun testPincerCoordinationBetweenAllies() {
        val player = PlayerState(
            x = 350f,
            y = 350f,
            isCoverSnapped = true,
            coverSnapNormalX = 1f,
            coverSnapNormalY = 0f,
            coverHeight = CoverHeight.HIGH
        )

        // Ally 1 is already flanking LEFT
        val ally1 = Enemy(
            id = "ally1",
            name = "Ally 1",
            type = EnemyType.FLANKER,
            x = 350f,
            y = 200f,
            health = 100f,
            maxHealth = 100f,
            state = AIState.FLANKING,
            flankDirection = FlankDirection.LEFT
        )

        // Enemy 2 evaluates flank target
        val enemy2 = Enemy(
            id = "enemy2",
            name = "Enemy 2",
            type = EnemyType.FLANKER,
            x = 350f,
            y = 480f,
            health = 100f,
            maxHealth = 100f
        )

        val target = VoxelPathfinder.calculateTacticalFlankTarget(
            terrain = terrain,
            enemy = enemy2,
            player = player,
            allEnemies = listOf(ally1, enemy2)
        )

        // Because ally1 is already flanking LEFT, enemy2 should choose RIGHT for a pincer maneuver
        assertEquals(FlankDirection.RIGHT, target.direction)
    }

    @Test
    fun testCoverSuppressionDamagesCoverTile() {
        // Place a destructible concrete cover block at grid (10, 10)
        terrain.tiles[10][10] = VoxelTile(
            gridX = 10,
            gridY = 10,
            type = VoxelType.CONCRETE_WALL,
            health = 50f,
            maxHealth = 50f,
            coverHeight = CoverHeight.HIGH
        )

        val player = PlayerState(
            x = 330f,
            y = 330f,
            isCoverSnapped = true,
            isBehindCover = true,
            coverTileX = 10,
            coverTileY = 10,
            coverHeight = CoverHeight.HIGH
        )

        val enforcer = Enemy(
            id = "enforcer",
            name = "Shield Enforcer",
            type = EnemyType.SHIELD_ENFORCER,
            x = 100f,
            y = 100f,
            health = 200f,
            maxHealth = 200f,
            weaponDamage = 20f
        )

        val bullets = mutableListOf<Bullet>()
        val spawnedBullets = mutableListOf<Bullet>()
        val particles = mutableListOf<Particle>()
        val sounds = mutableListOf<String>()

        val ctx = BTContext(
            enemy = enforcer,
            player = player,
            allEnemies = listOf(enforcer),
            terrain = terrain,
            worldManager = worldManager,
            coverSystem = coverSystem,
            bullets = bullets,
            spawnedBullets = spawnedBullets,
            particles = particles,
            soundList = sounds,
            muzzleFlashes = mutableListOf(),
            now = 2000L,
            deltaSec = 0.016f
        )

        // First detect player cover
        DetectPlayerCoverNode().tick(ctx)

        // Tick CoverSuppressionNode
        val suppressionNode = CoverSuppressionNode()
        val status = suppressionNode.tick(ctx)
        assertEquals(BTNodeStatus.RUNNING, status)

        // Verify state is SUPPRESSING, bullets fired, and cover tile took damage
        assertEquals(AIState.SUPPRESSING, enforcer.state)
        assertEquals("PIN/SUPPRESS", enforcer.tacticalManeuverLabel)
        assertTrue("Suppression bullets should be spawned", spawnedBullets.isNotEmpty())
        assertTrue("Concrete barrier tile should have taken damage", terrain.tiles[10][10].health < 50f)
        assertTrue("Debris particles should be spawned", particles.isNotEmpty())
    }

    @Test
    fun testEnemyBehaviorTreeBuilderTickExecution() {
        val player = PlayerState(
            x = 320f,
            y = 320f,
            isCoverSnapped = true,
            isBehindCover = true,
            coverTileX = 10,
            coverTileY = 10,
            coverHeight = CoverHeight.HIGH,
            coverSnapNormalX = 1f,
            coverSnapNormalY = 0f
        )

        val flanker = Enemy(
            id = "flanker_tree",
            name = "Flanker Tree",
            type = EnemyType.FLANKER,
            x = 150f,
            y = 150f,
            health = 100f,
            maxHealth = 100f,
            alertLevel = 60f
        )

        val ctx = BTContext(
            enemy = flanker,
            player = player,
            allEnemies = listOf(flanker),
            terrain = terrain,
            worldManager = worldManager,
            coverSystem = coverSystem,
            bullets = mutableListOf(),
            spawnedBullets = mutableListOf(),
            particles = mutableListOf(),
            soundList = mutableListOf(),
            muzzleFlashes = mutableListOf(),
            now = 3000L,
            deltaSec = 0.016f
        )

        val tree = EnemyBehaviorTreeBuilder.buildTreeFor(flanker.type)
        val status = tree.tick(ctx)

        // Should successfully execute tactical flank subtree
        assertTrue(status == BTNodeStatus.RUNNING || status == BTNodeStatus.SUCCESS)
        assertEquals(AIState.FLANKING, flanker.state)
        assertNotNull(flanker.tacticalManeuverLabel)
        assertTrue("Active path should be calculated towards flank waypoint", flanker.activePath.isNotEmpty())
    }
}
