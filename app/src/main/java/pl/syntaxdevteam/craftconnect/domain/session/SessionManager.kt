package pl.syntaxdevteam.craftconnect.domain.session

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.domain.model.ServerDialogEvent

interface SessionManager {
    val session: StateFlow<SessionSnapshot>
    val events: SharedFlow<SessionEvent>
    val dialogEvents: Flow<ServerDialogEvent>

    suspend fun connect(server: ServerProfile, username: String)
    suspend fun disconnect()
    suspend fun sendChat(message: String)
    suspend fun sendCommand(command: String)
    suspend fun submitDialog(actionId: String, values: Map<String, String>)
}

fun interface SessionManagerFactory {
    fun create(): SessionManager
}
