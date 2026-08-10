package com.example.engine

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.data.model.VoxelTile
import com.example.data.model.VoxelType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Categorized material types for voxel blocks in the VoxelWorldManager environment.
 */
enum class VoxelMaterialType {
    REINFORCED_METAL,
    CONCRETE,
    ALIEN_BIOMASS,
    ENERGY_PLASMA,
    VOLATILE_HAZARD,
    CRYSTALLINE_NODE,
    PLAZA_STONE,
    DIRT_EARTH
}

/**
 * Structural, specular, and optical properties of a voxel block material.
 */
data class VoxelMaterialProperties(
    val type: VoxelMaterialType,
    val name: String,
    val baseColor: Color,
    val secondaryColor: Color,
    val specularColor: Color,
    val glowColor: Color,
    val roughness: Float = 0.5f,
    val metallicSheen: Float = 0.0f,
    val pulseRate: Float = 0.0f,
    val isOrganic: Boolean = false,
    val isGlowing: Boolean = false,
    val fractureResilience: Float = 1.0f
)

/**
 * Strategy interface for shader-based rendering of voxel block materials.
 */
interface VoxelMaterialShaderStrategy {
    fun drawMaterialBlock(
        drawScope: DrawScope,
        halfW: Float,
        halfH: Float,
        tile: VoxelTile,
        material: VoxelMaterialProperties,
        animTimeSec: Float,
        lightIntensity: Float,
        lightDirX: Float,
        lightDirY: Float,
        addR: Float,
        addG: Float,
        addB: Float
    )
}

/**
 * Shader Strategy for Reinforced Metal / Steel Plate materials.
 * Features brushed metallic specular gradients, welded seam bevels, rivet points,
 * and light-responsive directional sheen highlights.
 */
class MetalShaderStrategy : VoxelMaterialShaderStrategy {
    override fun drawMaterialBlock(
        drawScope: DrawScope,
        halfW: Float,
        halfH: Float,
        tile: VoxelTile,
        material: VoxelMaterialProperties,
        animTimeSec: Float,
        lightIntensity: Float,
        lightDirX: Float,
        lightDirY: Float,
        addR: Float,
        addG: Float,
        addB: Float
    ) {
        val blockW = halfW * 2f
        val blockH = halfH * 2f

        // Brushed metallic linear gradient brush
        val litBase = Color(
            red = (material.baseColor.red + addR * 0.5f).coerceIn(0f, 1f),
            green = (material.baseColor.green + addG * 0.5f).coerceIn(0f, 1f),
            blue = (material.baseColor.blue + addB * 0.5f).coerceIn(0f, 1f)
        )
        val litSecondary = Color(
            red = (material.secondaryColor.red + addR * 0.5f).coerceIn(0f, 1f),
            green = (material.secondaryColor.green + addG * 0.5f).coerceIn(0f, 1f),
            blue = (material.secondaryColor.blue + addB * 0.5f).coerceIn(0f, 1f)
        )

        val metalBrush = Brush.linearGradient(
            colors = listOf(
                litSecondary,
                litBase,
                material.specularColor.copy(alpha = 0.85f),
                litBase,
                litSecondary
            ),
            start = Offset(-halfW, -halfH),
            end = Offset(halfW, halfH)
        )

        // Draw Base Metallic Body
        drawScope.drawRoundRect(
            brush = metalBrush,
            topLeft = Offset(-halfW, -halfH),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(6f)
        )

        // Directional Light Specular Sheen Line
        if (lightIntensity > 0.02f) {
            val sheenColor = Color(
                red = (material.specularColor.red + addR).coerceIn(0f, 1f),
                green = (material.specularColor.green + addG).coerceIn(0f, 1f),
                blue = (material.specularColor.blue + addB).coerceIn(0f, 1f)
            ).copy(alpha = (lightIntensity * 0.85f).coerceAtMost(0.95f))

            drawScope.drawLine(
                color = sheenColor,
                start = Offset(-halfW + 4f, -halfH + 4f),
                end = Offset(-halfW + 4f + lightDirX * halfW * 1.5f, -halfH + 4f + lightDirY * halfH * 1.5f),
                strokeWidth = 3.5f
            )
        }

        // Rivets / Bolts on 4 Corners
        val rivetColor = litSecondary.copy(alpha = 0.9f)
        val rivetRadius = 2.5f
        val inset = 6f
        drawScope.drawCircle(color = rivetColor, radius = rivetRadius, center = Offset(-halfW + inset, -halfH + inset))
        drawScope.drawCircle(color = rivetColor, radius = rivetRadius, center = Offset(halfW - inset, -halfH + inset))
        drawScope.drawCircle(color = rivetColor, radius = rivetRadius, center = Offset(-halfW + inset, halfH - inset))
        drawScope.drawCircle(color = rivetColor, radius = rivetRadius, center = Offset(halfW - inset, halfH - inset))

        // Bevel Outer Border Frame
        drawScope.drawRoundRect(
            color = material.specularColor.copy(alpha = 0.6f),
            topLeft = Offset(-halfW, -halfH),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(6f),
            style = Stroke(width = 2f)
        )
    }
}

