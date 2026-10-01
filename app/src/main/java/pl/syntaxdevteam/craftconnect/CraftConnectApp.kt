package pl.syntaxdevteam.craftconnect

import pl.syntaxdevteam.craftconnect.domain.auth.AuthProblem
import pl.syntaxdevteam.craftconnect.data.auth.MicrosoftAccountManager
import pl.syntaxdevteam.craftconnect.ui.components.authProblemText

import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import pl.syntaxdevteam.craftconnect.data.DemoRepository
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.domain.model.ServerDialogEvent
import pl.syntaxdevteam.craftconnect.domain.model.ServerDialogRequest
import pl.syntaxdevteam.craftconnect.domain.session.ConnectionState
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
    sessionScope: CoroutineScope,
    onBeforeConnect: () -> Unit,
    microsoftAccounts: MicrosoftAccountManager,
) {
    val sessionManager = remember { sessionManagerFactory.create() }
    var destination by rememberSaveable {
        mutableStateOf(if (sessionManager.session.value.connectionState == ConnectionState.CONNECTED) AppDestination.Chat else AppDestination.Servers)
    }
    var activeServer by remember { mutableStateOf(sessionManager.session.value.server) }
    var connectionState by remember { mutableStateOf(sessionManager.session.value.connectionState) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var serverDialog by remember { mutableStateOf<ServerDialogRequest?>(null) }
    val players by sessionManager.players.collectAsState()
    val chatMessages by sessionManager.chatMessages.collectAsState()
    val savedServers by serverRepository.servers.collectAsState()
    val savedAccounts by accountRepository.accounts.collectAsState()
    val microsoftState by microsoftAccounts.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = sessionScope

    LaunchedEffect(sessionManager) {
        sessionManager.session.collect { snapshot ->
            val previousState = connectionState
            connectionState = snapshot.connectionState
            if (snapshot.connectionState == ConnectionState.CONNECTED) {
                activeServer = snapshot.server?.copy(online = true)
                if (previousState != ConnectionState.CONNECTED) destination = AppDestination.Chat
            }
            connectionError = snapshot.lastError?.let { error ->
                AuthProblem.entries.firstOrNull {
                    error.diagnosticCode == "premium_${it.name.lowercase()}"
                }?.let { context.getString(authProblemText(it)) }
                    ?: listOfNotNull(error.serverMessage, error.diagnosticCode).distinct().joinToString("\n")
            }
            if (snapshot.connectionState != ConnectionState.CONNECTED) serverDialog = null
            if (snapshot.connectionState == ConnectionState.FAILED) {
                activeServer = activeServer?.copy(online = false)
                destination = AppDestination.Servers
            }
        }
    }

    LaunchedEffect(sessionManager) {
        sessionManager.dialogEvents.collect { event ->
            serverDialog = when (event) {
                is ServerDialogEvent.Show -> event.dialog
                ServerDialogEvent.Clear -> null
            }
        }
    }

    CraftBackground {
        Scaffold(
            modifier = Modifier.imePadding(),
            containerColor = Color.Transparent,
            bottomBar = {
                CraftBottomBar(
                    destination = destination,
                    onDestination = { destination = it },
                )
            },
        ) { contentPadding ->
            val currentServer = (activeServer ?: savedServers.firstOrNull() ?: DemoRepository.servers.first()).let {
                if (connectionState == ConnectionState.CONNECTED) it.copy(playersOnline = players.size) else it
            }

            Box(Modifier.padding(contentPadding)) {
                when (destination) {
                    AppDestination.Servers -> ServersScreen(
                        servers = savedServers,
                        connectionState = connectionState,
                        connectionError = connectionError,
                        accounts = savedAccounts,
                        onCreate = { name, address, favorite, version ->
                            serverRepository.create(name, address, favorite, version)
                        },
                        onUpdate = serverRepository::update,
                        onDelete = serverRepository::delete,
                        onConnect = { server, account ->
                            onBeforeConnect()
                            coroutineScope.launch {
                                sessionManager.disconnect()
                                val manager = sessionManagerFactory.create()
                                connectionState = ConnectionState.CONNECTING
                                connectionError = null
                                manager.connect(server, account)
                                connectionState = manager.session.value.connectionState
                                connectionError = manager.session.value.lastError?.let { error ->
                                    AuthProblem.entries.firstOrNull {
                                        error.diagnosticCode == "premium_${it.name.lowercase()}"
                                    }?.let { context.getString(authProblemText(it)) }
                                        ?: listOfNotNull(error.serverMessage, error.diagnosticCode).distinct().joinToString("\n")
                                }
                                if (connectionState == ConnectionState.CONNECTED) {
                                    activeServer = server.copy(online = true)
                                    destination = AppDestination.Chat
                                }
                            }
                        },
                    )
                    AppDestination.Chat -> ChatScreen(currentServer, chatMessages, connectionState == ConnectionState.CONNECTED) { message ->
                        coroutineScope.launch {
                            val manager = sessionManager
                            if (message.startsWith('/')) manager.sendCommand(message) else manager.sendChat(message)
                        }
                    }
                    AppDestination.Players -> PlayersScreen(currentServer, players, connectionState == ConnectionState.CONNECTED)
                    AppDestination.Accounts -> AccountsScreen(
                        accounts = savedAccounts,
                        onCreate = accountRepository::createOffline,
                        onUpdate = accountRepository::update,
                        onDelete = { id -> coroutineScope.launch {
                            if (id == "microsoft:${sessionManager.session.value.uuid?.replace("-", "")}") sessionManager.disconnect()
                            microsoftAccounts.delete(id)
                        } },
                        microsoftState = microsoftState,
                        onMicrosoftSignIn = microsoftAccounts::start,
                        onCancelSignIn = microsoftAccounts::cancel,
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
                    coroutineScope.launch { sessionManager.submitDialog(actionId, values) }
                },
                onCancel = { actionId ->
                    serverDialog = null
                    coroutineScope.launch { sessionManager.submitDialog(actionId, emptyMap()) }
                },
            )
        }
    }
}




