package com.example.engine

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.data.model.CoverHeight
import com.example.data.model.DynamicLight
import com.example.data.model.VoxelTile
import com.example.data.model.VoxelType
import com.example.ui.theme.*
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Rendering configuration options for the VoxelTerrainRenderer.
 */
data class VoxelRenderOptions(
    val enablePseudo3DExtrusions: Boolean = true,
    val enableAmbientOcclusion: Boolean = true,
    val enableTacticalGrid: Boolean = true,
    val enableCoverHeightIndicators: Boolean = true,
    val enableDamageCracks: Boolean = true,
    val enableDynamicLighting: Boolean = true,
    val enableHazardAnimations: Boolean = true,
    val elevationHeightScale: Float = 14f,
    val showGridCoordinates: Boolean = false
)

/**
 * A specialized, high-performance Canvas-based rendering engine for procedurally generated
 * tactical voxel terrain.
 *
 * Handles:
 * - Viewport frustum culling (rendering only visible tiles).
 * - Multi-layer pseudo-3D isometric voxel block extrusions with elevation shading.
 * - Dynamic lighting attenuation and ambient occlusion shadows.
 * - Material-specific surface details (metal panels, hazard stripes, acid pools, alien biomass).
 * - Structural damage deformation, crack lines, hit flash animations, and health indicators.
 * - Tactical cyber-grid overlays with cover classification badges.
 */
