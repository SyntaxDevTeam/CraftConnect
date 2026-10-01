package pl.syntaxdevteam.craftconnect.protocol

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JoinVisibilityCommandsTest {
    @Test
    fun sendsSpectatorThenOneFallbackWithoutConfirmation() = runTest {
        val sent = mutableListOf<String>()
        val commands = JoinVisibilityCommands(this, { sent += it })
        commands.onWorldReady()
        runCurrent()
        assertEquals(listOf("gamemode spectator"), sent)
        advanceTimeBy(3_000)
        runCurrent()
        commands.onWorldReady()
        advanceUntilIdle()
        assertEquals(listOf("gamemode spectator", "vanish"), sent)
    }

    @Test
    fun confirmationCancelsFallback() = runTest {
        val sent = mutableListOf<String>()
        val commands = JoinVisibilityCommands(this, { sent += it })
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
        commands.onWorldReady()
        advanceUntilIdle()
        assertEquals(emptyList<String>(), sent)
    }

    @Test
    fun disconnectCancelsPendingCommandsAndReconnectStartsFresh() = runTest {
        val sent = mutableListOf<String>()
        val commands = JoinVisibilityCommands(this, { sent += it })
        commands.onWorldReady()
        runCurrent()
        commands.reset()
        advanceUntilIdle()
        assertEquals(listOf("gamemode spectator"), sent)
        commands.onWorldReady()
        advanceUntilIdle()
        assertEquals(listOf("gamemode spectator", "gamemode spectator", "vanish"), sent)
    }
}
