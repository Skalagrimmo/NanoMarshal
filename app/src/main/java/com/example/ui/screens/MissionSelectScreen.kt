package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.example.data.model.DefaultMissions
import com.example.data.model.Mission
import com.example.data.repository.PlayerProfile
import com.example.ui.theme.*

@Composable
fun MissionSelectScreen(
    profile: PlayerProfile,
    onSelectMission: (Mission) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedMission by remember { mutableStateOf(DefaultMissions.MISSION_1) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .testTag("mission_select_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = NanoCyan)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "BOUNTY SECTOR BOARD", color = NanoCyan, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(text = "FBM + Catmull-Rom Spline World Synthesis Engine", color = NaniteGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Missions List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(DefaultMissions.getAll()) { mission ->
                    val isUnlocked = mission.id == "m_outpost9" || profile.completedMissionIds.contains("m_outpost9") || mission.difficulty <= 2
                    val isSelected = mission.id == selectedMission.id

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isUnlocked) { selectedMission = mission }
                            .testTag("mission_item_${mission.id}"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) SlateCard else SlateSurface,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) NanoCyan else SlateBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = mission.sectorName, color = NanoPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(text = mission.title, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.Terrain, contentDescription = "FBM Terrain", tint = NaniteGreen, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = "FBM + Spline Curve Terrain Map", color = NaniteGreen, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "TARGET: ${mission.bountyTargetName}", color = HazardYellow, fontSize = 12.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(imageVector = Icons.Default.MonetizationOn, contentDescription = "Reward", tint = HazardYellow, modifier = Modifier.size(14.dp))
                                    Text(text = "+${mission.rewardCredits} CR", color = TextSecondary, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Icon(imageVector = Icons.Default.Hexagon, contentDescription = "Cores", tint = NanoCyan, modifier = Modifier.size(14.dp))
                                    Text(text = "+${mission.rewardCores} CORES", color = NanoCyan, fontSize = 12.sp)
                                }
                            }

                            if (!isUnlocked) {
                                Icon(imageVector = Icons.Default.Lock, contentDescription = "Locked", tint = TextMuted)
                            } else if (isSelected) {
                                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Selected", tint = NanoCyan)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Launch Mission CTA
            Button(
                onClick = { onSelectMission(selectedMission) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("launch_mission_button"),
                colors = ButtonDefaults.buttonColors(containerColor = NanoCyan),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Deploy", tint = VoidDark)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "DEPLOY MARSHAL TO SECTOR", color = VoidDark, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
        }
    }
}
