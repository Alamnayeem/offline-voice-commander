package com.offlinevoicecommander.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.offlinevoicecommander.app.viewmodel.MainViewModel

private data class TabItem(val label: String, val icon: ImageVector)

private val tabs = listOf(
    TabItem("Home", Icons.Default.Home),
    TabItem("Macros", Icons.Default.Build),
    TabItem("Commands", Icons.Default.Info),
    TabItem("Privacy", Icons.Default.Lock)
)

@Composable
fun MainScreen(viewModel: MainViewModel) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (selected) {
                0 -> HomeScreen(viewModel)
                1 -> MacrosScreen(viewModel)
                2 -> CommandsScreen()
                else -> PrivacyScreen()
            }
        }
    }
}
