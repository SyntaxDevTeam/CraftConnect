package pl.syntaxdevteam.craftconnect.protocol.modern

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.syntaxdevteam.craftconnect.protocol.legacy.writeVarInt

class ServerDialogCodecTest {
    @Test
    fun parsesRegistrationDialogSentByServer() {
        val dialog = DataInputStream(ByteArrayInputStream(registrationDialog())).readServerDialog()

        assertEquals("Rejestracja", dialog.title)
        assertEquals("Utwórz hasło i wpisz je ponownie.", dialog.message)
        assertEquals("authgatewayx:register_submit", dialog.submitActionId)
        assertEquals("Potwierdź", dialog.submitLabel)
        assertEquals("authgatewayx:auth_cancel", dialog.cancelActionId)
        assertEquals(listOf("password", "repeat_password"), dialog.fields.map { it.key })
        assertTrue(dialog.fields.all { it.secret })
        assertEquals(128, dialog.fields.first().maxLength)
    }

    @Test
    fun writesLengthPrefixedUnnamedCompoundExpectedByCustomClickAction() {
        val encoded = encodeLengthPrefixedStringCompound(mapOf("password" to "hello world"))

        assertEquals(
            "1a0a08000870617373776f7264000b68656c6c6f20776f726c6400",
            encoded.joinToString("") { "%02x".format(it) },
        )
    }

    private fun registrationDialog(): ByteArray = ByteArrayOutputStream().use { bytes ->
        DataOutputStream(bytes).use { output ->
            output.writeVarInt(0)
            output.writeByte(10)
            output.compound("yes") {
                compound("action") {
                    string("type", "minecraft:dynamic/custom")
                    string("id", "authgatewayx:register_submit")
                }
                string("label", "Potwierdź")
            }
            output.compound("no") {
                compound("action") {
                    string("type", "minecraft:dynamic/custom")
                    string("id", "authgatewayx:auth_cancel")
                }
                string("label", "Wróć")
            }
            output.compound("title") { string("text", "Rejestracja") }
            output.writeByte(9)
            output.nbtString("inputs")
            output.writeByte(10)
            output.writeInt(2)
            output.textInput("password", "Hasło")
            output.textInput("repeat_password", "Powtórz hasło")
            output.compound("body") {
                compound("contents") { string("text", "Utwórz hasło i wpisz je ponownie.") }
            }
            output.writeByte(0)
        }
        bytes.toByteArray()
    }

    private fun DataOutputStream.textInput(key: String, label: String) {
        writeByte(3)
        nbtString("max_length")
        writeInt(128)
        string("key", key)
        string("label", label)
        string("type", "minecraft:text")
        writeByte(0)
    }

    private fun DataOutputStream.compound(name: String, body: DataOutputStream.() -> Unit) {
        writeByte(10)
        nbtString(name)
        body()
        writeByte(0)
    }

    private fun DataOutputStream.string(name: String, value: String) {
        writeByte(8)
        nbtString(name)
        nbtString(value)
    }

    private fun DataOutputStream.nbtString(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        writeShort(encoded.size)
        write(encoded)
    }
}
