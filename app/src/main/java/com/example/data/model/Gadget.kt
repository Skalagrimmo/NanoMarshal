package com.example.data.model

enum class GadgetType {
    GRENADE, EMP_MINE, DECOY, STIM_PACK
}

data class Gadget(
    val id: String,
    val name: String,
    val type: GadgetType,
    val description: String,
    val maxCount: Int,
    val cooldownMs: Long,
    val iconName: String,
    val cost: Int = 0,
    var isUnlocked: Boolean = false
)

object DefaultGadgets {
    val NANO_GRENADE = Gadget(
        id = "g_grenade",
        name = "Nano-Plasma Grenade",
        type = GadgetType.GRENADE,
        description = "High-explosive charge that disintegrates voxel cover and damages clustered foes.",
        maxCount = 3,
        cooldownMs = 4000,
        iconName = "ic_grenade",
        cost = 0,
        isUnlocked = true
    )

    val EMP_MINE = Gadget(
        id = "g_emp",
        name = "EMP Shield Disruptor",
        type = GadgetType.EMP_MINE,
        description = "Deploys a proximity mine that disables cybernetic shields and stuns mechanical units.",
        maxCount = 2,
        cooldownMs = 6000,
        iconName = "ic_emp",
        cost = 1000,
        isUnlocked = false
    )

    val HOLO_DECOY = Gadget(
        id = "g_decoy",
        name = "Holographic Marshal Decoy",
        type = GadgetType.DECOY,
        description = "Projects an active hologram that attracts enemy vision cones away from your cover.",
        maxCount = 2,
        cooldownMs = 8000,
        iconName = "ic_decoy",
        cost = 1800,
        isUnlocked = false
    )

    val NANITE_STIM = Gadget(
        id = "g_stim",
        name = "Nanite Repair Stim",
        type = GadgetType.STIM_PACK,
        description = "Instantly recharges 50 HP + 50 Nano Shield and grants temporary movement boost.",
        maxCount = 3,
        cooldownMs = 10000,
        iconName = "ic_stim",
        cost = 1200,
        isUnlocked = false
    )

    fun getAll() = listOf(NANO_GRENADE, EMP_MINE, HOLO_DECOY, NANITE_STIM)
}
