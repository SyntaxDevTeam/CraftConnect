package pl.syntaxdevteam.craftconnect.domain.session

import pl.syntaxdevteam.craftconnect.domain.model.AccountProfile
import pl.syntaxdevteam.craftconnect.domain.model.AccountType.OFFLINE
import pl.syntaxdevteam.craftconnect.domain.model.ReceivedChatMessage
import pl.syntaxdevteam.craftconnect.domain.model.ServerPlayer

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.domain.model.ServerDialogEvent

interface SessionManager {
    val players: kotlinx.coroutines.flow.StateFlow<List<ServerPlayer>>
        get() = kotlinx.coroutines.flow.MutableStateFlow(emptyList())

    val chatMessages: kotlinx.coroutines.flow.StateFlow<List<ReceivedChatMessage>>
        get() = kotlinx.coroutines.flow.MutableStateFlow(emptyList())

    val session: StateFlow<SessionSnapshot>
    val events: SharedFlow<SessionEvent>
    val dialogEvents: Flow<ServerDialogEvent>

    suspend fun connect(server: ServerProfile, username: String)
    suspend fun connect(server: ServerProfile, account: AccountProfile) {
        require(account.type == OFFLINE)
        connect(server, account.username)
    }
    suspend fun disconnect()
    suspend fun sendChat(message: String)
    suspend fun sendCommand(command: String)
    suspend fun submitDialog(actionId: String, values: Map<String, String>)
}

fun interface SessionManagerFactory {
    fun create(): SessionManager
}


