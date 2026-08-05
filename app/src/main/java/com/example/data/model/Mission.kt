package com.example.data.model

enum class ObjectiveType {
    ELIMINATE_BOUNTY,
    SABOTAGE_POWER_CORE,
    SURVIVE_AMBUSH,
    STEALTH_INFILTRATION
}

data class Mission(
    val id: String,
    val title: String,
    val sectorName: String,
    val description: String,
    val difficulty: Int, // 1 to 5 stars
    val bountyTargetName: String,
    val bountyTargetRole: String,
    val bountyImageName: String,
    val rewardCredits: Int,
    val rewardCores: Int,
    val objectiveType: ObjectiveType,
    val gridWidth: Int = 24,
    val gridHeight: Int = 24,
    var isUnlocked: Boolean = false,
    var isCompleted: Boolean = false,
    var starRating: Int = 0 // 0 to 3 stars
)

object DefaultMissions {
    val MISSION_1 = Mission(
        id = "m_outpost9",
        title = "Operation: Neon Outpost",
        sectorName = "Sector 09 - Frontier Rim",
        description = "Rogue Syndicate Warlord 'Jax Neon' has captured an abandoned nanopunk mining station. Infiltrate the voxel canyon, neutralize guards using cover tactics, and eliminate Jax.",
        difficulty = 1,
        bountyTargetName = "Jax 'The Neon' Vex",
        bountyTargetRole = "Syndicate Outpost Leader",
        bountyImageName = "bounty_jax",
        rewardCredits = 800,
        rewardCores = 3,
        objectiveType = ObjectiveType.ELIMINATE_BOUNTY,
        gridWidth = 24,
        gridHeight = 24,
        isUnlocked = true
    )

    val MISSION_2 = Mission(
        id = "m_canyon_ruins",
        title = "Sabotage: Core Breach",
        sectorName = "Sector 14 - Crystal Canyons",
        description = "Rogue nanite refinery creating destabilized plasma explosives. Stealth behind destructible cover barriers, overload the central power core, and extract cleanly.",
        difficulty = 2,
        bountyTargetName = "Overseer Karr",
        bountyTargetRole = "Nanite Core Master",
        bountyImageName = "bounty_karr",
        rewardCredits = 1500,
        rewardCores = 5,
        objectiveType = ObjectiveType.SABOTAGE_POWER_CORE,
        gridWidth = 28,
        gridHeight = 28,
        isUnlocked = false
    )

    val MISSION_3 = Mission(
        id = "m_stealth_hive",
        title = "Ghost Infiltration",
        sectorName = "Sector 21 - Shadow Fortress",
        description = "Heavy sniper nests and armed shield enforcers guard the cyber prison. Use silent weapons, decoy gadgets, and crouched stealth to eliminate targets without raising alarm.",
        difficulty = 3,
        bountyTargetName = "Phantom Vex",
        bountyTargetRole = "Shadow Sniper Assassin",
        bountyImageName = "bounty_phantom",
        rewardCredits = 2500,
        rewardCores = 8,
        objectiveType = ObjectiveType.STEALTH_INFILTRATION,
        gridWidth = 30,
        gridHeight = 30,
        isUnlocked = false
    )

    val MISSION_4 = Mission(
        id = "m_aethel_behemoth",
        title = "Apex Bounty: The Behemoth",
        sectorName = "Sector 99 - Void Core",
        description = "The ultimate nanopunk warlord commands a massive nanite mech armor capable of smashing voxel walls. Use heavy railguns, explosives, and flank cover maneuver to bring it down!",
        difficulty = 5,
        bountyTargetName = "Goliath Prime",
        bountyTargetRole = "Nanite Mech Warlord",
        bountyImageName = "bounty_goliath",
        rewardCredits = 5000,
        rewardCores = 15,
        objectiveType = ObjectiveType.ELIMINATE_BOUNTY,
        gridWidth = 32,
        gridHeight = 32,
        isUnlocked = false
    )

    fun getAll() = listOf(MISSION_1, MISSION_2, MISSION_3, MISSION_4)
}
