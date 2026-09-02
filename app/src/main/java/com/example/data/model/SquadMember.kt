package com.example.data.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.NaniteGreen
import com.example.ui.theme.NanoCyan
import com.example.ui.theme.ShieldBlue

enum class SquadRole {
    RECON_DRONE,
    POINT_MAN,
    TACTICAL_SCOUT,
    COMBAT_MEDIC
}

/**
 * Squad companion unit providing shared tactical line-of-sight, recon sensor coverage,
 * and combat support within the alien combat zone.
 */
data class SquadMember(
    val id: String,
    val name: String,
    val role: SquadRole,
    var x: Float,
    var y: Float,
    var facingAngle: Float = 0f,
    var health: Float = 100f,
    var maxHealth: Float = 100f,
    var shieldHp: Float = 50f,
    var maxShieldHp: Float = 50f,
    val visionRange: Float = 480f,
    val fovAngleRad: Float = Math.toRadians(360.0).toFloat(), // 360 deg for omni drones, directional for ground units
    val isOmnidirectionalVision: Boolean = true,
    var isAlive: Boolean = true,
    var isActive: Boolean = true,
    val followDistance: Float = 95f,
    val followAngleOffset: Float = 0f,
    val callsign: String = if (role == SquadRole.RECON_DRONE) "AEGIS-1" else "ECHO-2",
    val accentColor: Color = NanoCyan,
    val iconSymbol: String = "DRONE",
    var statusText: String = "ONLINE"
)

object DefaultSquad {
    fun createDefaultSquad(spawnX: Float, spawnY: Float): List<SquadMember> {
        return listOf(
            SquadMember(
                id = "squad_drone_1",
                name = "AEGIS-1 Recon Drone",
                role = SquadRole.RECON_DRONE,
                x = spawnX - 70f,
                y = spawnY - 60f,
                facingAngle = 0f,
                health = 80f,
                maxHealth = 80f,
                shieldHp = 60f,
                maxShieldHp = 60f,
                visionRange = 520f,
                fovAngleRad = Math.toRadians(360.0).toFloat(),
                isOmnidirectionalVision = true,
                followDistance = 110f,
                followAngleOffset = (Math.PI * 0.75).toFloat(),
                accentColor = NanoCyan,
                iconSymbol = "AEGIS-1",
                statusText = "SENSORS LINKED"
            ),
            SquadMember(
                id = "squad_vanguard_2",
                name = "Vanguard Scout Echo",
                role = SquadRole.TACTICAL_SCOUT,
                x = spawnX + 60f,
                y = spawnY + 70f,
                facingAngle = 0f,
                health = 120f,
                maxHealth = 120f,
                shieldHp = 40f,
                maxShieldHp = 40f,
                visionRange = 460f,
                fovAngleRad = Math.toRadians(140.0).toFloat(),
                isOmnidirectionalVision = false,
                followDistance = 120f,
                followAngleOffset = (-Math.PI * 0.75).toFloat(),
                accentColor = NaniteGreen,
                iconSymbol = "VANGUARD",
                statusText = "RECON ACTIVE"
            )
        )
    }
}
