package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.repository.PlayerProfile
import com.example.ui.theme.*

@Composable
fun MainMenuScreen(
    profile: PlayerProfile,
    onNavigateToMissions: () -> Unit,
    onNavigateToWorkbench: () -> Unit,
    onNavigateToLore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(VoidDark, SlateSurface, VoidDark)
                )
            )
            .testTag("main_menu_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar Currency Stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SlateCard.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                    .border(1.dp, SlateBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.MonetizationOn, contentDescription = "Credits", tint = HazardYellow)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "${profile.credits} CREDITS", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Hexagon, contentDescription = "Cores", tint = NanoCyan)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "${profile.naniteCores} NANITE CORES", color = NanoCyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }

            // Hero Brand Title & Icon Banner
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(90.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = SlateCard,
                    border = androidx.compose.foundation.BorderStroke(2.dp, NanoCyan)
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_nanomarshal_logo_1785925551625),
                        contentDescription = "Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "NANO MARSHAL",
                    color = NanoCyan,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )

                Text(
                    text = "SECTOR 09 • TACTICAL VOXEL SHOOTER",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp
                )
            }

            // Main Menu Buttons
            Column(
                modifier = Modifier.fillMaxWidth(0.9f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                MenuButton(
                    text = "BOUNTY BOARD (MISSIONS)",
                    icon = Icons.Default.SportsEsports,
                    testTag = "start_game_button",
                    accentColor = NanoCyan,
                    onClick = onNavigateToMissions
                )

                MenuButton(
                    text = "WEAPON WORKBENCH",
                    icon = Icons.Default.Build,
                    testTag = "workbench_button",
                    accentColor = NanoPurple,
                    onClick = onNavigateToWorkbench
                )

                MenuButton(
                    text = "VOXEL ARCHITECTURE & LORE",
                    icon = Icons.Default.MenuBook,
                    testTag = "lore_button",
                    accentColor = PlasmaPink,
                    onClick = onNavigateToLore
                )
            }

            // Footer Version
            Text(
                text = "Sparse Voxel DAG & LOD Engine v2.4 • Nanopunk OS",
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun MenuButton(
    text: String,
    icon: ImageVector,
    testTag: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(testTag),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SlateCard),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, accentColor)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(imageVector = icon, contentDescription = text, tint = accentColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = text, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}
