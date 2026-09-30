package pl.syntaxdevteam.craftconnect.protocol.modern

import java.net.InetAddress
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MinecraftServerAddressResolverTest {
    @Test
    fun usesSrvTargetForAddressWithoutExplicitPort() {
        var query = ""
        val resolver = MinecraftServerAddressResolver(
            lookupSrv = {
                query = it
                listOf(SrvTarget("minecraft.example.net.", 23_457, priority = 1, weight = 10))
            },
            lookupAddresses = { listOf(InetAddress.getByAddress(byteArrayOf(10, 0, 0, 1))) },
        )

        val endpoint = resolver.resolve("play.example.com")

        assertEquals("_minecraft._tcp.play.example.com", query)
        assertEquals("minecraft.example.net", endpoint.connectionHost)
        assertEquals(listOf("10.0.0.1"), endpoint.connectionAddresses.map(InetAddress::getHostAddress))
        assertEquals(23_457, endpoint.connectionPort)
        assertEquals("play.example.com", endpoint.handshakeHost)
        assertEquals(25_565, endpoint.handshakePort)
    }

    @Test
    fun explicitPortBypassesSrvLookup() {
        val resolver = MinecraftServerAddressResolver(lookupSrv = { error("SRV must not be queried") })

        val endpoint = resolver.resolve("play.example.com:25566")

        assertEquals("play.example.com", endpoint.connectionHost)
        assertEquals(25_566, endpoint.connectionPort)
        assertEquals(endpoint.connectionHost, endpoint.handshakeHost)
        assertEquals(endpoint.connectionPort, endpoint.handshakePort)
    }

    @Test
    fun failedSrvLookupFallsBackToDefaultPort() {
        val resolver = MinecraftServerAddressResolver(lookupSrv = { throw IllegalStateException("DNS unavailable") })

        val endpoint = resolver.resolve("play.example.com")

        assertEquals("play.example.com", endpoint.connectionHost)
        assertEquals(25_565, endpoint.connectionPort)
    }

    @Test
    fun selectsOnlyTargetsWithBestPriority() {
        val resolver = MinecraftServerAddressResolver(
            lookupSrv = {
                listOf(
                    SrvTarget("preferred.example.", 20_001, priority = 0, weight = 0),
                    SrvTarget("backup.example.", 20_002, priority = 1, weight = 100),
                )
            },
            lookupAddresses = { emptyList() },
            random = Random(1),
        )

        val endpoints = List(20) { resolver.resolve("play.example.com") }

        assertTrue(endpoints.all { it.connectionHost == "preferred.example" && it.connectionPort == 20_001 })
    }
}
