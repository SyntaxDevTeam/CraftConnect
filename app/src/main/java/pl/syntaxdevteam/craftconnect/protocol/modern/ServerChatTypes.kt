package pl.syntaxdevteam.craftconnect.protocol.modern

import java.io.DataInputStream
import pl.syntaxdevteam.craftconnect.protocol.legacy.readProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.readVarInt

internal data class ChatDecoration(val translationKey: String, val parameters: List<Int>) {
    fun render(name: String, message: String, target: String?): String = renderChatTranslation(
        translationKey,
        parameters.map { when (it) { 0 -> message; 1 -> name; else -> target.orEmpty() } },
    )
}

/** Server registry IDs are connection-specific, including Paper's content-only raw type. */
internal class ServerChatTypes {
    private val decorations = mutableMapOf<Int, ChatDecoration>()
    fun clear() = decorations.clear()

    fun readRegistry(input: DataInputStream) {
        if (input.readProtocolString() != "minecraft:chat_type") return
        val count = input.readVarInt().also { require(it in 0..1_024) }
        val received = mutableMapOf<Int, ChatDecoration>()
        repeat(count) { id ->
            val key = input.readProtocolString()
            val data = if (input.readBoolean()) input.readAnonymousNbt() as? NbtTag.CompoundTag else null
            val chat = data?.value?.get("chat") as? NbtTag.CompoundTag
            val translation = (chat?.value?.get("translation_key") as? NbtTag.StringTag)?.value
            val parameters = (chat?.value?.get("parameters") as? NbtTag.ListTag)?.value?.map {
                when ((it as? NbtTag.StringTag)?.value) {
                    "content" -> 0; "sender" -> 1; "target" -> 2; else -> error("Invalid chat parameter")
                }
            }
            received[id] = if (translation != null && parameters != null) {
                require(parameters.size <= 3)
                ChatDecoration(translation, parameters)
            } else fallback(key)
        }
        require(input.available() == 0)
        decorations.clear(); decorations.putAll(received)
    }

    fun readHolder(input: DataInputStream): ChatDecoration {
        val id = input.readVarInt().also { require(it >= 0) }
        if (id != 0) return decorations[id - 1] ?: ChatDecoration("chat.type.text", listOf(1, 0))
        val chat = readInlineDecoration(input)
        readInlineDecoration(input) // narration is not displayed
        return chat
    }

    private fun readInlineDecoration(input: DataInputStream): ChatDecoration {
        val key = input.readProtocolString()
        val parameters = List(input.readVarInt().also { require(it in 0..3) }) {
            input.readVarInt().also { require(it in 0..2) }
        }
        input.readAnonymousNbt() // style
        return ChatDecoration(key, parameters)
    }

    private fun fallback(key: String): ChatDecoration = when (key) {
        "paper:raw", "minecraft:raw" -> ChatDecoration("%s", listOf(0))
        "minecraft:msg_command_incoming" -> ChatDecoration("commands.message.display.incoming", listOf(1, 0))
        "minecraft:msg_command_outgoing" -> ChatDecoration("commands.message.display.outgoing", listOf(2, 0))
        "minecraft:emote_command" -> ChatDecoration("chat.type.emote", listOf(1, 0))
        "minecraft:say_command" -> ChatDecoration("chat.type.announcement", listOf(1, 0))
        else -> ChatDecoration("chat.type.text", listOf(1, 0))
    }
}

/** Minecraft supports %s, %1$s and %% rather than Java's complete formatting language. */
internal fun renderChatTranslation(key: String, args: List<String>, fallback: String? = null): String {
    val template = fallback ?: when (key) {
        "chat.type.text" -> "%1\$s: %2\$s"
        "chat.type.announcement" -> "[%1\$s] %2\$s"
        "chat.type.emote" -> "* %1\$s %2\$s"
        "commands.message.display.incoming" -> "%1\$s → %2\$s"
        "commands.message.display.outgoing" -> "→ %1\$s: %2\$s"
        else -> key
    }
    if (!template.contains('%')) return template + if (args.isEmpty()) "" else " " + args.joinToString(" ")
    var next = 0
    return Regex("%(?:(\\d+)\\$)?s|%%").replace(template) { match ->
        if (match.value == "%%") "%" else {
            val index = match.groupValues[1].toIntOrNull()?.minus(1) ?: next++
            args.getOrNull(index).orEmpty()
        }
    }.take(16_384)
}
