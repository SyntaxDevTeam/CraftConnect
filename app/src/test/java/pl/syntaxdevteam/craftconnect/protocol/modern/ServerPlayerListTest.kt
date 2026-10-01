package pl.syntaxdevteam.craftconnect.protocol.modern

import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.craftconnect.protocol.legacy.packet
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeVarInt

class ServerPlayerListTest {
    private fun input(bytes: ByteArray) = DataInputStream(ByteArrayInputStream(bytes))
    private fun DataOutputStream.uuid(id: Long) { writeLong(0); writeLong(id) }
    private fun initial() = input(packet {
        writeByte(0xFF); writeVarInt(2)
        for ((id, name) in listOf(1L to "Luna", 2L to "Adrian")) {
            uuid(id); writeProtocolString(name); writeVarInt(1)
            writeProtocolString("textures"); writeProtocolString("value"); writeBoolean(true); writeProtocolString("signature")
            writeBoolean(true); uuid(3); writeLong(42)
            writeVarInt(2); write(byteArrayOf(1, 2)); writeVarInt(1); writeByte(3)
            writeVarInt(3); writeBoolean(true); writeVarInt(50)
            writeBoolean(true); writeByte(8); writeUTF("[VIP] $name")
            writeVarInt(if (id == 1L) 127 else 128); writeBoolean(true)
        }
    })

    @Test fun allActionsAndMultipleEntriesPreserveAlignment() {
        val list = ServerPlayerList()
        list.update(initial())
        assertEquals(listOf("Adrian", "Luna"), list.players.value.map { it.name })
        assertEquals("[VIP] Adrian", list.players.value.first().displayName)
        assertEquals(50, list.players.value.first().pingMs)
        assertEquals(3, list.players.value.first().gameMode)
    }

    @Test fun latencyDisplayNameUnlistingRelistingAndRemoval() {
        val list = ServerPlayerList(); list.update(initial())
        list.update(input(packet {
            writeByte(0x38); writeVarInt(1); uuid(2)
            writeBoolean(false); writeVarInt(123); writeBoolean(false)
        }))
        assertEquals(listOf("Luna"), list.players.value.map { it.name })
        list.update(input(packet { writeByte(0x08); writeVarInt(1); uuid(2); writeBoolean(true) }))
        assertEquals(123, list.players.value.first().pingMs)
        assertNull(list.players.value.first().displayName)
        list.remove(input(packet { writeVarInt(1); uuid(2) }))
        assertEquals(listOf("Luna"), list.players.value.map { it.name })
        list.clear(); assertTrue(list.players.value.isEmpty())
    }

    @Test fun unknownUpdatesDoNotInventPlayers() {
        val list = ServerPlayerList()
        list.update(input(packet { writeByte(0x18); writeVarInt(1); uuid(1); writeBoolean(true); writeVarInt(5) }))
        assertTrue(list.players.value.isEmpty())
    }

    @Test fun malformedUpdateDoesNotPartiallyApply() {
        val list = ServerPlayerList(); list.update(initial())
        val before = list.players.value
        val invalid = input(packet {
            writeByte(0x10); writeVarInt(2); uuid(1); writeVarInt(900); uuid(2)
        })
        assertTrue(runCatching { list.update(invalid) }.isFailure)
        assertEquals(before, list.players.value)
    }

    @Test fun unlistedInitialProfileIsNotExposed() {
        val list = ServerPlayerList()
        list.update(input(packet { writeByte(1); writeVarInt(1); uuid(1); writeProtocolString("Hidden"); writeVarInt(0) }))
        assertTrue(list.players.value.isEmpty())
    }
}
