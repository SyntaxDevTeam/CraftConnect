package pl.syntaxdevteam.craftconnect.protocol.legacy

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

                    output.write(frame(packet {
                        writeVarInt(0)
                        writeInt(123_456)
                    }, null))
                    output.flush()
                    val keepAlive = readPacket(input, null)
                    assertEquals(0, keepAlive.readVarInt())
                    assertEquals(123_456, keepAlive.readInt())
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
            withTimeout(2_000) { server.await() }
            withContext(Dispatchers.IO) { connection.disconnect() }
        }
    }
}
