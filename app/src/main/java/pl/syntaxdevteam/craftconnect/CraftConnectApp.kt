package pl.syntaxdevteam.craftconnect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import pl.syntaxdevteam.craftconnect.data.DemoRepository
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.domain.model.ServerDialogEvent
import pl.syntaxdevteam.craftconnect.domain.model.ServerDialogRequest
import pl.syntaxdevteam.craftconnect.domain.session.ConnectionState
import pl.syntaxdevteam.craftconnect.domain.session.SessionManager
import pl.syntaxdevteam.craftconnect.domain.session.SessionManagerFactory
import pl.syntaxdevteam.craftconnect.domain.server.ServerRepository
import pl.syntaxdevteam.craftconnect.domain.account.AccountRepository
import pl.syntaxdevteam.craftconnect.ui.components.CraftBackground
import pl.syntaxdevteam.craftconnect.ui.components.CraftBottomBar
import pl.syntaxdevteam.craftconnect.ui.components.ServerDialogForm
import pl.syntaxdevteam.craftconnect.ui.screens.ChatScreen
import pl.syntaxdevteam.craftconnect.ui.screens.PlayersScreen
import pl.syntaxdevteam.craftconnect.ui.screens.ServersScreen
import pl.syntaxdevteam.craftconnect.ui.screens.SettingsScreen
import pl.syntaxdevteam.craftconnect.ui.screens.AccountsScreen
import kotlinx.coroutines.launch

enum class AppDestination {
    Servers,
    Chat,
    Players,
    Accounts,
    Settings,
}

@Composable
fun CraftConnectApp(
    sessionManagerFactory: SessionManagerFactory,
    serverRepository: ServerRepository,
    accountRepository: AccountRepository,
) {
    var destination by remember { mutableStateOf(AppDestination.Servers) }
    var activeServer by remember { mutableStateOf<ServerProfile?>(null) }
    var sessionManager by remember { mutableStateOf<SessionManager?>(null) }
    var connectionState by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var serverDialog by remember { mutableStateOf<ServerDialogRequest?>(null) }
    val emptyChat = remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList<pl.syntaxdevteam.craftconnect.domain.model.ReceivedChatMessage>()) }
    val chatMessages by (sessionManager?.chatMessages ?: emptyChat).collectAsState()
    val savedServers by serverRepository.servers.collectAsState()
    val savedAccounts by accountRepository.accounts.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(sessionManager) {
        sessionManager?.dialogEvents?.collect { event ->
            serverDialog = when (event) {
                is ServerDialogEvent.Show -> event.dialog
                ServerDialogEvent.Clear -> null
            }
        }
    }

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
            val currentServer = activeServer ?: savedServers.firstOrNull() ?: DemoRepository.servers.first()

            Box(Modifier.padding(contentPadding)) {
                when (destination) {
                    AppDestination.Servers -> ServersScreen(
                        servers = savedServers,
                        connectionState = connectionState,
                        connectionError = connectionError,
                        accounts = savedAccounts,
                        onCreate = { name, address, favorite ->
                            serverRepository.create(name, address, favorite)
                        },
                        onUpdate = serverRepository::update,
                        onDelete = serverRepository::delete,
                        onConnect = { server, username ->
                            coroutineScope.launch {
                                sessionManager?.disconnect()
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
                    AppDestination.Chat -> ChatScreen(currentServer, chatMessages, connectionState == ConnectionState.CONNECTED) { message ->
                        coroutineScope.launch {
                            val manager = sessionManager ?: return@launch
                            if (message.startsWith('/')) manager.sendCommand(message) else manager.sendChat(message)
                        }
                    }
                    AppDestination.Players -> PlayersScreen(currentServer)
                    AppDestination.Accounts -> AccountsScreen(
                        accounts = savedAccounts,
                        onCreate = accountRepository::createOffline,
                        onUpdate = accountRepository::update,
                        onDelete = accountRepository::delete,
                    )
                    AppDestination.Settings -> SettingsScreen()
                }
            }
        }

        serverDialog?.let { dialog ->
            ServerDialogForm(
                dialog = dialog,
                onSubmit = { actionId, values ->
                    serverDialog = null
                    coroutineScope.launch { sessionManager?.submitDialog(actionId, values) }
                },
                onCancel = { actionId ->
                    serverDialog = null
                    coroutineScope.launch { sessionManager?.submitDialog(actionId, emptyMap()) }
                },
            )
        }
    }
}

