package pl.syntaxdevteam.craftconnect.protocol.legacy

import pl.syntaxdevteam.craftconnect.bridge.protocol.AuthenticationBridgeProtocol
import java.net.ServerSocket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile

class LegacyOfflineMinecraftConnectionTest {
    @Test
    fun logsInAndRepliesToKeepAlive() = runBlocking {
        ServerSocket(0).use { serverSocket ->
            val server = async(Dispatchers.IO) {
                serverSocket.accept().use { client ->
                    client.soTimeout = 2_000
                    val input = client.getInputStream()
                    val output = client.getOutputStream()
                    val handshake = readPacket(input, null)
                    assertEquals(0, handshake.readVarInt())
                    assertEquals(47, handshake.readVarInt())
                    handshake.readProtocolString()
                    handshake.readUnsignedShort()
                    assertEquals(2, handshake.readVarInt())

                    val login = readPacket(input, null)
                    assertEquals(0, login.readVarInt())
                    assertEquals("OfflineUser", login.readProtocolString(16))
                    output.write(frame(packet {
                        writeVarInt(2)
                        writeProtocolString("01234567-89ab-cdef-0123-456789abcdef")
                        writeProtocolString("OfflineUser")
                    }, null))
                    output.flush()

                    val settings = readPacket(input, null)
                    assertEquals(0x15, settings.readVarInt())
                    assertEquals("en_US", settings.readProtocolString())
                    val brand = readPacket(input, null)
                    assertEquals(0x17, brand.readVarInt())
                    assertEquals("MC|Brand", brand.readProtocolString())
                    assertEquals("CraftConnect", brand.readProtocolString())

                    output.write(frame(packet {
                        writeVarInt(0x02); writeProtocolString("\"Welcome before UI\""); writeByte(0)
                    }, null))
                    output.write(frame(packet {
                        writeVarInt(0)
                        writeVarInt(123_456)
                    }, null))
                    output.flush()
                    val keepAlive = readPacket(input, null)
                    assertEquals(0, keepAlive.readVarInt())
                    assertEquals(123_456, keepAlive.readVarInt())
                    // No visibility command is sent at protocol login. Only after
                    // world readiness and an API-backed bridge confirmation.
                    output.write(frame(packet {
                        writeVarInt(0x08)
                        writeDouble(1.0); writeDouble(64.0); writeDouble(2.0)
                        writeFloat(0f); writeFloat(0f)
                        writeByte(0)
                    }, null))
                    output.flush()
                    assertEquals(0x06, readPacket(input, null).readVarInt())
                    val registration = readPacket(input, null)
                    assertEquals(0x17, registration.readVarInt())
                    assertEquals("REGISTER", registration.readProtocolString())
                    val subscription = readPacket(input, null)
                    assertEquals(0x17, subscription.readVarInt())
                    assertEquals(AuthenticationBridgeProtocol.CHANNEL, subscription.readProtocolString())
                    val nonce = AuthenticationBridgeProtocol.subscriptionNonce(
                        ByteArray(subscription.available()).also(subscription::readFully))
                    output.write(frame(packet {
                        writeVarInt(0x3F)
                        writeProtocolString(AuthenticationBridgeProtocol.CHANNEL)
                        write(AuthenticationBridgeProtocol.authenticated(nonce, setOf("AuthMe")))
                    }, null))
                    output.flush()
                    val command = readPacket(input, null)
                    assertEquals(0x01, command.readVarInt())
                    assertEquals("/gamemode spectator", command.readProtocolString())
                    output.write(frame(packet { writeVarInt(0x2B); writeByte(3); writeFloat(3f) }, null))
                    output.flush()

                }
            }
            val connection = LegacyOfflineMinecraftConnection(connectTimeoutMillis = 2_000)
            val profile = ServerProfile(
                id = "test",
                name = "Test",
                address = "127.0.0.1:${serverSocket.localPort}",
                online = false,
                playersOnline = 0,
                playersMax = 0,
                pingMs = null,
            )

            val session = connection.connect(profile, "OfflineUser")

            assertEquals(47, session.protocolVersion)
            assertEquals("OfflineUser", session.username)
            assertEquals("01234567-89ab-cdef-0123-456789abcdef", session.uuid)
            withTimeout(5_000) { server.await() }
            withContext(Dispatchers.IO) { connection.disconnect() }
        }
    }
}

