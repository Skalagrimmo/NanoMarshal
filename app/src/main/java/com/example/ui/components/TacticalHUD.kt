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
import com.example.data.model.ObjectiveStatus
import com.example.data.model.PlayerStance
import com.example.engine.GameState
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrackChanges,
                                contentDescription = "Auto-Aim",
                                tint = if (player.isAutoAimEnabled) PlasmaPink else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "AIM: ${player.autoAimMode.name}",
                                color = if (player.isAutoAimEnabled) PlasmaPink else TextMuted,
                                fontSize = 10.sp,
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
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Grid4x4,
                                contentDescription = "Grid",
                                tint = if (gameState.isTacticalGridOverlayEnabled) NanoCyan else TextMuted,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (gameState.isTacticalGridOverlayEnabled) "GRID: ON" else "GRID: OFF",
                                color = if (gameState.isTacticalGridOverlayEnabled) NanoCyan else TextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Pause Button
                    IconButton(
                        onClick = onPauseToggle,
                        modifier = Modifier
                            .testTag("pause_button")
                            .size(36.dp)
                            .background(VoidDark.copy(alpha = 0.85f), CircleShape)
                            .border(1.dp, SlateBorder, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pause,
                            contentDescription = "Pause",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "SVDAG COMP: $compStr% // NODES: ${gameState.uniqueDagNodes}/${gameState.totalDagNodes}",
                        color = NanoCyan.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "LOD [L0:${gameState.lod0Count} L1:${gameState.lod1Count} L2:${gameState.lod2Count}]",
                        color = TextMuted,
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
                    Surface(
                        onClick = onToggleStance,
                        modifier = Modifier
                            .testTag("stance_button")
                            .size(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = SlateCard,
                        border = androidx.compose.foundation.BorderStroke(1.dp, NanoCyan.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Cover",
                                tint = NanoCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(text = "COVER", color = NanoCyan.copy(alpha = 0.8f), fontSize = 8.sp, fontWeight = FontWeight.Bold)
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