/**
 * Shader Strategy for Weathered Concrete / Stone Masonry materials.
 * Features matte stone grain texture noise, bevel edge trims, and visible internal
 * rebar steel mesh when damaged.
 */
class ConcreteShaderStrategy : VoxelMaterialShaderStrategy {
    override fun drawMaterialBlock(
        drawScope: DrawScope,
        halfW: Float,
        halfH: Float,
        tile: VoxelTile,
        material: VoxelMaterialProperties,
        animTimeSec: Float,
        lightIntensity: Float,
        lightDirX: Float,
        lightDirY: Float,
        addR: Float,
        addG: Float,
        addB: Float
    ) {
        val blockW = halfW * 2f
        val blockH = halfH * 2f

        val litBase = Color(
            red = (material.baseColor.red + addR * 0.4f).coerceIn(0f, 1f),
            green = (material.baseColor.green + addG * 0.4f).coerceIn(0f, 1f),
            blue = (material.baseColor.blue + addB * 0.4f).coerceIn(0f, 1f)
        )
        val litSecondary = Color(
            red = (material.secondaryColor.red + addR * 0.4f).coerceIn(0f, 1f),
            green = (material.secondaryColor.green + addG * 0.4f).coerceIn(0f, 1f),
            blue = (material.secondaryColor.blue + addB * 0.4f).coerceIn(0f, 1f)
        )

        val concreteBrush = Brush.verticalGradient(
            colors = listOf(
                litBase,
                litSecondary,
                litBase
            ),
            startY = -halfH,
            endY = halfH
        )

        // Draw Base Weathered Concrete Body
        drawScope.drawRoundRect(
            brush = concreteBrush,
            topLeft = Offset(-halfW, -halfH),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(4f)
        )

        // Micro-Grain Texture Dots
        val grainColor = Color.Black.copy(alpha = 0.15f)
        val grainPositions = listOf(
            Offset(-halfW + 8f, -halfH + 12f),
            Offset(halfW - 12f, -halfH + 18f),
            Offset(-halfW + 16f, halfH - 10f),
            Offset(4f, -6f),
            Offset(-10f, 10f)
        )
        for (pos in grainPositions) {
            drawScope.drawCircle(color = grainColor, radius = 1.8f, center = pos)
        }

        // Rebar Steel Mesh Grid visible when block is severely damaged
        val hpRatio = if (tile.maxHp > 0f) tile.currentHp / tile.maxHp else 1f
        if (hpRatio < 0.70f) {
            val rebarColor = Color(0xFFB45309).copy(alpha = (1f - hpRatio) * 0.9f)
            val gridStep = 10f
            var gx = -halfW + 6f
            while (gx < halfW - 6f) {
                drawScope.drawLine(
                    color = rebarColor,
                    start = Offset(gx, -halfH + 6f),
                    end = Offset(gx, halfH - 6f),
                    strokeWidth = 1.5f
                )
                gx += gridStep
            }
            var gy = -halfH + 6f
            while (gy < halfH - 6f) {
                drawScope.drawLine(
                    color = rebarColor,
                    start = Offset(-halfW + 6f, gy),
                    end = Offset(halfW - 6f, gy),
                    strokeWidth = 1.5f
                )
                gy += gridStep
            }
        }

        // Outer Bevel Outline
        drawScope.drawRoundRect(
            color = material.secondaryColor.copy(alpha = 0.7f),
            topLeft = Offset(-halfW, -halfH),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(4f),
            style = Stroke(width = 1.5f)
        )
    }
}

