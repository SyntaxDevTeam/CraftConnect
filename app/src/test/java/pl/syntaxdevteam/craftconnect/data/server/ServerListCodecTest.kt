package pl.syntaxdevteam.craftconnect.data.server

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.syntaxdevteam.craftconnect.domain.model.MinecraftVersion
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile

class ServerListCodecTest {
    @Test fun preservesEverySelectedVersion() {
        val servers = MinecraftVersion.entries.map {
            ServerProfile(it.name, "Test", "localhost", false, 0, 0, null, minecraftVersion = it)
        }
        assertEquals(servers, ServerListCodec.decode(ServerListCodec.encode(servers)))
    }

    @Test fun migratesOldListsWithoutChangingProtocol() {
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use {
            it.writeInt(1); it.writeInt(1)
            it.writeUTF("old"); it.writeUTF("Test"); it.writeUTF("localhost"); it.writeBoolean(true)
        }
        val server = ServerListCodec.decode(Base64.getEncoder().encodeToString(buffer.toByteArray())).single()
        assertEquals(MinecraftVersion.JAVA_26_1, server.minecraftVersion)
        assertEquals(true, server.favorite)
        assertEquals("localhost", server.address)
    }
}
