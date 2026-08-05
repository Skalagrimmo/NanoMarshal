package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.data.model.DefaultMissions
import com.example.data.model.Mission
import com.example.data.repository.GameRepository
import com.example.ui.screens.*
import com.example.ui.theme.NanoMarshalTheme
import com.example.ui.theme.VoidDark

enum class Screen {
    MAIN_MENU, MISSION_SELECT, WORKBENCH, LORE_GUIDE, PLAY_GAME
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = GameRepository(this)

        setContent {
            NanoMarshalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = VoidDark
                ) {
                    var currentScreen by remember { mutableStateOf(Screen.MAIN_MENU) }
                    var activeMission by remember { mutableStateOf(DefaultMissions.MISSION_1) }

                    val profile by repository.profile.collectAsState()

                    when (currentScreen) {
                        Screen.MAIN_MENU -> {
                            MainMenuScreen(
                                profile = profile,
                                onNavigateToMissions = { currentScreen = Screen.MISSION_SELECT },
                                onNavigateToWorkbench = { currentScreen = Screen.WORKBENCH },
                                onNavigateToLore = { currentScreen = Screen.LORE_GUIDE }
                            )
                        }

                        Screen.MISSION_SELECT -> {
                            MissionSelectScreen(
                                profile = profile,
                                onSelectMission = { mission ->
                                    activeMission = mission
                                    currentScreen = Screen.PLAY_GAME
                                },
                                onBack = { currentScreen = Screen.MAIN_MENU }
                            )
                        }

                        Screen.WORKBENCH -> {
                            WorkbenchScreen(
                                profile = profile,
                                repository = repository,
                                onBack = { currentScreen = Screen.MAIN_MENU }
                            )
                        }

                        Screen.LORE_GUIDE -> {
                            LoreGuideScreen(
                                onBack = { currentScreen = Screen.MAIN_MENU }
                            )
                        }

                        Screen.PLAY_GAME -> {
                            GameScreen(
                                mission = activeMission,
                                profile = profile,
                                repository = repository,
                                onExitGame = { currentScreen = Screen.MAIN_MENU }
                            )
                        }
                    }
                }
            }
        }
    }
}
