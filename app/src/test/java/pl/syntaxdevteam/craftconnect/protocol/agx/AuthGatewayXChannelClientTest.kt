package pl.syntaxdevteam.craftconnect.protocol.agx

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.syntaxdevteam.craftconnect.domain.integration.AuthGatewayXPairingChallenge
import pl.syntaxdevteam.craftconnect.domain.integration.AuthGatewayXPairingSignaturePayload
import pl.syntaxdevteam.craftconnect.domain.integration.CraftConnectDeviceIdentity
import pl.syntaxdevteam.craftconnect.domain.integration.CraftConnectDeviceKeyStore
import pl.syntaxdevteam.craftconnect.domain.integration.ServerCapability

class AuthGatewayXChannelClientTest {
    @Test
    fun discoveryMapsServerCapabilitiesAndPairingRequiresApproval() = runBlocking {
        val keyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        val deviceId = "cc-test-device"
        val keyStore = object : CraftConnectDeviceKeyStore {
            override suspend fun identity() = CraftConnectDeviceIdentity(deviceId, keyPair.public.encoded)

            override suspend fun sign(payload: ByteArray): ByteArray =
                Signature.getInstance("SHA256withECDSA").run {
                    initSign(keyPair.private)
                    update(payload)
                    sign()
                }
        }
        val now = Instant.parse("2026-10-02T20:00:00Z")
        val client = AuthGatewayXChannelClient("0.1.0", keyStore) { now }
        val hello = AuthGatewayXWireProtocol.decode(client.createHelloPayload())

        client.onPayload(
            AuthGatewayXWireProtocol.encode(
                AuthGatewayXFrame(
                    hello.requestId,
                    AuthGatewayXMessage.ServerHello(
                        serverId = "server-one",
                        serverName = "Server One",
                        supported = setOf(
                            AuthGatewayXCapability.PAIRING,
                            AuthGatewayXCapability.STATUS,
                            AuthGatewayXCapability.CONSOLE_VIEW,
                        ),
                    ),
                ),
            ),
            AuthGatewayXPayloadSender { _, _ -> },
        )
        client.onPayload(
            AuthGatewayXWireProtocol.encode(
                AuthGatewayXFrame(
                    hello.requestId,
                    AuthGatewayXMessage.Capabilities(setOf(AuthGatewayXCapability.PAIRING)),
                ),
            ),
            AuthGatewayXPayloadSender { _, _ -> },
        )

        assertEquals("server-one", client.serverInfo.value?.serverId)
        assertTrue(ServerCapability.CONSOLE_VIEW in checkNotNull(client.serverInfo.value).supported)
        assertEquals(setOf(ServerCapability.PAIRING), client.capabilities.value)

        val sent = mutableListOf<ByteArray>()
        val playerUuid = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
        val sender = AuthGatewayXPayloadSender { channel, payload ->
            assertEquals(AuthGatewayXProtocol.CHANNEL, channel)
            sent += payload
        }
        client.beginPairing(playerUuid, sender)
        val begin = AuthGatewayXWireProtocol.decode(sent.single())
        val challengeId = UUID.fromString("11111111-2222-3333-4444-555555555555")
        val nonce = ByteArray(32) { (it + 1).toByte() }
        val expiry = now.plusSeconds(60)

        client.onPayload(
            AuthGatewayXWireProtocol.encode(
                AuthGatewayXFrame(
                    begin.requestId,
                    AuthGatewayXMessage.PairingChallenge(
                        challengeId = challengeId,
                        serverId = "server-one",
                        nonce = nonce,
                        expiresAt = expiry,
                    ),
                ),
            ),
            sender,
        )

        assertEquals(2, sent.size)
        val confirm = AuthGatewayXWireProtocol.decode(sent[1])
        val signature = (confirm.message as AuthGatewayXMessage.PairingConfirm).signature
        val signedPayload = AuthGatewayXPairingSignaturePayload.encode(
            AuthGatewayXPairingChallenge(
                challengeId = challengeId,
                serverId = "server-one",
                playerUuid = playerUuid,
                deviceId = deviceId,
                nonce = nonce,
                expiresAt = expiry,
            ),
        )
        assertTrue(
            Signature.getInstance("SHA256withECDSA").run {
                initVerify(keyPair.public)
                update(signedPayload)
                verify(signature)
            },
        )

        client.onPayload(
            AuthGatewayXWireProtocol.encode(
                AuthGatewayXFrame(
                    begin.requestId,
                    AuthGatewayXMessage.PairingResult(false, "player_approval_required"),
                ),
            ),
            sender,
        )
        assertEquals(AuthGatewayXPairingState.AWAITING_PLAYER_APPROVAL, client.pairingState.value)
    }

    @Test
    fun expiredOrCrossServerChallengeIsRejectedWithoutSigning() = runBlocking {
        var signCalls = 0
        val keyPair = KeyPairGenerator.getInstance("EC").run {
            initialize(ECGenParameterSpec("secp256r1"))
            generateKeyPair()
        }
        val keyStore = object : CraftConnectDeviceKeyStore {
            override suspend fun identity() = CraftConnectDeviceIdentity("cc-test-device", keyPair.public.encoded)
            override suspend fun sign(payload: ByteArray): ByteArray {
                signCalls++
                return ByteArray(1)
            }
        }
        val now = Instant.parse("2026-10-02T20:00:00Z")
        val client = AuthGatewayXChannelClient("0.1.0", keyStore) { now }
        val hello = AuthGatewayXWireProtocol.decode(client.createHelloPayload())
        val noOp = AuthGatewayXPayloadSender { _, _ -> }
        client.onPayload(
            AuthGatewayXWireProtocol.encode(
                AuthGatewayXFrame(
                    hello.requestId,
                    AuthGatewayXMessage.ServerHello(
                        "server-one",
                        "Server One",
                        setOf(AuthGatewayXCapability.PAIRING),
                    ),
                ),
            ),
            noOp,
        )
        client.onPayload(
            AuthGatewayXWireProtocol.encode(
                AuthGatewayXFrame(
                    hello.requestId,
                    AuthGatewayXMessage.Capabilities(setOf(AuthGatewayXCapability.PAIRING)),
                ),
            ),
            noOp,
        )
        val sent = mutableListOf<ByteArray>()
        val sender = AuthGatewayXPayloadSender { _, payload -> sent += payload }
        client.beginPairing(UUID.randomUUID(), sender)
        val begin = AuthGatewayXWireProtocol.decode(sent.single())

        client.onPayload(
            AuthGatewayXWireProtocol.encode(
                AuthGatewayXFrame(
                    begin.requestId,
                    AuthGatewayXMessage.PairingChallenge(
                        UUID.randomUUID(),
                        "other-server",
                        ByteArray(32),
                        now.plusSeconds(60),
                    ),
                ),
            ),
            sender,
        )

        assertEquals(0, signCalls)
        assertEquals(AuthGatewayXPairingState.FAILED, client.pairingState.value)
        assertEquals(1, sent.size)
    }
}
