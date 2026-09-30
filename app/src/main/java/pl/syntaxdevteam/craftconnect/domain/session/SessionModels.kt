package pl.syntaxdevteam.craftconnect.domain.session

import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    AUTHENTICATING,
    JOINING,
    CONNECTED,
    RECONNECTING,
    FAILED,
}

data class SessionSnapshot(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val server: ServerProfile? = null,
    val protocolVersion: Int? = null,
    val username: String? = null,
    val uuid: String? = null,
    val connectedAtEpochMillis: Long? = null,
    val reconnectCount: Int = 0,
    val lastError: SessionError? = null,
)

sealed interface SessionError {
    val diagnosticCode: String

    data class Network(override val diagnosticCode: String) : SessionError
    data class Authentication(override val diagnosticCode: String) : SessionError
    data class Protocol(override val diagnosticCode: String) : SessionError
    data class Disconnected(override val diagnosticCode: String) : SessionError
}

sealed interface SessionEvent {
    data class StateChanged(
        val previous: ConnectionState,
        val current: ConnectionState,
    ) : SessionEvent

    data class ConnectionFailed(val error: SessionError) : SessionEvent
    data class ConnectionClosed(val error: SessionError?) : SessionEvent
}