class VoxelTerrainRenderer(
    val options: VoxelRenderOptions = VoxelRenderOptions()
) {
    private val reusableWallSidePath = Path()
    private val reusableShadowPath = Path()

    /**
     * Renders the entire visible slice of procedurally generated voxel terrain onto a Compose Canvas DrawScope.
     */
    fun render(
        drawScope: DrawScope,
        terrain: VoxelTerrain,
        cameraX: Float,
        cameraY: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        playerX: Float? = null,
        playerY: Float? = null,
        dynamicLights: List<DynamicLight> = emptyList(),
        textMeasurer: TextMeasurer? = null,
        isTacticalOverlayActive: Boolean = options.enableTacticalGrid,
        animTimeSec: Float = (System.currentTimeMillis() % 100000L) / 1000f
    ) {
        val tileSize = terrain.tileSize

        // Calculate visible bounding box in tile grid coordinates with margin for elevated 3D blocks
        val minGx = max(0, ((cameraX - tileSize * 2f) / tileSize).toInt())
        val maxGx = min(terrain.width - 1, ((cameraX + viewportWidth + tileSize * 2f) / tileSize).toInt())
        val minGy = max(0, ((cameraY - tileSize * 2f) / tileSize).toInt())
        val maxGy = min(terrain.height - 1, ((cameraY + viewportHeight + tileSize * 4f) / tileSize).toInt())

        // Pass 1: Render Ground Floor Tiles, Hazards, and Ambient Shadows
        for (gx in minGx..maxGx) {
            for (gy in minGy..maxGy) {
                val tile = terrain.tiles[gx][gy]
                val worldX = gx * tileSize
                val worldY = gy * tileSize

                // Compute dynamic lighting contributions for this tile
                val lightData = if (options.enableDynamicLighting) {
                    computeLightingAt(worldX + tileSize / 2f, worldY + tileSize / 2f, dynamicLights)
                } else LightSample.AMBIENT

                drawFloorTile(
                    drawScope = drawScope,
                    tile = tile,
                    worldX = worldX,
                    worldY = worldY,
                    tileSize = tileSize,
                    lightData = lightData,
                    animTimeSec = animTimeSec
                )

                if (options.enableAmbientOcclusion) {
                    drawAmbientOcclusionShadow(
                        drawScope = drawScope,
                        terrain = terrain,
                        gx = gx,
                        gy = gy,
                        worldX = worldX,
                        worldY = worldY,
                        tileSize = tileSize
                    )
                }
            }
        }

        // Pass 2: Tactical Grid & Coordinate Overlay
        if (isTacticalOverlayActive) {
            drawTacticalGridOverlay(
                drawScope = drawScope,
                minGx = minGx,
                maxGx = maxGx,
                minGy = minGy,
                maxGy = maxGy,
                tileSize = tileSize,
                playerX = playerX,
                playerY = playerY,
                textMeasurer = textMeasurer
            )
        }

        // Pass 3: Render Elevated 3D Voxel Blocks & Destructible Obstacles (Sorted by Y for depth layering)
        for (gy in minGy..maxGy) {
            for (gx in minGx..maxGx) {
                val tile = terrain.tiles[gx][gy]
                if (tile.coverHeight == CoverHeight.NONE || tile.isDisintegrated) continue

                val worldX = gx * tileSize
                val worldY = gy * tileSize

                val lightData = if (options.enableDynamicLighting) {
                    computeLightingAt(worldX + tileSize / 2f, worldY + tileSize / 2f, dynamicLights)
                } else LightSample.AMBIENT

                drawElevatedVoxelBlock(
                    drawScope = drawScope,
                    tile = tile,
                    worldX = worldX,
                    worldY = worldY,
                    tileSize = tileSize,
                    lightData = lightData,
                    animTimeSec = animTimeSec,
                    textMeasurer = textMeasurer
                )
            }
        }
    }

    /**
     * Renders a single floor/ground voxel tile with material colors, grid borders, or hazard textures.
     */
    private fun drawFloorTile(
        drawScope: DrawScope,
        tile: VoxelTile,
        worldX: Float,
        worldY: Float,
        tileSize: Float,
        lightData: LightSample,
        animTimeSec: Float
    ) {
        val baseFloorColor = when (tile.type) {
            VoxelType.FLOOR_PLAZA -> Color(0xFF0F172A)
            VoxelType.FLOOR_DIRT -> Color(0xFF0B1120)
            VoxelType.ACID_POOL -> Color(0xFF064E3B)
            else -> Color(0xFF070D18)
        }

        val litFloorColor = Color(
            red = (baseFloorColor.red + lightData.addR * 0.65f).coerceIn(0f, 1f),
            green = (baseFloorColor.green + lightData.addG * 0.65f).coerceIn(0f, 1f),
            blue = (baseFloorColor.blue + lightData.addB * 0.65f).coerceIn(0f, 1f),
            alpha = baseFloorColor.alpha
        )

        // Draw base floor rectangle
        drawScope.drawRoundRect(
            color = litFloorColor,
            topLeft = Offset(worldX + 1f, worldY + 1f),
            size = Size(tileSize - 2f, tileSize - 2f),
            cornerRadius = CornerRadius(3f)
        )

        // Special rendering for Toxic / Acid Hazard Pools
        if (tile.type == VoxelType.ACID_POOL && options.enableHazardAnimations) {
            val ripple1 = (sin(animTimeSec * 2.8f + worldX * 0.05f) * 0.5f + 0.5f)
            val ripple2 = (cos(animTimeSec * 3.4f + worldY * 0.05f) * 0.5f + 0.5f)
            val acidColor = Color(0xFF10B981).copy(alpha = 0.35f + ripple1 * 0.25f)
            val innerAcid = Color(0xFF34D399).copy(alpha = 0.5f + ripple2 * 0.3f)

            drawScope.drawCircle(
                color = acidColor,
                radius = (tileSize * 0.35f) * (0.85f + ripple1 * 0.25f),
                center = Offset(worldX + tileSize / 2f, worldY + tileSize / 2f)
            )
            drawScope.drawCircle(
                color = innerAcid,
                radius = tileSize * 0.18f,
                center = Offset(worldX + tileSize / 2f + cos(animTimeSec * 2f) * 4f, worldY + tileSize / 2f + sin(animTimeSec * 2f) * 4f)
            )
            drawScope.drawRoundRect(
                color = Color(0xFF059669).copy(alpha = 0.4f),
                topLeft = Offset(worldX + 2f, worldY + 2f),
                size = Size(tileSize - 4f, tileSize - 4f),
                cornerRadius = CornerRadius(4f),
                style = Stroke(width = 1.5f)
            )
        } else {
            // Floor cybernetic grid border
            drawScope.drawRoundRect(
                color = Color(0xFF1E293B).copy(alpha = 0.45f),
                topLeft = Offset(worldX, worldY),
                size = Size(tileSize, tileSize),
                cornerRadius = CornerRadius(2f),
                style = Stroke(width = 1f)
            )
        }

        // Dynamic light glow over ground
        if (lightData.totalIntensity > 0.05f) {
            val glowColor = Color(
                red = lightData.addR.coerceIn(0f, 1f),
                green = lightData.addG.coerceIn(0f, 1f),
                blue = lightData.addB.coerceIn(0f, 1f),
                alpha = (lightData.totalIntensity * 0.35f).coerceIn(0f, 0.65f)
            )
            drawScope.drawRoundRect(
                color = glowColor,
                topLeft = Offset(worldX + 2f, worldY + 2f),
                size = Size(tileSize - 4f, tileSize - 4f),
                cornerRadius = CornerRadius(3f)
            )
        }
    }

    /**
     * Renders ambient occlusion shadow cast by adjacent elevated voxels.
     */
    private fun drawAmbientOcclusionShadow(
        drawScope: DrawScope,
        terrain: VoxelTerrain,
        gx: Int,
        gy: Int,
        worldX: Float,
        worldY: Float,
        tileSize: Float
    ) {
        // If the neighbor above or left is an elevated wall, cast an ambient shadow
        val topNeighbor = if (gy > 0) terrain.tiles[gx][gy - 1] else null
        val leftNeighbor = if (gx > 0) terrain.tiles[gx - 1][gy] else null

        if (topNeighbor != null && topNeighbor.coverHeight != CoverHeight.NONE && !topNeighbor.isDisintegrated) {
            drawScope.drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                    startY = worldY,
                    endY = worldY + tileSize * 0.35f
                ),
                topLeft = Offset(worldX, worldY),
                size = Size(tileSize, tileSize * 0.35f)
            )
        }

        if (leftNeighbor != null && leftNeighbor.coverHeight != CoverHeight.NONE && !leftNeighbor.isDisintegrated) {
            drawScope.drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent),
                    startX = worldX,
                    endX = worldX + tileSize * 0.25f
                ),
                topLeft = Offset(worldX, worldY),
                size = Size(tileSize * 0.25f, tileSize)
            )
        }
    }

    /**
     * Renders an elevated voxel block with pseudo-3D extrusion walls, material texturing,
     * structural damage cracks, deformation, and health status indicators.
     */
    private fun drawElevatedVoxelBlock(
        drawScope: DrawScope,
        tile: VoxelTile,
        worldX: Float,
        worldY: Float,
        tileSize: Float,
        lightData: LightSample,
        animTimeSec: Float,
        textMeasurer: TextMeasurer?
    ) {
        val elevationLevels = tile.elevationZ.coerceAtLeast(1)
        val heightOffset = elevationLevels * options.elevationHeightScale
        val blockW = tileSize - 6f
        val blockH = tileSize - 6f
        val halfW = blockW / 2f
        val halfH = blockH / 2f

        val blockCenterX = worldX + tileSize / 2f + tile.deformationX
        val blockCenterY = worldY + tileSize / 2f + tile.deformationY - heightOffset

        val baseColors = getVoxelMaterialColors(tile.type)

        // 1. Render Pseudo-3D Extrusion Side Wall Face (Front & Side Depth)
        if (options.enablePseudo3DExtrusions && heightOffset > 0f) {
            val frontWallTop = blockCenterY + halfH
            val frontWallBottom = worldY + tileSize - 3f

            val sideFaceColor = Color(
                red = (baseColors.darkShade.red * 0.7f + lightData.addR * 0.4f).coerceIn(0f, 1f),
                green = (baseColors.darkShade.green * 0.7f + lightData.addG * 0.4f).coerceIn(0f, 1f),
                blue = (baseColors.darkShade.blue * 0.7f + lightData.addB * 0.4f).coerceIn(0f, 1f),
                alpha = 1f
            )

            // Draw front vertical drop face
            drawScope.drawRoundRect(
                color = sideFaceColor,
                topLeft = Offset(blockCenterX - halfW, frontWallTop - 2f),
                size = Size(blockW, max(2f, frontWallBottom - frontWallTop + 2f)),
                cornerRadius = CornerRadius(3f)
            )

            // Panel seam lines on side wall face
            drawScope.drawLine(
                color = Color.Black.copy(alpha = 0.45f),
                start = Offset(blockCenterX - halfW, frontWallBottom),
                end = Offset(blockCenterX + halfW, frontWallBottom),
                strokeWidth = 2f
            )
        }

        // 2. Render Top Deck Face with Matrix Transformations (Deformation & Rotation)
        drawScope.withTransform({
            translate(left = blockCenterX, top = blockCenterY)
            if (tile.rotationAngle != 0f) {
                rotate(degrees = Math.toDegrees(tile.rotationAngle.toDouble()).toFloat(), pivot = Offset.Zero)
            }
            if (tile.meshScaleX != 1f || tile.meshScaleY != 1f) {
                scale(scaleX = tile.meshScaleX, scaleY = tile.meshScaleY, pivot = Offset.Zero)
            }
        }) {
            // Material Shader & Detail rendering
            VoxelMaterialShader.drawBlockShader(
                drawScope = this,
                halfW = halfW,
                halfH = halfH,
                tile = tile,
                lightIntensity = lightData.totalIntensity,
                lightDirX = lightData.dirX,
                lightDirY = lightData.dirY,
                addR = lightData.addR,
                addG = lightData.addG,
                addB = lightData.addB
            )

            // Destructible Crack Lines Overlay
            if (options.enableDamageCracks && tile.damageCracksCount > 0) {
                val crackColor = HazardYellow.copy(alpha = 0.85f)
                val numCracks = tile.damageCracksCount.coerceAtMost(5)
                for (c in 0 until numCracks) {
                    val startX = (-halfW + 8f + c * 11f).coerceIn(-halfW + 2f, halfW - 2f)
                    val startY = -halfH + 4f
                    val midX = startX + if (c % 2 == 0) 10f else -8f
                    val midY = 0f
                    val endX = startX + if (c % 3 == 0) -6f else 12f
                    val endY = halfH - 4f

                    drawLine(crackColor, Offset(startX, startY), Offset(midX, midY), strokeWidth = 1.8f)
                    drawLine(crackColor, Offset(midX, midY), Offset(endX, endY), strokeWidth = 1.2f)
                }
            }

            // Hit Flash Impact Glow
            if (tile.hitFlashTimer > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = tile.hitFlashTimer * 0.7f),
                    topLeft = Offset(-halfW, -halfH),
                    size = Size(blockW, blockH),
                    cornerRadius = CornerRadius(5f)
                )
                drawRoundRect(
                    color = NanoCyan.copy(alpha = tile.hitFlashTimer),
                    topLeft = Offset(-halfW, -halfH),
                    size = Size(blockW, blockH),
                    cornerRadius = CornerRadius(5f),
                    style = Stroke(width = 3.5f)
                )
            }

            // Health Status Bar for Damaged Voxels
            if (tile.isDestructible && tile.currentHp < tile.maxHp) {
                val hpPct = (tile.currentHp / tile.maxHp).coerceIn(0f, 1f)
                val barW = blockW - 10f
                val barH = 3.5f

                drawRect(
                    color = Color.Black.copy(alpha = 0.8f),
                    topLeft = Offset(-halfW + 5f, -halfH + 4f),
                    size = Size(barW, barH)
                )
                val hpColor = when {
                    hpPct > 0.6f -> NaniteGreen
                    hpPct > 0.3f -> HazardYellow
                    else -> LaserRed
                }
                drawRect(
                    color = hpColor,
                    topLeft = Offset(-halfW + 5f, -halfH + 4f),
                    size = Size(barW * hpPct, barH)
                )
            }

            // Cover Badge Indicator (Low vs High Cover)
            if (options.enableCoverHeightIndicators) {
                val badgeColor = when (tile.coverHeight) {
                    CoverHeight.HIGH -> NanoCyan
                    CoverHeight.LOW -> HazardYellow
                    CoverHeight.NONE -> Color.Transparent
                }
                if (tile.coverHeight != CoverHeight.NONE) {
                    drawCircle(
                        color = badgeColor.copy(alpha = 0.6f),
                        radius = 3.5f,
                        center = Offset(halfW - 8f, halfH - 8f)
                    )
                }
            }
        }
    }

    /**
     * Renders tactical grid lines, sector markings, and coordinate tags.
     */
    private fun drawTacticalGridOverlay(
        drawScope: DrawScope,
        minGx: Int,
        maxGx: Int,
        minGy: Int,
        maxGy: Int,
        tileSize: Float,
        playerX: Float?,
        playerY: Float?,
        textMeasurer: TextMeasurer?
    ) {
        val gridLineColor = NanoCyan.copy(alpha = 0.22f)
        val cornerTickColor = NanoCyan.copy(alpha = 0.65f)
        val tickLen = 6f

        for (gx in minGx..maxGx) {
            for (gy in minGy..maxGy) {
                val wx = gx * tileSize
                val wy = gy * tileSize

                // Draw Grid Cell Box
                drawScope.drawRect(
                    color = gridLineColor,
                    topLeft = Offset(wx, wy),
                    size = Size(tileSize, tileSize),
                    style = Stroke(width = 1f)
                )

                // Cybernetic Corner Ticks
                drawScope.drawLine(cornerTickColor, Offset(wx, wy), Offset(wx + tickLen, wy), strokeWidth = 1.5f)
                drawScope.drawLine(cornerTickColor, Offset(wx, wy), Offset(wx, wy + tickLen), strokeWidth = 1.5f)
                drawScope.drawLine(cornerTickColor, Offset(wx + tileSize, wy + tileSize), Offset(wx + tileSize - tickLen, wy + tileSize), strokeWidth = 1.5f)
                drawScope.drawLine(cornerTickColor, Offset(wx + tileSize, wy + tileSize), Offset(wx + tileSize, wy + tileSize - tickLen), strokeWidth = 1.5f)

                // Optional Grid Coordinate Text (e.g. "A4", "C8")
                if (options.showGridCoordinates && textMeasurer != null && gx % 2 == 0 && gy % 2 == 0) {
                    val coordLabel = "${('A'.code + (gx % 26)).toChar()}$gy"
                    drawScope.drawText(
                        textMeasurer = textMeasurer,
                        text = coordLabel,
                        style = TextStyle(
                            color = NanoCyan.copy(alpha = 0.35f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        ),
                        topLeft = Offset(wx + 3f, wy + 3f)
                    )
                }
            }
        }
    }

    /**
     * Samples and sums dynamic lights at a specific world coordinate.
     */
    private fun computeLightingAt(x: Float, y: Float, dynamicLights: List<DynamicLight>): LightSample {
        var addR = 0f
        var addG = 0f
        var addB = 0f
        var totalIntensity = 0f
        var dirX = 0f
        var dirY = 0f
        var maxFalloff = 0f

        for (light in dynamicLights) {
            val lx = light.x - x
            val ly = light.y - y
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
                    totalIntensity += falloff

                    if (falloff > maxFalloff && dist > 0.1f) {
                        maxFalloff = falloff
                        dirX = -lx / dist
                        dirY = -ly / dist
                    }
                }
            }
        }

        return LightSample(
            addR = addR,
            addG = addG,
            addB = addB,
            totalIntensity = totalIntensity,
            dirX = dirX,
            dirY = dirY
        )
    }

    private fun getVoxelMaterialColors(type: VoxelType): VoxelColorPalette {
        return when (type) {
            VoxelType.REINFORCED_METAL -> VoxelColorPalette(
                base = Color(0xFF334155),
                darkShade = Color(0xFF1E293B),
                highlight = Color(0xFF64748B)
            )
            VoxelType.CONCRETE_WALL, VoxelType.HIGH_COVER_WALL -> VoxelColorPalette(
                base = Color(0xFF1E293B),
                darkShade = Color(0xFF0F172A),
                highlight = Color(0xFF475569)
            )
            VoxelType.LOW_COVER_CRATE -> VoxelColorPalette(
                base = Color(0xFF78350F),
                darkShade = Color(0xFF451A03),
                highlight = Color(0xFFB45309)
            )
            VoxelType.EXPLOSIVE_BARREL -> VoxelColorPalette(
                base = Color(0xFF991B1B),
                darkShade = Color(0xFF7F1D1D),
                highlight = Color(0xFFEF4444)
            )
            VoxelType.ALIEN_BIOMASS -> VoxelColorPalette(
                base = Color(0xFF581C87),
                darkShade = Color(0xFF3B0764),
                highlight = Color(0xFFA855F7)
            )
            VoxelType.ENERGY_BARRIER -> VoxelColorPalette(
                base = Color(0xFF0369A1),
                darkShade = Color(0xFF075985),
                highlight = Color(0xFF38BDF8)
            )
            VoxelType.DESTRUCTIBLE_PILLAR -> VoxelColorPalette(
                base = Color(0xFF475569),
                darkShade = Color(0xFF334155),
                highlight = Color(0xFF94A3B8)
            )
            VoxelType.OBJECTIVE_NODE -> VoxelColorPalette(
                base = Color(0xFF6B21A8),
                darkShade = Color(0xFF4C1D95),
                highlight = Color(0xFFD946EF)
            )
            else -> VoxelColorPalette(
                base = Color(0xFF1E293B),
                darkShade = Color(0xFF0F172A),
                highlight = Color(0xFF334155)
            )
        }
    }

    private data class LightSample(
        val addR: Float,
        val addG: Float,
        val addB: Float,
        val totalIntensity: Float,
        val dirX: Float,
        val dirY: Float
    ) {
        companion object {
            val AMBIENT = LightSample(0f, 0f, 0f, 0f, 0f, 0f)
        }
    }

    private data class VoxelColorPalette(
        val base: Color,
        val darkShade: Color,
        val highlight: Color
    )
}

/**
 * Standalone Composable for rendering a procedural tactical voxel terrain preview or editor.
 */
@Composable
fun VoxelTerrainCanvas(
    terrain: VoxelTerrain,
    modifier: Modifier = Modifier,
    cameraOffsetX: Float = 0f,
    cameraOffsetY: Float = 0f,
    options: VoxelRenderOptions = VoxelRenderOptions(),
    dynamicLights: List<DynamicLight> = emptyList(),
    playerX: Float? = null,
    playerY: Float? = null
) {
    val renderer = remember(options) { VoxelTerrainRenderer(options) }
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

    Canvas(modifier = modifier.fillMaxSize()) {
        withTransform({
            translate(left = -cameraOffsetX, top = -cameraOffsetY)
        }) {
            renderer.render(
                drawScope = this,
                terrain = terrain,
                cameraX = cameraOffsetX,
                cameraY = cameraOffsetY,
                viewportWidth = size.width,
                viewportHeight = size.height,
                playerX = playerX,
                playerY = playerY,
                dynamicLights = dynamicLights,
                textMeasurer = textMeasurer
            )
        }
    }
}
