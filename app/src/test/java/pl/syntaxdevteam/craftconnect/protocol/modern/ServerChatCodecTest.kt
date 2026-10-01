package pl.syntaxdevteam.craftconnect.protocol.modern

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.craftconnect.protocol.legacy.packet
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeVarInt

class ServerChatCodecTest {
    private fun DataOutputStream.text(value: String) { writeByte(8); writeUTF(value) }
    private fun input(bytes: ByteArray) = DataInputStream(ByteArrayInputStream(bytes))

    @Test fun systemAndActionBar() {
        assertEquals("Welcome", input(packet { text("Welcome"); writeBoolean(false) }).readSystemChat())
        assertNull(input(packet { text("Overlay"); writeBoolean(true) }).readSystemChat())
    }

    @Test fun profilelessAndComponentChildren() {
        assertEquals("Admin: Hello", input(packet {
            text("Hello"); writeVarInt(1); text("Admin"); writeBoolean(false)
        }).readProfilelessChat())
        val component = NbtTag.CompoundTag(mapOf(
            "translate" to NbtTag.StringTag("chat.type.text"),
            "with" to NbtTag.ListTag(listOf(NbtTag.StringTag("Luna"), NbtTag.StringTag("hi"))),
            "extra" to NbtTag.ListTag(listOf(NbtTag.StringTag("!"))),
        ))
        assertEquals("Luna: hi!", component.chatText())
    }

    private fun player(filter: Int = 0) = input(packet {
        writeVarInt(7); writeLong(0); writeLong(1); writeVarInt(0)
        writeBoolean(true); write(ByteArray(256))
        writeProtocolString("plain"); writeLong(0); writeLong(0)
        writeVarInt(2); writeVarInt(0); write(ByteArray(256)); writeVarInt(1)
        writeBoolean(true); text("plugin format")
        writeVarInt(filter)
        if (filter == 2) { writeVarInt(1); writeLong(1) }
        writeVarInt(1); text("Luna"); writeBoolean(false)
    })

    @Test fun signedUnsignedAndFiltering() {
        assertEquals(PlayerChat("Luna: plugin format", true), player().readPlayerChat())
        assertEquals("", player(1).readPlayerChat().text)
        assertEquals("", player(2).readPlayerChat().text)
    }

    @Test fun inlineChatTypeHolder() {
        assertEquals("Admin: Hello", input(packet {
            text("Hello"); writeVarInt(0)
            repeat(2) {
                writeProtocolString("chat.type.text"); writeVarInt(2); writeVarInt(0); writeVarInt(2)
                writeByte(10); writeByte(0)
            }
            text("Admin"); writeBoolean(false)
        }).readProfilelessChat())
    }

    @Test fun unsignedPluginContentUsesTheServerRawDecoration() {
        val types = ServerChatTypes()
        types.readRegistry(input(packet {
            writeProtocolString("minecraft:chat_type"); writeVarInt(1)
            writeProtocolString("paper:raw"); writeBoolean(false)
        }))
        assertEquals(PlayerChat("plugin format", true), player().readPlayerChat(types))
        assertEquals("", player(1).readPlayerChat(types).text)
    }

    @Test fun malformedPacketIsRejected() {
        assertTrue(runCatching { input(byteArrayOf(8, 0, 4, 65)).readSystemChat() }.isFailure)
    }
}


