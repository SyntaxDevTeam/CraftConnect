package pl.syntaxdevteam.craftconnect.data.session

import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.domain.session.ConnectionState
import pl.syntaxdevteam.craftconnect.domain.session.SessionError
import pl.syntaxdevteam.craftconnect.domain.session.SessionEvent
import pl.syntaxdevteam.craftconnect.protocol.ConnectedSession
import pl.syntaxdevteam.craftconnect.protocol.MinecraftConnection
import pl.syntaxdevteam.craftconnect.protocol.MinecraftConnectionException

class DefaultSessionManagerTest {
    @Test
    fun premiumAuthenticationFailureDoesNotFallBackToOffline() = runTest {
        val connection = FakeConnection()
        val manager = DefaultSessionManager(connection, observerScope = backgroundScope, premiumIdentity = {
            throw pl.syntaxdevteam.craftconnect.domain.auth.AuthenticationException(pl.syntaxdevteam.craftconnect.domain.auth.AuthProblem.REAUTHENTICATE)
        })
        manager.connect(server, pl.syntaxdevteam.craftconnect.domain.model.AccountProfile("microsoft:id", "Player", pl.syntaxdevteam.craftconnect.domain.model.AccountType.MICROSOFT))
        assertEquals(ConnectionState.FAILED, manager.session.value.connectionState)
        assertEquals("premium_reauthenticate", manager.session.value.lastError?.diagnosticCode)
        assertEquals(0, connection.offlineConnections)
    }

    @Test
    fun premiumConnectPassesVerifiedIdentityInsteadOfStoredDisplayName() = runTest {
        val connection = FakeConnection()
        val identity = pl.syntaxdevteam.craftconnect.domain.auth.MinecraftIdentity("NewName", "profile-id", "secret")
        val manager = DefaultSessionManager(connection, observerScope = backgroundScope, premiumIdentity = { identity })
        manager.connect(server, pl.syntaxdevteam.craftconnect.domain.model.AccountProfile("microsoft:id", "OldName", pl.syntaxdevteam.craftconnect.domain.model.AccountType.MICROSOFT))
        assertEquals(ConnectionState.CONNECTED, manager.session.value.connectionState)
        assertEquals("NewName", manager.session.value.username)
        assertEquals("profile-id", manager.session.value.uuid)
        assertEquals(0, connection.offlineConnections)
        assertEquals(identity, connection.premiumIdentity)
    }

    @Test
    fun successfulConnectionPublishesConnectedSession() = runTest {
        val manager = DefaultSessionManager(FakeConnection(), clock = { 42L })

        manager.connect(server, "CraftyDev")

        assertEquals(ConnectionState.CONNECTED, manager.session.value.connectionState)
        assertEquals(47, manager.session.value.protocolVersion)
        assertEquals("CraftyDev", manager.session.value.username)
        assertEquals("01234567-89ab-cdef-0123-456789abcdef", manager.session.value.uuid)
        assertEquals(42L, manager.session.value.connectedAtEpochMillis)
        assertNull(manager.session.value.lastError)
    }

    @Test
    fun typedProtocolFailureBecomesSafeDomainError() = runTest {
        val manager = DefaultSessionManager(
            FakeConnection(connectFailure = MinecraftConnectionException.Network("tcp_timeout")),
        )
        val failureEvent = async(start = CoroutineStart.UNDISPATCHED) {
            manager.events.first { it is SessionEvent.ConnectionFailed }
        }

        manager.connect(server, "CraftyDev")

        assertEquals(ConnectionState.FAILED, manager.session.value.connectionState)
        assertEquals(SessionError.Network("tcp_timeout"), manager.session.value.lastError)
        assertEquals(
            SessionError.Network("tcp_timeout"),
            (failureEvent.await() as SessionEvent.ConnectionFailed).error,
        )
    }

    @Test
    fun serverRejectionMessageIsPreservedForTheUi() = runTest {
        val manager = DefaultSessionManager(
            FakeConnection(
                connectFailure = MinecraftConnectionException.Authentication(
                    "login_rejected",
                    "Outdated client!",
                ),
            ),
        )

        manager.connect(server, "CraftyDev")

        assertEquals("Outdated client!", manager.session.value.lastError?.serverMessage)
    }

    @Test
    fun staleActionsAreIgnoredAfterDisconnection() = runTest {
        val manager = DefaultSessionManager(FakeConnection())

        manager.sendChat("hello")
        manager.sendCommand("/login secret")
        manager.submitDialog("authgatewayx:login_submit", emptyMap())
        assertEquals(ConnectionState.DISCONNECTED, manager.session.value.connectionState)
    }

