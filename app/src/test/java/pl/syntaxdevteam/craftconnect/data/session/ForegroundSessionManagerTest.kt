package pl.syntaxdevteam.craftconnect.data.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.domain.model.ServerDialogEvent
import pl.syntaxdevteam.craftconnect.domain.session.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ForegroundSessionManagerTest {
    private class FakeSession(private val calls: MutableList<String>) : SessionManager {
        override val session = MutableStateFlow(SessionSnapshot())
        override val events = MutableSharedFlow<SessionEvent>()
        override val dialogEvents = MutableSharedFlow<ServerDialogEvent>()
        override suspend fun connect(server: ServerProfile, username: String) {
            calls += "connect"
            session.value = SessionSnapshot(connectionState = ConnectionState.CONNECTED, server = server, username = username)
        }
        override suspend fun disconnect() { calls += "disconnect"; session.value = SessionSnapshot() }
        override suspend fun sendChat(message: String) { calls += "chat:$message" }
        override suspend fun sendCommand(command: String) { }
        override suspend fun submitDialog(actionId: String, values: Map<String, String>) { }
    }
    private fun controller(calls: MutableList<String>) = object : SessionServiceController {
        override fun start() { calls += "start" }
        override fun stop() { calls += "stop" }
    }

    @Test fun serviceStartsBeforeSocketAndUiRecreationDoesNotDisconnectSession() = runTest {
        val calls = mutableListOf<String>()
        val fake = FakeSession(calls)
        val manager = ForegroundSessionManager(controller(calls), backgroundScope, fake)
        runCurrent(); calls.clear()
        manager.connect(ServerProfile("server", "Test", "localhost", false, 0, 0, null), "Adrian")
        runCurrent()
        assertEquals(listOf("start", "connect"), calls)
        val uiScope = CoroutineScope(coroutineContext + Job())
        uiScope.launch { manager.session.collect { } }
        runCurrent()
        uiScope.cancel() // Activity/Compose subscription is torn down on rotation or exit.
        assertEquals(ConnectionState.CONNECTED, manager.session.value.connectionState)
        val newUi = CoroutineScope(coroutineContext + Job())
        var restored: SessionSnapshot? = null
        newUi.launch { manager.session.collect { restored = it } }
        runCurrent()
        assertEquals("Adrian", restored?.username)
        manager.sendChat("still connected")
        assertTrue(calls.contains("chat:still connected"))
        assertFalse(calls.contains("disconnect"))
        newUi.cancel()
        manager.disconnect(); runCurrent()
        assertEquals(ConnectionState.DISCONNECTED, manager.session.value.connectionState)
        assertTrue(calls.indexOf("disconnect") < calls.lastIndexOf("stop"))
    }

    @Test fun remoteFailureStopsBackgroundService() = runTest {
        val calls = mutableListOf<String>()
        val fake = FakeSession(calls)
        val manager = ForegroundSessionManager(controller(calls), backgroundScope, fake)
        runCurrent()
        manager.connect(ServerProfile("server", "Test", "localhost", false, 0, 0, null), "Adrian")
        runCurrent(); calls.clear()
        fake.session.value = fake.session.value.copy(connectionState = ConnectionState.FAILED)
        runCurrent()
        assertEquals(listOf("stop"), calls)
    }
}
