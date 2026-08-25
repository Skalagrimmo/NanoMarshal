package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Help
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun LoreGuideScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .testTag("lore_guide_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("lore_back")) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = PlasmaPink)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "VOXEL ARCHITECTURE & TACTICS", color = PlasmaPink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Lore Card 1: Setting
            GuideCard(
                title = "NANOPUNK SETTING: SECTOR 09",
                content = "You are a Space Marshal deployed to the alien frontier rim. Criminal syndicates have seized automated voxel refinery outposts. Using advanced nano-technology, stealth field generators, and cover maneuvers, you must track down rogue bounties."
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Lore Card 2: Sparse Voxel DAG & LOD Engine
            GuideCard(
                title = "SPARSE VOXEL DAG & LOD RENDERING",
                content = "The planet's terrain is constructed from dynamic 3D sparse voxel structures organized in Directed Acyclic Graphs (DAG). Near the Marshal, Level of Detail 0 renders isometric bevel height blocks with glowing neon seams. At long distances, LOD 1 condenses voxel blocks into optimized geometry to ensure a smooth 60 FPS update cycle."
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Lore Card 3: Destructible Cover & Flanking Mechanics
            GuideCard(
                title = "TACTICAL COVER & ADAPTIVE ENEMY AI",
                content = "• Low Cover (50% Def): Crouch behind crates to hide from vision cones and absorb incoming plasma bolts.\n• High Cover (90% Def): Direct gunfire is absorbed. Use grenades or flanked angles to destroy wall structures.\n• Flanking Criticals: Attacking enemies outside their cover angle deals 180% Critical Damage!\n• Adaptive AI: Syndicate guards inspect gunfire noise, call reinforcements, and attempt to flank your cover position."
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Lore Card 4: Tactical Grid Visual Overlay & Line-of-Sight
            GuideCard(
                title = "TACTICAL GRID & LINE-OF-SIGHT OVERLAY",
                content = "• Tactical Grid Toggle: Tap [GRID: ON] in top HUD bar to activate real-time cybernetic grid overlay.\n• Line-of-Sight Grid Cells: Cyan grid tiles highlight valid line of sight from player position. Unseen regions behind heavy obstacles are dimmed in tactical fog.\n• Cover Badges: Terrain tiles with cover display glowing 90% DEF (High Cover) or 50% DEF (Low Cover) badges.\n• Combat Vector Rays: Dashed targeting rays connect to visible enemies, highlighting FLANKED target opportunities in green."
            )
        }
    }
}

@Composable
private fun GuideCard(title: String, content: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = SlateSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, color = NanoCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = content, color = TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}
