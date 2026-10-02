package pl.syntaxdevteam.craftconnect.data.session

import pl.syntaxdevteam.craftconnect.BuildConfig
import pl.syntaxdevteam.craftconnect.data.integration.KeystoreCraftConnectDeviceKeyStore
import pl.syntaxdevteam.craftconnect.domain.auth.AuthProblem.CONFIGURATION
import pl.syntaxdevteam.craftconnect.domain.auth.AuthenticationException
import pl.syntaxdevteam.craftconnect.domain.auth.MinecraftIdentity
import pl.syntaxdevteam.craftconnect.domain.integration.ServerCapabilityResolver
import pl.syntaxdevteam.craftconnect.domain.integration.ServerCapabilitySnapshot
import pl.syntaxdevteam.craftconnect.domain.model.AccountProfile
import pl.syntaxdevteam.craftconnect.domain.model.AccountType.MICROSOFT
import pl.syntaxdevteam.craftconnect.protocol.ConnectedSession

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.firstOrNull
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
import pl.syntaxdevteam.craftconnect.protocol.agx.AuthGatewayXChannelClient
import pl.syntaxdevteam.craftconnect.protocol.agx.AuthGatewayXPayloadSender
import pl.syntaxdevteam.craftconnect.protocol.agx.AuthGatewayXProtocol

