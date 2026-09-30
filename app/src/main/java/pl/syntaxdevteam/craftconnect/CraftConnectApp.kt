package pl.syntaxdevteam.craftconnect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import pl.syntaxdevteam.craftconnect.data.DemoRepository
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.ui.components.CraftBackground
import pl.syntaxdevteam.craftconnect.ui.components.CraftBottomBar
import pl.syntaxdevteam.craftconnect.ui.screens.ChatScreen
import pl.syntaxdevteam.craftconnect.ui.screens.PlayersScreen
import pl.syntaxdevteam.craftconnect.ui.screens.ServersScreen
import pl.syntaxdevteam.craftconnect.ui.screens.SettingsScreen

enum class AppDestination {
    Servers,
    Chat,
    Players,
    Settings,
}

@Composable
fun CraftConnectApp() {
    var destination by remember { mutableStateOf(AppDestination.Servers) }
    var activeServer by remember { mutableStateOf<ServerProfile?>(null) }

    CraftBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                CraftBottomBar(
                    destination = destination,
                    onDestination = { destination = it },
                )
            },
        ) { contentPadding ->
            val currentServer = activeServer ?: DemoRepository.servers.first()

            Box(Modifier.padding(contentPadding)) {
                when (destination) {
                    AppDestination.Servers -> ServersScreen(
                        onConnect = { server ->
                            activeServer = server
                            destination = AppDestination.Chat
                        },
                    )
                    AppDestination.Chat -> ChatScreen(currentServer)
                    AppDestination.Players -> PlayersScreen(currentServer)
                    AppDestination.Settings -> SettingsScreen()
                }
            }
        }
    }
}
