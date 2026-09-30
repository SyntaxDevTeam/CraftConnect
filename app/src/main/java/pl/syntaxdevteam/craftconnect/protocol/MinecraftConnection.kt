package pl.syntaxdevteam.craftconnect.protocol

import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val server: ServerProfile) : ConnectionState
    data class Connected(val server: ServerProfile, val protocolVersion: Int) : ConnectionState
    data class Failed(val reason: String) : ConnectionState
}

/**
 * Stable boundary between Android presentation code and the future headless
 * Minecraft protocol implementation.
 *
 * Compose screens must never depend directly on packet classes.
 */
interface MinecraftConnection {
    val state: ConnectionState

    suspend fun connect(server: ServerProfile)
    suspend fun disconnect()
    suspend fun sendChat(message: String)
    suspend fun sendCommand(command: String)
}
