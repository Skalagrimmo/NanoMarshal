package com.example.data.model

import androidx.compose.ui.graphics.Color
import kotlin.random.Random

enum class DynamicLightType {
    PROJECTILE_BULLET,
    IMPACT_FLASH,
    EXPLOSION_BURST,
    MUZZLE_FLASH,
    ENVIRONMENTAL_EMITTER
}

data class DynamicLight(
    val id: String = Random.nextLong().toString(),
    var x: Float,
    var y: Float,
    var color: Color,
    var radius: Float,
    var maxRadius: Float = radius,
    var intensity: Float = 1.0f,
    var maxIntensity: Float = intensity,
    var life: Float = 1.0f, // 1.0 down to 0.0
    var decayRate: Float = 1.0f, // per sec
    var expansionRate: Float = 0f, // pixels per sec
    var type: DynamicLightType = DynamicLightType.IMPACT_FLASH,
    var flickerPhase: Float = Random.nextFloat() * 6.28f
)