/**
 * Shader Strategy for Alien Biomass / Organic Bio-Chitin structures.
 * Features pulsating radial bio-cell shader, animated bio-luminescent vein tendrils,
 * creeping spores, and oozing acid fluid when breached.
 */
class AlienBiomassShaderStrategy : VoxelMaterialShaderStrategy {
    override fun drawMaterialBlock(
        drawScope: DrawScope,
        halfW: Float,
        halfH: Float,
        tile: VoxelTile,
        material: VoxelMaterialProperties,
        animTimeSec: Float,
        lightIntensity: Float,
        lightDirX: Float,
        lightDirY: Float,
        addR: Float,
        addG: Float,
        addB: Float
    ) {
        val blockW = halfW * 2f
        val blockH = halfH * 2f

        val pulse = (sin(animTimeSec * material.pulseRate.toDouble()) * 0.5 + 0.5).toFloat()

        val glowColor = Color(
            red = (material.glowColor.red + addR).coerceIn(0f, 1f),
            green = (material.glowColor.green + addG).coerceIn(0f, 1f),
            blue = (material.glowColor.blue + addB).coerceIn(0f, 1f)
        )

        val biomassBrush = Brush.radialGradient(
            colors = listOf(
                glowColor.copy(alpha = 0.6f + pulse * 0.35f),
                material.baseColor,
                material.secondaryColor
            ),
            center = Offset(0f, 0f),
            radius = halfW * 1.3f
        )

        // Draw Base Organic Cell Body
        drawScope.drawRoundRect(
            brush = biomassBrush,
            topLeft = Offset(-halfW, -halfH),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(10f)
        )

        // Animated Bio-Luminescent Vein Tendrils
        val veinColor = material.glowColor.copy(alpha = 0.7f + pulse * 0.3f)
        val veinPath = Path().apply {
            moveTo(-halfW + 4f, -halfH + 6f)
            quadraticTo(
                -2f + sin(animTimeSec * 2f) * 6f, -halfH / 2f,
                0f, 0f
            )
            quadraticTo(
                4f + cos(animTimeSec * 2f) * 6f, halfH / 2f,
                halfW - 6f, halfH - 4f
            )
        }
        drawScope.drawPath(
            path = veinPath,
            color = veinColor,
            style = Stroke(width = 2.5f)
        )

        val veinPath2 = Path().apply {
            moveTo(halfW - 6f, -halfH + 6f)
            quadraticTo(
                2f + cos(animTimeSec * 2.5f) * 5f, 0f,
                -halfW + 6f, halfH - 6f
            )
        }
        drawScope.drawPath(
            path = veinPath2,
            color = veinColor.copy(alpha = 0.5f),
            style = Stroke(width = 1.8f)
        )

        // Bio-Spores / Nodules
        val spore1Radius = 3f + pulse * 1.5f
        val spore2Radius = 2.5f + (1f - pulse) * 1.2f
        drawScope.drawCircle(color = glowColor, radius = spore1Radius, center = Offset(-halfW * 0.4f, -halfH * 0.3f))
        drawScope.drawCircle(color = glowColor, radius = spore2Radius, center = Offset(halfW * 0.5f, halfH * 0.4f))

        // Organic Chitin Border
        drawScope.drawRoundRect(
            color = material.glowColor.copy(alpha = 0.8f),
            topLeft = Offset(-halfW, -halfH),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(10f),
            style = Stroke(width = 2f)
        )
    }
}

/**
 * Shader Strategy for Energy Barriers / Forcefields.
 * Features high-frequency oscillating cyan/purple sweep gradients, energy grid lattice,
 * and forcefield ripple waves.
 */
