package pl.syntaxdevteam.craftconnect.protocol.modern

/** Serialize component styles for the domain/UI without exposing protocol tags to Compose. */
internal fun NbtTag.chatFormattedText(depth: Int = 0, inherited: Map<String, NbtTag> = emptyMap()): String {
    require(depth <= 64)
    return when (this) {
        is NbtTag.StringTag -> inherited.formattingCodes() + value
        is NbtTag.ListTag -> {
            // Array components inherit the first component's style in Minecraft.
            val first = (value.firstOrNull() as? NbtTag.CompoundTag)?.value.orEmpty()
            val style = inherited + first.filterKeys { it in STYLE_KEYS }
            value.joinToString("") { it.chatFormattedText(depth + 1, style) }
        }
        is NbtTag.CompoundTag -> {
            val style = inherited + value.filterKeys { it in STYLE_KEYS }
            val codes = style.formattingCodes()
            val args = (value["with"] as? NbtTag.ListTag)?.value.orEmpty().map {
                it.chatFormattedText(depth + 1, style) + codes
            }
            val text = (value["text"] as? NbtTag.StringTag)?.value
            val key = (value["translate"] as? NbtTag.StringTag)?.value
            val main = text ?: key?.let {
                renderChatTranslation(it, args, (value["fallback"] as? NbtTag.StringTag)?.value)
            }.orEmpty()
            codes + main + (value["extra"] as? NbtTag.ListTag)?.value.orEmpty().joinToString("") {
                it.chatFormattedText(depth + 1, style)
            }
        }
        else -> ""
    }.take(65_536)
}

private val STYLE_KEYS = setOf("color", "bold", "italic", "underlined", "strikethrough", "obfuscated")
internal fun Map<String, NbtTag>.formattingCodes(): String = buildString {
    append("§r")
    val color = (this@formattingCodes["color"] as? NbtTag.StringTag)?.value
    val names = listOf("black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white")
    val index = names.indexOf(color)
    if (index >= 0) append("§" + index.toString(16))
    else if (color != null && Regex("#[0-9a-fA-F]{6}").matches(color)) {
        append("§x"); color.drop(1).forEach { append('§'); append(it) }
    }
    listOf("bold" to 'l', "italic" to 'o', "underlined" to 'n', "strikethrough" to 'm', "obfuscated" to 'k').forEach { (key, code) ->
        if ((this@formattingCodes[key] as? NbtTag.ByteTag)?.value?.toInt() == 1) { append('§'); append(code) }
    }
}
