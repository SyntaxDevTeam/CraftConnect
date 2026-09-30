package pl.syntaxdevteam.craftconnect.domain.session

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile

interface SessionManager {
    val session: StateFlow<SessionSnapshot>
    val events: SharedFlow<SessionEvent>

    suspend fun connect(server: ServerProfile, username: String)
    suspend fun disconnect()
    suspend fun sendChat(message: String)
    suspend fun sendCommand(command: String)
}

fun interface SessionManagerFactory {
    fun create(): SessionManager
}
