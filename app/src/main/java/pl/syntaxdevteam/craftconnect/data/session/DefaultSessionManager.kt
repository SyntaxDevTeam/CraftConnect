package pl.syntaxdevteam.craftconnect.data.session

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import java.io.IOException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.domain.model.ServerDialogEvent
import pl.syntaxdevteam.craftconnect.domain.session.ConnectionState
import pl.syntaxdevteam.craftconnect.domain.session.SessionError
import pl.syntaxdevteam.craftconnect.domain.session.SessionEvent
import pl.syntaxdevteam.craftconnect.domain.session.SessionManager
import pl.syntaxdevteam.craftconnect.domain.session.SessionSnapshot
import pl.syntaxdevteam.craftconnect.protocol.MinecraftConnection
import pl.syntaxdevteam.craftconnect.protocol.MinecraftConnectionException

class DefaultSessionManager(
    private val connection: MinecraftConnection,
    private val clock: () -> Long = System::currentTimeMillis,
    private val observerScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : SessionManager {
    private val operationMutex = Mutex()
    private var failureObserver: Job? = null
    private val mutableSession = MutableStateFlow(SessionSnapshot())
    private val mutableEvents = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 16)

    override val session: StateFlow<SessionSnapshot> = mutableSession.asStateFlow()
    override val events: SharedFlow<SessionEvent> = mutableEvents.asSharedFlow()
    override val chatMessages = connection.chatMessages
    override val players = connection.players
    override val dialogEvents: Flow<ServerDialogEvent> = connection.dialogEvents

    override suspend fun connect(server: ServerProfile, username: String) = operationMutex.withLock {
        if (session.value.connectionState != ConnectionState.DISCONNECTED &&
            session.value.connectionState != ConnectionState.FAILED
        ) {
            return@withLock
        }

        failureObserver?.cancel()
        update(SessionSnapshot(connectionState = ConnectionState.CONNECTING, server = server))
        try {
            val connected = connection.connect(server, username)
            update(
                session.value.copy(
                    connectionState = ConnectionState.CONNECTED,
                    protocolVersion = connected.protocolVersion,
                    username = connected.username,
                    uuid = connected.uuid,
                    connectedAtEpochMillis = clock(),
                    lastError = null,
                ),
            )
            failureObserver = observerScope.launch {
                val failure = connection.connectionFailures.firstOrNull() ?: return@launch
                operationMutex.withLock {
                    if (session.value.connectionState == ConnectionState.CONNECTED) fail(failure.toSessionError())
                }
            }
        } catch (cancelled: CancellationException) {
            update(SessionSnapshot())
            throw cancelled
        } catch (failure: MinecraftConnectionException) {
            fail(failure.toSessionError())
        } catch (_: Exception) {
            fail(SessionError.Protocol("unexpected_connection_failure"))
        }
    }

    override suspend fun disconnect() = operationMutex.withLock {
        if (session.value.connectionState == ConnectionState.DISCONNECTED) return@withLock

        failureObserver?.cancel()
        failureObserver = null
        var error: SessionError? = null
        try {
            connection.disconnect()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            error = SessionError.Disconnected("disconnect_cleanup_failure")
        }
        update(SessionSnapshot(lastError = error))
        mutableEvents.emit(SessionEvent.ConnectionClosed(error))
    }

    override suspend fun sendChat(message: String) = send { connection.sendChat(message) }

    override suspend fun sendCommand(command: String) = send { connection.sendCommand(command) }

    override suspend fun submitDialog(actionId: String, values: Map<String, String>) =
        send { connection.submitDialog(actionId, values) }

    private suspend fun send(action: suspend () -> Unit) = operationMutex.withLock {
        // A queued UI action may resume after the receive loop has closed the socket.
        if (session.value.connectionState != ConnectionState.CONNECTED) return@withLock
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: MinecraftConnectionException) {
            handleSendFailure(failure.toSessionError())
        } catch (_: IOException) {
            handleSendFailure(SessionError.Network("connection_write_failed"))
        } catch (_: IllegalStateException) {
            handleSendFailure(SessionError.Network("connection_closed_before_send"))
        }
    }

    private suspend fun handleSendFailure(error: SessionError) {
        failureObserver?.cancel()
        failureObserver = null
        try {
            connection.disconnect()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Preserve the original write failure instead of replacing it with cleanup failure.
        }
        fail(error)
    }

    private suspend fun fail(error: SessionError) {
        update(session.value.copy(connectionState = ConnectionState.FAILED, lastError = error))
        mutableEvents.emit(SessionEvent.ConnectionFailed(error))
    }

    private suspend fun update(snapshot: SessionSnapshot) {
        val previous = mutableSession.value.connectionState
        mutableSession.value = snapshot
        if (previous != snapshot.connectionState) {
            mutableEvents.emit(SessionEvent.StateChanged(previous, snapshot.connectionState))
        }
    }
}

private fun MinecraftConnectionException.toSessionError(): SessionError = when (this) {
    is MinecraftConnectionException.Network -> SessionError.Network(diagnosticCode, serverMessage)
    is MinecraftConnectionException.Authentication -> SessionError.Authentication(diagnosticCode, serverMessage)
    is MinecraftConnectionException.Protocol -> SessionError.Protocol(diagnosticCode, serverMessage)
}


