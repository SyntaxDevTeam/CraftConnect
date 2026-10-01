package pl.syntaxdevteam.craftconnect.protocol

import java.util.UUID
import pl.syntaxdevteam.craftconnect.bridge.protocol.AuthenticationBridgeProtocol
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JoinVisibilityCommandsTest {
    private fun confirm(commands: JoinVisibilityCommands) {
        val nonce = AuthenticationBridgeProtocol.subscriptionNonce(commands.subscriptionPayload())
        commands.onBridgeMessage(AuthenticationBridgeProtocol.authenticated(nonce, setOf("AuthMe")))
    }

    @Test
    fun staleConnectionProofCannotAuthenticateReconnect() = runTest {
        val sent = mutableListOf<String>()
        val commands = JoinVisibilityCommands(this, { sent += it })
        val nonce = AuthenticationBridgeProtocol.subscriptionNonce(commands.subscriptionPayload())
        val stale = AuthenticationBridgeProtocol.authenticated(nonce, setOf("nLogin"))
        commands.reset()
        commands.onWorldReady()
        commands.onBridgeMessage(stale)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), sent)
        confirm(commands)
        runCurrent()
        assertEquals(listOf("gamemode spectator"), sent)
        commands.reset()
    }

    @Test
    fun worldJoinAndFailedLoginNeverSendCommands() = runTest {
        val sent = mutableListOf<String>()
        val commands = JoinVisibilityCommands(this, { sent += it })
        commands.onWorldReady()
        commands.onBridgeMessage(byteArrayOf(1, 2, 3))
        advanceUntilIdle()
        assertEquals(emptyList<String>(), sent)
        confirm(commands)
        runCurrent()
        assertEquals(listOf("gamemode spectator"), sent)
        commands.reset()
    }

    @Test
    fun authenticationBeforeWorldJoinWaitsForWorld() = runTest {
        val sent = mutableListOf<String>()
        val commands = JoinVisibilityCommands(this, { sent += it })
        confirm(commands)
        advanceUntilIdle()
        assertEquals(emptyList<String>(), sent)
        commands.onWorldReady()
        runCurrent()
        assertEquals(listOf("gamemode spectator"), sent)
        commands.reset()
    }

    @Test
    fun sendsSpectatorThenOneFallbackWithoutConfirmation() = runTest {
        val sent = mutableListOf<String>()
        val commands = JoinVisibilityCommands(this, { sent += it })
        confirm(commands)
        commands.onWorldReady()
        runCurrent()
        assertEquals(listOf("gamemode spectator"), sent)
        advanceTimeBy(3_000)
        runCurrent()
        confirm(commands)
        commands.onWorldReady()
        advanceUntilIdle()
        assertEquals(listOf("gamemode spectator", "vanish"), sent)
    }

    @Test
    fun confirmationCancelsFallback() = runTest {
        val sent = mutableListOf<String>()
        val commands = JoinVisibilityCommands(this, { sent += it })
        confirm(commands)
        commands.onWorldReady()
        runCurrent()
        commands.onGameMode(3)
        advanceUntilIdle()
        assertEquals(listOf("gamemode spectator"), sent)
    }

    @Test
    fun alreadySpectatingDoesNotToggleVanish() = runTest {
        val sent = mutableListOf<String>()
        val commands = JoinVisibilityCommands(this, { sent += it })
        commands.onGameMode(3)
        confirm(commands)
        commands.onWorldReady()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun disconnectCancelsPendingCommandsAndReconnectStartsFresh() = runTest {
        val sent = mutableListOf<String>()
        val commands = JoinVisibilityCommands(this, { sent += it })
        confirm(commands)
        commands.onWorldReady()
        runCurrent()
        commands.reset()
        advanceUntilIdle()
        assertEquals(listOf("gamemode spectator"), sent)
        confirm(commands)
        commands.onWorldReady()
        advanceUntilIdle()
        assertEquals(listOf("gamemode spectator", "gamemode spectator", "vanish"), sent)
    }
}
