package pl.syntaxdevteam.craftconnect.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.*
import org.junit.Test

class MinecraftChatTextTest {
    @Test fun rendersRgbBoldAndResetWithoutLeakingControlCodes() {
        val text = minecraftChatText("§x§1§2§a§b§e§f§lVIP§r: hello 🐴")
        assertEquals("VIP: hello 🐴", text.text)
        assertEquals(Color(0xFF12ABEFL), text.spanStyles.first().item.color)
        assertEquals(FontWeight.Bold, text.spanStyles.first().item.fontWeight)
        assertNull(text.spanStyles.last().item.fontWeight)
    }
}
