package pl.syntaxdevteam.craftconnect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import pl.syntaxdevteam.craftconnect.data.DemoRepository
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.domain.session.ConnectionState
import pl.syntaxdevteam.craftconnect.domain.session.SessionManager
import pl.syntaxdevteam.craftconnect.domain.session.SessionManagerFactory
import pl.syntaxdevteam.craftconnect.ui.components.CraftBackground
import pl.syntaxdevteam.craftconnect.ui.components.CraftBottomBar
import pl.syntaxdevteam.craftconnect.ui.screens.ChatScreen
import pl.syntaxdevteam.craftconnect.ui.screens.PlayersScreen
import pl.syntaxdevteam.craftconnect.ui.screens.ServersScreen
import pl.syntaxdevteam.craftconnect.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

enum class AppDestination {
    Servers,
    Chat,
    Players,
    Settings,
}

@Composable
fun CraftConnectApp(sessionManagerFactory: SessionManagerFactory) {
    var destination by remember { mutableStateOf(AppDestination.Servers) }
    var activeServer by remember { mutableStateOf<ServerProfile?>(null) }
    var sessionManager by remember { mutableStateOf<SessionManager?>(null) }
    var connectionState by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

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
                        connectionState = connectionState,
                        connectionError = connectionError,
                        onConnect = { server, username ->
                            coroutineScope.launch {
                                val manager = sessionManagerFactory.create()
                                sessionManager = manager
                                connectionState = ConnectionState.CONNECTING
                                connectionError = null
                                manager.connect(server, username)
                                connectionState = manager.session.value.connectionState
                                connectionError = manager.session.value.lastError?.serverMessage
                                if (connectionState == ConnectionState.CONNECTED) {
                                    activeServer = server.copy(online = true)
                                    destination = AppDestination.Chat
                                }
                            }
                        },
                    )
                    AppDestination.Chat -> ChatScreen(currentServer) { message ->
                        coroutineScope.launch {
                            val manager = sessionManager ?: return@launch
                            if (message.startsWith('/')) manager.sendCommand(message) else manager.sendChat(message)
                        }
                    }
                    AppDestination.Players -> PlayersScreen(currentServer)
                    AppDestination.Settings -> SettingsScreen()
                }
            }
        }
    }
}
