package pl.syntaxdevteam.craftconnect.protocol

import java.util.UUID
import pl.syntaxdevteam.craftconnect.bridge.protocol.AuthenticationBridgeProtocol
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
    private val sendAuthenticationRequest: (suspend (ByteArray) -> Unit)? = null,
) {
    private var started = false
    private var worldReady = false
    private var authenticated = false
    private var spectator = false
    private var attempt: Job? = null
    private var request: Job? = null
    private var nonce = UUID.randomUUID().toString()

    @Synchronized
    fun onWorldReady() {
        if (worldReady) return
        worldReady = true
        if (sendAuthenticationRequest != null) request = scope.launch {
            try {
                while (!isAuthenticated()) {
                    sendAuthenticationRequest(subscriptionPayload())
                    delay(2_000)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Connection shutdown stops the subscription.
            }
        }
        startIfReady()
    }

    /** Only a proof received on the dedicated plugin channel confirms login. */
    @Synchronized
    fun onBridgeMessage(payload: ByteArray) {
        if (!AuthenticationBridgeProtocol.confirms(payload, nonce)) return
        authenticated = true
        request?.cancel()
        startIfReady()
    }

    @Synchronized
    fun subscriptionPayload(): ByteArray = AuthenticationBridgeProtocol.subscribe(nonce)

    @Synchronized
    private fun isAuthenticated() = authenticated

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
        request?.cancel()
        request = null
        nonce = UUID.randomUUID().toString()
        attempt = null
        started = false
        worldReady = false
        authenticated = false
        spectator = false
    }
}