class EnergyPlasmaShaderStrategy : VoxelMaterialShaderStrategy {
    override fun drawMaterialBlock(
        drawScope: DrawScope,
        halfW: Float,
        halfH: Float,
        tile: VoxelTile,
        material: VoxelMaterialProperties,
        animTimeSec: Float,
        lightIntensity: Float,
        lightDirX: Float,
        lightDirY: Float,
        addR: Float,
        addG: Float,
        addB: Float
    ) {
        val blockW = halfW * 2f
        val blockH = halfH * 2f

        val sweepPhase = (animTimeSec * 3f) % (2f * Math.PI.toFloat())
        val glowColor = Color(
            red = (material.glowColor.red + addR).coerceIn(0f, 1f),
            green = (material.glowColor.green + addG).coerceIn(0f, 1f),
            blue = (material.glowColor.blue + addB).coerceIn(0f, 1f)
        )

        val plasmaBrush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.9f),
                glowColor.copy(alpha = 0.7f),
                material.baseColor.copy(alpha = 0.35f)
            ),
            center = Offset(sin(sweepPhase) * halfW * 0.5f, cos(sweepPhase) * halfH * 0.5f),
            radius = halfW * 1.4f
        )

        // Draw Translucent Forcefield Base
        drawScope.drawRoundRect(
            brush = plasmaBrush,
            topLeft = Offset(-halfW, -halfH),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(6f)
        )

        // Diagonal Energy Lattice Lines
        val lineStep = 10f
        val gridColor = glowColor.copy(alpha = 0.45f)
        var offset = -halfW - halfH
        while (offset < halfW + halfH) {
            drawScope.drawLine(
                color = gridColor,
                start = Offset(offset, -halfH),
                end = Offset(offset + blockH, halfH),
                strokeWidth = 1f
            )
            offset += lineStep
        }

        // Oscillating Outer Frame Glow
        drawScope.drawRoundRect(
            color = glowColor,
            topLeft = Offset(-halfW, -halfH),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(6f),
            style = Stroke(width = 2.5f)
        )
    }
}

/**
 * Shader Strategy for Volatile Explosive Barrels.
 * Features high-contrast diagonal hazard caution stripes and an internal thermal heat core
 * that brightens and pulses dramatically as HP drops!
 */
class VolatileHazardShaderStrategy : VoxelMaterialShaderStrategy {
    override fun drawMaterialBlock(
        drawScope: DrawScope,
        halfW: Float,
        halfH: Float,
        tile: VoxelTile,
        material: VoxelMaterialProperties,
        animTimeSec: Float,
        lightIntensity: Float,
        lightDirX: Float,
        lightDirY: Float,
        addR: Float,
        addG: Float,
        addB: Float
    ) {
        val blockW = halfW * 2f
        val blockH = halfH * 2f

        // Base Hazard Barrel Container
        drawScope.drawRoundRect(
            color = material.baseColor,
            topLeft = Offset(-halfW, -halfH),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(8f)
        )

        // Diagonal Yellow/Black Caution Stripes
        val stripeWidth = 8f
        val stripeColor = Color(0xFFF59E0B) // Hazard Yellow
        var stripeX = -halfW * 1.5f
        while (stripeX < halfW * 1.5f) {
            val stripePath = Path().apply {
                moveTo(stripeX, -halfH)
                lineTo(stripeX + stripeWidth, -halfH)
                lineTo(stripeX + stripeWidth - blockH * 0.6f, halfH)
                lineTo(stripeX - blockH * 0.6f, halfH)
                close()
            }
            drawScope.drawPath(path = stripePath, color = stripeColor.copy(alpha = 0.85f))
            stripeX += stripeWidth * 2.2f
        }

        // Internal Thermal Heat Core Bloom (Accelerates as HP depletes!)
        val hpRatio = if (tile.maxHp > 0f) (tile.currentHp / tile.maxHp).coerceIn(0f, 1f) else 1f
        val heatIntensity = 1.0f - hpRatio
        if (heatIntensity > 0.05f) {
            val pulseFreq = 4f + heatIntensity * 16f
            val thermalPulse = (sin(animTimeSec * pulseFreq.toDouble()) * 0.5 + 0.5).toFloat()

            val coreColor = Color(0xFFFF3D00).copy(alpha = (heatIntensity * 0.8f + thermalPulse * 0.2f).coerceAtMost(0.95f))
            drawScope.drawCircle(
                color = coreColor,
                radius = halfW * (0.3f + heatIntensity * 0.4f),
                center = Offset(0f, 0f)
            )
            drawScope.drawCircle(
                color = Color.White.copy(alpha = heatIntensity * 0.9f),
                radius = halfW * (0.15f + heatIntensity * 0.2f),
                center = Offset(0f, 0f)
            )
        }

        // Bevel Frame
        drawScope.drawRoundRect(
            color = Color(0xFFF59E0B),
            topLeft = Offset(-halfW, -halfH),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(8f),
            style = Stroke(width = 2f)
        )
    }
}

