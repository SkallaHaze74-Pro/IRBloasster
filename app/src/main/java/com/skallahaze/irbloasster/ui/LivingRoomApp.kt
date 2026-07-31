package com.skallahaze.irbloasster.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.skallahaze.irbloasster.LivingRoomViewModel

private data class AppTab(
    val label: String,
    val icon: ImageVector
)

private val tabs = listOf(
    AppTab("Home", Icons.Rounded.Home),
    AppTab("TV", Icons.Rounded.Tv),
    AppTab("Touch", Icons.Rounded.TouchApp),
    AppTab("Sony", Icons.Rounded.Speaker),
    AppTab("Diagnose", Icons.Rounded.BugReport)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivingRoomApp(viewModel: LivingRoomViewModel) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Living Room Controller") }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> HomeTab(viewModel, Modifier.padding(paddingValues))
            1 -> TvRemoteTab(viewModel, Modifier.padding(paddingValues))
            2 -> TouchpadTab(viewModel, Modifier.padding(paddingValues))
            3 -> SonyRemoteTab(viewModel, Modifier.padding(paddingValues))
            else -> DiagnosticsTab(viewModel, Modifier.padding(paddingValues))
        }
    }
}
