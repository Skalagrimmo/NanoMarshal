package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class PlayerProfile(
    val credits: Int = 2000,
    val naniteCores: Int = 8,
    val unlockedWeaponIds: Set<String> = setOf("w_needle", "w_plasma"),
    val unlockedGadgetIds: Set<String> = setOf("g_grenade"),
    val primaryWeaponId: String = "w_plasma",
    val secondaryWeaponId: String = "w_needle",
    val activeGadgetId: String = "g_grenade",
    val completedMissionIds: Set<String> = emptySet(),
    val missionStars: Map<String, Int> = emptyMap()
)

class GameRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("nanomarshal_prefs", Context.MODE_PRIVATE)

    private val _profile = MutableStateFlow(loadProfile())
    val profile: StateFlow<PlayerProfile> = _profile

    private fun loadProfile(): PlayerProfile {
        val credits = prefs.getInt("credits", 2000)
        val cores = prefs.getInt("cores", 8)
        val weapons = prefs.getStringSet("weapons", setOf("w_needle", "w_plasma")) ?: setOf("w_needle", "w_plasma")
        val gadgets = prefs.getStringSet("gadgets", setOf("g_grenade")) ?: setOf("g_grenade")
        val primary = prefs.getString("primary", "w_plasma") ?: "w_plasma"
        val secondary = prefs.getString("secondary", "w_needle") ?: "w_needle"
        val activeGadget = prefs.getString("gadget", "g_grenade") ?: "g_grenade"
        val completedMissions = prefs.getStringSet("completed_missions", emptySet()) ?: emptySet()

        return PlayerProfile(
            credits = credits,
            naniteCores = cores,
            unlockedWeaponIds = weapons,
            unlockedGadgetIds = gadgets,
            primaryWeaponId = primary,
            secondaryWeaponId = secondary,
            activeGadgetId = activeGadget,
            completedMissionIds = completedMissions
        )
    }

    fun saveProfile(profile: PlayerProfile) {
        _profile.value = profile
        prefs.edit()
            .putInt("credits", profile.credits)
            .putInt("cores", profile.naniteCores)
            .putStringSet("weapons", profile.unlockedWeaponIds)
            .putStringSet("gadgets", profile.unlockedGadgetIds)
            .putString("primary", profile.primaryWeaponId)
            .putString("secondary", profile.secondaryWeaponId)
            .putString("gadget", profile.activeGadgetId)
            .putStringSet("completed_missions", profile.completedMissionIds)
            .apply()
    }

    fun buyWeapon(weapon: Weapon): Boolean {
        val curr = _profile.value
        if (curr.credits >= weapon.cost && !curr.unlockedWeaponIds.contains(weapon.id)) {
            val newWeapons = curr.unlockedWeaponIds + weapon.id
            saveProfile(curr.copy(credits = curr.credits - weapon.cost, unlockedWeaponIds = newWeapons))
            return true
        }
        return false
    }

    fun buyGadget(gadget: Gadget): Boolean {
        val curr = _profile.value
        if (curr.credits >= gadget.cost && !curr.unlockedGadgetIds.contains(gadget.id)) {
            val newGadgets = curr.unlockedGadgetIds + gadget.id
            saveProfile(curr.copy(credits = curr.credits - gadget.cost, unlockedGadgetIds = newGadgets))
            return true
        }
        return false
    }

    fun recordMissionVictory(missionId: String, stars: Int, creditsEarned: Int, coresEarned: Int) {
        val curr = _profile.value
        val newCompleted = curr.completedMissionIds + missionId
        val newStars = curr.missionStars.toMutableMap().apply { put(missionId, maxOf(this[missionId] ?: 0, stars)) }
        saveProfile(
            curr.copy(
                credits = curr.credits + creditsEarned,
                naniteCores = curr.naniteCores + coresEarned,
                completedMissionIds = newCompleted,
                missionStars = newStars
            )
        )
    }
}