/**
 * Shader Strategy for Stone Masonry / Plaza ground paving.
 */
class DefaultStoneShaderStrategy : VoxelMaterialShaderStrategy {
    override fun drawMaterialBlock(
        drawScope: DrawScope,
        halfW: Float,
        halfH: Float,
        tile: VoxelTile,
        material: VoxelMaterialProperties,
        animTimeSec: Float,
        lightIntensity: Float,
        lightDirX: Float,
        lightDirY: Float,
        addR: Float,
        addG: Float,
        addB: Float
    ) {
        val blockW = halfW * 2f
        val blockH = halfH * 2f

        val litBase = Color(
            red = (material.baseColor.red + addR * 0.3f).coerceIn(0f, 1f),
            green = (material.baseColor.green + addG * 0.3f).coerceIn(0f, 1f),
            blue = (material.baseColor.blue + addB * 0.3f).coerceIn(0f, 1f)
        )

        drawScope.drawRoundRect(
            color = litBase,
            topLeft = Offset(-halfW, -halfH),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(4f)
        )

        drawScope.drawRoundRect(
            color = material.secondaryColor.copy(alpha = 0.5f),
            topLeft = Offset(-halfW, -halfH),
            size = Size(blockW, blockH),
            cornerRadius = CornerRadius(4f),
            style = Stroke(width = 1.5f)
        )
    }
}

/**
 * Central VoxelMaterialShader Registry & Dispatcher engine for VoxelWorldManager.
 */
object VoxelMaterialShader {
    private val metalStrategy = MetalShaderStrategy()
    private val concreteStrategy = ConcreteShaderStrategy()
    private val alienBiomassStrategy = AlienBiomassShaderStrategy()
    private val energyPlasmaStrategy = EnergyPlasmaShaderStrategy()
    private val volatileHazardStrategy = VolatileHazardShaderStrategy()
    private val defaultStoneStrategy = DefaultStoneShaderStrategy()

