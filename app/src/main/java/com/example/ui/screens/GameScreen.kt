package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Mission
import com.example.data.repository.GameRepository
import com.example.data.repository.PlayerProfile
import com.example.engine.GameEngine
import com.example.ui.components.TacticalHUD
import com.example.ui.components.VoxelCanvas
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun GameScreen(
    mission: Mission,
    profile: PlayerProfile,
    repository: GameRepository,
    onExitGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val engine = remember(mission.id) { GameEngine(mission) }
    val gameState by engine.gameState.collectAsState()

    var isPaused by remember { mutableStateOf(false) }

    // 60 FPS Game Engine Loop (16ms ticker)
    LaunchedEffect(isPaused, gameState.isGameOver) {
        var lastTime = System.currentTimeMillis()
        while (!isPaused && !gameState.isGameOver) {
            val now = System.currentTimeMillis()
            val dt = now - lastTime
            lastTime = now
            engine.update(dt.coerceIn(1, 50))
            delay(16)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidDark)
            .testTag("game_screen_root")
    ) {
        // 1. Interactive 60 FPS Sparse Voxel Canvas
        VoxelCanvas(
            gameState = gameState,
            terrain = engine.terrain,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Tactical HUD Overlay
        TacticalHUD(
            gameState = gameState,
            onMove = { dx, dy -> engine.handlePlayerMoveInput(dx, dy) },
            onAim = { angle, isFiring -> engine.handlePlayerAimInput(angle, isFiring) },
            onAimRelease = { engine.handlePlayerAimInput(gameState.player.facingAngle, false) },
            onToggleStance = { engine.toggleStance() },
            onSwapWeapon = { engine.swapWeapon() },
            onReload = { engine.reloadWeapon() },
            onThrowGadget = {
                val p = gameState.player
                val targetX = p.x + kotlin.math.cos(p.aimAngle) * 180f
                val targetY = p.y + kotlin.math.sin(p.aimAngle) * 180f
                engine.throwGadget(targetX, targetY)
            },
            onPauseToggle = { isPaused = !isPaused },
            onToggleTacticalOverlay = { engine.toggleTacticalOverlay() }
        )

        // 3. Pause Dialog Overlay
        if (isPaused) {
            AlertDialog(
                onDismissRequest = { isPaused = false },
                title = { Text("MISSION PAUSED", color = NanoCyan, fontWeight = FontWeight.Bold) },
                text = { Text("Sector: ${mission.sectorName}\nTarget: ${mission.bountyTargetName}") },
                confirmButton = {
                    Button(onClick = { isPaused = false }, colors = ButtonDefaults.buttonColors(containerColor = NanoCyan)) {
                        Text("RESUME MISSION", color = VoidDark, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = onExitGame) {
                        Text("ABORT MISSION", color = LaserRed)
                    }
                },
                containerColor = SlateSurface
            )
        }

        // 4. Mission Victory Modal
        if (gameState.isVictory) {
            LaunchedEffect(Unit) {
                repository.recordMissionVictory(
                    missionId = mission.id,
                    stars = 3,
                    creditsEarned = mission.rewardCredits,
                    coresEarned = mission.rewardCores
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VoidDark.copy(alpha = 0.9f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .testTag("victory_modal"),
                    shape = RoundedCornerShape(20.dp),
                    color = SlateSurface,
                    border = androidx.compose.foundation.BorderStroke(2.dp, NaniteGreen)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.MilitaryTech, contentDescription = "Victory", tint = NaniteGreen, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "BOUNTY SECURED!", color = NaniteGreen, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text(text = "${mission.bountyTargetName} eliminated", color = TextSecondary, fontSize = 13.sp)

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "REWARD CREDITS", color = TextMuted, fontSize = 11.sp)
                                Text(text = "+${mission.rewardCredits} CR", color = HazardYellow, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "NANITE CORES", color = TextMuted, fontSize = 11.sp)
                                Text(text = "+${mission.rewardCores} CORES", color = NanoCyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = onExitGame,
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NaniteGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(text = "RETURN TO STARSHIP BASE", color = VoidDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 5. Game Over Modal
        if (gameState.isGameOver && !gameState.isVictory) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VoidDark.copy(alpha = 0.9f))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .testTag("game_over_modal"),
                    shape = RoundedCornerShape(20.dp),
                    color = SlateSurface,
                    border = androidx.compose.foundation.BorderStroke(2.dp, LaserRed)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(imageVector = Icons.Default.Dangerous, contentDescription = "Defeat", tint = LaserRed, modifier = Modifier.size(56.dp))
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(text = "MARSHAL KILLED IN ACTION", color = LaserRed, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        Text(text = "Cybernetic suit critical breach", color = TextSecondary, fontSize = 13.sp)

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onExitGame,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("ABORT", color = TextPrimary)
                            }

                            Button(
                                onClick = { engine.initMission() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = LaserRed),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("RETRY", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