class DefaultSessionManager(
    private val connection: MinecraftConnection,
    private val clock: () -> Long = System::currentTimeMillis,
    private val observerScope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val premiumIdentity: suspend (AccountProfile) -> MinecraftIdentity = {
        throw AuthenticationException(CONFIGURATION)
    },
    private val authGatewayX: AuthGatewayXChannelClient? = defaultAuthGatewayXClient(),
) : SessionManager {
    private val operationMutex = Mutex()
    private var failureObserver: Job? = null
    private var customPayloadObserver: Job? = null
    private var capabilityObserver: Job? = null
    private var enhancedHelloJob: Job? = null
    private val mutableSession = MutableStateFlow(SessionSnapshot())
    private val mutableEvents = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 16)
    private val mutableServerCapabilities = MutableStateFlow(ServerCapabilitySnapshot())

    override val session: StateFlow<SessionSnapshot> = mutableSession.asStateFlow()
    override val events: SharedFlow<SessionEvent> = mutableEvents.asSharedFlow()
    override val serverCapabilities: StateFlow<ServerCapabilitySnapshot> = mutableServerCapabilities.asStateFlow()
    override val chatMessages = connection.chatMessages
    override val players = connection.players
    override val dialogEvents: Flow<ServerDialogEvent> = connection.dialogEvents

    override suspend fun connect(server: ServerProfile, username: String) = connectUsing(server) { connection.connect(server, username) }

    override suspend fun connect(server: ServerProfile, account: AccountProfile) =
        connectUsing(server) {
            if (account.type == MICROSOFT) connection.connect(server, premiumIdentity(account))
            else connection.connect(server, account.username)
        }

    private suspend fun connectUsing(server: ServerProfile, login: suspend () -> ConnectedSession) = operationMutex.withLock {
        if (session.value.connectionState != ConnectionState.DISCONNECTED &&
            session.value.connectionState != ConnectionState.FAILED
        ) {
            return@withLock
        }

        failureObserver?.cancel()
        stopEnhancedIntegration()
        update(SessionSnapshot(connectionState = ConnectionState.CONNECTING, server = server))
        try {
            val connected = login()
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
            mutableServerCapabilities.value = ServerCapabilityResolver.resolve(
                minecraftConnected = true,
                rconAvailable = false,
            )
            startEnhancedIntegration()
            failureObserver = observerScope.launch {
                val failure = connection.connectionFailures.firstOrNull() ?: return@launch
                operationMutex.withLock {
                    if (session.value.connectionState == ConnectionState.CONNECTED) fail(failure.toSessionError())
                }
            }
        } catch (cancelled: CancellationException) {
            stopEnhancedIntegration()
            update(SessionSnapshot())
            throw cancelled
        } catch (failure: AuthenticationException) {
            stopEnhancedIntegration()
            fail(SessionError.Authentication("premium_${failure.problem.name.lowercase()}"))
        } catch (failure: MinecraftConnectionException) {
            stopEnhancedIntegration()
            fail(failure.toSessionError())
        } catch (_: Exception) {
            stopEnhancedIntegration()
            fail(SessionError.Protocol("unexpected_connection_failure"))
        }
    }

    override suspend fun disconnect() = operationMutex.withLock {
        if (session.value.connectionState == ConnectionState.DISCONNECTED) return@withLock

        failureObserver?.cancel()
        failureObserver = null
        stopEnhancedIntegration()
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

    override suspend fun beginEnhancedPairing() {
        val client = authGatewayX ?: return
        val snapshot = session.value
        if (snapshot.connectionState != ConnectionState.CONNECTED) return
        val playerUuid = runCatching { UUID.fromString(snapshot.uuid) }.getOrNull() ?: return
        runCatching {
            client.beginPairing(playerUuid, payloadSender())
        }
    }

    override suspend fun submitDialog(actionId: String, values: Map<String, String>) =
        send { connection.submitDialog(actionId, values) }

    private fun startEnhancedIntegration() {
        val client = authGatewayX ?: return
        client.reset()
        customPayloadObserver?.cancel()
        capabilityObserver?.cancel()
        enhancedHelloJob?.cancel()

        customPayloadObserver = observerScope.launch {
            connection.customPayloads.collect { customPayload ->
                if (customPayload.channel == AuthGatewayXProtocol.CHANNEL) {
                    runCatching { client.onPayload(customPayload.payload, payloadSender()) }
                }
            }
        }
        capabilityObserver = observerScope.launch {
            client.capabilities.collect { granted ->
                if (session.value.connectionState == ConnectionState.CONNECTED) {
                    mutableServerCapabilities.value = ServerCapabilityResolver.resolve(
                        minecraftConnected = true,
                        rconAvailable = false,
                        authGatewayXCapabilities = granted,
                    )
                }
            }
        }
        enhancedHelloJob = observerScope.launch {
            runCatching {
                connection.sendCustomPayload(
                    "minecraft:register",
                    AuthGatewayXProtocol.CHANNEL.toByteArray(Charsets.UTF_8),
                )
                connection.sendCustomPayload(AuthGatewayXProtocol.CHANNEL, client.createHelloPayload())
            }
            // Enhanced Mode is optional. Failure here must never fail the Minecraft session.
        }
    }

    private fun stopEnhancedIntegration() {
        customPayloadObserver?.cancel()
        capabilityObserver?.cancel()
        enhancedHelloJob?.cancel()
        customPayloadObserver = null
        capabilityObserver = null
        enhancedHelloJob = null
        authGatewayX?.reset()
        mutableServerCapabilities.value = ServerCapabilitySnapshot()
    }

    private fun payloadSender() = AuthGatewayXPayloadSender { channel, payload ->
        connection.sendCustomPayload(channel, payload)
    }

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
        stopEnhancedIntegration()
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
        stopEnhancedIntegration()
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

private fun defaultAuthGatewayXClient(): AuthGatewayXChannelClient =
    AuthGatewayXChannelClient(BuildConfig.VERSION_NAME, KeystoreCraftConnectDeviceKeyStore())

private fun MinecraftConnectionException.toSessionError(): SessionError = when (this) {
    is MinecraftConnectionException.Network -> SessionError.Network(diagnosticCode, serverMessage)
    is MinecraftConnectionException.Authentication -> SessionError.Authentication(diagnosticCode, serverMessage)
    is MinecraftConnectionException.Protocol -> SessionError.Protocol(diagnosticCode, serverMessage)
}
