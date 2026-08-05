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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.WeaponInventoryEntity
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
    var selectedTab by remember { mutableStateOf(0) } // 0 = Weapons, 1 = Room Inventory & Ammo, 2 = Gadgets
    val dbInventory by repository.weaponInventoryFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val inventoryMap = remember(dbInventory) { dbInventory.associateBy { it.id } }

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
                    Text(text = "NANO WORKBENCH", color = NanoPurple, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.MonetizationOn, contentDescription = "Credits", tint = HazardYellow, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "${profile.credits} CR", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Memory, contentDescription = "Cores", tint = NanoCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "${profile.naniteCores} CORES", color = NanoCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tab Row with Room DB indicator
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = SlateSurface,
                contentColor = NanoCyan,
                edgePadding = 0.dp
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("WEAPONS LOADOUT", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("AMMO & ROOM INVENTORY", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = NaniteGreen) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("TACTICAL GADGETS", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                        val dbEntity = inventoryMap[weapon.id]

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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(text = weapon.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        if (dbEntity != null && dbEntity.upgradeLevel > 1) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(color = NanoPurple.copy(alpha = 0.25f), shape = RoundedCornerShape(6.dp)) {
                                                Text(
                                                    text = "LVL ${dbEntity.upgradeLevel}",
                                                    color = NanoPurple,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (weapon.isSilenced) {
                                        Surface(color = NaniteGreen.copy(alpha = 0.2f), shape = RoundedCornerShape(8.dp)) {
                                            Text(text = "SILENCED", color = NaniteGreen, fontSize = 10.sp, modifier = Modifier.padding(6.dp))
                                        }
                                    }
                                }

                                Text(text = weapon.description, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(vertical = 6.dp))

                                // Ammo & Damage Row
                                val effectiveDmg = dbEntity?.damage ?: weapon.damage
                                val reserveAmmo = dbEntity?.reserveAmmo ?: 100
                                val magAmmo = dbEntity?.currentMagAmmo ?: weapon.magSize

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "DMG: $effectiveDmg | MAG: $magAmmo | RESERVE: $reserveAmmo",
                                        color = HazardYellow,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )

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
            } else if (selectedTab == 1) {
                // AMMO & ROOM INVENTORY TAB
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = SlateCard,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NaniteGreen.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(imageVector = Icons.Default.Storage, contentDescription = "Room DB", tint = NaniteGreen)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = "PERSISTENT ROOM DATABASE INVENTORY", color = NaniteGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "Tracks ammunition reserves, magazine states & weapon upgrade levels across game sessions.", color = TextSecondary, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    items(dbInventory) { item ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("inventory_item_${item.id}"),
                            shape = RoundedCornerShape(16.dp),
                            color = SlateSurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = item.name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Surface(color = NanoCyan.copy(alpha = 0.2f), shape = RoundedCornerShape(6.dp)) {
                                        Text(text = item.weaponType, color = NanoCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Ammunition Reserve Progress Bar
                                val ammoRatio = (item.reserveAmmo / item.maxReserveAmmo.toFloat()).coerceIn(0f, 1f)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(text = "RESERVE AMMO", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(text = "${item.reserveAmmo} / ${item.maxReserveAmmo}", color = NaniteGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(SlateCard)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(ammoRatio)
                                            .background(if (ammoRatio < 0.25f) HazardYellow else NaniteGreen)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(text = "BASE DMG: ${item.damage}", color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(text = "MAGAZINE: ${item.currentMagAmmo}/${item.magSize}", color = TextSecondary, fontSize = 11.sp)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Refill Ammo Button (250 CR)
                                        Button(
                                            onClick = { repository.buyAmmoRefill(item.id, 250) },
                                            enabled = item.reserveAmmo < item.maxReserveAmmo && profile.credits >= 250,
                                            colors = ButtonDefaults.buttonColors(containerColor = HazardYellow),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(text = "+50% AMMO (250 CR)", fontSize = 10.sp, color = VoidDark, fontWeight = FontWeight.Bold)
                                        }

                                        // Upgrade Level Button (2 Cores)
                                        Button(
                                            onClick = { repository.upgradeWeaponLevel(item.id, 2) },
                                            enabled = profile.naniteCores >= 2,
                                            colors = ButtonDefaults.buttonColors(containerColor = NanoPurple),
                                            shape = RoundedCornerShape(10.dp)
                                        ) {
                                            Text(text = "UPGRADE (2 CORES)", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
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

