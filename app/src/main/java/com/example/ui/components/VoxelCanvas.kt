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
import com.example.engine.VoxelMaterialShader
import com.example.engine.VoxelTerrain
import com.example.ui.theme.*
import kotlin.math.*
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
                textMeasurer = textMeasurer,
                dynamicLights = gameState.dynamicLights
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

                // Enemy Cover Snap Field
                if (enemy.isCoverSnapped) {
                    val nx = enemy.coverSnapNormalX
                    val ny = enemy.coverSnapNormalY
                    val tangentX = -ny
                    val tangentY = nx

                    val contactStartX = enemy.x - nx * 4f + tangentX * 16f
                    val contactStartY = enemy.y - ny * 4f + tangentY * 16f
                    val contactEndX = enemy.x - nx * 4f - tangentX * 16f
                    val contactEndY = enemy.y - ny * 4f - tangentY * 16f

                    drawLine(
                        color = ShieldBlue,
                        start = Offset(contactStartX, contactStartY),
                        end = Offset(contactEndX, contactEndY),
                        strokeWidth = 3.5f
                    )

                    drawCircle(
                        color = ShieldBlue,
                        radius = 19f,
                        center = Offset(enemy.x, enemy.y),
                        style = Stroke(width = 1.8f)
                    )
                }

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

                // AI State Tag & Alert
                if (enemy.state != AIState.PATROL) {
                    val stateTag = when (enemy.state) {
                        AIState.FLANKING -> "FLANK"
                        AIState.SEEKING_COVER -> "COVER"
                        AIState.ENGAGED -> "ENGAGE"
                        AIState.RETREAT -> "RETREAT"
                        AIState.SUSPICIOUS, AIState.INVESTIGATING -> "ALERT"
                        else -> "!"
                    }
                    val alertColor = when (enemy.state) {
                        AIState.FLANKING -> PlasmaPink
                        AIState.SEEKING_COVER -> ShieldBlue
                        AIState.ENGAGED -> LaserRed
                        AIState.RETREAT -> HazardYellow
                        else -> HazardYellow
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "[$stateTag]",
                        style = TextStyle(color = alertColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        topLeft = Offset(enemy.x - 22f, enemy.y - 42f)
                    )
                }

                // Render active tactical navigation path waypoints if tactical overlay enabled or flanking
                if (gameState.isTacticalGridOverlayEnabled && enemy.activePath.isNotEmpty()) {
                    var prevPt = Offset(enemy.x, enemy.y)
                    for (idx in enemy.activePathIndex until enemy.activePath.size) {
                        val pt = Offset(enemy.activePath[idx].first, enemy.activePath[idx].second)
                        drawLine(
                            color = PlasmaPink.copy(alpha = 0.55f),
                            start = prevPt,
                            end = pt,
                            strokeWidth = 2f
                        )
                        drawCircle(
                            color = PlasmaPink.copy(alpha = 0.7f),
                            radius = 3f,
                            center = pt
                        )
                        prevPt = pt
                    }
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
                when (p.type) {
                    ParticleType.HIT_NUMBER -> {
                        drawText(
                            textMeasurer = textMeasurer,
                            text = p.text,
                            style = TextStyle(color = p.color.copy(alpha = p.life), fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            topLeft = Offset(p.x, p.y)
                        )
                    }
                    ParticleType.DEBRIS_VOXEL -> {
                        // Flying tumbling voxel block chunk
                        withTransform({
                            translate(left = p.x, top = p.y)
                            rotate(degrees = p.rotation, pivot = Offset.Zero)
                        }) {
                            val w = p.size * p.aspectRatio
                            val h = p.size
                            drawRoundRect(
                                color = p.color.copy(alpha = p.life),
                                topLeft = Offset(-w / 2f, -h / 2f),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(2f)
                            )
                            drawRoundRect(
                                color = Color.White.copy(alpha = p.life * 0.6f),
                                topLeft = Offset(-w / 2f, -h / 2f),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(2f),
                                style = Stroke(width = 1f)
                            )
                        }
                    }
                    ParticleType.PLASMA_SPARK -> {
                        // High-speed energy spark streak
                        drawLine(
                            color = p.color.copy(alpha = p.life),
                            start = Offset(p.x, p.y),
                            end = Offset(p.x - p.vx * 0.035f, p.y - p.vy * 0.035f),
                            strokeWidth = 2.5f
                        )
                    }
                    ParticleType.SMOKE_NANO -> {
                        // Soft expanding particulate dust cloud
                        drawCircle(
                            color = p.color.copy(alpha = (p.life * 0.35f).coerceIn(0f, 1f)),
                            radius = p.size,
                            center = Offset(p.x, p.y)
                        )
                    }
                    else -> {
                        drawCircle(
                            color = p.color.copy(alpha = p.life),
                            radius = p.size * p.life,
                            center = Offset(p.x, p.y)
                        )
                    }
                }
            }

            // 7. Render Real-Time Dynamic Light Bloom & Explosion Shockwave Pass
            drawDynamicLightBloomPass(gameState.dynamicLights)

            // 7. Render Player Character & Aiming Mechanics
            if (player.isAlive) {
                // Render Ricochet Trajectory Preview Line
                if (gameState.ricochetTrajectoryPoints.size >= 2) {
                    for (i in 0 until gameState.ricochetTrajectoryPoints.size - 1) {
                        val p1 = gameState.ricochetTrajectoryPoints[i]
                        val p2 = gameState.ricochetTrajectoryPoints[i + 1]
                        val alpha = (0.8f - i * 0.22f).coerceAtLeast(0.3f)
                        val segColor = if (i == 0) NanoCyan else HazardYellow

                        drawLine(
                            color = segColor.copy(alpha = alpha),
                            start = Offset(p1.first, p1.second),
                            end = Offset(p2.first, p2.second),
                            strokeWidth = if (i == 0) 2.5f else 2f
                        )

                        // Draw ricochet bounce node circle
                        if (i > 0) {
                            drawCircle(
                                color = HazardYellow,
                                radius = 5.5f,
                                center = Offset(p1.first, p1.second)
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 2.5f,
                                center = Offset(p1.first, p1.second)
                            )
                        }
                    }
                } else {
                    // Standard Laser Aiming Guide Line
                    val aimEndX = player.x + cos(player.aimAngle) * 400f
                    val aimEndY = player.y + sin(player.aimAngle) * 400f
                    drawLine(
                        color = NanoCyan.copy(alpha = 0.45f),
                        start = Offset(player.x, player.y),
                        end = Offset(aimEndX, aimEndY),
                        strokeWidth = 2f
                    )
                }

                // Render Auto-Aim Lock Target Reticle
                if (player.isAutoAimLocked && player.autoAimTargetPos != null) {
                    val tx = player.autoAimTargetPos!!.first
                    val ty = player.autoAimTargetPos!!.second

                    // Lock-On Connecting Laser Ray
                    drawLine(
                        color = PlasmaPink.copy(alpha = 0.85f),
                        start = Offset(player.x, player.y),
                        end = Offset(tx, ty),
                        strokeWidth = 2.5f
                    )

                    // Animated Target Brackets around locked enemy
                    val reticleRadius = 28f
                    val bracketLen = 12f

                    val topLeft = Offset(tx - reticleRadius, ty - reticleRadius)
                    val topRight = Offset(tx + reticleRadius, ty - reticleRadius)
                    val bottomLeft = Offset(tx - reticleRadius, ty + reticleRadius)
                    val bottomRight = Offset(tx + reticleRadius, ty + reticleRadius)

                    drawLine(PlasmaPink, topLeft, Offset(topLeft.x + bracketLen, topLeft.y), strokeWidth = 3f)
                    drawLine(PlasmaPink, topLeft, Offset(topLeft.x, topLeft.y + bracketLen), strokeWidth = 3f)

                    drawLine(PlasmaPink, topRight, Offset(topRight.x - bracketLen, topRight.y), strokeWidth = 3f)
                    drawLine(PlasmaPink, topRight, Offset(topRight.x, topRight.y + bracketLen), strokeWidth = 3f)

                    drawLine(PlasmaPink, bottomLeft, Offset(bottomLeft.x + bracketLen, bottomLeft.y), strokeWidth = 3f)
                    drawLine(PlasmaPink, bottomLeft, Offset(bottomLeft.x, bottomLeft.y - bracketLen), strokeWidth = 3f)

                    drawLine(PlasmaPink, bottomRight, Offset(bottomRight.x - bracketLen, bottomRight.y), strokeWidth = 3f)
                    drawLine(PlasmaPink, bottomRight, Offset(bottomRight.x, bottomRight.y - bracketLen), strokeWidth = 3f)

                    drawCircle(color = PlasmaPink.copy(alpha = 0.35f), radius = reticleRadius, center = Offset(tx, ty), style = Stroke(width = 1.5f))
                    drawCircle(color = PlasmaPink, radius = 4f, center = Offset(tx, ty))

                    drawText(
                        textMeasurer = textMeasurer,
                        text = "AUTOAIM [${player.autoAimMode.name}]",
                        style = TextStyle(color = PlasmaPink, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        topLeft = Offset(tx - 36f, ty - 45f)
                    )
                }

                // Cover Snap Surface Contact Bar & Barrier Shield Arc
                if (player.isCoverSnapped) {
                    val nx = player.coverSnapNormalX
                    val ny = player.coverSnapNormalY
                    val tangentX = -ny
                    val tangentY = nx

                    // Contact Beam flush on voxel face
                    val contactStartX = player.x - nx * 4f + tangentX * 18f
                    val contactStartY = player.y - ny * 4f + tangentY * 18f
                    val contactEndX = player.x - nx * 4f - tangentX * 18f
                    val contactEndY = player.y - ny * 4f - tangentY * 18f

                    drawLine(
                        color = NaniteGreen,
                        start = Offset(contactStartX, contactStartY),
                        end = Offset(contactEndX, contactEndY),
                        strokeWidth = 4f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.8f),
                        start = Offset(contactStartX, contactStartY),
                        end = Offset(contactEndX, contactEndY),
                        strokeWidth = 2f
                    )

                    // Defensive Barrier Arc facing away from obstacle face
                    val awayAngleRad = atan2(-ny, -nx)
                    val awayAngleDeg = Math.toDegrees(awayAngleRad.toDouble()).toFloat()
                    val pulseAlpha = 0.6f + sin(player.coverAnimPulse.toDouble()).toFloat() * 0.25f

                    drawArc(
                        color = NaniteGreen.copy(alpha = pulseAlpha),
                        startAngle = awayAngleDeg - 60f,
                        sweepAngle = 120f,
                        useCenter = false,
                        topLeft = Offset(player.x - 24f, player.y - 24f),
                        size = Size(48f, 48f),
                        style = Stroke(width = 3.5f)
                    )
                }

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
                if (player.isCoverSnapped) {
                    drawCircle(
                        color = NaniteGreen,
                        radius = 21f,
                        center = Offset(player.x, player.y),
                        style = Stroke(width = 2f)
                    )
                }
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
                    val coverTxt = if (player.isCoverSnapped) {
                        "COVER LOCKED (${if (player.coverHeight == CoverHeight.HIGH) "90%" else "50%"})"
                    } else {
                        if (player.coverHeight == CoverHeight.HIGH) "HIGH COVER (90%)" else "LOW COVER (50%)"
                    }
                    val badgeColor = if (player.isCoverSnapped) NaniteGreen else NanoCyan
                    drawSafeText(
                        textMeasurer = textMeasurer,
                        text = coverTxt,
                        style = TextStyle(color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        topLeft = Offset(player.x - 42f, player.y - 38f)
                    )
                }

                // 8. Render World-Space Objective Zones, Terminal Rings & Beacons
                drawWorldObjectiveOverlays(gameState = gameState, textMeasurer = textMeasurer)
            }
        }

        // 9. Screen-Space Offscreen Objective Waypoint Directional Indicators
        drawOffscreenObjectiveWaypoints(
            gameState = gameState,
            cameraX = cameraX,
            cameraY = cameraY,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            textMeasurer = textMeasurer
        )
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
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    dynamicLights: List<DynamicLight> = emptyList()
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

            // Real-time Dynamic Lighting Accumulation for current Voxel Tile
            var addR = 0f
            var addG = 0f
            var addB = 0f
            var totalLightIntensity = 0f
            var lightDirX = 0f
            var lightDirY = 0f
            var maxLightFalloff = 0f

            for (light in dynamicLights) {
                val lx = tileCenterX - light.x
                val ly = tileCenterY - light.y
                val distSq = lx * lx + ly * ly
                val radSq = light.radius * light.radius
                if (distSq < radSq) {
                    val dist = sqrt(distSq)
                    val normDist = (1.0f - dist / light.radius).coerceIn(0f, 1f)
                    val falloff = normDist * normDist * light.intensity
                    if (falloff > 0.005f) {
                        addR += light.color.red * falloff
                        addG += light.color.green * falloff
                        addB += light.color.blue * falloff
                        totalLightIntensity += falloff

                        if (falloff > maxLightFalloff && dist > 0.1f) {
                            maxLightFalloff = falloff
                            lightDirX = -lx / dist
                            lightDirY = -ly / dist
                        }
                    }
                }
            }

            // Base Floor Color
            val baseFloorColor = when (tile.type) {
                VoxelType.FLOOR_PLAZA -> SlateCard
                VoxelType.ACID_POOL -> Color(0xFF052E16)
                else -> VoidDark
            }

            val floorColor = if (totalLightIntensity > 0f) {
                Color(
                    red = (baseFloorColor.red + addR * 0.65f).coerceIn(0f, 1f),
                    green = (baseFloorColor.green + addG * 0.65f).coerceIn(0f, 1f),
                    blue = (baseFloorColor.blue + addB * 0.65f).coerceIn(0f, 1f),
                    alpha = baseFloorColor.alpha
                )
            } else baseFloorColor

            drawRoundRect(
                color = floorColor,
                topLeft = Offset(worldX, worldY),
                size = Size(terrain.tileSize - 2f, terrain.tileSize - 2f),
                cornerRadius = CornerRadius(4f)
            )

            // Dynamic Light Floor Inner Glow Overlay
            if (totalLightIntensity > 0.04f) {
                drawRoundRect(
                    color = Color(
                        red = addR.coerceIn(0f, 1f),
                        green = addG.coerceIn(0f, 1f),
                        blue = addB.coerceIn(0f, 1f)
                    ).copy(alpha = (totalLightIntensity * 0.35f).coerceAtMost(0.6f)),
                    topLeft = Offset(worldX + 2f, worldY + 2f),
                    size = Size(terrain.tileSize - 4f, terrain.tileSize - 4f),
                    cornerRadius = CornerRadius(4f)
                )
            }

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

            // Voxel Elevation Blocks (LOD rendering with Dynamic Lighting, Mesh Deformation & Fractures)
            if (tile.coverHeight != CoverHeight.NONE && !tile.isDisintegrated) {
                val baseBlockColor = when (tile.type) {
                    VoxelType.LOW_COVER_CRATE -> Color(0xFF1E293B)
                    VoxelType.HIGH_COVER_WALL -> Color(0xFF0F172A)
                    VoxelType.EXPLOSIVE_BARREL -> Color(0xFF7F1D1D)
                    VoxelType.ENERGY_BARRIER -> Color(0xFF0284C7)
                    VoxelType.DESTRUCTIBLE_PILLAR -> Color(0xFF334155)
                    VoxelType.OBJECTIVE_NODE -> Color(0xFF581C87)
                    else -> SlateCard
                }

                val blockColor = if (totalLightIntensity > 0f) {
                    Color(
                        red = (baseBlockColor.red + addR * 0.75f).coerceIn(0f, 1f),
                        green = (baseBlockColor.green + addG * 0.75f).coerceIn(0f, 1f),
                        blue = (baseBlockColor.blue + addB * 0.75f).coerceIn(0f, 1f),
                        alpha = baseBlockColor.alpha
                    )
                } else baseBlockColor

                val strokeColor = when (tile.type) {
                    VoxelType.EXPLOSIVE_BARREL -> HazardYellow
                    VoxelType.ENERGY_BARRIER -> NanoCyan
                    VoxelType.OBJECTIVE_NODE -> PlasmaPink
                    else -> NanoCyanDim
                }

                val heightOffset = tile.elevationZ * 12f
                val blockCenterX = worldX + terrain.tileSize / 2f + (if (tile.lodLevel == 0) tile.deformationX else 0f)
                val blockCenterY = worldY + terrain.tileSize / 2f + (if (tile.lodLevel == 0) tile.deformationY else 0f) - heightOffset

                val blockW = terrain.tileSize - 8f
                val blockH = terrain.tileSize - 8f
                val halfW = blockW / 2f
                val halfH = blockH / 2f

                when (tile.lodLevel) {
                    0 -> {
                        // LOD 0 (Ultra Detail): Shader Material Strategy, Dynamic Lighting, Deformation, Cracks, Hit Flash, Matrix Transforms
                        withTransform({
                            translate(left = blockCenterX, top = blockCenterY)
                            rotate(degrees = Math.toDegrees(tile.rotationAngle.toDouble()).toFloat(), pivot = Offset.Zero)
                            scale(scaleX = tile.meshScaleX, scaleY = tile.meshScaleY, pivot = Offset.Zero)
                        }) {
                            // Render Shader-based Material Surface (Metal, Concrete, Alien Biomass, Energy Plasma, Volatile Hazard)
                            VoxelMaterialShader.drawBlockShader(
                                drawScope = this,
                                halfW = halfW,
                                halfH = halfH,
                                tile = tile,
                                lightIntensity = totalLightIntensity,
                                lightDirX = lightDirX,
                                lightDirY = lightDirY,
                                addR = addR,
                                addG = addG,
                                addB = addB
                            )

                            // Damage Fracture Crack Lines
                            if (tile.damageCracksCount > 0) {
                                val crackColor = HazardYellow.copy(alpha = 0.85f)
                                val numCracks = tile.damageCracksCount.coerceAtMost(5)
                                for (c in 0 until numCracks) {
                                    val startX = (-halfW + 8f + c * 10f).coerceIn(-halfW, halfW)
                                    val startY = -halfH + 4f
                                    val midX = startX + if (c % 2 == 0) 10f else -8f
                                    val midY = 0f
                                    val endX = startX + if (c % 3 == 0) -6f else 12f
                                    val endY = halfH - 4f

                                    drawLine(crackColor, Offset(startX, startY), Offset(midX, midY), strokeWidth = 1.8f)
                                    drawLine(crackColor, Offset(midX, midY), Offset(endX, endY), strokeWidth = 1.2f)
                                }
                            }

                            // Impact Hit Flash Glow Overlay
                            if (tile.hitFlashTimer > 0f) {
                                drawRoundRect(
                                    color = Color.White.copy(alpha = tile.hitFlashTimer * 0.65f),
                                    topLeft = Offset(-halfW, -halfH),
                                    size = Size(blockW, blockH),
                                    cornerRadius = CornerRadius(6f)
                                )
                                drawRoundRect(
                                    color = NanoCyan.copy(alpha = tile.hitFlashTimer),
                                    topLeft = Offset(-halfW, -halfH),
                                    size = Size(blockW, blockH),
                                    cornerRadius = CornerRadius(6f),
                                    style = Stroke(width = 3.5f)
                                )
                            }

                            // HP Bar if damaged
                            if (tile.currentHp < tile.maxHp) {
                                val hpPct = (tile.currentHp / tile.maxHp).coerceIn(0f, 1f)
                                drawRect(
                                    color = Color.Black,
                                    topLeft = Offset(-halfW + 4f, -halfH + 4f),
                                    size = Size(blockW - 8f, 4f)
                                )
                                drawRect(
                                    color = HazardYellow,
                                    topLeft = Offset(-halfW + 4f, -halfH + 4f),
                                    size = Size((blockW - 8f) * hpPct, 4f)
                                )
                            }
                        }
                    }
                    1 -> {
                        // LOD 1 (Medium Detail): Standard voxel bevels & HP bar
                        drawRoundRect(
                            color = blockColor,
                            topLeft = Offset(worldX + 4f, worldY + 4f - heightOffset),
                            size = Size(blockW, blockH),
                            cornerRadius = CornerRadius(4f)
                        )
                        drawRoundRect(
                            color = strokeColor,
                            topLeft = Offset(worldX + 4f, worldY + 4f - heightOffset),
                            size = Size(blockW, blockH),
                            cornerRadius = CornerRadius(4f),
                            style = Stroke(width = 1.5f)
                        )
                        if (tile.currentHp < tile.maxHp) {
                            val hpPct = (tile.currentHp / tile.maxHp).coerceIn(0f, 1f)
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(worldX + 6f, worldY + 6f - heightOffset),
                                size = Size(terrain.tileSize - 12f, 3f)
                            )
                            drawRect(
                                color = HazardYellow,
                                topLeft = Offset(worldX + 6f, worldY + 6f - heightOffset),
                                size = Size((terrain.tileSize - 12f) * hpPct, 3f)
                            )
                        }
                    }
                    else -> {
                        // LOD 2 (Macro SVDAG Detail): Condensed fast-rendering quad representation
                        drawRect(
                            color = blockColor.copy(alpha = 0.85f),
                            topLeft = Offset(worldX + 2f, worldY + 2f - heightOffset),
                            size = Size(terrain.tileSize - 4f, terrain.tileSize - 4f)
                        )
                        drawRect(
                            color = strokeColor.copy(alpha = 0.5f),
                            topLeft = Offset(worldX + 2f, worldY + 2f - heightOffset),
                            size = Size(terrain.tileSize - 4f, terrain.tileSize - 4f),
                            style = Stroke(width = 1f)
                        )
                    }
                }

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

private fun DrawScope.drawDynamicLightBloomPass(dynamicLights: List<DynamicLight>) {
    for (light in dynamicLights) {
        if (light.intensity <= 0.01f || light.radius <= 1f) continue

        when (light.type) {
            DynamicLightType.EXPLOSION_BURST -> {
                val pulseAlpha = (light.life * 0.85f).coerceIn(0f, 0.95f)
                // Expanding shockwave ring
                drawCircle(
                    color = light.color.copy(alpha = pulseAlpha),
                    radius = light.radius,
                    center = Offset(light.x, light.y),
                    style = Stroke(width = (10f * light.life + 3f))
                )
                // White-hot shockwave rim
                drawCircle(
                    color = Color.White.copy(alpha = (light.life * 0.7f).coerceIn(0f, 0.9f)),
                    radius = (light.radius * 0.85f).coerceAtLeast(2f),
                    center = Offset(light.x, light.y),
                    style = Stroke(width = (5f * light.life + 1.5f))
                )
                // Explosion thermal core bloom
                drawCircle(
                    color = light.color.copy(alpha = (light.life * 0.4f).coerceIn(0f, 0.6f)),
                    radius = light.radius * 0.45f,
                    center = Offset(light.x, light.y)
                )
                drawCircle(
                    color = Color.White.copy(alpha = (light.life * 0.8f).coerceIn(0f, 0.9f)),
                    radius = light.radius * 0.2f,
                    center = Offset(light.x, light.y)
                )
            }
            DynamicLightType.IMPACT_FLASH, DynamicLightType.MUZZLE_FLASH -> {
                drawCircle(
                    color = light.color.copy(alpha = (light.life * 0.5f).coerceIn(0f, 0.7f)),
                    radius = light.radius * 0.65f,
                    center = Offset(light.x, light.y)
                )
                drawCircle(
                    color = Color.White.copy(alpha = (light.life * 0.8f).coerceIn(0f, 0.9f)),
                    radius = light.radius * 0.3f,
                    center = Offset(light.x, light.y)
                )
            }
            DynamicLightType.PROJECTILE_BULLET -> {
                drawCircle(
                    color = light.color.copy(alpha = 0.25f),
                    radius = light.radius * 0.7f,
                    center = Offset(light.x, light.y)
                )
            }
            DynamicLightType.ENVIRONMENTAL_EMITTER -> {
                drawCircle(
                    color = light.color.copy(alpha = 0.18f * light.intensity),
                    radius = light.radius * 0.85f,
                    center = Offset(light.x, light.y)
                )
                drawCircle(
                    color = light.color.copy(alpha = 0.35f * light.intensity),
                    radius = light.radius * 0.35f,
                    center = Offset(light.x, light.y)
                )
            }
        }
    }
}

/**
 * Renders world-space objective overlays including defense zone perimeters,
 * terminal health progress rings, pulsing beacons, and target badges.
 */
private fun DrawScope.drawWorldObjectiveOverlays(
    gameState: GameState,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val now = gameState.missionTimeMs
    val pulseFactor = (sin(now / 220.0) * 0.5 + 0.5).toFloat()

    for (obj in gameState.objectives) {
        if (obj.status == ObjectiveStatus.COMPLETED || obj.status == ObjectiveStatus.FAILED) continue
        val tx = obj.targetWorldX ?: continue
        val ty = obj.targetWorldY ?: continue

        val primaryColor = if (obj.isPrimary) NanoCyan else HazardYellow

        when (obj.category) {
            ObjectiveCategory.DEFEND_TERMINAL -> {
                // 1. Pulsing Defense Zone Ground Ring
                val zoneRadius = obj.targetRadiusWorld
                drawCircle(
                    color = primaryColor.copy(alpha = 0.08f + pulseFactor * 0.06f),
                    radius = zoneRadius,
                    center = Offset(tx, ty)
                )
                drawCircle(
                    color = primaryColor.copy(alpha = 0.45f + pulseFactor * 0.35f),
                    radius = zoneRadius,
                    center = Offset(tx, ty),
                    style = Stroke(width = 2.5f)
                )

                // 2. Terminal Health & Timer Arc Rings
                val terminalHp = obj.terminalHpRatio
                val timerProgress = obj.timerProgressRatio

                drawArc(
                    color = NaniteGreen,
                    startAngle = -90f,
                    sweepAngle = 360f * terminalHp,
                    useCenter = false,
                    topLeft = Offset(tx - 36f, ty - 36f),
                    size = Size(72f, 72f),
                    style = Stroke(width = 4.5f)
                )
                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = 360f * timerProgress,
                    useCenter = false,
                    topLeft = Offset(tx - 44f, ty - 44f),
                    size = Size(88f, 88f),
                    style = Stroke(width = 3.0f)
                )

                // Central Beacon Ring
                drawCircle(
                    color = primaryColor,
                    radius = 16f + pulseFactor * 4f,
                    center = Offset(tx, ty),
                    style = Stroke(width = 2f)
                )

                // Objective Badge Label
                val timerSec = obj.timerRemainingSec?.toInt() ?: 0
                val hpPct = (terminalHp * 100).toInt()
                val badgeLabel = "[DEFEND TERMINAL] ${timerSec}s (${hpPct}% HP)"

                drawSafeText(
                    textMeasurer = textMeasurer,
                    text = badgeLabel,
                    style = TextStyle(color = primaryColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    topLeft = Offset(tx - 70f, ty - 65f)
                )
            }

            ObjectiveCategory.SABOTAGE_POWER_CORE -> {
                // Core Pulsing Zone
                drawCircle(
                    color = PlasmaPink.copy(alpha = 0.15f + pulseFactor * 0.10f),
                    radius = 80f,
                    center = Offset(tx, ty)
                )
                drawCircle(
                    color = PlasmaPink.copy(alpha = 0.6f + pulseFactor * 0.3f),
                    radius = 80f,
                    center = Offset(tx, ty),
                    style = Stroke(width = 3f)
                )

                drawSafeText(
                    textMeasurer = textMeasurer,
                    text = "🎯 [POWER CORE]",
                    style = TextStyle(color = PlasmaPink, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    topLeft = Offset(tx - 45f, ty - 42f)
                )
            }

            ObjectiveCategory.ELIMINATE_BOUNTY -> {
                // Target Locking Reticle on Warlord
                drawCircle(
                    color = LaserRed.copy(alpha = 0.4f + pulseFactor * 0.4f),
                    radius = 32f + pulseFactor * 8f,
                    center = Offset(tx, ty),
                    style = Stroke(width = 2.5f)
                )
                drawLine(
                    color = LaserRed,
                    start = Offset(tx - 42f, ty),
                    end = Offset(tx + 42f, ty),
                    strokeWidth = 1.5f
                )
                drawLine(
                    color = LaserRed,
                    start = Offset(tx, ty - 42f),
                    end = Offset(tx, ty + 42f),
                    strokeWidth = 1.5f
                )

                drawSafeText(
                    textMeasurer = textMeasurer,
                    text = "☠ [BOUNTY TARGET]",
                    style = TextStyle(color = LaserRed, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    topLeft = Offset(tx - 55f, ty - 56f)
                )
            }

            else -> {
                // Generic Waypoint Beacon
                drawCircle(
                    color = primaryColor.copy(alpha = 0.3f + pulseFactor * 0.3f),
                    radius = 24f + pulseFactor * 6f,
                    center = Offset(tx, ty),
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}

/**
 * Renders directional waypoint indicators along canvas edges for active objectives
 * located off-screen relative to camera view.
 */
private fun DrawScope.drawOffscreenObjectiveWaypoints(
    gameState: GameState,
    cameraX: Float,
    cameraY: Float,
    canvasWidth: Float,
    canvasHeight: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val player = gameState.player

    for (obj in gameState.objectives) {
        if (obj.status == ObjectiveStatus.COMPLETED || obj.status == ObjectiveStatus.FAILED) continue
        val tx = obj.targetWorldX ?: continue
        val ty = obj.targetWorldY ?: continue

        // Convert world target to screen space
        val targetScreenX = tx - cameraX
        val targetScreenY = ty - cameraY

        val margin = 50f
        val isOffscreen = targetScreenX < margin || targetScreenX > (canvasWidth - margin) ||
                targetScreenY < margin || targetScreenY > (canvasHeight - margin)

        if (isOffscreen) {
            val screenCenterX = canvasWidth / 2f
            val screenCenterY = canvasHeight / 2f

            val dx = targetScreenX - screenCenterX
            val dy = targetScreenY - screenCenterY
            val angle = atan2(dy, dx)

            // Calculate border intersection
            val clampedX = (targetScreenX).coerceIn(margin, canvasWidth - margin)
            val clampedY = (targetScreenY).coerceIn(margin, canvasHeight - margin)

            val indicatorColor = if (obj.isPrimary) NanoCyan else HazardYellow

            // Draw Waypoint Arrow Triangle
            val arrowPath = Path().apply {
                val headX = clampedX + cos(angle) * 14f
                val headY = clampedY + sin(angle) * 14f
                val leftX = clampedX + cos(angle + 2.5f) * 10f
                val leftY = clampedY + sin(angle + 2.5f) * 10f
                val rightX = clampedX + cos(angle - 2.5f) * 10f
                val rightY = clampedY + sin(angle - 2.5f) * 10f

                moveTo(headX, headY)
                lineTo(leftX, leftY)
                lineTo(rightX, rightY)
                close()
            }

            drawPath(path = arrowPath, color = indicatorColor)
            drawPath(path = arrowPath, color = Color.White, style = Stroke(width = 1.5f))

            // Distance in meters
            val distWorld = hypot(tx - player.x, ty - player.y)
            val distMeters = (distWorld / 64f * 5f).toInt() // 64f tile ~ 5 meters
            val label = "${obj.title} (${distMeters}m)"

            drawSafeText(
                textMeasurer = textMeasurer,
                text = label,
                style = TextStyle(color = indicatorColor, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                topLeft = Offset((clampedX - 40f).coerceIn(10f, canvasWidth - 140f), (clampedY + 12f).coerceIn(10f, canvasHeight - 20f))
            )
        }
    }
}

private fun DrawScope.drawSafeText(
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    style: TextStyle,
    topLeft: Offset
) {
    try {
        val layoutResult = textMeasurer.measure(
            text = androidx.compose.ui.text.AnnotatedString(text),
            style = style
        )
        drawText(
            textLayoutResult = layoutResult,
            topLeft = topLeft
        )
    } catch (_: Throwable) {}
}
