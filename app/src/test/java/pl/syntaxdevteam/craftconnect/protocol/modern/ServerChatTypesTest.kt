package pl.syntaxdevteam.craftconnect.protocol.modern

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.craftconnect.protocol.legacy.packet
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeVarInt

class ServerChatTypesTest {
    private fun input(bytes: ByteArray) = DataInputStream(ByteArrayInputStream(bytes))

    @Test fun serverRegistryRawChatDoesNotAddTheNicknameAgain() {
        val types = ServerChatTypes()
        types.readRegistry(input(packet {
            writeProtocolString("minecraft:chat_type"); writeVarInt(1)
            writeProtocolString("paper:raw"); writeBoolean(true)
            writeByte(10) // root
            writeByte(10); writeUTF("chat")
            writeByte(8); writeUTF("translation_key"); writeUTF("%s")
            writeByte(9); writeUTF("parameters"); writeByte(8); writeInt(1); writeUTF("content")
            writeByte(0); writeByte(0)
        }))
        val chat = input(packet {
            writeByte(8); writeUTF("[VIP] Adrian: hello")
            writeVarInt(1); writeByte(8); writeUTF("Adrian"); writeBoolean(false)
        }).readProfilelessChat(types)
        assertEquals("[VIP] Adrian: hello", chat)
    }

    @Test fun inlineContentOnlyDecorationAndParameterOrder() {
        val chat = input(packet {
            writeByte(8); writeUTF("Adrian: hello"); writeVarInt(0)
            repeat(2) {
                writeProtocolString("%s"); writeVarInt(1); writeVarInt(2)
                writeByte(10); writeByte(0)
            }
            writeByte(8); writeUTF("Adrian"); writeBoolean(false)
        }).readProfilelessChat()
        assertEquals("Adrian: hello", chat)
        assertEquals("hello / Adrian", ChatDecoration("%1\$s / %2\$s", listOf(0, 1)).render("Adrian", "hello", null))
    }

    @Test fun componentFallbackFormatsArgumentsInsteadOfRepeatingThem() {
        val component = NbtTag.CompoundTag(mapOf(
            "translate" to NbtTag.StringTag("plugin.chat"),
            "fallback" to NbtTag.StringTag("<%1\$s> %2\$s %%"),
            "with" to NbtTag.ListTag(listOf(NbtTag.StringTag("Adrian"), NbtTag.StringTag("hello"))),
        ))
        assertEquals("<Adrian> hello %", component.chatText())
    }

    @Test fun resetDoesNotReusePreviousServersChatRegistry() {
        val types = ServerChatTypes()
        types.readRegistry(input(packet {
            writeProtocolString("minecraft:chat_type"); writeVarInt(1)
            writeProtocolString("paper:raw"); writeBoolean(false)
        }))
        assertEquals("hello", types.readHolder(input(packet { writeVarInt(1) })).render("Adrian", "hello", null))
        types.clear()
        assertEquals("Adrian: hello", types.readHolder(input(packet { writeVarInt(1) })).render("Adrian", "hello", null))
    }
}

