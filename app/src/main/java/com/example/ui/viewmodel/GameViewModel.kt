package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.GadgetInventoryEntity
import com.example.data.database.LevelProgressEntity
import com.example.data.database.PlayerStatsEntity
import com.example.data.database.WeaponInventoryEntity
import com.example.data.model.Gadget
import com.example.data.model.Mission
import com.example.data.model.Weapon
import com.example.data.repository.GameRepository
import com.example.data.repository.PlayerProfile
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GameUiState(
    val profile: PlayerProfile = PlayerProfile(),
    val playerStats: PlayerStatsEntity? = null,
    val levelProgressList: List<LevelProgressEntity> = emptyList(),
    val weaponInventory: List<WeaponInventoryEntity> = emptyList(),
    val gadgetInventory: List<GadgetInventoryEntity> = emptyList(),
    val activeMission: Mission? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class GameViewModel(
    private val repository: GameRepository
) : ViewModel() {

    private val _activeMission = MutableStateFlow<Mission?>(null)
    val activeMission: StateFlow<Mission?> = _activeMission.asStateFlow()

    // Combined UI state observing repository flows for complete state sync
    val uiState: StateFlow<GameUiState> = combine(
        combine(
            repository.profile,
            repository.playerStatsFlow,
            repository.levelProgressFlow
        ) { profile, stats, levels -> Triple(profile, stats, levels) },
        combine(
            repository.weaponInventoryFlow,
            repository.gadgetInventoryFlow,
            _activeMission
        ) { weapons, gadgets, activeMission -> Triple(weapons, gadgets, activeMission) }
    ) { (profile, stats, levels), (weapons, gadgets, activeMission) ->
        GameUiState(
            profile = profile,
            playerStats = stats,
            levelProgressList = levels,
            weaponInventory = weapons,
            gadgetInventory = gadgets,
            activeMission = activeMission,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GameUiState(isLoading = true)
    )

    fun setActiveMission(mission: Mission) {
        _activeMission.value = mission
    }

    fun recordMissionVictory(
        missionId: String,
        stars: Int,
        creditsEarned: Int,
        coresEarned: Int
    ) {
        viewModelScope.launch {
            repository.recordMissionVictory(
                missionId = missionId,
                stars = stars,
                creditsEarned = creditsEarned,
                coresEarned = coresEarned
            )
        }
    }

    fun updateWeaponAmmoState(weaponId: String, currentMag: Int, reserveAmmo: Int) {
        viewModelScope.launch {
            repository.updateWeaponAmmoState(weaponId, currentMag, reserveAmmo)
        }
    }

    fun buyWeapon(weapon: Weapon): Boolean {
        return repository.buyWeapon(weapon)
    }

    fun buyGadget(gadget: Gadget): Boolean {
        return repository.buyGadget(gadget)
    }

    fun upgradeWeaponLevel(weaponId: String, costCores: Int): Boolean {
        return repository.upgradeWeaponLevel(weaponId, costCores)
    }

    fun buyAmmoRefill(weaponId: String, costCredits: Int): Boolean {
        return repository.buyAmmoRefill(weaponId, costCredits)
    }

    fun saveProfile(profile: PlayerProfile) {
        viewModelScope.launch {
            repository.saveProfile(profile)
        }
    }

    class Factory(private val repository: GameRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(GameViewModel::class.java)) {
                return GameViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}
