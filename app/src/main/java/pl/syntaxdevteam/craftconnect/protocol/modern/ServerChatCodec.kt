package pl.syntaxdevteam.craftconnect.protocol.modern

import java.io.DataInputStream
import pl.syntaxdevteam.craftconnect.protocol.legacy.readProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.readVarInt

internal data class PlayerChat(val text: String, val signed: Boolean)

internal fun DataInputStream.readSystemChat(): String? {
    val content = readAnonymousNbt().chatText()
    return if (readBoolean()) null else content // action bar is not chat history
}

internal fun DataInputStream.readProfilelessChat(): String {
    val message = readAnonymousNbt().chatText()
    readChatType()
    val name = readAnonymousNbt().chatText()
    val target = if (readBoolean()) readAnonymousNbt().chatText() else null
    return decorate(name, message, target)
}

internal fun DataInputStream.readPlayerChat(): PlayerChat {
    readVarInt() // global index, protocol 775
    readLong(); readLong() // sender UUID
    readVarInt() // sender message index
    val signed = readBoolean()
    if (signed) readFully(ByteArray(256))
    val plain = readProtocolString(256)
    readLong(); readLong() // timestamp and salt
    val previous = readVarInt()
    require(previous in 0..20)
    repeat(previous) { if (readVarInt() == 0) readFully(ByteArray(256)) }
    val unsigned = if (readBoolean()) readAnonymousNbt().chatText() else null
    val filter = readVarInt()
    require(filter in 0..2)
    if (filter == 2) {
        val words = readVarInt()
        require(words in 0..4)
        repeat(words) { readLong() }
    }
    readChatType()
    val name = readAnonymousNbt().chatText()
    val target = if (readBoolean()) readAnonymousNbt().chatText() else null
    // Never disclose content the server marked as filtered.
    return PlayerChat(if (filter == 0) decorate(name, unsigned ?: plain, target) else "", signed)
}

private fun DataInputStream.readChatType() {
    val id = readVarInt()
    require(id >= 0)
    if (id == 0) repeat(2) { // chat and narration decorations in an inline holder
        readProtocolString()
        val parameters = readVarInt()
        require(parameters in 0..3)
        repeat(parameters) { require(readVarInt() in 0..2) }
        readAnonymousNbt() // decoration style
    }
}

private fun decorate(name: String, message: String, target: String?): String =
    if (name.isBlank()) message else if (target == null) "$name: $message" else "$name → $target: $message"

internal fun NbtTag.chatText(depth: Int = 0): String {
    require(depth <= 64)
    return when (this) {
        is NbtTag.StringTag -> value
        is NbtTag.ListTag -> value.joinToString("") { it.chatText(depth + 1) }
        is NbtTag.CompoundTag -> {
            val text = (value["text"] as? NbtTag.StringTag)?.value
            val key = (value["translate"] as? NbtTag.StringTag)?.value
            val args = (value["with"] as? NbtTag.ListTag)?.value.orEmpty().map { it.chatText(depth + 1) }
            val fallback = (value["fallback"] as? NbtTag.StringTag)?.value
            val main = text ?: when (key) {
                "chat.type.text" -> args.getOrNull(0).orEmpty() + ": " + args.getOrNull(1).orEmpty()
                "chat.type.announcement" -> "[" + args.getOrNull(0).orEmpty() + "] " + args.getOrNull(1).orEmpty()
                "chat.type.emote" -> "* " + args.joinToString(" ")
                null -> ""
                else -> (fallback ?: key) + if (args.isEmpty()) "" else " " + args.joinToString(" ")
            }
            main + (value["extra"] as? NbtTag.ListTag)?.chatText(depth + 1).orEmpty()
        }
        else -> ""
    }.take(16_384)
}
