package pl.syntaxdevteam.craftconnect.data.session

import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
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
    fun sendingRequiresConnectedSession() = runTest {
        val manager = DefaultSessionManager(FakeConnection())

        val failure = runCatching { manager.sendChat("hello") }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
    }

    private class FakeConnection(
        private val connectFailure: MinecraftConnectionException? = null,
    ) : MinecraftConnection {
        override suspend fun connect(server: ServerProfile, username: String): ConnectedSession {
            connectFailure?.let { throw it }
            return ConnectedSession(
                protocolVersion = 47,
                username = username,
                uuid = "01234567-89ab-cdef-0123-456789abcdef",
            )
        }

        override suspend fun disconnect() = Unit
        override suspend fun sendChat(message: String) = Unit
        override suspend fun sendCommand(command: String) = Unit
    }

    private companion object {
        val server = ServerProfile("test", "Test", "localhost", true, 0, 20, 1)
    }
}
