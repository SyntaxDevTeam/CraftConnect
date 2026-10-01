package pl.syntaxdevteam.craftconnect.protocol

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One attempt per connection; sending a command is never proof of protection. */
internal class JoinVisibilityCommands(
    private val scope: CoroutineScope,
    private val sendCommand: suspend (String) -> Unit,
    private val confirmationTimeoutMillis: Long = 3_000,
) {
    private var started = false
    private var worldReady = false
    private var authenticated = false
    private var spectator = false
    private var attempt: Job? = null

    @Synchronized
    fun onWorldReady() {
        worldReady = true
        startIfReady()
    }

    /** Only server system messages may reach this method, never player chat. */
    @Synchronized
    fun onServerMessage(message: String) {
        if (!ServerLoginConfirmation.isSuccessful(message)) return
        authenticated = true
        startIfReady()
    }

    private fun startIfReady() {
        if (started || !worldReady || !authenticated) return
        started = true
        if (spectator) return
        attempt = scope.launch {
            try {
                sendCommand("gamemode spectator")
                delay(confirmationTimeoutMillis)
                if (!isSpectator()) sendCommand("vanish")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A closed socket must not crash the app or trigger another toggle.
            }
        }
    }

    @Synchronized
    fun onGameMode(mode: Int) {
        spectator = mode == 3
        if (spectator) attempt?.cancel()
    }

    @Synchronized
    private fun isSpectator() = spectator

    @Synchronized
    fun reset() {
        attempt?.cancel()
        attempt = null
        started = false
        worldReady = false
        authenticated = false
        spectator = false
    }
}
