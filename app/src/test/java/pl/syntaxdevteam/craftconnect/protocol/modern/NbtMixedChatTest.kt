package pl.syntaxdevteam.craftconnect.protocol.modern

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.craftconnect.protocol.legacy.packet
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeVarInt
import pl.syntaxdevteam.craftconnect.protocol.chat.ChatHistory
import pl.syntaxdevteam.craftconnect.ui.components.minecraftChatText

class NbtMixedChatTest {
    private fun DataOutputStream.field(name: String, text: String) {
        writeByte(8); writeUTF(name); writeUTF(text)
    }
    private fun DataOutputStream.mixedChat() {
        writeByte(10)
        field("text", "")
        writeByte(9); writeUTF("extra"); writeByte(10); writeInt(5)
        field("text", "[VIP]"); field("color", "gold"); writeByte(0)
        field("", " "); writeByte(0) // wrapped collapsed literal in a mixed list
        field("text", "Adrian"); field("color", "green"); writeByte(0)
        field("", ": "); writeByte(0)
        field("", "Treść wiadomości 🐴"); writeByte(0)
        writeByte(0)
    }
    private fun input(bytes: ByteArray) = DataInputStream(ByteArrayInputStream(bytes))

    @Test fun mixedWireComponentsRetainSpacesAndMessageThroughHistoryAndUi() {
        val data = packet { mixedChat(); writeBoolean(false) }
        val formatted = input(data).readSystemChat(formatted = true)!!
        val history = ChatHistory { 42L }
        history.append(formatted)
        val message = history.messages.value.single()
        assertEquals("[VIP] Adrian: Treść wiadomości 🐴", message.content)
        val rendered = minecraftChatText(message.formattedContent)
        assertEquals(message.content, rendered.text)
        assertTrue(rendered.spanStyles.size >= 3)
        assertEquals(message.content, input(data).readSystemChat())
    }

    @Test fun wrappedTranslationArgumentsAndRootListsRetainContent() {
        val data = packet {
            writeByte(9); writeByte(10); writeInt(2)
            field("", "prefix "); writeByte(0)
            field("translate", "chat.type.text")
            writeByte(9); writeUTF("with"); writeByte(10); writeInt(2)
            field("text", "Adrian"); field("color", "green"); writeByte(0)
            field("", "hello"); writeByte(0)
            writeByte(0)
        }
        val component = input(data).readAnonymousNbt()
        assertEquals("prefix Adrian: hello", component.chatText())
        assertEquals(component.chatText(), minecraftChatText(component.chatFormattedText()).text)
    }

    @Test fun onlyListSingletonWrappersAreUnwrappedAndOnlyOneLayer() {
        val root = input(packet { writeByte(10); field("", "literal"); writeByte(0) }).readAnonymousNbt()
        assertTrue(root is NbtTag.CompoundTag)
        val data = packet {
            writeByte(9); writeByte(10); writeInt(2)
            field("", "metadata"); field("text", "real"); writeByte(0)
            writeByte(10); writeUTF(""); field("", "nested"); writeByte(0); writeByte(0)
        }
        val list = input(data).readAnonymousNbt() as NbtTag.ListTag
        assertEquals("real", list.value.first().chatText())
        assertTrue(list.value.last() is NbtTag.CompoundTag)
    }

    @Test fun profilelessRawChatKeepsEntireMixedMessage() {
        val types = ServerChatTypes()
        types.readRegistry(input(packet {
            writeProtocolString("minecraft:chat_type")
            writeVarInt(1)
            writeProtocolString("paper:raw")
            writeBoolean(false)
        }))
        val data = packet {
            mixedChat(); writeVarInt(1); writeByte(8); writeUTF("Adrian"); writeBoolean(false)
        }
        assertEquals("[VIP] Adrian: Treść wiadomości 🐴", minecraftChatText(input(data).readProfilelessChat(types, true)).text)
    }
}
