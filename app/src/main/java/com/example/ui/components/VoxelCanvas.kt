package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.engine.GameState
import com.example.engine.VoxelTerrain
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun VoxelCanvas(
    gameState: GameState,
    terrain: VoxelTerrain,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("voxel_game_canvas")
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height

        val player = gameState.player

        // Camera Offset (Keep player at screen center)
        val shakeOffset = if (gameState.screenShakeMs > 0) {
            Offset(
                (Random.nextFloat() * 12f - 6f),
                (Random.nextFloat() * 12f - 6f)
            )
        } else Offset.Zero

        val cameraX = player.x - canvasWidth / 2f + shakeOffset.x
        val cameraY = player.y - canvasHeight / 2f + shakeOffset.y

        withTransform({
            translate(left = -cameraX, top = -cameraY)
        }) {
            // 1. Render Ground Terrain Grid & Tactical Overlay
            drawTerrainGrid(
                terrain = terrain,
                cameraX = cameraX,
                cameraY = cameraY,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                playerX = player.x,
                playerY = player.y,
                isTacticalOverlayEnabled = gameState.isTacticalGridOverlayEnabled,
                textMeasurer = textMeasurer
            )

            // 1b. Render Tactical Line-of-Sight & Flanking combat overlay rays
            if (gameState.isTacticalGridOverlayEnabled && player.isAlive) {
                drawTacticalCombatOverlay(
                    gameState = gameState,
                    terrain = terrain,
                    textMeasurer = textMeasurer
                )
            }

            // 2. Render Stealth Noise Circle
            if (player.stealthNoiseRadius > 0) {
                drawCircle(
                    color = NanoCyan.copy(alpha = 0.08f),
                    radius = player.stealthNoiseRadius,
                    center = Offset(player.x, player.y)
                )
                drawCircle(
                    color = NanoCyan.copy(alpha = 0.25f),
                    radius = player.stealthNoiseRadius,
                    center = Offset(player.x, player.y),
                    style = Stroke(width = 1.5f)
                )
            }

            // 3. Render Enemies & Vision Cones
            for (enemy in gameState.enemies) {
                if (!enemy.isAlive) continue

                // Draw AI Vision Cone Fan
                val coneColor = when (enemy.state) {
                    AIState.PATROL -> NaniteGreen.copy(alpha = 0.18f)
                    AIState.SUSPICIOUS, AIState.INVESTIGATING -> HazardYellow.copy(alpha = 0.28f)
                    else -> LaserRed.copy(alpha = 0.35f)
                }

                drawVisionCone(
                    origin = Offset(enemy.x, enemy.y),
                    facingAngle = enemy.facingAngle,
                    range = enemy.visionRange,
                    fovAngleRad = enemy.visionAngleRad,
                    color = coneColor
                )

                // Draw Enemy Entity
                val enemyColor = when (enemy.type) {
                    EnemyType.GRUNT -> LaserRed
                    EnemyType.FLANKER -> PlasmaPink
                    EnemyType.SHIELD_ENFORCER -> ShieldBlue
                    EnemyType.SNIPER_STALKER -> HazardYellow
                    EnemyType.BOUNTY_BOSS -> NanoPurple
                }

                drawCircle(
                    color = enemyColor,
                    radius = 16f,
                    center = Offset(enemy.x, enemy.y)
                )

                // Facing Direction Pointer
                drawLine(
                    color = Color.White,
                    start = Offset(enemy.x, enemy.y),
                    end = Offset(enemy.x + cos(enemy.facingAngle) * 24f, enemy.y + sin(enemy.facingAngle) * 24f),
                    strokeWidth = 3f
                )

                // Shield Enforcer Arc
                if (enemy.shieldHp > 0) {
                    drawArc(
                        color = ShieldBlue,
                        startAngle = Math.toDegrees((enemy.facingAngle - 0.7f).toDouble()).toFloat(),
                        sweepAngle = 80f,
                        useCenter = false,
                        topLeft = Offset(enemy.x - 22f, enemy.y - 22f),
                        size = Size(44f, 44f),
                        style = Stroke(width = 5f)
                    )
                }

                // Health Bar
                val hpPct = (enemy.health / enemy.maxHealth).coerceIn(0f, 1f)
                drawRect(
                    color = Color.Black,
                    topLeft = Offset(enemy.x - 18f, enemy.y - 26f),
                    size = Size(36f, 5f)
                )
                drawRect(
                    color = if (hpPct > 0.5f) NaniteGreen else LaserRed,
                    topLeft = Offset(enemy.x - 18f, enemy.y - 26f),
                    size = Size(36f * hpPct, 5f)
                )

                // AI Alert Icon
                if (enemy.state != AIState.PATROL) {
                    val alertText = if (enemy.state == AIState.ENGAGED || enemy.state == AIState.FLANKING) "!" else "?"
                    val alertColor = if (alertText == "!") LaserRed else HazardYellow
                    drawText(
                        textMeasurer = textMeasurer,
                        text = alertText,
                        style = TextStyle(color = alertColor, fontSize = 16.sp),
                        topLeft = Offset(enemy.x - 4f, enemy.y - 48f)
                    )
                }
            }

            // 4. Render Throwables (Grenades in arc)
            for (t in gameState.throwables) {
                // Landing reticle
                drawCircle(
                    color = HazardYellow.copy(alpha = 0.4f),
                    radius = 40f,
                    center = Offset(t.targetX, t.targetY),
                    style = Stroke(width = 2f)
                )
                // Flying item
                drawCircle(
                    color = PlasmaPink,
                    radius = 8f,
                    center = Offset(t.x, t.y - sin(t.progress * Math.PI.toFloat()) * 60f)
                )
            }

            // 5. Render Bullets
            for (b in gameState.bullets) {
                drawLine(
                    color = b.color,
                    start = Offset(b.x, b.y),
                    end = Offset(b.x - b.vx * 0.04f, b.y - b.vy * 0.04f),
                    strokeWidth = 4f
                )
            }

            // 6. Render Particles & Floating Damage Numbers
            for (p in gameState.particles) {
                if (p.type == ParticleType.HIT_NUMBER) {
                    drawText(
                        textMeasurer = textMeasurer,
                        text = p.text,
                        style = TextStyle(color = p.color.copy(alpha = p.life), fontSize = 14.sp),
                        topLeft = Offset(p.x, p.y)
                    )
                } else {
                    drawCircle(
                        color = p.color.copy(alpha = p.life),
                        radius = p.size * p.life,
                        center = Offset(p.x, p.y)
                    )
                }
            }

            // 7. Render Player Character
            if (player.isAlive) {
                // Laser Aiming Guide Line
                val aimEndX = player.x + cos(player.aimAngle) * 400f
                val aimEndY = player.y + sin(player.aimAngle) * 400f
                drawLine(
                    color = NanoCyan.copy(alpha = 0.45f),
                    start = Offset(player.x, player.y),
                    end = Offset(aimEndX, aimEndY),
                    strokeWidth = 2f
                )

                // Player Body
                val playerColor = when (player.stance) {
                    PlayerStance.STAND -> NanoCyan
                    PlayerStance.CROUCH -> NanoCyanDim
                    PlayerStance.PRONE -> NanoPurple
                }

                drawCircle(
                    color = playerColor,
                    radius = if (player.stance == PlayerStance.PRONE) 12f else 18f,
                    center = Offset(player.x, player.y)
                )
                drawCircle(
                    color = Color.White,
                    radius = 6f,
                    center = Offset(player.x, player.y)
                )

                // Facing Arrow
                drawLine(
                    color = Color.White,
                    start = Offset(player.x, player.y),
                    end = Offset(player.x + cos(player.facingAngle) * 26f, player.y + sin(player.facingAngle) * 26f),
                    strokeWidth = 4f
                )

                // Cover Indicator Badge
                if (player.isBehindCover) {
                    val coverTxt = if (player.coverHeight == CoverHeight.HIGH) "HIGH COVER (90%)" else "LOW COVER (50%)"
                    drawText(
                        textMeasurer = textMeasurer,
                        text = coverTxt,
                        style = TextStyle(color = NaniteGreen, fontSize = 11.sp),
                        topLeft = Offset(player.x - 35f, player.y - 36f)
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawTerrainGrid(
    terrain: VoxelTerrain,
    cameraX: Float,
    cameraY: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    playerX: Float,
    playerY: Float,
    isTacticalOverlayEnabled: Boolean,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val startGx = (cameraX / terrain.tileSize).toInt().coerceIn(0, terrain.width - 1)
    val endGx = ((cameraX + canvasWidth) / terrain.tileSize).toInt().coerceAtMost(terrain.width - 1)
    val startGy = (cameraY / terrain.tileSize).toInt().coerceIn(0, terrain.height - 1)
    val endGy = ((cameraY + canvasHeight) / terrain.tileSize).toInt().coerceAtMost(terrain.height - 1)

    val playerSightRange = 520f

    for (gx in startGx..endGx) {
        for (gy in startGy..endGy) {
            val tile = terrain.tiles[gx][gy]
            val worldX = gx * terrain.tileSize
            val worldY = gy * terrain.tileSize
            val tileCenterX = worldX + terrain.tileSize / 2f
            val tileCenterY = worldY + terrain.tileSize / 2f

            val dxP = tileCenterX - playerX
            val dyP = tileCenterY - playerY
            val distToPlayer = kotlin.math.sqrt(dxP * dxP + dyP * dyP)

            val inLoSRange = distToPlayer <= playerSightRange
            val hasLoS = if (inLoSRange) {
                hasLineOfSight(playerX, playerY, tileCenterX, tileCenterY, terrain)
            } else false

            // Base Floor Color
            val floorColor = when (tile.type) {
                VoxelType.FLOOR_PLAZA -> SlateCard
                VoxelType.ACID_POOL -> Color(0xFF052E16)
                else -> VoidDark
            }

            drawRoundRect(
                color = floorColor,
                topLeft = Offset(worldX, worldY),
                size = Size(terrain.tileSize - 2f, terrain.tileSize - 2f),
                cornerRadius = CornerRadius(4f)
            )

            // Dynamic Tactical Grid Overlay
            if (isTacticalOverlayEnabled) {
                if (hasLoS) {
                    // Line of sight visible grid cell
                    drawRoundRect(
                        color = NanoCyan.copy(alpha = 0.07f),
                        topLeft = Offset(worldX + 1f, worldY + 1f),
                        size = Size(terrain.tileSize - 2f, terrain.tileSize - 2f),
                        cornerRadius = CornerRadius(4f)
                    )
                    drawRect(
                        color = NanoCyan.copy(alpha = 0.35f),
                        topLeft = Offset(worldX, worldY),
                        size = Size(terrain.tileSize, terrain.tileSize),
                        style = Stroke(width = 1f)
                    )

                    // Corner Ticks for cybernetic grid look
                    val tickLen = 6f
                    val tickColor = NanoCyan.copy(alpha = 0.6f)
                    // Top-Left corner
                    drawLine(tickColor, Offset(worldX, worldY), Offset(worldX + tickLen, worldY), strokeWidth = 1.5f)
                    drawLine(tickColor, Offset(worldX, worldY), Offset(worldX, worldY + tickLen), strokeWidth = 1.5f)
                    // Bottom-Right corner
                    drawLine(tickColor, Offset(worldX + terrain.tileSize, worldY + terrain.tileSize), Offset(worldX + terrain.tileSize - tickLen, worldY + terrain.tileSize), strokeWidth = 1.5f)
                    drawLine(tickColor, Offset(worldX + terrain.tileSize, worldY + terrain.tileSize), Offset(worldX + terrain.tileSize, worldY + terrain.tileSize - tickLen), strokeWidth = 1.5f)

                } else if (!inLoSRange) {
                    // Unseen Fog overlay
                    drawRect(
                        color = Color.Black.copy(alpha = 0.35f),
                        topLeft = Offset(worldX, worldY),
                        size = Size(terrain.tileSize, terrain.tileSize)
                    )
                    drawRect(
                        color = SlateBorder.copy(alpha = 0.15f),
                        topLeft = Offset(worldX, worldY),
                        size = Size(terrain.tileSize, terrain.tileSize),
                        style = Stroke(width = 1f)
                    )
                } else {
                    // Blocked sight shadow
                    drawRect(
                        color = Color.Black.copy(alpha = 0.5f),
                        topLeft = Offset(worldX, worldY),
                        size = Size(terrain.tileSize, terrain.tileSize)
                    )
                }
            } else {
                // Standard Voxel Grid Lines
                drawRect(
                    color = SlateBorder.copy(alpha = 0.3f),
                    topLeft = Offset(worldX, worldY),
                    size = Size(terrain.tileSize, terrain.tileSize),
                    style = Stroke(width = 1f)
                )
            }

            // Voxel Elevation Blocks (LOD rendering)
            if (tile.coverHeight != CoverHeight.NONE && !tile.isDisintegrated) {
                val blockColor = when (tile.type) {
                    VoxelType.LOW_COVER_CRATE -> Color(0xFF1E293B)
                    VoxelType.HIGH_COVER_WALL -> Color(0xFF0F172A)
                    VoxelType.EXPLOSIVE_BARREL -> Color(0xFF7F1D1D)
                    VoxelType.ENERGY_BARRIER -> Color(0xFF0284C7)
                    VoxelType.DESTRUCTIBLE_PILLAR -> Color(0xFF334155)
                    VoxelType.OBJECTIVE_NODE -> Color(0xFF581C87)
                    else -> SlateCard
                }

                val strokeColor = when (tile.type) {
                    VoxelType.EXPLOSIVE_BARREL -> HazardYellow
                    VoxelType.ENERGY_BARRIER -> NanoCyan
                    VoxelType.OBJECTIVE_NODE -> PlasmaPink
                    else -> NanoCyanDim
                }

                val heightOffset = tile.elevationZ * 12f

                // Top Face of Voxel Block
                drawRoundRect(
                    color = blockColor,
                    topLeft = Offset(worldX + 4f, worldY + 4f - heightOffset),
                    size = Size(terrain.tileSize - 8f, terrain.tileSize - 8f),
                    cornerRadius = CornerRadius(6f)
                )

                // Neon Outline Bevel
                drawRoundRect(
                    color = strokeColor,
                    topLeft = Offset(worldX + 4f, worldY + 4f - heightOffset),
                    size = Size(terrain.tileSize - 8f, terrain.tileSize - 8f),
                    cornerRadius = CornerRadius(6f),
                    style = Stroke(width = 2f)
                )

                // Cover Effectiveness Badge on Tile Block
                if (isTacticalOverlayEnabled && hasLoS) {
                    val coverLabel = when (tile.coverHeight) {
                        CoverHeight.HIGH -> "90% DEF"
                        CoverHeight.LOW -> "50% DEF"
                        else -> ""
                    }
                    val badgeColor = when (tile.coverHeight) {
                        CoverHeight.HIGH -> NaniteGreen
                        CoverHeight.LOW -> HazardYellow
                        else -> NanoCyan
                    }

                    if (coverLabel.isNotEmpty()) {
                        drawRoundRect(
                            color = VoidDark.copy(alpha = 0.85f),
                            topLeft = Offset(worldX + 6f, worldY + 8f - heightOffset),
                            size = Size(terrain.tileSize - 12f, 16f),
                            cornerRadius = CornerRadius(4f)
                        )
                        drawRoundRect(
                            color = badgeColor,
                            topLeft = Offset(worldX + 6f, worldY + 8f - heightOffset),
                            size = Size(terrain.tileSize - 12f, 16f),
                            cornerRadius = CornerRadius(4f),
                            style = Stroke(width = 1f)
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = coverLabel,
                            style = TextStyle(color = badgeColor, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            topLeft = Offset(worldX + 10f, worldY + 9f - heightOffset)
                        )
                    }
                }

                // HP Bar if damaged
                if (tile.currentHp < tile.maxHp) {
                    val hpPct = (tile.currentHp / tile.maxHp).coerceIn(0f, 1f)
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(worldX + 6f, worldY + 6f - heightOffset),
                        size = Size(terrain.tileSize - 12f, 4f)
                    )
                    drawRect(
                        color = HazardYellow,
                        topLeft = Offset(worldX + 6f, worldY + 6f - heightOffset),
                        size = Size((terrain.tileSize - 12f) * hpPct, 4f)
                    )
                }
            }
        }
    }
}

// Raycasting function for tactical line of sight
private fun hasLineOfSight(
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    terrain: VoxelTerrain
): Boolean {
    val steps = 14
    for (i in 1 until steps) {
        val t = i / steps.toFloat()
        val px = startX + (endX - startX) * t
        val py = startY + (endY - startY) * t
        val tile = terrain.getTileAtWorld(px, py)
        if (tile != null && (tile.type == VoxelType.HIGH_COVER_WALL || tile.type == VoxelType.DESTRUCTIBLE_PILLAR) && !tile.isDisintegrated) {
            return false
        }
    }
    return true
}

// Render tactical flanking lines, target ranges & LoS vector arcs
private fun DrawScope.drawTacticalCombatOverlay(
    gameState: GameState,
    terrain: VoxelTerrain,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val p = gameState.player

    for (e in gameState.enemies) {
        if (!e.isAlive) continue

        val dist = kotlin.math.sqrt((e.x - p.x) * (e.x - p.x) + (e.y - p.y) * (e.y - p.y))
        if (dist > 650f) continue

        val hasLoS = hasLineOfSight(p.x, p.y, e.x, e.y, terrain)
        if (!hasLoS) continue

        val angleToEnemy = kotlin.math.atan2(e.y - p.y, e.x - p.x)
        val angleFromEnemyToPlayer = kotlin.math.atan2(p.y - e.y, p.x - e.x)

        // Calculate if player is flanking enemy (behind enemy facing direction)
        val enemyFacingDiff = kotlin.math.abs(angleFromEnemyToPlayer - e.facingAngle)
        val normalizedDiff = (enemyFacingDiff % (2f * Math.PI.toFloat()))
        val isFlanking = normalizedDiff > (Math.PI.toFloat() / 2f) && normalizedDiff < (3f * Math.PI.toFloat() / 2f)

        val vectorColor = when {
            isFlanking -> NaniteEmerald
            e.isBehindCover -> HazardYellow
            else -> NanoCyan
        }

        // Tactical LoS Target Line
        drawLine(
            color = vectorColor.copy(alpha = 0.65f),
            start = Offset(p.x, p.y),
            end = Offset(e.x, e.y),
            strokeWidth = 1.5f,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
        )

        // Target Reticle Box around enemy
        drawRect(
            color = vectorColor,
            topLeft = Offset(e.x - 22f, e.y - 22f),
            size = Size(44f, 44f),
            style = Stroke(width = 1.5f)
        )

        // Flanking Badge / LoS Distance Tag
        val statusTag = when {
            isFlanking -> "FLANKED (180% CRIT)"
            e.isBehindCover -> "IN COVER (50% RED)"
            else -> "${dist.toInt()}m LoS"
        }

        drawRoundRect(
            color = VoidDark.copy(alpha = 0.9f),
            topLeft = Offset(e.x - 45f, e.y + 24f),
            size = Size(90f, 18f),
            cornerRadius = CornerRadius(4f)
        )
        drawRoundRect(
            color = vectorColor,
            topLeft = Offset(e.x - 45f, e.y + 24f),
            size = Size(90f, 18f),
            cornerRadius = CornerRadius(4f),
            style = Stroke(width = 1f)
        )
        drawText(
            textMeasurer = textMeasurer,
            text = statusTag,
            style = TextStyle(color = vectorColor, fontSize = 9.sp, fontWeight = FontWeight.Bold),
            topLeft = Offset(e.x - 40f, e.y + 26f)
        )
    }
}

private fun DrawScope.drawVisionCone(
    origin: Offset,
    facingAngle: Float,
    range: Float,
    fovAngleRad: Float,
    color: Color
) {
    val path = Path().apply {
        moveTo(origin.x, origin.y)
        val startAngle = facingAngle - fovAngleRad / 2f
        val endAngle = facingAngle + fovAngleRad / 2f
        val steps = 10
        for (i in 0..steps) {
            val a = startAngle + (endAngle - startAngle) * (i / steps.toFloat())
            val px = origin.x + cos(a) * range
            val py = origin.y + sin(a) * range
            lineTo(px, py)
        }
        close()
    }
    drawPath(path = path, color = color)
}
