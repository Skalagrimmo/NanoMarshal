package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.database.WeaponInventoryEntity
import com.example.data.model.DefaultWeapons
import com.example.data.model.Weapon
import com.example.data.repository.GameRepository
import com.example.data.repository.PlayerProfile
import com.example.engine.SoundFX
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// Subsystem Node Types for Visual Weapon Upgrade Blueprint
enum class UpgradeNode(val icon: @Composable () -> Unit, val color: Color) {
    ENERGY_CORE(
        icon = { Icon(Icons.Default.FlashOn, contentDescription = "Damage", tint = HazardYellow) },
        color = HazardYellow
    ),
    RICOCHET_LENS(
        icon = { Icon(Icons.Default.TrackChanges, contentDescription = "Ricochet", tint = NanoCyan) },
        color = NanoCyan
    ),
    MAG_BATTERY(
        icon = { Icon(Icons.Default.BatteryChargingFull, contentDescription = "Ammo", tint = NaniteGreen) },
        color = NaniteGreen
    ),
    HEAT_SINK(
        icon = { Icon(Icons.Default.LocalFireDepartment, contentDescription = "Cooling", tint = PlasmaPink) },
        color = PlasmaPink
    )
}

data class SparkParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var color: Color,
    var alpha: Float = 1.0f,
    var size: Float = 4.0f
)

