package pl.syntaxdevteam.craftconnect.protocol

import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile

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
    suspend fun connect(server: ServerProfile, username: String): ConnectedSession
    suspend fun disconnect()
    suspend fun sendChat(message: String)
    suspend fun sendCommand(command: String)
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
