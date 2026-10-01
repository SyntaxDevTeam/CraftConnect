package pl.syntaxdevteam.craftconnect.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration

/** Preserve server formatting and Android's Unicode font fallback. */
internal fun minecraftChatText(value: String): AnnotatedString = buildAnnotatedString {
    val palette = listOf(0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF)
    var style = SpanStyle()
    var start = 0
    var index = 0
    fun flush(end: Int) {
        if (end > start) {
            val offset = length
            append(value.substring(start, end))
            addStyle(style, offset, length)
        }
    }
    while (index + 1 < value.length) {
        if (value[index] != '§') { index++; continue }
        val code = value[index + 1].lowercaseChar()
        var consumed = 2
        val rgb = if (code == 'x' && index + 14 <= value.length) {
            val sequence = value.substring(index + 2, index + 14)
            if ((0..5).all { sequence[it * 2] == '§' && sequence[it * 2 + 1].digitToIntOrNull(16) != null })
                (0..5).map { sequence[it * 2 + 1] }.joinToString("").toIntOrNull(16) else null
        } else null
        if (code !in "0123456789abcdefklmnor" && rgb == null) { index++; continue }
        flush(index)
        style = when {
            rgb != null -> { consumed = 14; SpanStyle(color = Color(0xFF000000L or rgb.toLong())) }
            code.digitToIntOrNull(16) != null -> SpanStyle(color = Color(0xFF000000L or palette[code.digitToInt(16)].toLong()))
            code == 'r' -> SpanStyle()
            code == 'l' -> style.copy(fontWeight = FontWeight.Bold)
            code == 'o' -> style.copy(fontStyle = FontStyle.Italic)
            code == 'n' -> style.copy(textDecoration = TextDecoration.combine(listOfNotNull(style.textDecoration, TextDecoration.Underline)))
            code == 'm' -> style.copy(textDecoration = TextDecoration.combine(listOfNotNull(style.textDecoration, TextDecoration.LineThrough)))
            else -> style // Keep obfuscated text readable in a headless client.
        }
        index += consumed
        start = index
    }
    flush(value.length)
}