@Composable
fun InteractiveWeaponUpgradeScreen(
    profile: PlayerProfile,
    repository: GameRepository,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dbInventory by repository.weaponInventoryFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val inventoryMap = remember(dbInventory) { dbInventory.associateBy { it.id } }

    val allWeapons = remember { DefaultWeapons.getAll() }
    var selectedWeaponId by remember { mutableStateOf(allWeapons.firstOrNull()?.id ?: "w_plasma") }
    val selectedWeapon = remember(selectedWeaponId) { allWeapons.find { it.id == selectedWeaponId } ?: allWeapons.first() }
    val selectedDbEntity = inventoryMap[selectedWeapon.id]

    var activeNode by remember { mutableStateOf(UpgradeNode.ENERGY_CORE) }
    var sparkParticles by remember { mutableStateOf(listOf<SparkParticle>()) }
    var pulseTrigger by remember { mutableIntStateOf(0) }

    val coroutineScope = rememberCoroutineScope()

    // Infinite pulsing glow for interactive nodes
    val infiniteTransition = rememberInfiniteTransition(label = "BlueprintPulse")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowAlpha"
    )

    // Particle Animation Loop for Interactive Clicks
    LaunchedEffect(pulseTrigger) {
        if (pulseTrigger > 0) {
            val newSparks = mutableListOf<SparkParticle>()
            val baseColor = activeNode.color
            for (i in 0 until 18) {
                val angle = Random.nextFloat() * 2f * Math.PI.toFloat()
                val speed = Random.nextFloat() * 220f + 80f
                newSparks.add(
                    SparkParticle(
                        x = 0f,
                        y = 0f,
                        vx = cos(angle) * speed,
                        vy = sin(angle) * speed,
                        color = if (Random.nextBoolean()) baseColor else Color.White,
                        alpha = 1.0f,
                        size = Random.nextFloat() * 6f + 3f
                    )
                )
            }
            sparkParticles = newSparks

            var elapsed = 0f
            val dt = 0.033f
            while (elapsed < 0.6f && sparkParticles.isNotEmpty()) {
                delay(33)
                elapsed += dt
                sparkParticles = sparkParticles.mapNotNull { p ->
                    val newAlpha = p.alpha - 0.06f
                    if (newAlpha <= 0f) null
                    else p.copy(
                        x = p.x + p.vx * dt,
                        y = p.y + p.vy * dt,
                        alpha = newAlpha
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(VoidDark)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(12.dp)
            .testTag("interactive_weapon_upgrade_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP HUD BAR (Minimal text, visual currency counters & back)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            SoundFX.play(SoundFX.SoundType.RELOAD)
                            onBack()
                        },
                        modifier = Modifier
                            .testTag("upgrade_back_button")
                            .background(SlateCard, RoundedCornerShape(12.dp))
                            .border(1.dp, NanoCyan.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = NanoCyan)
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Surface(
                        color = SlateCard,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NanoPurple)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Build, contentDescription = "Workbench", tint = NanoPurple, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("UPGRADE CORE", color = NanoPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Currency Displays
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Credits Pill
                    Surface(
                        color = SlateCard,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, HazardYellow)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.MonetizationOn, contentDescription = "Credits", tint = HazardYellow, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${profile.credits}", color = TextPrimary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }

                    // Cores Pill
                    Surface(
                        color = SlateCard,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NanoCyan)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Hexagon, contentDescription = "Cores", tint = NanoCyan, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${profile.naniteCores}", color = NanoCyan, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. HORIZONTAL WEAPON SELECTOR CAROUSEL
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("weapon_selector_carousel")
            ) {
                items(allWeapons) { weapon ->
                    val isUnlocked = profile.unlockedWeaponIds.contains(weapon.id)
                    val isSelected = weapon.id == selectedWeaponId
                    val dbEntity = inventoryMap[weapon.id]
                    val level = dbEntity?.upgradeLevel ?: 1

                    Surface(
                        onClick = {
                            selectedWeaponId = weapon.id
                            SoundFX.play(SoundFX.SoundType.RELOAD)
                        },
                        modifier = Modifier.testTag("weapon_tab_${weapon.id}"),
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) SlateSurface else SlateCard,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = when {
                                isSelected -> NanoCyan
                                !isUnlocked -> TextMuted
                                else -> SlateBorder
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (weapon.type) {
                                    com.example.data.model.WeaponType.PLASMA_RIFLE -> Icons.Default.Bolt
                                    com.example.data.model.WeaponType.PISTOL -> Icons.Default.GpsFixed
                                    com.example.data.model.WeaponType.RAILGUN -> Icons.Default.ElectricBolt
                                    else -> Icons.Default.SportsEsports
                                },
                                contentDescription = weapon.name,
                                tint = if (isSelected) NanoCyan else if (isUnlocked) TextPrimary else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = weapon.name,
                                    color = if (isSelected) NanoCyan else if (isUnlocked) TextPrimary else TextMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (isUnlocked) "LVL $level" else "LOCKED",
                                    color = if (isUnlocked) HazardYellow else TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 3. INTERACTIVE 2D BLUEPRINT CANVAS (Vector Weapon Schematic & Interactive Nodes)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.2f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF07131D), VoidDark, Color(0xFF0A1C2A))
                        )
                    )
                    .border(1.5.dp, NanoCyan.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .testTag("blueprint_canvas_container")
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(selectedWeaponId) {
                            detectTapGestures { offset ->
                                // Detect tap on blueprint nodes dynamically
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val distCore = (offset - center).getDistance()
                                val distMuzzle = (offset - Offset(center.x + size.width * 0.32f, center.y)).getDistance()
                                val distBattery = (offset - Offset(center.x - size.width * 0.12f, center.y + size.height * 0.28f)).getDistance()
                                val distHeat = (offset - Offset(center.x, center.y - size.height * 0.28f)).getDistance()

                                activeNode = when {
                                    distMuzzle < 90f -> UpgradeNode.RICOCHET_LENS
                                    distBattery < 90f -> UpgradeNode.MAG_BATTERY
                                    distHeat < 90f -> UpgradeNode.HEAT_SINK
                                    else -> UpgradeNode.ENERGY_CORE
                                }
                                SoundFX.play(SoundFX.SoundType.RICOCHET)
                                pulseTrigger++
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val center = Offset(w / 2f, h / 2f)

                    // Draw Technical Grid Lines
                    val gridSpacing = 32f
                    var x = 0f
                    while (x < w) {
                        drawLine(
                            color = NanoCyan.copy(alpha = 0.08f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1f
                        )
                        x += gridSpacing
                    }
                    var y = 0f
                    while (y < h) {
                        drawLine(
                            color = NanoCyan.copy(alpha = 0.08f),
                            start = Offset(0f, y),
                            end = Offset(w, y),
                            strokeWidth = 1f
                        )
                        y += gridSpacing
                    }

                    // Blueprint Target Reticle
                    drawCircle(
                        color = NanoCyan.copy(alpha = 0.15f),
                        radius = h * 0.4f,
                        center = center,
                        style = Stroke(width = 1.5f)
                    )

                    // Vector Weapon Blueprint Silhouette Paths
                    val barrelLength = w * 0.35f
                    val stockLength = w * 0.25f

                    // Main Barrel Shroud
                    val mainPath = Path().apply {
                        moveTo(center.x - stockLength, center.y + 12f)
                        lineTo(center.x - stockLength, center.y - 12f)
                        lineTo(center.x + barrelLength * 0.6f, center.y - 18f)
                        lineTo(center.x + barrelLength, center.y - 8f)
                        lineTo(center.x + barrelLength, center.y + 8f)
                        lineTo(center.x + barrelLength * 0.6f, center.y + 18f)
                        close()
                    }
                    drawPath(
                        path = mainPath,
                        color = SlateCard,
                        style = Stroke(width = 3f)
                    )

                    // Energy Core Shroud
                    drawCircle(
                        color = HazardYellow.copy(alpha = glowAlpha * 0.6f),
                        radius = 28f,
                        center = center
                    )
                    drawCircle(
                        color = HazardYellow,
                        radius = 16f,
                        center = center,
                        style = Stroke(width = 2.5f)
                    )

                    // Node 1: Muzzle Ricochet Lens
                    val muzzlePos = Offset(center.x + barrelLength, center.y)
                    drawLine(
                        color = NanoCyan.copy(alpha = 0.7f),
                        start = center,
                        end = muzzlePos,
                        strokeWidth = 2f
                    )
                    drawCircle(color = NanoCyan.copy(alpha = 0.3f), radius = 22f, center = muzzlePos)
                    drawCircle(color = NanoCyan, radius = 12f, center = muzzlePos, style = Stroke(width = 2f))

                    // Node 2: Mag Battery
                    val batteryPos = Offset(center.x - w * 0.12f, center.y + h * 0.28f)
                    drawLine(
                        color = NaniteGreen.copy(alpha = 0.7f),
                        start = center,
                        end = batteryPos,
                        strokeWidth = 2f
                    )
                    drawCircle(color = NaniteGreen.copy(alpha = 0.3f), radius = 22f, center = batteryPos)
                    drawCircle(color = NaniteGreen, radius = 12f, center = batteryPos, style = Stroke(width = 2f))

                    // Node 3: Heat Sink
                    val heatPos = Offset(center.x, center.y - h * 0.28f)
                    drawLine(
                        color = PlasmaPink.copy(alpha = 0.7f),
                        start = center,
                        end = heatPos,
                        strokeWidth = 2f
                    )
                    drawCircle(color = PlasmaPink.copy(alpha = 0.3f), radius = 22f, center = heatPos)
                    drawCircle(color = PlasmaPink, radius = 12f, center = heatPos, style = Stroke(width = 2f))

                    // Highlight Active Selected Node
                    val activePos = when (activeNode) {
                        UpgradeNode.ENERGY_CORE -> center
                        UpgradeNode.RICOCHET_LENS -> muzzlePos
                        UpgradeNode.MAG_BATTERY -> batteryPos
                        UpgradeNode.HEAT_SINK -> heatPos
                    }
                    drawCircle(
                        color = activeNode.color.copy(alpha = glowAlpha),
                        radius = 34f,
                        center = activePos,
                        style = Stroke(width = 3.5f)
                    )

                    // Draw Spark Particles on Canvas
                    sparkParticles.forEach { particle ->
                        drawCircle(
                            color = particle.color.copy(alpha = particle.alpha),
                            radius = particle.size,
                            center = activePos + Offset(particle.x, particle.y)
                        )
                    }
                }

                // Interactive Overlay Nodes (Clickable Badges on Top of Canvas)
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(HazardYellow.copy(alpha = 0.25f))
                        .clickable {
                            activeNode = UpgradeNode.ENERGY_CORE
                            SoundFX.play(SoundFX.SoundType.RELOAD)
                            pulseTrigger++
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.FlashOn, contentDescription = "Core", tint = HazardYellow, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 4. TEXTLESS GRAPHICAL LED STAT GAUGES (Segmented Meter Bars)
            val isUnlocked = profile.unlockedWeaponIds.contains(selectedWeapon.id)
            val currentLevel = selectedDbEntity?.upgradeLevel ?: 1
            val damageVal = selectedDbEntity?.damage ?: selectedWeapon.damage

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = SlateSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, SlateBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            activeNode.icon()
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${selectedWeapon.name} • ${activeNode.name.replace("_", " ")}",
                                color = activeNode.color,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Surface(
                            color = activeNode.color.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "LVL $currentLevel",
                                color = activeNode.color,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4 LED Stat Bar Rows
                    LedStatBarRow(
                        icon = Icons.Default.FlashOn,
                        label = "POWER",
                        level = (currentLevel + 2).coerceAtMost(10),
                        maxLevel = 10,
                        color = HazardYellow
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LedStatBarRow(
                        icon = Icons.Default.BatteryChargingFull,
                        label = "CAPACITY",
                        level = ((selectedDbEntity?.reserveAmmo ?: 100) / 30).coerceIn(1, 10),
                        maxLevel = 10,
                        color = NaniteGreen
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    LedStatBarRow(
                        icon = Icons.Default.TrackChanges,
                        label = "RICOCHET",
                        level = selectedWeapon.maxRicochets + (currentLevel / 2),
                        maxLevel = 5,
                        color = NanoCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 5. INTERACTIVE ACTION CORE (Spend Currency & Upgrade)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!isUnlocked) {
                    // Unlock Weapon Button
                    val canAffordUnlock = profile.credits >= selectedWeapon.cost
                    Button(
                        onClick = {
                            if (repository.buyWeapon(selectedWeapon)) {
                                SoundFX.play(SoundFX.SoundType.PLASMA_BLAST)
                                pulseTrigger++
                            }
                        },
                        enabled = canAffordUnlock,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("unlock_weapon_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = HazardYellow),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.LockOpen, contentDescription = "Unlock", tint = VoidDark)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("UNLOCK (${selectedWeapon.cost} CR)", color = VoidDark, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                        }
                    }
                } else {
                    // Spend Credits Upgrade (Cost: 350 CR)
                    val creditCost = 350
                    val canAffordCredits = profile.credits >= creditCost
                    Button(
                        onClick = {
                            if (repository.upgradeWeaponWithCredits(selectedWeapon.id, creditCost)) {
                                SoundFX.play(SoundFX.SoundType.RELOAD)
                                pulseTrigger++
                            }
                        },
                        enabled = canAffordCredits,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("upgrade_button_credits"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canAffordCredits) HazardYellow else SlateCard
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.MonetizationOn,
                                contentDescription = "Credits",
                                tint = if (canAffordCredits) VoidDark else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "+1 LVL ($creditCost CR)",
                                color = if (canAffordCredits) VoidDark else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    // Spend Cores Upgrade (Cost: 2 Cores)
                    val coreCost = 2
                    val canAffordCores = profile.naniteCores >= coreCost
                    Button(
                        onClick = {
                            if (repository.upgradeWeaponLevel(selectedWeapon.id, coreCost)) {
                                SoundFX.play(SoundFX.SoundType.PLASMA_BLAST)
                                pulseTrigger++
                            }
                        },
                        enabled = canAffordCores,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("upgrade_button_cores"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canAffordCores) NanoCyan else SlateCard
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Hexagon,
                                contentDescription = "Cores",
                                tint = if (canAffordCores) VoidDark else TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "OVERCLOCK ($coreCost CORES)",
                                color = if (canAffordCores) VoidDark else TextMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// Graphical LED Segmented Meter Bar
@Composable
private fun LedStatBarRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    level: Int,
    maxLevel: Int = 10,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(65.dp))

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (i in 1..maxLevel) {
                val isActive = i <= level
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isActive) color else SlateCard)
                        .border(
                            0.5.dp,
                            if (isActive) color.copy(alpha = 0.8f) else SlateBorder,
                            RoundedCornerShape(2.dp)
                        )
                )
            }
        }
    }
}
