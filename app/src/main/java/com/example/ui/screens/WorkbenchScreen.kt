package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.data.model.DefaultGadgets
import com.example.data.model.DefaultWeapons
import com.example.data.model.Weapon
import com.example.data.repository.GameRepository
import com.example.data.repository.PlayerProfile
import com.example.ui.theme.*

@Composable
fun WorkbenchScreen(
    profile: PlayerProfile,
    repository: GameRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Weapons, 1 = Gadgets

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .testTag("workbench_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("workbench_back")) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = NanoPurple)
                    }
                    Text(text = "NANO WORKBENCH", color = NanoPurple, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.MonetizationOn, contentDescription = "Credits", tint = HazardYellow)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = "${profile.credits} CR", color = TextPrimary, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = SlateSurface,
                contentColor = NanoCyan
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("WEAPONS LOADOUT", fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("TACTICAL GADGETS", fontWeight = FontWeight.Bold) }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                // Weapons List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(DefaultWeapons.getAll()) { weapon ->
                        val isUnlocked = profile.unlockedWeaponIds.contains(weapon.id)
                        val isEquippedPrimary = profile.primaryWeaponId == weapon.id
                        val isEquippedSecondary = profile.secondaryWeaponId == weapon.id

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("weapon_item_${weapon.id}"),
                            shape = RoundedCornerShape(16.dp),
                            color = SlateSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (isEquippedPrimary || isEquippedSecondary) NanoCyan else SlateBorder
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = weapon.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    if (weapon.isSilenced) {
                                        Surface(color = NaniteGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                                            Text(text = "SILENCED", color = NaniteGreen, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                                        }
                                    }
                                }

                                Text(text = weapon.description, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "DMG: ${weapon.damage} | COVER DMG: ${weapon.coverDamage}", color = HazardYellow, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                                    if (!isUnlocked) {
                                        Button(
                                            onClick = { repository.buyWeapon(weapon) },
                                            colors = ButtonDefaults.buttonColors(containerColor = HazardYellow),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(text = "UNLOCK ${weapon.cost} CR", color = VoidDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    } else {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = { repository.saveProfile(profile.copy(primaryWeaponId = weapon.id)) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isEquippedPrimary) NanoCyan else SlateCard
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(text = if (isEquippedPrimary) "PRIMARY" else "EQUIP PRI", fontSize = 11.sp)
                                            }

                                            Button(
                                                onClick = { repository.saveProfile(profile.copy(secondaryWeaponId = weapon.id)) },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isEquippedSecondary) NanoPurple else SlateCard
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Text(text = if (isEquippedSecondary) "SIDEARM" else "EQUIP SEC", fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Gadgets List
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(DefaultGadgets.getAll()) { gadget ->
                        val isUnlocked = profile.unlockedGadgetIds.contains(gadget.id)
                        val isEquipped = profile.activeGadgetId == gadget.id

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("gadget_item_${gadget.id}"),
                            shape = RoundedCornerShape(16.dp),
                            color = SlateSurface,
                            border = androidx.compose.foundation.BorderStroke(
                                width = 1.dp,
                                color = if (isEquipped) PlasmaPink else SlateBorder
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(text = gadget.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text(text = gadget.description, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = "CAPACITY: ${gadget.maxCount} CHARGES", color = NanoCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                                    if (!isUnlocked) {
                                        Button(
                                            onClick = { repository.buyGadget(gadget) },
                                            colors = ButtonDefaults.buttonColors(containerColor = HazardYellow),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(text = "UNLOCK ${gadget.cost} CR", color = VoidDark, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    } else {
                                        Button(
                                            onClick = { repository.saveProfile(profile.copy(activeGadgetId = gadget.id)) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isEquipped) PlasmaPink else SlateCard
                                            ),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Text(text = if (isEquipped) "EQUIPPED" else "EQUIP GADGET", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
