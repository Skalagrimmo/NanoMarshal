package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.example.engine.GaussianElimination
import com.example.engine.GameState
import com.example.engine.OpenGlVboRenderer
import com.example.engine.StealthStatus
import com.example.engine.VoronoiDiagram
import com.example.engine.VoxelMaterialShader
import com.example.engine.VoxelTerrain
import com.example.ui.theme.*
import kotlin.math.*
import kotlin.random.Random

private val reusableVisionConePath = Path()
private val reusableWaypointArrowPath = Path()
private val AmberAccent = Color(0xFFF59E0B)

@Composable
fun VoxelCanvas(
    gameState: GameState,
    terrain: VoxelTerrain,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val openGlVboRenderer = remember { OpenGlVboRenderer() }

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
            // 1. Render Ground Terrain Grid & Tactical Overlay with Fog-of-War
            drawTerrainGrid(
                terrain = terrain,
                cameraX = cameraX,
                cameraY = cameraY,
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                gameState = gameState,
                textMeasurer = textMeasurer
            )

            // 1b. Render Voronoi Diagram Tactical Territory Cells & OpenGL VBO Buffer Stream with Nanopunk GLSL Shaders
            if (gameState.isTacticalGridOverlayEnabled && gameState.voronoiDiagram != null) {
                val animTimeSec = (System.currentTimeMillis() % 100000L) / 1000f
                openGlVboRenderer.beginFrame()
                openGlVboRenderer.pushVoronoiDiagram(gameState.voronoiDiagram)
                openGlVboRenderer.drawVboBridgeToCanvas(
                    drawScope = this,
                    timeSec = animTimeSec,
                    nanopunkPreset = 0,
                    glowIntensity = 1.35f
                )
            }

            // 1c. Render Tactical Line-of-Sight & Flanking combat overlay rays
            if (gameState.isTacticalGridOverlayEnabled && player.isAlive) {
                drawTacticalCombatOverlay(
                    gameState = gameState,
                    terrain = terrain,
                    textMeasurer = textMeasurer
                )
            }

            // 1d. Render Active Recon Radar / Sonar Sweeps
            for (ping in gameState.activeRadarPings) {
                val pingAlpha = (ping.life * 0.9f).coerceIn(0f, 1f)
                drawCircle(
                    color = NanoCyan.copy(alpha = pingAlpha * 0.8f),
                    radius = ping.currentRadius,
                    center = Offset(ping.originX, ping.originY),
                    style = Stroke(width = 3.5f)
                )
                drawCircle(
                    color = Color.White.copy(alpha = pingAlpha * 0.7f),
                    radius = (ping.currentRadius - 4f).coerceAtLeast(1f),
                    center = Offset(ping.originX, ping.originY),
                    style = Stroke(width = 1.5f)
                )
                drawCircle(
                    color = NanoCyan.copy(alpha = pingAlpha * 0.08f),
                    radius = ping.currentRadius,
                    center = Offset(ping.originX, ping.originY)
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

            // 2b. Render Companion Squad Members (Drones & Scouts)
            for (member in gameState.squadMembers) {
                if (!member.isAlive || !member.isActive) continue

                // Squad Vision Cone or Omnidirectional Scanner Field
                if (member.isOmnidirectionalVision) {
                    drawCircle(
                        color = NanoCyan.copy(alpha = 0.08f),
                        radius = member.visionRange,
                        center = Offset(member.x, member.y)
                    )
                    drawCircle(
                        color = NanoCyan.copy(alpha = 0.25f),
                        radius = member.visionRange,
                        center = Offset(member.x, member.y),
                        style = Stroke(width = 1f)
                    )
                } else {
                    drawVisionCone(
                        origin = Offset(member.x, member.y),
                        facingAngle = member.facingAngle,
                        range = member.visionRange,
                        fovAngleRad = member.fovAngleRad,
                        color = NaniteGreen.copy(alpha = 0.16f)
                    )
                }

                if (member.role == SquadRole.RECON_DRONE) {
                    // AEGIS-1 Recon Drone Visuals
                    val dronePulse = (sin((System.currentTimeMillis() % 1000) / 1000f * Math.PI * 2f).toFloat() * 0.5f + 0.5f)
                    drawCircle(
                        color = NanoCyan.copy(alpha = 0.5f + dronePulse * 0.5f),
                        radius = 17f,
                        center = Offset(member.x, member.y),
                        style = Stroke(width = 2f)
                    )
                    val rotAngle = member.facingAngle
                    for (nodeIdx in 0 until 4) {
                        val nodeA = rotAngle + nodeIdx * (Math.PI.toFloat() / 2f)
                        val nx = member.x + cos(nodeA) * 17f
                        val ny = member.y + sin(nodeA) * 17f
                        drawCircle(color = NanoCyan, radius = 3.5f, center = Offset(nx, ny))
                    }
                    drawCircle(
                        color = VoidDark,
                        radius = 12f,
                        center = Offset(member.x, member.y)
                    )
                    drawCircle(
                        color = NanoCyan,
                        radius = 6f,
                        center = Offset(member.x, member.y)
                    )
                    // Scanner Pointer Beam
                    drawLine(
                        color = NanoCyan,
                        start = Offset(member.x, member.y),
                        end = Offset(member.x + cos(member.facingAngle) * 26f, member.y + sin(member.facingAngle) * 26f),
                        strokeWidth = 2.5f
                    )
                } else {
                    // Vanguard Scout Echo Ground Unit
                    drawCircle(
                        color = NaniteGreen,
                        radius = 15f,
                        center = Offset(member.x, member.y)
                    )
                    drawCircle(
                        color = VoidDark,
                        radius = 9f,
                        center = Offset(member.x, member.y)
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 4f,
                        center = Offset(member.x, member.y)
                    )
                    drawLine(
                        color = NaniteGreen,
                        start = Offset(member.x, member.y),
                        end = Offset(member.x + cos(member.facingAngle) * 22f, member.y + sin(member.facingAngle) * 22f),
                        strokeWidth = 3.5f
                    )
                }

                // Squad Member Callsign Badge
                val callsignColor = if (member.role == SquadRole.RECON_DRONE) NanoCyan else NaniteGreen
                drawSafeText(
                    textMeasurer = textMeasurer,
                    text = "[${member.callsign}]",
                    style = TextStyle(color = callsignColor, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    topLeft = Offset(member.x - 30f, member.y - 28f)
                )
            }

            // 3. Render Enemies & Fog-of-War Visibility Filtering
            for (enemy in gameState.enemies) {
                if (!enemy.isAlive) continue

                val isFullyVisible = !gameState.isFogOfWarEnabled || enemy.isVisibleInFog
                val isSonarPinged = !isFullyVisible && enemy.radarPingAlpha > 0.04f
                val isTremorDetected = !isFullyVisible && !isSonarPinged && enemy.audioTremorDetected

                if (isFullyVisible) {
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

                    // AI State Tag & Tactical Flank Maneuver Label
                    if (enemy.state != AIState.PATROL || enemy.tacticalManeuverLabel != null) {
                        val stateTag = enemy.tacticalManeuverLabel ?: when (enemy.state) {
                            AIState.FLANKING -> "FLANK"
                            AIState.SEEKING_COVER -> "COVER"
                            AIState.ENGAGED -> "ENGAGE"
                            AIState.SUPPRESSING -> "SUPPRESS"
                            AIState.RETREAT -> "RETREAT"
                            AIState.SUSPICIOUS, AIState.INVESTIGATING -> "ALERT"
                            else -> "!"
                        }
                        val alertColor = when (enemy.state) {
                            AIState.FLANKING -> PlasmaPink
                            AIState.SEEKING_COVER -> ShieldBlue
                            AIState.ENGAGED -> LaserRed
                            AIState.SUPPRESSING -> HazardYellow
                            AIState.RETREAT -> HazardYellow
                            else -> HazardYellow
                        }
                        drawSafeText(
                            textMeasurer = textMeasurer,
                            text = "[$stateTag]",
                            style = TextStyle(color = alertColor, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            topLeft = Offset(enemy.x - 28f, enemy.y - 42f)
                        )
                    }

                    // Render active tactical navigation path waypoints if tactical overlay enabled or during flanking maneuvers
                    if ((gameState.isTacticalGridOverlayEnabled || enemy.state == AIState.FLANKING) && enemy.activePath.isNotEmpty()) {
                        var prevPt = Offset(enemy.x, enemy.y)
                        val pathColor = if (enemy.state == AIState.FLANKING) PlasmaPink.copy(alpha = 0.75f) else Color.Cyan.copy(alpha = 0.5f)
                        for (idx in enemy.activePathIndex until enemy.activePath.size) {
                            val pt = Offset(enemy.activePath[idx].first, enemy.activePath[idx].second)
                            drawLine(
                                color = pathColor,
                                start = prevPt,
                                end = pt,
                                strokeWidth = if (enemy.state == AIState.FLANKING) 2.5f else 1.8f
                            )
                            drawCircle(
                                color = pathColor,
                                radius = if (enemy.state == AIState.FLANKING) 3.5f else 2.5f,
                                center = pt
                            )
                            prevPt = pt
                        }
                    }
                } else if (isSonarPinged) {
                    // Sonar Echolocation Ghost in Fog-of-War
                    val pingAlpha = enemy.radarPingAlpha.coerceIn(0f, 1f)
                    drawCircle(
                        color = LaserRed.copy(alpha = pingAlpha * 0.4f),
                        radius = 20f,
                        center = Offset(enemy.x, enemy.y)
                    )
                    drawCircle(
                        color = LaserRed.copy(alpha = pingAlpha),
                        radius = 22f,
                        center = Offset(enemy.x, enemy.y),
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = pingAlpha),
                        radius = 4f,
                        center = Offset(enemy.x, enemy.y)
                    )
                    drawSafeText(
                        textMeasurer = textMeasurer,
                        text = "[SONAR PING]",
                        style = TextStyle(color = LaserRed.copy(alpha = pingAlpha), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        topLeft = Offset(enemy.x - 32f, enemy.y - 34f)
                    )
                } else if (isTremorDetected) {
                    // Acoustic Tremor Wave Ripple in Fog-of-War
                    val tremorAlpha = 0.6f
                    drawCircle(
                        color = HazardYellow.copy(alpha = tremorAlpha * 0.2f),
                        radius = 16f,
                        center = Offset(enemy.x, enemy.y)
                    )
                    drawCircle(
                        color = HazardYellow.copy(alpha = tremorAlpha),
                        radius = 18f,
                        center = Offset(enemy.x, enemy.y),
                        style = Stroke(width = 1.5f)
                    )
                    drawSafeText(
                        textMeasurer = textMeasurer,
                        text = "[TREMOR]",
                        style = TextStyle(color = HazardYellow.copy(alpha = tremorAlpha), fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        topLeft = Offset(enemy.x - 22f, enemy.y - 30f)
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
                    ParticleType.ELECTRIC_BOLT -> {
                        // Jagged electric discharge bolt
                        val midX = (p.x + (p.x - p.vx * 0.04f)) / 2f + (Random.nextFloat() - 0.5f) * 12f
                        val midY = (p.y + (p.y - p.vy * 0.04f)) / 2f + (Random.nextFloat() - 0.5f) * 12f
                        val sparkPath = Path()
                        sparkPath.moveTo(p.x, p.y)
                        sparkPath.lineTo(midX, midY)
                        sparkPath.lineTo(p.x - p.vx * 0.04f, p.y - p.vy * 0.04f)
                        drawPath(sparkPath, p.color.copy(alpha = p.life), style = Stroke(width = 2.5f))
                    }
                    ParticleType.NANITE_SPORE -> {
                        // Glowing biohazard toxic spore
                        drawCircle(
                            color = p.color.copy(alpha = p.life * 0.45f),
                            radius = p.size,
                            center = Offset(p.x, p.y)
                        )
                        drawCircle(
                            color = Color(0xFF34D399).copy(alpha = p.life * 0.8f),
                            radius = p.size * 0.4f,
                            center = Offset(p.x, p.y)
                        )
                    }
                    ParticleType.CRYO_CRYSTAL -> {
                        // Crystalline ice shard
                        val halfS = p.size * 0.6f * p.life
                        val icePath = Path()
                        icePath.moveTo(p.x, p.y - halfS)
                        icePath.lineTo(p.x + halfS, p.y)
                        icePath.lineTo(p.x, p.y + halfS)
                        icePath.lineTo(p.x - halfS, p.y)
                        icePath.close()
                        drawPath(icePath, p.color.copy(alpha = p.life * 0.7f))
                        drawPath(icePath, Color.White.copy(alpha = p.life), style = Stroke(width = 1f))
                    }
                    ParticleType.PLASMA_WAVE -> {
                        // Expanding shock ring
                        drawCircle(
                            color = p.color.copy(alpha = p.life * 0.8f),
                            radius = p.size * (1f - p.life + 0.2f),
                            center = Offset(p.x, p.y),
                            style = Stroke(width = 3f)
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

            // 6b. Render Active Environmental Hazards (Gas Clouds, Cryo Fields, Electric Arcs, Shockwaves)
            for (cloud in gameState.activeGasClouds) {
                // Expanding Corrosive Nanite Gas Aura
                val pulse = (sin(cloud.pulseAnim.toDouble()) * 0.15 + 0.85).toFloat()
                val alphaBase = (cloud.remainingSec / 10f).coerceIn(0.2f, 0.65f)
                drawCircle(
                    color = Color(0xFF10B981).copy(alpha = alphaBase * 0.3f * pulse),
                    radius = cloud.currentRadius * pulse,
                    center = Offset(cloud.x, cloud.y)
                )
                drawCircle(
                    color = Color(0xFF047857).copy(alpha = alphaBase * 0.5f),
                    radius = cloud.currentRadius * 0.65f,
                    center = Offset(cloud.x, cloud.y)
                )
                drawCircle(
                    color = Color(0xFF34D399).copy(alpha = alphaBase * 0.8f),
                    radius = cloud.currentRadius * pulse,
                    center = Offset(cloud.x, cloud.y),
                    style = Stroke(width = 2f)
                )
                drawSafeText(
                    textMeasurer = textMeasurer,
                    text = "☣ NANITE GAS",
                    style = TextStyle(color = Color(0xFF34D399).copy(alpha = alphaBase), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    topLeft = Offset(cloud.x - 36f, cloud.y - 12f)
                )
            }

            for (cryo in gameState.activeCryoFields) {
                // Subzero Cryo Frost Zone
                drawCircle(
                    color = Color(0xFF0284C7).copy(alpha = 0.25f),
                    radius = cryo.radius,
                    center = Offset(cryo.x, cryo.y)
                )
                drawCircle(
                    color = Color(0xFF38BDF8).copy(alpha = 0.6f),
                    radius = cryo.radius,
                    center = Offset(cryo.x, cryo.y),
                    style = Stroke(width = 2.5f)
                )
                drawSafeText(
                    textMeasurer = textMeasurer,
                    text = "❄ CRYO ZONE",
                    style = TextStyle(color = Color(0xFF38BDF8), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                    topLeft = Offset(cryo.x - 34f, cryo.y - 10f)
                )
            }

            for (arc in gameState.activeElectricArcs) {
                // High-Voltage Lightning Arc
                val segs = 5
                val arcPath = Path()
                arcPath.moveTo(arc.startX, arc.startY)
                for (s in 1 until segs) {
                    val frac = s.toFloat() / segs
                    val lx = arc.startX + (arc.endX - arc.startX) * frac + (Random.nextFloat() - 0.5f) * 22f
                    val ly = arc.startY + (arc.endY - arc.startY) * frac + (Random.nextFloat() - 0.5f) * 22f
                    arcPath.lineTo(lx, ly)
                }
                arcPath.lineTo(arc.endX, arc.endY)
                val arcAlpha = (arc.lifeSec / arc.maxLifeSec).coerceIn(0f, 1f)
                drawPath(arcPath, Color(0xFF00F0FF).copy(alpha = arcAlpha), style = Stroke(width = 3.5f))
                drawPath(arcPath, Color.White.copy(alpha = arcAlpha), style = Stroke(width = 1.5f))
            }

            for (sw in gameState.activeShockwaves) {
                // Detonation Blast Ring
                val swAlpha = (sw.lifeSec / sw.maxLifeSec).coerceIn(0f, 1f)
                drawCircle(
                    color = sw.color.copy(alpha = swAlpha * 0.35f),
                    radius = sw.currentRadius,
                    center = Offset(sw.x, sw.y)
                )
                drawCircle(
                    color = sw.color.copy(alpha = swAlpha),
                    radius = sw.currentRadius,
                    center = Offset(sw.x, sw.y),
                    style = Stroke(width = 4f)
                )
            }

            // Render HUD Interaction Reticles for nearby Hazards
            for (h in gameState.activeHazards) {
                val dist = hypot(h.worldX - player.x, h.worldY - player.y)
                if (dist <= 160f && h.status == com.example.engine.HazardStatus.DORMANT) {
                    val pAlpha = (sin(h.pulsePhase.toDouble()) * 0.3 + 0.7).toFloat()
                    val col = Color(h.type.baseColorHex)
                    drawCircle(
                        color = col.copy(alpha = pAlpha * 0.7f),
                        radius = 28f,
                        center = Offset(h.worldX, h.worldY),
                        style = Stroke(width = 2f)
                    )
                    drawSafeText(
                        textMeasurer = textMeasurer,
                        text = h.type.iconTag,
                        style = TextStyle(color = col, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        topLeft = Offset(h.worldX - 22f, h.worldY - 34f)
                    )
                } else if (h.status == com.example.engine.HazardStatus.CHARGING) {
                    // Critical Reactor Countdown warning
                    drawCircle(
                        color = Color(0xFFFF5500),
                        radius = 35f,
                        center = Offset(h.worldX, h.worldY),
                        style = Stroke(width = 3f)
                    )
                    drawSafeText(
                        textMeasurer = textMeasurer,
                        text = "OVERLOAD: ${"%.1f".format(h.chargeCountdownSec)}s",
                        style = TextStyle(color = Color(0xFFFF5500), fontSize = 10.sp, fontWeight = FontWeight.Black),
                        topLeft = Offset(h.worldX - 38f, h.worldY - 38f)
                    )
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

                // State-Based Cover Visuals: Barrier Arc, Contact Bar, and Tangent Guides
                if (player.isCoverSnapped || player.movementState == PlayerMovementState.COVER_SNAPPED ||
                    player.movementState == PlayerMovementState.COVER_TRAVERSING || player.movementState == PlayerMovementState.COVER_PEEKING) {
                    val nx = player.coverSnapNormalX
                    val ny = player.coverSnapNormalY
                    val tangentX = -ny
                    val tangentY = nx

                    // Contact Beam flush on voxel face
                    val contactStartX = player.x - nx * 4f + tangentX * 20f
                    val contactStartY = player.y - ny * 4f + tangentY * 20f
                    val contactEndX = player.x - nx * 4f - tangentX * 20f
                    val contactEndY = player.y - ny * 4f - tangentY * 20f

                    val beamColor = when (player.movementState) {
                        PlayerMovementState.COVER_PEEKING -> AmberAccent
                        PlayerMovementState.COVER_TRAVERSING -> NanoCyan
                        else -> NaniteGreen
                    }

                    drawLine(
                        color = beamColor,
                        start = Offset(contactStartX, contactStartY),
                        end = Offset(contactEndX, contactEndY),
                        strokeWidth = 4.5f
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(contactStartX, contactStartY),
                        end = Offset(contactEndX, contactEndY),
                        strokeWidth = 2f
                    )

                    // Traversing Guideline arrows along the cover face
                    if (player.movementState == PlayerMovementState.COVER_TRAVERSING) {
                        val slideDir = if (player.vx * tangentX + player.vy * tangentY >= 0) 1f else -1f
                        val arrowTipX = player.x + tangentX * (28f * slideDir)
                        val arrowTipY = player.y + tangentY * (28f * slideDir)
                        drawLine(
                            color = NanoCyan,
                            start = Offset(player.x, player.y),
                            end = Offset(arrowTipX, arrowTipY),
                            strokeWidth = 3f
                        )
                    }

                    // Defensive Barrier Arc facing away from obstacle face
                    val awayAngleRad = atan2(-ny, -nx)
                    val awayAngleDeg = Math.toDegrees(awayAngleRad.toDouble()).toFloat()
                    val pulseAlpha = 0.65f + sin(player.coverAnimPulse.toDouble()).toFloat() * 0.25f

                    val arcAngle = if (player.movementState == PlayerMovementState.COVER_PEEKING) 70f else 125f
                    drawArc(
                        color = beamColor.copy(alpha = pulseAlpha),
                        startAngle = awayAngleDeg - arcAngle / 2f,
                        sweepAngle = arcAngle,
                        useCenter = false,
                        topLeft = Offset(player.x - 24f, player.y - 24f),
                        size = Size(48f, 48f),
                        style = Stroke(width = 3.5f)
                    )
                }

                // Vaulting Motion Trail
                if (player.isVaulting) {
                    drawLine(
                        color = NanoCyan.copy(alpha = 0.5f),
                        start = Offset(player.vaultStartX, player.vaultStartY),
                        end = Offset(player.vaultTargetX, player.vaultTargetY),
                        strokeWidth = 3f
                    )
                    drawCircle(
                        color = NanoCyan.copy(alpha = 0.4f),
                        radius = 24f * (1f - player.vaultProgress),
                        center = Offset(player.x, player.y)
                    )
                }

                // Player Body
                val playerColor = when {
                    player.movementState == PlayerMovementState.COVER_VAULTING -> NanoCyan
                    player.stance == PlayerStance.STAND -> NanoCyan
                    player.stance == PlayerStance.CROUCH -> NanoCyanDim
                    player.stance == PlayerStance.PRONE -> NanoPurple
                    else -> NanoCyan
                }

                drawCircle(
                    color = playerColor,
                    radius = if (player.stance == PlayerStance.PRONE) 12f else 18f,
                    center = Offset(player.x, player.y)
                )
                if (player.isCoverSnapped) {
                    drawCircle(
                        color = if (player.movementState == PlayerMovementState.COVER_PEEKING) AmberAccent else NaniteGreen,
                        radius = 21f,
                        center = Offset(player.x, player.y),
                        style = Stroke(width = 2.2f)
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

                // Cover & Hit Probability Indicator Badge
                if (player.isBehindCover || player.isCoverSnapped || player.movementState == PlayerMovementState.COVER_VAULTING) {
                    val pctEvade = ((1.0f - player.incomingHitProbability) * 100).toInt()
                    val statePrefix = when (player.movementState) {
                        PlayerMovementState.COVER_SNAPPED -> "SNAPPED"
                        PlayerMovementState.COVER_TRAVERSING -> "TRAVERSING"
                        PlayerMovementState.COVER_PEEKING -> "PEEKING"
                        PlayerMovementState.COVER_VAULTING -> "VAULTING"
                        else -> "COVER"
                    }
                    val coverTxt = "$statePrefix: -$pctEvade% HIT"
                    val badgeColor = when (player.movementState) {
                        PlayerMovementState.COVER_PEEKING -> AmberAccent
                        PlayerMovementState.COVER_TRAVERSING -> NanoCyan
                        PlayerMovementState.COVER_VAULTING -> NanoCyan
                        else -> NaniteGreen
                    }
                    drawSafeText(
                        textMeasurer = textMeasurer,
                        text = coverTxt,
                        style = TextStyle(color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                        topLeft = Offset(player.x - 46f, player.y - 38f)
                    )
                }

                // Stealth & Ambush status floating badge
                val stealth = gameState.stealthEval
                if (gameState.isFogOfWarEnabled && player.isAlive) {
                    if (stealth.status == StealthStatus.HIDDEN) {
                        val pulse = 0.55f + 0.25f * sin((System.currentTimeMillis() % 2000L) / 318f)
                        drawCircle(
                            color = NaniteEmerald.copy(alpha = pulse),
                            radius = 23f,
                            center = Offset(player.x, player.y),
                            style = Stroke(width = 1.8f)
                        )
                        drawSafeText(
                            textMeasurer = textMeasurer,
                            text = "AMBUSH READY (+75%)",
                            style = TextStyle(color = NaniteEmerald, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            topLeft = Offset(player.x - 50f, player.y + 24f)
                        )
                    } else if (stealth.status == StealthStatus.CAUTION) {
                        drawCircle(
                            color = HazardYellow.copy(alpha = 0.65f),
                            radius = 23f,
                            center = Offset(player.x, player.y),
                            style = Stroke(width = 1.8f)
                        )
                        drawSafeText(
                            textMeasurer = textMeasurer,
                            text = "NOISE HEARD!",
                            style = TextStyle(color = HazardYellow, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            topLeft = Offset(player.x - 32f, player.y + 24f)
                        )
                    } else if (stealth.status == StealthStatus.DETECTED) {
                        drawCircle(
                            color = LaserRed.copy(alpha = 0.75f),
                            radius = 23f,
                            center = Offset(player.x, player.y),
                            style = Stroke(width = 2f)
                        )
                        drawSafeText(
                            textMeasurer = textMeasurer,
                            text = "DETECTED (${stealth.detectingEnemiesCount})",
                            style = TextStyle(color = LaserRed, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            topLeft = Offset(player.x - 38f, player.y + 24f)
                        )
                    }

                    // Tactical Sensor Flashlight Cone for Player
                    val fovAngleRad = when (player.stance) {
                        PlayerStance.STAND -> Math.toRadians(150.0).toFloat()
                        PlayerStance.CROUCH -> Math.toRadians(135.0).toFloat()
                        PlayerStance.PRONE -> Math.toRadians(115.0).toFloat()
                    }
                    val aimDir = if (player.aimAngle != 0f) player.aimAngle else player.facingAngle
                    val beamRange = if (player.isFiring) 640f else 540f
                    drawVisionCone(
                        origin = Offset(player.x, player.y),
                        facingAngle = aimDir,
                        range = beamRange,
                        fovAngleRad = fovAngleRad,
                        color = NanoCyan.copy(alpha = 0.05f)
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
    gameState: GameState,
    textMeasurer: androidx.compose.ui.text.TextMeasurer
) {
    val startGx = (cameraX / terrain.tileSize).toInt().coerceIn(0, terrain.width - 1)
    val endGx = ((cameraX + canvasWidth) / terrain.tileSize).toInt().coerceAtMost(terrain.width - 1)
    val startGy = (cameraY / terrain.tileSize).toInt().coerceIn(0, terrain.height - 1)
    val endGy = ((cameraY + canvasHeight) / terrain.tileSize).toInt().coerceAtMost(terrain.height - 1)

    val player = gameState.player
    val playerX = player.x
    val playerY = player.y
    val isTacticalOverlayEnabled = gameState.isTacticalGridOverlayEnabled
    val dynamicLights = gameState.dynamicLights
    val isFogActive = gameState.isFogOfWarEnabled
    val fogSnapshot = gameState.fogSnapshot

    for (gx in startGx..endGx) {
        for (gy in startGy..endGy) {
            val tile = terrain.tiles[gx][gy]
            val worldX = gx * terrain.tileSize
            val worldY = gy * terrain.tileSize
            val tileCenterX = worldX + terrain.tileSize / 2f
            val tileCenterY = worldY + terrain.tileSize / 2f

            val isVisibleInSight = if (isFogActive && fogSnapshot != null) {
                fogSnapshot.currentVisible.getOrNull(gx)?.getOrNull(gy) ?: false
            } else true
            val isExplored = if (isFogActive && fogSnapshot != null) {
                fogSnapshot.explored.getOrNull(gx)?.getOrNull(gy) ?: true
            } else true

            val hasLoS = isVisibleInSight

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

                } else {
                    // Shrouded or Unseen Fog overlay
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

            // Fog of War Top-Down Alien Obscuration Shroud
            if (isFogActive && !isVisibleInSight) {
                if (isExplored) {
                    // Explored tactical memory blueprint shroud
                    drawRect(
                        color = VoidDark.copy(alpha = 0.58f),
                        topLeft = Offset(worldX, worldY),
                        size = Size(terrain.tileSize, terrain.tileSize)
                    )
                    drawRect(
                        color = NanoCyan.copy(alpha = 0.08f),
                        topLeft = Offset(worldX, worldY),
                        size = Size(terrain.tileSize, terrain.tileSize),
                        style = Stroke(width = 1f)
                    )
                } else {
                    // Dense alien pitch black void
                    drawRect(
                        color = VoidDark.copy(alpha = 0.96f),
                        topLeft = Offset(worldX, worldY),
                        size = Size(terrain.tileSize, terrain.tileSize)
                    )
                    val mistRand = ((gx * 31 + gy * 17) % 5)
                    if (mistRand == 0) {
                        drawCircle(
                            color = NanoPurple.copy(alpha = 0.28f),
                            radius = 5f,
                            center = Offset(tileCenterX, tileCenterY)
                        )
                    }
                }
            } else if (isFogActive && isVisibleInSight) {
                // Line-of-sight perimeter boundary glow
                val hasAdjacentHidden = (gx > 0 && !(fogSnapshot?.currentVisible?.getOrNull(gx - 1)?.getOrNull(gy) ?: true)) ||
                        (gx < terrain.width - 1 && !(fogSnapshot?.currentVisible?.getOrNull(gx + 1)?.getOrNull(gy) ?: true)) ||
                        (gy > 0 && !(fogSnapshot?.currentVisible?.getOrNull(gx)?.getOrNull(gy - 1) ?: true)) ||
                        (gy < terrain.height - 1 && !(fogSnapshot?.currentVisible?.getOrNull(gx)?.getOrNull(gy + 1) ?: true))
                if (hasAdjacentHidden) {
                    drawRect(
                        color = NanoCyan.copy(alpha = 0.22f),
                        topLeft = Offset(worldX, worldY),
                        size = Size(terrain.tileSize, terrain.tileSize),
                        style = Stroke(width = 1.2f)
                    )
                }
            }
        }
    }

    // Drifting Alien Fog Mist Particles across visible canvas
    if (isFogActive && fogSnapshot != null) {
        for (mp in fogSnapshot.mistParticles) {
            if (mp.x >= cameraX - 100f && mp.x <= cameraX + canvasWidth + 100f &&
                mp.y >= cameraY - 100f && mp.y <= cameraY + canvasHeight + 100f) {
                val pAlpha = (mp.alpha * (0.8f + 0.2f * sin(mp.pulsePhase))).coerceIn(0f, 1f)
                drawCircle(
                    color = Color(0xFF090D1A).copy(alpha = pAlpha),
                    radius = mp.size,
                    center = Offset(mp.x, mp.y)
                )
            }
        }
    }
}

// Raycasting function for squad and player visibility check
private fun isTileVisibleToSquadOrPlayer(
    tileCenterX: Float,
    tileCenterY: Float,
    player: PlayerState,
    squad: List<SquadMember>,
    terrain: VoxelTerrain
): Boolean {
    // 1. Check Player Line-of-sight
    if (player.isAlive) {
        val dxP = tileCenterX - player.x
        val dyP = tileCenterY - player.y
        val distToPlayerSq = dxP * dxP + dyP * dyP
        val playerSightRange = 560f
        if (distToPlayerSq <= playerSightRange * playerSightRange) {
            if (distToPlayerSq <= 180f * 180f) {
                if (hasLineOfSight(player.x, player.y, tileCenterX, tileCenterY, terrain)) return true
            } else {
                val angleToTile = atan2(dyP, dxP)
                var angleDiff = abs(angleToTile - player.facingAngle)
                if (angleDiff > Math.PI) angleDiff = (2 * Math.PI - angleDiff).toFloat()
                if (angleDiff <= Math.toRadians(80.0).toFloat()) {
                    if (hasLineOfSight(player.x, player.y, tileCenterX, tileCenterY, terrain)) return true
                }
            }
        }
    }

    // 2. Check Squad Members Line-of-sight
    for (member in squad) {
        if (!member.isAlive || !member.isActive) continue
        val dx = tileCenterX - member.x
        val dy = tileCenterY - member.y
        val distSq = dx * dx + dy * dy
        if (distSq <= member.visionRange * member.visionRange) {
            if (member.isOmnidirectionalVision) {
                if (hasLineOfSight(member.x, member.y, tileCenterX, tileCenterY, terrain)) return true
            } else {
                val angleToTile = atan2(dy, dx)
                var angleDiff = abs(angleToTile - member.facingAngle)
                if (angleDiff > Math.PI) angleDiff = (2 * Math.PI - angleDiff).toFloat()
                if (angleDiff <= member.fovAngleRad / 2f) {
                    if (hasLineOfSight(member.x, member.y, tileCenterX, tileCenterY, terrain)) return true
                }
            }
        }
    }

    return false
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
    reusableVisionConePath.reset()
    reusableVisionConePath.moveTo(origin.x, origin.y)
    val startAngle = facingAngle - fovAngleRad / 2f
    val endAngle = facingAngle + fovAngleRad / 2f
    val steps = 10
    for (i in 0..steps) {
        val a = startAngle + (endAngle - startAngle) * (i / steps.toFloat())
        val px = origin.x + cos(a) * range
        val py = origin.y + sin(a) * range
        reusableVisionConePath.lineTo(px, py)
    }
    reusableVisionConePath.close()

    drawPath(path = reusableVisionConePath, color = color)
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
            reusableWaypointArrowPath.reset()
            val headX = clampedX + cos(angle) * 14f
            val headY = clampedY + sin(angle) * 14f
            val leftX = clampedX + cos(angle + 2.5f) * 10f
            val leftY = clampedY + sin(angle + 2.5f) * 10f
            val rightX = clampedX + cos(angle - 2.5f) * 10f
            val rightY = clampedY + sin(angle - 2.5f) * 10f

            reusableWaypointArrowPath.moveTo(headX, headY)
            reusableWaypointArrowPath.lineTo(leftX, leftY)
            reusableWaypointArrowPath.lineTo(rightX, rightY)
            reusableWaypointArrowPath.close()

            drawPath(path = reusableWaypointArrowPath, color = indicatorColor)
            drawPath(path = reusableWaypointArrowPath, color = Color.White, style = Stroke(width = 1.5f))

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
