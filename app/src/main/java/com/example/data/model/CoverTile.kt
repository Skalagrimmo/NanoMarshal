package com.example.data.model

enum class VoxelType {
    FLOOR_DIRT,
    FLOOR_PLAZA,
    LOW_COVER_CRATE,
    HIGH_COVER_WALL,
    EXPLOSIVE_BARREL,
    ENERGY_BARRIER,
    ACID_POOL,
    OBJECTIVE_NODE,
    DESTRUCTIBLE_PILLAR,
    REINFORCED_METAL,
    CONCRETE_WALL,
    ALIEN_BIOMASS
}

enum class CoverHeight {
    NONE,       // 0% cover
    LOW,        // 50% damage reduction when crouching/behind
    HIGH        // 90% damage reduction from front angle
}

data class VoxelTile(
    val gridX: Int,
    val gridY: Int,
    var elevationZ: Int = 0, // 0 = ground, 1 = low cover, 2 = wall/pillar, 3 = high structure
    var type: VoxelType,
    var currentHp: Float = 100f,
    var maxHp: Float = 100f,
    var isDestructible: Boolean = true,
    var coverHeight: CoverHeight = CoverHeight.NONE,
    var lodLevel: Int = 0,
    var isDisintegrated: Boolean = false,
    var deformationX: Float = 0f,
    var deformationY: Float = 0f,
    var meshScaleX: Float = 1.0f,
    var meshScaleY: Float = 1.0f,
    var rotationAngle: Float = 0f,
    var damageCracksCount: Int = 0,
    var hitFlashTimer: Float = 0f
) {
    val isWalkable: Boolean get() = (coverHeight == CoverHeight.NONE && type != VoxelType.ACID_POOL) || isDisintegrated
    val isHazard: Boolean get() = type == VoxelType.ACID_POOL && !isDisintegrated
}
