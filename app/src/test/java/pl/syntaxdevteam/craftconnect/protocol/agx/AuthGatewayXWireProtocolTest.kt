package pl.syntaxdevteam.craftconnect.protocol.agx

import java.util.Base64
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthGatewayXWireProtocolTest {
    @Test
    fun clientHelloMatchesAuthGatewayXProtocolV1Vector() {
        val frame = AuthGatewayXFrame(
            UUID.fromString("12345678-1234-5678-1234-567812345678"),
            AuthGatewayXMessage.ClientHello("0.1.0", null),
        )

        assertEquals(
            "QUdYQwEBEjRWeBI0VngSNFZ4EjRWeAAAAAUwLjEuMAA=",
            Base64.getEncoder().encodeToString(AuthGatewayXWireProtocol.encode(frame)),
        )
    }

    @Test
    fun capabilityIdsMapToProviderNeutralFeatures() {
        assertEquals(ServerCapability.CONSOLE_EXECUTE, AuthGatewayXCapability.CONSOLE_EXECUTE.localCapability)
        assertEquals(ServerCapability.SERVER_STATS, AuthGatewayXCapability.STATS.localCapability)
        assertTrue(AuthGatewayXCapability.fromWireId("future.capability") == null)
    }
}
