package pl.syntaxdevteam.craftconnect.protocol.modern

import pl.syntaxdevteam.craftconnect.bridge.protocol.AuthenticationBridgeProtocol
import java.net.ServerSocket
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue
import pl.syntaxdevteam.craftconnect.protocol.MinecraftConnectionException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile
import pl.syntaxdevteam.craftconnect.domain.model.MinecraftVersion
import pl.syntaxdevteam.craftconnect.protocol.legacy.frame
import pl.syntaxdevteam.craftconnect.protocol.legacy.packet
import pl.syntaxdevteam.craftconnect.protocol.legacy.readPacket
import pl.syntaxdevteam.craftconnect.protocol.legacy.readProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.readVarInt
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeVarInt

class ModernOfflineMinecraftConnectionTest {
    @Test
    fun completesLoginConfigurationAndPlayKeepAlive() = verifyVersion(MinecraftVersion.JAVA_26_1)

    @Test fun connectsWith26_2() = verifyVersion(MinecraftVersion.JAVA_26_2)
    @Test fun connectsWith26_3() = verifyVersion(MinecraftVersion.JAVA_26_3)

    @Test fun configurationRequestsWithCompression() = verifyVersion(MinecraftVersion.JAVA_26_2, compressed = true)

    private fun verifyVersion(version: MinecraftVersion, compressed: Boolean = false) = runBlocking {
        val compressionThreshold = if (compressed) 256 else null
        val latest = version == MinecraftVersion.JAVA_26_3
        ServerSocket(0).use { serverSocket ->
            val server = async(Dispatchers.IO) {
                serverSocket.accept().use { client ->
                    client.soTimeout = 2_000
                    val input = client.getInputStream()
                    val output = client.getOutputStream()
                    readPacket(input, null).also {
                        assertEquals(0, it.readVarInt())
                        assertEquals(version.protocol, it.readVarInt())
                        it.readProtocolString()
                        it.readUnsignedShort()
                        assertEquals(2, it.readVarInt())
                    }
                    val login = readPacket(input, null)
                    assertEquals(0, login.readVarInt())
                    assertEquals("OfflineUser", login.readProtocolString())
                    val uuid = UUID(login.readLong(), login.readLong())

                    output.write(frame(packet { writeVarInt(5); writeProtocolString("test:missing") }, null))
                    output.flush()
                    val loginCookie = readPacket(input, null)
                    assertEquals(4, loginCookie.readVarInt())
                    assertEquals("test:missing", loginCookie.readProtocolString())
                    assertEquals(false, loginCookie.readBoolean())

                    if (compressed) {
                        output.write(frame(packet { writeVarInt(3); writeVarInt(256) }, null))
                        output.flush()
                    }

                    output.write(frame(packet {
                        writeVarInt(2)
                        writeLong(uuid.mostSignificantBits)
                        writeLong(uuid.leastSignificantBits)
                        writeProtocolString("OfflineUser")
                        writeVarInt(0)
                        if (version.protocol >= 776) { writeLong(0); writeLong(0) }
                    }, compressionThreshold))
                    output.flush()
                    assertEquals(3, readPacket(input, compressionThreshold).readVarInt())
                    assertEquals(0, readPacket(input, compressionThreshold).readVarInt())
                    val brand = readPacket(input, compressionThreshold)
                    assertEquals(2, brand.readVarInt())
                    assertEquals("minecraft:brand", brand.readProtocolString())
                    assertEquals("CraftConnect", brand.readProtocolString())

                    // A real server waits for these replies before finishing configuration.
                    output.write(frame(packet {
                        writeVarInt(if (latest) 0x0B else 0x0A)
                        writeProtocolString("test:cookie"); writeVarInt(3); write(byteArrayOf(1, 2, 3))
                    }, compressionThreshold))
                    output.write(frame(packet { writeVarInt(0); writeProtocolString("test:cookie") }, compressionThreshold))
                    output.flush()
                    val cookie = readPacket(input, compressionThreshold)
                    assertEquals(1, cookie.readVarInt())
                    assertEquals("test:cookie", cookie.readProtocolString())
                    assertEquals(true, cookie.readBoolean())
                    assertEquals(3, cookie.readVarInt())
                    assertEquals(1, cookie.readUnsignedByte())
                    assertEquals(2, cookie.readUnsignedByte())
                    assertEquals(3, cookie.readUnsignedByte())

                    output.write(frame(packet {
                        writeVarInt(9); writeLong(123); writeLong(456)
                        writeProtocolString("https://example.invalid/pack.zip")
                        writeProtocolString(""); writeBoolean(true); writeBoolean(false)
                    }, compressionThreshold))
                    output.flush()
                    for (expected in intArrayOf(3, 4, 0)) {
                        val pack = readPacket(input, compressionThreshold)
                        assertEquals(6, pack.readVarInt())
                        assertEquals(123L, pack.readLong()); assertEquals(456L, pack.readLong())
                        assertEquals(expected, pack.readVarInt())
                        assertEquals(0, pack.available())
                    }
                    output.write(frame(packet { writeVarInt(if (latest) 0x14 else 0x13); writeProtocolString("Rules") }, compressionThreshold))
                    output.flush()
                    assertEquals(9, readPacket(input, compressionThreshold).readVarInt())

                    output.write(frame(packet {
                        writeVarInt(if (latest) 0x0F else 0x0E)
                        writeVarInt(1)
                        writeProtocolString("minecraft")
                        writeProtocolString("core")
                        writeProtocolString(version.label)
                    }, compressionThreshold))
                    output.flush()
                    val knownPacks = readPacket(input, compressionThreshold)
                    assertEquals(7, knownPacks.readVarInt())
                    assertEquals(0, knownPacks.readVarInt())
                    assertEquals(0, knownPacks.available())

                    output.write(frame(packet { writeVarInt(3) }, compressionThreshold))
                    output.flush()
                    assertEquals(3, readPacket(input, compressionThreshold).readVarInt())

                    output.write(frame(packet {
                        writeVarInt(if (latest) 0x7C else 0x79); writeByte(8); writeUTF("Welcome before UI"); writeBoolean(false)
                    }, compressionThreshold))
                    output.write(frame(packet { writeVarInt(if (latest) 0x2D else 0x2C); writeLong(987_654_321L) }, compressionThreshold))
                    output.flush()
                    val keepAlive = readPacket(input, compressionThreshold)
                    assertEquals(0x1C, keepAlive.readVarInt())
                    assertEquals(987_654_321L, keepAlive.readLong())
                    // No visibility command is sent at protocol login. Only after
                    // world readiness and an API-backed bridge confirmation.
                    output.write(frame(packet {
                        writeVarInt(if (latest) 0x49 else 0x48)
                        writeVarInt(42)
                        writeDouble(1.0); writeDouble(64.0); writeDouble(2.0)
                        repeat(3) { writeDouble(0.0) }
                        writeFloat(0f); writeFloat(0f)
                        writeInt(0)
                    }, compressionThreshold))
                    output.flush()
                    val confirmation = readPacket(input, compressionThreshold)
                    assertEquals(0, confirmation.readVarInt())
                    assertEquals(42, confirmation.readVarInt())
                    if (latest) {
                        assertEquals(1.0, confirmation.readDouble(), 0.0)
                        assertEquals(64.0, confirmation.readDouble(), 0.0)
                        assertEquals(2.0, confirmation.readDouble(), 0.0)
                        assertEquals(0f, confirmation.readFloat(), 0f)
                        assertEquals(0f, confirmation.readFloat(), 0f)
                    }
                    assertEquals(0, confirmation.available())
                    assertEquals(0x1F, readPacket(input, compressionThreshold).readVarInt())
                    val registration = readPacket(input, compressionThreshold)
                    assertEquals(0x16, registration.readVarInt())
                    assertEquals("minecraft:register", registration.readProtocolString())
                    val subscription = readPacket(input, compressionThreshold)
                    assertEquals(0x16, subscription.readVarInt())
                    assertEquals(AuthenticationBridgeProtocol.CHANNEL, subscription.readProtocolString())
                    val nonce = AuthenticationBridgeProtocol.subscriptionNonce(
                        ByteArray(subscription.available()).also(subscription::readFully))
                    output.write(frame(packet {
                        writeVarInt(0x18)
                        writeProtocolString(AuthenticationBridgeProtocol.CHANNEL)
                        write(AuthenticationBridgeProtocol.authenticated(nonce, setOf("nLogin")))
                    }, compressionThreshold))
                    output.flush()
                    val command = readPacket(input, compressionThreshold)
                    assertEquals(0x07, command.readVarInt())
                    assertEquals("gamemode spectator", command.readProtocolString())
                    output.write(frame(packet { writeVarInt(if (latest) 0x27 else 0x26); writeByte(3); writeFloat(3f) }, compressionThreshold))
                    output.flush()
                    output.write(frame(packet {
                        writeVarInt(0x20); writeByte(8); writeUTF("Server restarting")
                    }, compressionThreshold))
                    output.flush()

                }
            }
            val connection = ModernOfflineMinecraftConnection(2_000)
            val profile = ServerProfile(
                "test", "Test", "127.0.0.1:${serverSocket.localPort}", false, 0, 0, null, minecraftVersion = version,
            )

            val session = connection.connect(profile, "OfflineUser")

            assertEquals(version.protocol, session.protocolVersion)
            assertEquals("OfflineUser", session.username)
            withTimeout(5_000) { server.await() }
            assertEquals("Welcome before UI", connection.chatMessages.value.first().content)
            val failure = withTimeout(5_000) { connection.connectionFailures.first() }
            assertEquals("Server restarting", failure.serverMessage)
            assertTrue(failure.diagnosticCode.startsWith("play_disconnected_p${version.protocol}"))
            // A command queued just before disconnect reports the original reason.
            val sendFailure = runCatching { connection.sendCommand("/help") }.exceptionOrNull()
            assertTrue(sendFailure is MinecraftConnectionException)
            assertEquals("Server restarting", (sendFailure as MinecraftConnectionException).serverMessage)
            connection.disconnect()
        }
    }
}



