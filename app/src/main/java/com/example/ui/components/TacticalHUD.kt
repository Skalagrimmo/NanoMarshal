package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.*
import com.example.engine.AudioIntensityCategory
import com.example.engine.GameState
import com.example.engine.StealthStatus
import com.example.ui.theme.*

@Composable
fun TacticalHUD(
    gameState: GameState,
    onMove: (dx: Float, dy: Float) -> Unit,
    onAim: (angle: Float, isFiring: Boolean) -> Unit,
    onAimRelease: () -> Unit,
    onToggleStance: () -> Unit,
    onSwapWeapon: () -> Unit,
    onReload: () -> Unit,
    onThrowGadget: () -> Unit,
    onPauseToggle: () -> Unit,
    onToggleTacticalOverlay: () -> Unit = {},
    onToggleAutoAim: () -> Unit = {},
    onToggleFogOfWar: () -> Unit = {},
    onTriggerSonarScan: () -> Unit = {},
    onTriggerHazardInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val player = gameState.player

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(12.dp)
            .testTag("tactical_hud_root")
    ) {
        // TOP HUD: SYSTEM STATUS & OBJECTIVES
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Sector & Objective Title
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Radar",
                            tint = NanoCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "SECTOR 7-GAMMA",
                            color = NanoCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                    Text(
                        text = "Target: ${gameState.currentMission.bountyTargetName}",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // System DAG/LOD Status & Tactical Overlay Toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Fog of War Toggle Pill
                    Surface(
                        onClick = onToggleFogOfWar,
                        modifier = Modifier.testTag("fog_of_war_toggle"),
                        shape = RoundedCornerShape(16.dp),
                        color = VoidDark.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (gameState.isFogOfWarEnabled) NanoCyan else SlateBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (gameState.isFogOfWarEnabled) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Fog of War",
                                tint = if (gameState.isFogOfWarEnabled) NanoCyan else TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (gameState.isFogOfWarEnabled) "FOG: ${gameState.explorationPercentage.toInt()}%" else "FOG: OFF",
                                color = if (gameState.isFogOfWarEnabled) NanoCyan else TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Recon Sonar Scan Pulse Button
                    Surface(
                        onClick = onTriggerSonarScan,
                        modifier = Modifier.testTag("recon_sonar_scan_button"),
                        shape = RoundedCornerShape(16.dp),
                        color = VoidDark.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NanoCyan)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "Sonar Scan",
                                tint = NanoCyan,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "SCAN",
                                color = NanoCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Tactical Stealth Status Indicator
                    val stealth = gameState.stealthEval
                    val stealthColor = Color(stealth.statusColorHex)
                    Surface(
                        modifier = Modifier.testTag("tactical_stealth_pill"),
                        shape = RoundedCornerShape(16.dp),
                        color = VoidDark.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, stealthColor)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (stealth.status) {
                                    StealthStatus.HIDDEN -> Icons.Default.Shield
                                    StealthStatus.CAUTION -> Icons.Default.Warning
                                    StealthStatus.DETECTED -> Icons.Default.PriorityHigh
                                },
                                contentDescription = "Stealth Status",
                                tint = stealthColor,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = when (stealth.status) {
                                    StealthStatus.HIDDEN -> "STEALTH"
                                    StealthStatus.CAUTION -> "CAUTION"
                                    StealthStatus.DETECTED -> "ALERT"
                                },
                                color = stealthColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Auto-Aim Mode Toggle Pill
                    Surface(
                        onClick = onToggleAutoAim,
                        modifier = Modifier.testTag("autoaim_mode_toggle"),
                        shape = RoundedCornerShape(16.dp),
                        color = VoidDark.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (player.isAutoAimEnabled) PlasmaPink else SlateBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrackChanges,
                                contentDescription = "Auto-Aim",
                                tint = if (player.isAutoAimEnabled) PlasmaPink else TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "AIM: ${player.autoAimMode.name}",
                                color = if (player.isAutoAimEnabled) PlasmaPink else TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Tactical Overlay Toggle Pill
                    Surface(
                        onClick = onToggleTacticalOverlay,
                        modifier = Modifier.testTag("tactical_grid_toggle"),
                        shape = RoundedCornerShape(16.dp),
                        color = VoidDark.copy(alpha = 0.85f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (gameState.isTacticalGridOverlayEnabled) NanoCyan else SlateBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Grid4x4,
                                contentDescription = "Grid",
                                tint = if (gameState.isTacticalGridOverlayEnabled) NanoCyan else TextMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (gameState.isTacticalGridOverlayEnabled) "GRID" else "GRID",
                                color = if (gameState.isTacticalGridOverlayEnabled) NanoCyan else TextMuted,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Pause Button
                    IconButton(
                        onClick = onPauseToggle,
                        modifier = Modifier
                            .testTag("pause_button")
                            .size(34.dp)
                            .background(VoidDark.copy(alpha = 0.85f), CircleShape)
                            .border(1.dp, SlateBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // ADAPTIVE AUDIO INTENSITY HUD BAR
            val audio = gameState.audioState
            val audioColor = when (audio.category) {
                AudioIntensityCategory.CALM_PATROL -> NaniteGreen
                AudioIntensityCategory.CAUTION_SUSPICIOUS -> HazardYellow
                AudioIntensityCategory.COMBAT_HIGH -> LaserRed
                AudioIntensityCategory.BOSS_CRITICAL -> PlasmaPink
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("adaptive_audio_hud_bar"),
                shape = RoundedCornerShape(8.dp),
                color = VoidDark.copy(alpha = 0.85f),
                border = androidx.compose.foundation.BorderStroke(1.dp, audioColor.copy(alpha = 0.5f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Adaptive Audio",
                        tint = audioColor,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = audio.category.label,
                        color = audioColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    LinearProgressIndicator(
                        progress = { audio.intensity },
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = audioColor,
                        trackColor = VoidDark
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(audio.intensity * 100).toInt()}% | ${audio.tempoBpm} BPM",
                        color = TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Sub-status DAG Indicator & Stealth Badge Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stealth Badge
                val stealthPct = if (player.stealthNoiseRadius > 0) ((380f - player.stealthNoiseRadius) / 3.8f).coerceIn(0f, 100f).toInt() else 100
                Surface(
                    color = VoidDark.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(if (stealthPct > 50) NaniteGreen else HazardYellow, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "STEALTH: $stealthPct%",
                            color = TextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // DAG LOD Indicator & SVDAG Compression Metric
                val compStr = String.format("%.1f", gameState.svdagCompressionRatio)
                val voronoiCount = gameState.voronoiDiagram?.sites?.size ?: 0
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "SVDAG COMP: $compStr% // VORONOI SEEDS: $voronoiCount",
                        color = NanoCyan.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "GLSL SHADER: NANOPUNK [GLES20 | NANITE_CIRCUIT]",
                        color = NanoCyan.copy(alpha = 0.85f),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // MISSION OBJECTIVES OVERLAY CARD
            if (gameState.objectives.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("objective_manager_hud"),
                    color = VoidDark.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Flag,
                                    contentDescription = "Objectives",
                                    tint = NanoCyan,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "MISSION OBJECTIVES",
                                    color = NanoCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                            val completedCount = gameState.objectives.count { it.status == ObjectiveStatus.COMPLETED }
                            Text(
                                text = "$completedCount / ${gameState.objectives.size} DONE",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        gameState.objectives.take(3).forEach { obj ->
                            val statusColor = when (obj.status) {
                                ObjectiveStatus.COMPLETED -> NaniteGreen
                                ObjectiveStatus.FAILED -> LaserRed
                                ObjectiveStatus.IN_PROGRESS -> if (obj.isPrimary) NanoCyan else HazardYellow
                                ObjectiveStatus.NOT_STARTED -> TextMuted
                            }

                            val icon = when (obj.status) {
                                ObjectiveStatus.COMPLETED -> Icons.Default.CheckCircle
                                ObjectiveStatus.FAILED -> Icons.Default.Cancel
                                ObjectiveStatus.IN_PROGRESS -> Icons.Default.RadioButtonUnchecked
                                ObjectiveStatus.NOT_STARTED -> Icons.Default.Lock
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = statusColor,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = obj.title,
                                    color = if (obj.status == ObjectiveStatus.COMPLETED) TextMuted else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = if (obj.isPrimary) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f)
                                )

                                if (obj.timerRemainingSec != null && obj.status == ObjectiveStatus.IN_PROGRESS) {
                                    Text(
                                        text = "${obj.timerRemainingSec?.toInt()}s",
                                        color = statusColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                } else if (obj.requiredProgress > 1 && obj.status == ObjectiveStatus.IN_PROGRESS) {
                                    Text(
                                        text = "${obj.currentProgress}/${obj.requiredProgress}",
                                        color = statusColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ACTIVE OBJECTIVE TOAST BANNER
            AnimatedVisibility(visible = gameState.activeObjectiveToast != null) {
                gameState.activeObjectiveToast?.let { toast ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        color = VoidDark.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NanoCyan)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Toast",
                                tint = NanoCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = toast,
                                color = TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // BOTTOM CONTROLS & VITALITY HUD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        ) {
            // Active Cover Defensive Buff Status Pill
            AnimatedVisibility(
                visible = player.isBehindCover || player.isCoverSnapped || player.activeCoverBuffTitle != null,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = if (player.isCoverFlanked) LaserRed.copy(alpha = 0.25f) else NanoCyan.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (player.isCoverFlanked) LaserRed else if (player.isCoverSnapped) NaniteGreen else NanoCyan
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (player.isCoverFlanked) Icons.Default.Warning else Icons.Default.Shield,
                                contentDescription = "Cover Buff",
                                tint = if (player.isCoverFlanked) LaserRed else if (player.isCoverSnapped) NaniteGreen else NanoCyan,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                val stateTag = when (player.movementState) {
                                    PlayerMovementState.COVER_SNAPPED -> "[LOCKED]"
                                    PlayerMovementState.COVER_TRAVERSING -> "[TRAVERSING]"
                                    PlayerMovementState.COVER_PEEKING -> "[PEEKING]"
                                    PlayerMovementState.COVER_VAULTING -> "[VAULTING]"
                                    else -> ""
                                }
                                Text(
                                    text = "${player.activeCoverBuffTitle ?: "COVER BRACED"} $stateTag".trim(),
                                    color = if (player.isCoverFlanked) LaserRed else TextPrimary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                val hitReductionPct = ((1.0f - player.incomingHitProbability) * 100).toInt()
                                val subText = if (player.isCoverFlanked) {
                                    "WARNING: EXPOSED FLANK! +45% DAMAGE"
                                } else {
                                    "HIT PROBABILITY: -${hitReductionPct}% EVASION"
                                }
                                Text(
                                    text = subText,
                                    color = if (player.isCoverFlanked) LaserRed.copy(alpha = 0.85f) else NaniteGreen,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "STANCE: ${player.stance.name}",
                                color = TextMuted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "HIT: ${(player.incomingHitProbability * 100).toInt()}%",
                                color = if (player.incomingHitProbability < 0.4f) NaniteGreen else TextSecondary,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Vitality & Nano-Shell Status + Ammo Card Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vitality & Nano-Shell Bars
                Column(
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                ) {
                    // Vitality Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "VITALITY", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(text = "${player.health.toInt()}%", color = TextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(SlateCard)
                    ) {
                        val hpPct = (player.health / player.maxHealth).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(hpPct)
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(NaniteGreen, NaniteEmerald)
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Nano-Shell Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "NANO-SHELL", color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(text = "${player.nanoShield.toInt()}kw", color = NanoCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(SlateCard)
                    ) {
                        val shieldPct = (player.nanoShield / player.maxNanoShield).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(shieldPct)
                                .background(NanoCyan)
                        )
                    }
                }

                // Ammo Display Card
                Surface(
                    onClick = onSwapWeapon,
                    modifier = Modifier
                        .testTag("ammo_card")
                        .width(100.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = SlateCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "${player.currentAmmo}",
                                color = TextPrimary,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "/${player.activeWeapon.magSize}",
                                color = TextMuted,
                                fontSize = 11.sp
                            )
                        }
                        Text(
                            text = player.activeWeapon.name,
                            color = NanoCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Contextual Environmental Hazard Interaction Banner & Action Button
            gameState.hazardInteractionPrompt?.let { prompt ->
                Surface(
                    onClick = onTriggerHazardInteraction,
                    modifier = Modifier
                        .testTag("hazard_interact_button")
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = VoidDark.copy(alpha = 0.94f),
                    border = androidx.compose.foundation.BorderStroke(
                        1.5.dp,
                        Color(prompt.type.baseColorHex)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = prompt.actionName,
                                tint = Color(prompt.type.baseColorHex),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = prompt.title.uppercase(),
                                    color = Color(prompt.type.baseColorHex),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "${prompt.actionName} (TAP TO ACTIVATE)",
                                    color = TextSecondary,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(prompt.type.baseColorHex).copy(alpha = 0.25f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(prompt.type.baseColorHex))
                        ) {
                            Text(
                                text = "TRIGGER",
                                color = Color(prompt.type.baseColorHex),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Joysticks & Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Movement Joystick (Left)
                Joystick(
                    size = 115.dp,
                    testTagStr = "move_joystick",
                    accentColor = NanoCyan,
                    onMove = onMove,
                    onRelease = { onMove(0f, 0f) }
                )

                // Tactical Action Buttons (Cover, Fire / Aim, Ability)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cover / Stance Action Button
                    val isCoverActive = player.isBehindCover || player.isCoverSnapped
                    val canVault = player.isCoverSnapped && player.coverHeight == CoverHeight.LOW
                    val buttonColor = when {
                        player.isCoverSnapped -> NaniteGreen.copy(alpha = 0.25f)
                        isCoverActive -> NanoCyan.copy(alpha = 0.2f)
                        else -> SlateCard
                    }
                    val borderColor = when {
                        player.isCoverSnapped -> NaniteGreen
                        isCoverActive -> NanoCyan
                        else -> NanoCyan.copy(alpha = 0.4f)
                    }
                    val iconTint = when {
                        player.isCoverSnapped -> NaniteGreen
                        isCoverActive -> NanoCyan
                        else -> TextSecondary
                    }
                    val labelText = when {
                        canVault -> "VAULT"
                        player.isCoverSnapped -> "SNAPPED"
                        player.isBehindCover -> "COVER"
                        player.stance == PlayerStance.STAND -> "STAND"
                        player.stance == PlayerStance.CROUCH -> "CROUCH"
                        player.stance == PlayerStance.PRONE -> "PRONE"
                        else -> "COVER"
                    }

                    Surface(
                        onClick = onToggleStance,
                        modifier = Modifier
                            .testTag("stance_button")
                            .size(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = buttonColor,
                        border = androidx.compose.foundation.BorderStroke(
                            if (isCoverActive) 1.5.dp else 1.dp,
                            borderColor
                        )
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Cover",
                                tint = iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = labelText,
                                color = if (isCoverActive) borderColor else TextMuted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Sonar / Recon Scan Button
                    Surface(
                        onClick = onTriggerSonarScan,
                        modifier = Modifier
                            .testTag("sonar_scan_button")
                            .size(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = SlateCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, NanoCyan)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sensors,
                                contentDescription = "Sonar Scan",
                                tint = NanoCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(text = "SONAR", color = NanoCyan, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Reload Button
                    IconButton(
                        onClick = onReload,
                        modifier = Modifier
                            .testTag("reload_button")
                            .size(44.dp)
                            .background(SlateCard, CircleShape)
                            .border(1.dp, HazardYellow, CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Reload", tint = HazardYellow, modifier = Modifier.size(20.dp))
                    }

                    // Ability / Gadget Button
                    Surface(
                        onClick = onThrowGadget,
                        modifier = Modifier
                            .testTag("gadget_button")
                            .size(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = SlateCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, PlasmaPink)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Ability",
                                tint = PlasmaPink,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(text = "ABILITY", color = PlasmaPink, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Aim Joystick (Right)
                Joystick(
                    size = 115.dp,
                    testTagStr = "aim_joystick",
                    accentColor = LaserRed,
                    onMove = { dx, dy ->
                        val angle = kotlin.math.atan2(dy, dx)
                        val isFiring = kotlin.math.sqrt(dx * dx + dy * dy) > 0.4f
                        onAim(angle, isFiring)
                    },
                    onRelease = onAimRelease
                )
            }
        }
    }
}
