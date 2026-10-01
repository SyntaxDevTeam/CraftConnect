package pl.syntaxdevteam.craftconnect.protocol

import pl.syntaxdevteam.craftconnect.domain.auth.MinecraftIdentity
import pl.syntaxdevteam.craftconnect.domain.model.ReceivedChatMessage
import pl.syntaxdevteam.craftconnect.domain.model.ServerPlayer

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.domain.model.ServerDialogEvent

data class ConnectedSession(
    val protocolVersion: Int,
    val username: String,
    val uuid: String,
)

/**
 * Packet-independent boundary implemented by a version-aware protocol adapter.
 * Presentation code communicates through SessionManager instead of this type.
 */
interface MinecraftConnection {
    val players: kotlinx.coroutines.flow.StateFlow<List<ServerPlayer>>
        get() = kotlinx.coroutines.flow.MutableStateFlow(emptyList())

    val chatMessages: kotlinx.coroutines.flow.StateFlow<List<ReceivedChatMessage>>
        get() = kotlinx.coroutines.flow.MutableStateFlow(emptyList())

    val dialogEvents: Flow<ServerDialogEvent>
        get() = emptyFlow()

    /** Terminal receive failure, replayed so an immediate disconnect cannot be missed. */
    val connectionFailures: Flow<MinecraftConnectionException>
        get() = emptyFlow()

    suspend fun connect(server: ServerProfile, username: String): ConnectedSession
    suspend fun connect(server: ServerProfile, identity: MinecraftIdentity): ConnectedSession {
        throw MinecraftConnectionException.Authentication("premium_protocol_unsupported")
    }
    suspend fun disconnect()
    suspend fun sendChat(message: String)
    suspend fun sendCommand(command: String)
    suspend fun submitDialog(actionId: String, values: Map<String, String>) {
        error("This protocol does not support server dialogs")
    }
}

sealed class MinecraftConnectionException(
    val diagnosticCode: String,
    val serverMessage: String? = null,
    cause: Throwable? = null,
) : Exception(diagnosticCode, cause) {
    class Network(code: String, cause: Throwable? = null) :
        MinecraftConnectionException(code, cause?.localizedMessage, cause)
    class Authentication(code: String, serverMessage: String? = null) :
        MinecraftConnectionException(code, serverMessage)
    class Protocol(code: String, cause: Throwable? = null) : MinecraftConnectionException(code, code, cause)
}