    /**
     * Map of VoxelType to its corresponding VoxelMaterialProperties.
     */
    fun getMaterialProperties(voxelType: VoxelType): VoxelMaterialProperties {
        return when (voxelType) {
            VoxelType.REINFORCED_METAL, VoxelType.LOW_COVER_CRATE -> VoxelMaterialProperties(
                type = VoxelMaterialType.REINFORCED_METAL,
                name = "Titanium-Steel Plate",
                baseColor = Color(0xFF334155),
                secondaryColor = Color(0xFF1E293B),
                specularColor = Color(0xFF38BDF8),
                glowColor = Color(0xFF0284C7),
                roughness = 0.2f,
                metallicSheen = 0.95f,
                fractureResilience = 1.8f
            )

            VoxelType.CONCRETE_WALL, VoxelType.HIGH_COVER_WALL, VoxelType.DESTRUCTIBLE_PILLAR -> VoxelMaterialProperties(
                type = VoxelMaterialType.CONCRETE,
                name = "Reinforced Concrete",
                baseColor = Color(0xFF475569),
                secondaryColor = Color(0xFF0F172A),
                specularColor = Color(0xFF94A3B8),
                glowColor = Color(0xFF64748B),
                roughness = 0.85f,
                metallicSheen = 0.1f,
                fractureResilience = 1.2f
            )

            VoxelType.ALIEN_BIOMASS, VoxelType.ACID_POOL -> VoxelMaterialProperties(
                type = VoxelMaterialType.ALIEN_BIOMASS,
                name = "Xeno Bio-Chitin",
                baseColor = Color(0xFF4C1D95),
                secondaryColor = Color(0xFF1E1B4B),
                specularColor = Color(0xFFA855F7),
                glowColor = Color(0xFF22C55E),
                roughness = 0.4f,
                pulseRate = 2.5f,
                isOrganic = true,
                isGlowing = true,
                fractureResilience = 0.9f
            )

            VoxelType.ENERGY_BARRIER, VoxelType.OBJECTIVE_NODE -> VoxelMaterialProperties(
                type = VoxelMaterialType.ENERGY_PLASMA,
                name = "Plasma Forcefield",
                baseColor = Color(0xFF0369A1),
                secondaryColor = Color(0xFF0284C7),
                specularColor = Color(0xFF00E5FF),
                glowColor = Color(0xFF38BDF8),
                roughness = 0.1f,
                pulseRate = 4.0f,
                isGlowing = true,
                fractureResilience = 0.6f
            )

            VoxelType.EXPLOSIVE_BARREL -> VoxelMaterialProperties(
                type = VoxelMaterialType.VOLATILE_HAZARD,
                name = "Promethium Volatile Crate",
                baseColor = Color(0xFF7F1D1D),
                secondaryColor = Color(0xFF450A0A),
                specularColor = Color(0xFFF59E0B),
                glowColor = Color(0xFFEF4444),
                roughness = 0.3f,
                pulseRate = 1.5f,
                isGlowing = true,
                fractureResilience = 0.4f
            )

            else -> VoxelMaterialProperties(
                type = VoxelMaterialType.PLAZA_STONE,
                name = "Plaza Masonry",
                baseColor = Color(0xFF1E293B),
                secondaryColor = Color(0xFF0F172A),
                specularColor = Color(0xFF64748B),
                glowColor = Color(0xFF334155)
            )
        }
    }

    /**
     * Get the shader strategy for a specific VoxelType.
     */
    fun getStrategy(voxelType: VoxelType): VoxelMaterialShaderStrategy {
        return when (voxelType) {
            VoxelType.REINFORCED_METAL, VoxelType.LOW_COVER_CRATE -> metalStrategy
            VoxelType.CONCRETE_WALL, VoxelType.HIGH_COVER_WALL, VoxelType.DESTRUCTIBLE_PILLAR -> concreteStrategy
            VoxelType.ALIEN_BIOMASS, VoxelType.ACID_POOL -> alienBiomassStrategy
            VoxelType.ENERGY_BARRIER, VoxelType.OBJECTIVE_NODE -> energyPlasmaStrategy
            VoxelType.EXPLOSIVE_BARREL -> volatileHazardStrategy
            else -> defaultStoneStrategy
        }
    }

    /**
     * Draw a destructible voxel block using its material shader strategy.
     */
    fun drawBlockShader(
        drawScope: DrawScope,
        halfW: Float,
        halfH: Float,
        tile: VoxelTile,
        animTimeSec: Float = (System.currentTimeMillis() % 1000000L) / 1000f,
        lightIntensity: Float = 0f,
        lightDirX: Float = 0f,
        lightDirY: Float = 0f,
        addR: Float = 0f,
        addG: Float = 0f,
        addB: Float = 0f
    ) {
        val matProps = getMaterialProperties(tile.type)
        val strategy = getStrategy(tile.type)

        strategy.drawMaterialBlock(
            drawScope = drawScope,
            halfW = halfW,
            halfH = halfH,
            tile = tile,
            material = matProps,
            animTimeSec = animTimeSec,
            lightIntensity = lightIntensity,
            lightDirX = lightDirX,
            lightDirY = lightDirY,
            addR = addR,
            addG = addG,
            addB = addB
        )
    }
}
