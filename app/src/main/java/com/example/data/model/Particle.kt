package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class ParticleType {
    PLASMA_SPARK,
    DEBRIS_VOXEL,
    EXPLOSION_FLAME,
    LASER_TRAIL,
    STEALTH_PULSE,
    SMOKE_NANO,
    ACID_SPLASH,
    HIT_NUMBER,
    ELECTRIC_BOLT,
    NANITE_SPORE,
    CRYO_CRYSTAL,
    PLASMA_WAVE
}

data class Particle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var color: Color,
    var size: Float,
    var life: Float = 1.0f, // 1.0 down to 0.0
    var maxLife: Float = 1.0f,
    var type: ParticleType = ParticleType.PLASMA_SPARK,
    var text: String = "", // For damage floating numbers
    var rotation: Float = 0f,
    var vRot: Float = 0f,
    var aspectRatio: Float = 1.0f
)
