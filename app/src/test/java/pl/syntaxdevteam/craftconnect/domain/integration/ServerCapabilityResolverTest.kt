package pl.syntaxdevteam.craftconnect.domain.integration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCapabilityResolverTest {
    @Test
    fun minecraftProvidesOnlyBaselineFeatures() {
        val snapshot = ServerCapabilityResolver.resolve(
            minecraftConnected = true,
            rconAvailable = false,
        )

        assertTrue(snapshot.supports(ServerCapability.CHAT))
        assertTrue(snapshot.supports(ServerCapability.SERVER_STATUS))
        assertFalse(snapshot.supports(ServerCapability.CONSOLE_EXECUTE))
    }

    @Test
    fun rconAddsConsoleExecutionWithoutPretendingToBeLiveConsole() {
        val snapshot = ServerCapabilityResolver.resolve(
            minecraftConnected = true,
            rconAvailable = true,
        )

        assertEquals(CapabilityProvider.RCON, snapshot.preferredProvider(ServerCapability.CONSOLE_EXECUTE))
        assertFalse(snapshot.supports(ServerCapability.CONSOLE_VIEW))
    }

    @Test
    fun authGatewayXWinsForOverlappingCapabilities() {
        val snapshot = ServerCapabilityResolver.resolve(
            minecraftConnected = true,
            rconAvailable = true,
            authGatewayXCapabilities = setOf(
                ServerCapability.CONSOLE_EXECUTE,
                ServerCapability.CONSOLE_VIEW,
                ServerCapability.SERVER_STATS,
            ),
        )

        assertEquals(CapabilityProvider.AUTH_GATEWAY_X, snapshot.preferredProvider(ServerCapability.CONSOLE_EXECUTE))
        assertEquals(CapabilityProvider.AUTH_GATEWAY_X, snapshot.preferredProvider(ServerCapability.CONSOLE_VIEW))
        assertEquals(
            setOf(CapabilityProvider.RCON, CapabilityProvider.AUTH_GATEWAY_X),
            snapshot.providersFor(ServerCapability.CONSOLE_EXECUTE),
        )
    }
}
