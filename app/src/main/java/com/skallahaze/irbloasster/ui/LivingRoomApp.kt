package com.skallahaze.irbloasster.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.skallahaze.irbloasster.model.MainSection
import com.skallahaze.irbloasster.ui.theme.LivingRoomTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivingRoomApp(viewModel: LivingRoomViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var settingsOpen by remember { mutableStateOf(false) }

    LivingRoomTheme(darkTheme = state.darkTheme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            when (state.section) {
                                MainSection.HOME -> "Living Room"
                                else -> state.section.label
                            }
                        )
                    },
                    actions = {
                        IconButton(onClick = { settingsOpen = true }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Einstellungen")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    MainSection.entries.forEach { section ->
                        NavigationBarItem(
                            selected = state.section == section,
                            onClick = { viewModel.selectSection(section) },
                            icon = {
                                Icon(
                                    imageVector = section.icon(),
                                    contentDescription = section.label
                                )
                            },
                            label = { Text(section.shortLabel()) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                when (state.section) {
                    MainSection.HOME -> OverviewScreen(state, viewModel)
                    MainSection.TV -> TvRemoteScreen(state, viewModel)
                    MainSection.SONY -> SonyRemoteScreen(state, viewModel)
                    MainSection.SCENES -> ScenesScreen(state, viewModel)
                    MainSection.ANALYSIS -> AnalysisScreen(state, viewModel)
                }
            }
        }

        if (settingsOpen) {
            SettingsDialog(
                state = state,
                viewModel = viewModel,
                onDismiss = { settingsOpen = false }
            )
        }

        state.lastError?.let { error ->
            AlertDialog(
                onDismissRequest = viewModel::dismissError,
                title = { Text("Hinweis") },
                text = { Text(error) },
                confirmButton = {
                    TextButton(onClick = viewModel::dismissError) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

private fun MainSection.icon(): ImageVector = when (this) {
    MainSection.HOME -> Icons.Rounded.Home
    MainSection.TV -> Icons.Rounded.Tv
    MainSection.SONY -> Icons.Rounded.MusicNote
    MainSection.SCENES -> Icons.Rounded.AutoAwesome
    MainSection.ANALYSIS -> Icons.Rounded.BugReport
}

private fun MainSection.shortLabel(): String = when (this) {
    MainSection.HOME -> "Home"
    MainSection.TV -> "TV"
    MainSection.SONY -> "Sony"
    MainSection.SCENES -> "Szenen"
    MainSection.ANALYSIS -> "Analyse"
}