    @Test
    fun dialogSubmissionIsForwardedToTheConnection() = runTest {
        val connection = FakeConnection()
        val manager = DefaultSessionManager(connection)
        manager.connect(server, "CraftyDev")

        manager.submitDialog("authgatewayx:login_submit", mapOf("password" to "secret"))

        assertEquals("authgatewayx:login_submit", connection.submittedAction)
        assertEquals(mapOf("password" to "secret"), connection.submittedValues)
    }

    @Test
    fun closedSocketDuringCommandBecomesSessionFailure() = runTest {
        val connection = FakeConnection(sendFailure = MinecraftConnectionException.Network("connection_closed_before_send"))
        val manager = DefaultSessionManager(connection, observerScope = backgroundScope)
        manager.connect(server, "CraftyDev")

        manager.sendCommand("/login secret")

        assertEquals(ConnectionState.FAILED, manager.session.value.connectionState)
        assertEquals("connection_closed_before_send", manager.session.value.lastError?.diagnosticCode)
        assertTrue(connection.disconnected)
        manager.sendCommand("/login secret")
        assertEquals(1, connection.sentCommands)
    }

    @Test
    fun receiveDisconnectUpdatesSessionWithoutSending() = runTest {
        val connection = FakeConnection()
        val manager = DefaultSessionManager(connection, observerScope = backgroundScope)
        manager.connect(server, "CraftyDev")
        val failed = async(start = CoroutineStart.UNDISPATCHED) {
            manager.session.first { it.connectionState == ConnectionState.FAILED }
        }

        connection.failure.value = MinecraftConnectionException.Authentication("play_disconnected", "Server restarting")

        assertEquals("Server restarting", failed.await().lastError?.serverMessage)
    }

    @Test
    fun immediateReceiveFailureIsReplayedAfterConnect() = runTest {
        val connection = FakeConnection(receiveFailure = MinecraftConnectionException.Network("connection_play_closed"))
        val manager = DefaultSessionManager(connection, observerScope = backgroundScope)
        manager.connect(server, "CraftyDev")

        val failed = manager.session.first { it.connectionState == ConnectionState.FAILED }

        assertEquals("connection_play_closed", failed.lastError?.diagnosticCode)
    }

    @Test
    fun sendCancellationStillPropagates() = runTest {
        val manager = DefaultSessionManager(
            FakeConnection(sendFailure = CancellationException("cancelled")), observerScope = backgroundScope,
        )
        manager.connect(server, "CraftyDev")

        assertTrue(runCatching { manager.sendCommand("/help") }.exceptionOrNull() is CancellationException)
        assertEquals(ConnectionState.CONNECTED, manager.session.value.connectionState)
    }

    private class FakeConnection(
        private val connectFailure: MinecraftConnectionException? = null,
        private val sendFailure: Exception? = null,
        private val receiveFailure: MinecraftConnectionException? = null,
    ) : MinecraftConnection {
        val failure = MutableStateFlow<MinecraftConnectionException?>(null)
        override val connectionFailures = failure.filterNotNull()
        var offlineConnections = 0
        var premiumIdentity: pl.syntaxdevteam.craftconnect.domain.auth.MinecraftIdentity? = null
        override suspend fun connect(server: ServerProfile, identity: pl.syntaxdevteam.craftconnect.domain.auth.MinecraftIdentity): ConnectedSession {
            premiumIdentity = identity
            return ConnectedSession(775, identity.username, identity.uuid)
        }
        var disconnected = false
        var sentCommands = 0
        var submittedAction: String? = null
        var submittedValues: Map<String, String>? = null
        override suspend fun connect(server: ServerProfile, username: String): ConnectedSession {
            offlineConnections++
            connectFailure?.let { throw it }
            failure.value = receiveFailure
            return ConnectedSession(
                protocolVersion = 47,
                username = username,
                uuid = "01234567-89ab-cdef-0123-456789abcdef",
            )
        }

        override suspend fun disconnect() { disconnected = true }
        override suspend fun sendChat(message: String) = Unit
        override suspend fun sendCommand(command: String) {
            sentCommands++
            sendFailure?.let { throw it }
        }
        override suspend fun submitDialog(actionId: String, values: Map<String, String>) {
            submittedAction = actionId
            submittedValues = values
        }
    }

    private companion object {
        val server = ServerProfile("test", "Test", "localhost", true, 0, 20, 1)
    }
}

