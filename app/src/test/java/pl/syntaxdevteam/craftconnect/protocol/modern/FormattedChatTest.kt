package pl.syntaxdevteam.craftconnect.protocol.modern

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.craftconnect.protocol.legacy.packet
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeVarInt

class FormattedChatTest {
    @Test fun inlineWireParametersUseSenderTargetContentOrder() {
        val data = packet {
            writeByte(8); writeUTF("hello"); writeVarInt(0)
            repeat(2) {
                writeProtocolString("%1\$s: %2\$s"); writeVarInt(2)
                writeVarInt(0); writeVarInt(2) // sender, content on the wire
                writeByte(10); writeByte(0)
            }
            writeByte(8); writeUTF("Adrian"); writeBoolean(false)
        }
        assertEquals("Adrian: hello", DataInputStream(ByteArrayInputStream(data)).readProfilelessChat())
    }

    @Test fun nbtModifiedUtfPreservesSupplementaryCharactersAndNull() {
        val text = "✦ 🐴 🎉 \u0000 zażółć"
        val data = packet { writeByte(8); writeUTF(text) }
        assertEquals(text, DataInputStream(ByteArrayInputStream(data)).readAnonymousNbt().chatText())
    }

    @Test fun styleInheritanceAndExplicitFalseSurvive() {
        val component = NbtTag.CompoundTag(mapOf(
            "text" to NbtTag.StringTag("VIP "), "color" to NbtTag.StringTag("#12abef"),
            "bold" to NbtTag.ByteTag(1), "extra" to NbtTag.ListTag(listOf(
                NbtTag.CompoundTag(mapOf("text" to NbtTag.StringTag("hello"), "bold" to NbtTag.ByteTag(0))),
            )),
        ))
        assertEquals("§r§x§1§2§a§b§e§f§lVIP §r§x§1§2§a§b§e§fhello", component.chatFormattedText())
        assertEquals("VIP hello", component.chatText())
    }
}
