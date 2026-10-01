package pl.syntaxdevteam.craftconnect.protocol.legacy

import pl.syntaxdevteam.craftconnect.protocol.modern.NbtTag
import pl.syntaxdevteam.craftconnect.protocol.modern.chatFormattedText
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

internal fun readLegacyChat(json: String): String = legacyText(JSONTokener(json).nextValue(), 0)

private fun legacyText(value: Any?, depth: Int): String {
    require(depth <= 64)
    return when (value) {
        is String -> value
        is JSONArray -> (0 until value.length()).joinToString("") { legacyText(value.opt(it), depth + 1) }
        is JSONObject -> {
            val args = value.optJSONArray("with")?.let { array ->
                (0 until array.length()).map { legacyText(array.opt(it), depth + 1) }
            }.orEmpty()
            val key = value.optString("translate")
            val main = if (value.has("text")) value.optString("text") else when (key) {
                "chat.type.text" -> args.getOrNull(0).orEmpty() + ": " + args.getOrNull(1).orEmpty()
                "chat.type.announcement" -> "[" + args.getOrNull(0).orEmpty() + "] " + args.getOrNull(1).orEmpty()
                "chat.type.emote" -> "* " + args.joinToString(" ")
                "" -> ""
                else -> key + if (args.isEmpty()) "" else " " + args.joinToString(" ")
            }
            main + value.optJSONArray("extra")?.let { legacyText(it, depth + 1) }.orEmpty()
        }
        else -> ""
    }.take(16_384)
}


internal fun readFormattedLegacyChat(json: String): String =
    legacyComponent(JSONTokener(json).nextValue(), 0).chatFormattedText()

private fun legacyComponent(value: Any?, depth: Int): NbtTag {
    require(depth <= 64)
    return when (value) {
        is String -> NbtTag.StringTag(value)
        is Boolean -> NbtTag.ByteTag(if (value) 1 else 0)
        is JSONArray -> NbtTag.ListTag((0 until value.length()).map { legacyComponent(value.opt(it), depth + 1) })
        is JSONObject -> NbtTag.CompoundTag(value.keys().asSequence().associateWith { legacyComponent(value.opt(it), depth + 1) })
        else -> NbtTag.StringTag("")
    }
}
