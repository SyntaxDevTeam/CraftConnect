package pl.syntaxdevteam.craftconnect.data.server

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import pl.syntaxdevteam.craftconnect.domain.model.MinecraftVersion
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile

class ServerListCodecTest {
    @Test
    fun roundTripKeepsEditableFieldsAndResetsRuntimeStatus() {
        val source = ServerProfile(
            id = "one",
            name = "One",
            address = "one.example.net:25565",
            online = true,
            playersOnline = 12,
            playersMax = 100,
            pingMs = 42,
            favorite = true,
        )

        val decoded = ServerListCodec.decode(ServerListCodec.encode(listOf(source))).single()

        assertEquals(source.id, decoded.id)
        assertEquals(source.name, decoded.name)
        assertEquals(source.address, decoded.address)
        assertEquals(source.favorite, decoded.favorite)
        assertEquals(source.minecraftVersion, decoded.minecraftVersion)
        assertFalse(decoded.online)
        assertEquals(0, decoded.playersOnline)
        assertEquals(0, decoded.playersMax)
        assertNull(decoded.pingMs)
    }

    @Test
    fun preservesEverySelectedVersion() {
        val servers = MinecraftVersion.entries.map {
            ServerProfile(it.name, "Test", "localhost", false, 0, 0, null, minecraftVersion = it)
        }

        assertEquals(servers, ServerListCodec.decode(ServerListCodec.encode(servers)))
    }

    @Test
    fun migratesOldListsWithoutChangingProtocol() {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use {
            it.writeInt(1)
            it.writeInt(1)
            it.writeUTF("old")
            it.writeUTF("Test")
            it.writeUTF("localhost")
            it.writeBoolean(true)
        }

        val server = ServerListCodec.decode(Base64.getEncoder().encodeToString(buffer.toByteArray())).single()

        assertEquals(MinecraftVersion.JAVA_26_1, server.minecraftVersion)
        assertEquals(true, server.favorite)
        assertEquals("localhost", server.address)
    }

    @Test
    fun corruptedDataProducesEmptyList() {
        assertEquals(emptyList<ServerProfile>(), ServerListCodec.decode("not-base64"))
    }
}
