package pl.syntaxdevteam.craftconnect.protocol.modern

import java.io.DataInputStream
import pl.syntaxdevteam.craftconnect.domain.model.ServerDialogField
import pl.syntaxdevteam.craftconnect.domain.model.ServerDialogRequest
import pl.syntaxdevteam.craftconnect.protocol.legacy.readVarInt

internal fun DataInputStream.readServerDialog(): ServerDialogRequest {
    require(readVarInt() == 0) { "Referenced dialogs are not supported" }
    val root = readInlineNbtCompound()
    val positive = root.compound("yes")
    val negative = root.compoundOrNull("no")
    val action = positive.compound("action")
    val cancelAction = negative?.compoundOrNull("action")
    require(action.string("type") == "minecraft:dynamic/custom") { "Unsupported dialog action" }

    val fields = root.listOrNull("inputs")?.value.orEmpty()
        .mapNotNull { it as? NbtTag.CompoundTag }
        .mapNotNull { input ->
            if (input.stringOrNull("type") != "minecraft:text") return@mapNotNull null
            val key = input.stringOrNull("key") ?: return@mapNotNull null
            ServerDialogField(
                key = key,
                label = input["label"].plainText().ifBlank { key },
                maxLength = (input["max_length"] as? NbtTag.IntTag)?.value?.coerceIn(1, 4_096) ?: 128,
                secret = key.contains("password", ignoreCase = true) ||
                    key.contains("passwd", ignoreCase = true) ||
                    key.contains("haslo", ignoreCase = true),
            )
        }
    require(fields.isNotEmpty()) { "Dialog has no supported input fields" }

    return ServerDialogRequest(
        title = root["title"].plainText(),
        message = root.compoundOrNull("body")?.get("contents").plainText().ifBlank { null },
        fields = fields,
        submitActionId = action.string("id"),
        submitLabel = positive["label"].plainText(),
        cancelActionId = cancelAction?.stringOrNull("id"),
        cancelLabel = negative?.get("label").plainText().ifBlank { null },
    )
}

private operator fun NbtTag.CompoundTag.get(key: String): NbtTag? = value[key]

private fun NbtTag.CompoundTag.compound(key: String): NbtTag.CompoundTag =
    compoundOrNull(key) ?: error("Missing NBT compound '$key'")

private fun NbtTag.CompoundTag.compoundOrNull(key: String): NbtTag.CompoundTag? =
    this[key] as? NbtTag.CompoundTag

private fun NbtTag.CompoundTag.listOrNull(key: String): NbtTag.ListTag? = this[key] as? NbtTag.ListTag

private fun NbtTag.CompoundTag.string(key: String): String =
    stringOrNull(key) ?: error("Missing NBT string '$key'")

private fun NbtTag.CompoundTag.stringOrNull(key: String): String? =
    (this[key] as? NbtTag.StringTag)?.value

internal fun NbtTag?.plainText(): String = when (this) {
    is NbtTag.StringTag -> value
    is NbtTag.CompoundTag -> {
        val direct = (value["text"] as? NbtTag.StringTag)?.value.orEmpty()
        val translated = (value["translate"] as? NbtTag.StringTag)?.value.orEmpty()
        val children = (value["extra"] as? NbtTag.ListTag)?.value.orEmpty().joinToString("") { it.plainText() }
        direct.ifBlank { translated } + children
    }
    is NbtTag.ListTag -> value.joinToString("") { it.plainText() }
    else -> ""
}
