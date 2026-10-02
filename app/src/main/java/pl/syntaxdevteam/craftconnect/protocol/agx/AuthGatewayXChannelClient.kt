package pl.syntaxdevteam.craftconnect.protocol.agx

import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pl.syntaxdevteam.craftconnect.domain.integration.AuthGatewayXPairingChallenge
import pl.syntaxdevteam.craftconnect.domain.integration.AuthGatewayXPairingSignaturePayload
import pl.syntaxdevteam.craftconnect.domain.integration.CraftConnectDeviceKeyStore
import pl.syntaxdevteam.craftconnect.domain.integration.ServerCapability

fun interface AuthGatewayXPayloadSender {
    suspend fun send(channel: String, payload: ByteArray)
}

data class AuthGatewayXServerInfo(
    val serverId: String,
    val serverName: String,
    val supported: Set<ServerCapability>,
)

enum class AuthGatewayXPairingState {
    IDLE,
    REQUESTING,
    SIGNING_CHALLENGE,
    AWAITING_PLAYER_APPROVAL,
    PAIRED,
    FAILED,
}

/**
 * Transport-independent state machine for the native AuthGatewayX plugin channel.
 * Minecraft packet adapters only have to register the channel, send payload bytes
 * and route inbound bytes here.
 */
class AuthGatewayXChannelClient(
    private val appVersion: String,
    private val deviceKeys: CraftConnectDeviceKeyStore,
    private val now: () -> Instant = Instant::now,
) {
    private val mutableServerInfo = MutableStateFlow<AuthGatewayXServerInfo?>(null)
    val serverInfo: StateFlow<AuthGatewayXServerInfo?> = mutableServerInfo

    private val mutableCapabilities = MutableStateFlow<Set<ServerCapability>>(emptySet())
    val capabilities: StateFlow<Set<ServerCapability>> = mutableCapabilities

    private val mutablePairingState = MutableStateFlow(AuthGatewayXPairingState.IDLE)
    val pairingState: StateFlow<AuthGatewayXPairingState> = mutablePairingState

    private var helloRequestId: UUID? = null
    private var pairingRequestId: UUID? = null
    private var pairingPlayerUuid: UUID? = null
    private var pairingDeviceId: String? = null

    suspend fun createHelloPayload(): ByteArray {
        val identity = deviceKeys.identity()
        val requestId = UUID.randomUUID().also { helloRequestId = it }
        return AuthGatewayXWireProtocol.encode(
            AuthGatewayXFrame(
                requestId,
                AuthGatewayXMessage.ClientHello(appVersion, identity.deviceId),
            ),
        )
    }

    suspend fun beginPairing(playerUuid: UUID, sender: AuthGatewayXPayloadSender) {
        val info = mutableServerInfo.value ?: throw IllegalStateException("AuthGatewayX server was not discovered")
        require(ServerCapability.PAIRING in info.supported) { "AuthGatewayX server does not support pairing" }
        require(ServerCapability.PAIRING in mutableCapabilities.value) { "Player is not allowed to pair CraftConnect" }

        val identity = deviceKeys.identity()
        val requestId = UUID.randomUUID()
        pairingRequestId = requestId
        pairingPlayerUuid = playerUuid
        pairingDeviceId = identity.deviceId
        mutablePairingState.value = AuthGatewayXPairingState.REQUESTING
        sender.send(
            AuthGatewayXProtocol.CHANNEL,
            AuthGatewayXWireProtocol.encode(
                AuthGatewayXFrame(
                    requestId,
                    AuthGatewayXMessage.PairingBegin(identity.deviceId, identity.publicKey),
                ),
            ),
        )
    }

    suspend fun onPayload(payload: ByteArray, sender: AuthGatewayXPayloadSender) {
        val frame = AuthGatewayXWireProtocol.decode(payload)
        when (val message = frame.message) {
            is AuthGatewayXMessage.ServerHello -> handleServerHello(frame.requestId, message)
            is AuthGatewayXMessage.Capabilities -> handleCapabilities(frame.requestId, message)
            is AuthGatewayXMessage.PairingChallenge -> handlePairingChallenge(frame.requestId, message, sender)
            is AuthGatewayXMessage.PairingResult -> handlePairingResult(frame.requestId, message)
            is AuthGatewayXMessage.Error -> handleError(frame.requestId)
            else -> Unit
        }
    }

    fun reset() {
        helloRequestId = null
        pairingRequestId = null
        pairingPlayerUuid = null
        pairingDeviceId = null
        mutableServerInfo.value = null
        mutableCapabilities.value = emptySet()
        mutablePairingState.value = AuthGatewayXPairingState.IDLE
    }

    private fun handleServerHello(requestId: UUID, message: AuthGatewayXMessage.ServerHello) {
        if (requestId != helloRequestId) return
        mutableServerInfo.value = AuthGatewayXServerInfo(
            serverId = message.serverId,
            serverName = message.serverName,
            supported = message.supported.mapTo(linkedSetOf()) { it.localCapability },
        )
    }

    private fun handleCapabilities(requestId: UUID, message: AuthGatewayXMessage.Capabilities) {
        if (requestId != helloRequestId && requestId != pairingRequestId) return
        mutableCapabilities.value = message.granted.mapTo(linkedSetOf()) { it.localCapability }
    }

    private suspend fun handlePairingChallenge(
        requestId: UUID,
        message: AuthGatewayXMessage.PairingChallenge,
        sender: AuthGatewayXPayloadSender,
    ) {
        if (requestId != pairingRequestId) return
        val playerUuid = pairingPlayerUuid ?: return failPairing()
        val deviceId = pairingDeviceId ?: return failPairing()
        val discoveredServerId = mutableServerInfo.value?.serverId ?: return failPairing()
        if (message.serverId != discoveredServerId || !message.expiresAt.isAfter(now())) return failPairing()
        if (message.nonce.size !in 16..256) return failPairing()

        mutablePairingState.value = AuthGatewayXPairingState.SIGNING_CHALLENGE
        val signature = runCatching {
            deviceKeys.sign(
                AuthGatewayXPairingSignaturePayload.encode(
                    AuthGatewayXPairingChallenge(
                        challengeId = message.challengeId,
                        serverId = message.serverId,
                        playerUuid = playerUuid,
                        deviceId = deviceId,
                        nonce = message.nonce,
                        expiresAt = message.expiresAt,
                    ),
                ),
            )
        }.getOrElse {
            failPairing()
            return
        }
        sender.send(
            AuthGatewayXProtocol.CHANNEL,
            AuthGatewayXWireProtocol.encode(
                AuthGatewayXFrame(
                    requestId,
                    AuthGatewayXMessage.PairingConfirm(message.challengeId, signature),
                ),
            ),
        )
    }

    private fun handlePairingResult(requestId: UUID, message: AuthGatewayXMessage.PairingResult) {
        if (requestId != pairingRequestId) return
        mutablePairingState.value = when {
            message.paired -> AuthGatewayXPairingState.PAIRED
            message.errorCode == "player_approval_required" -> AuthGatewayXPairingState.AWAITING_PLAYER_APPROVAL
            else -> AuthGatewayXPairingState.FAILED
        }
    }

    private fun handleError(requestId: UUID) {
        if (requestId == pairingRequestId) failPairing()
    }

    private fun failPairing() {
        mutablePairingState.value = AuthGatewayXPairingState.FAILED
    }
}
