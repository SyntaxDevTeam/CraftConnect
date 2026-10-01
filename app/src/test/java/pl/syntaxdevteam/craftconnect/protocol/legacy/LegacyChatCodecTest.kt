package pl.syntaxdevteam.craftconnect.protocol.legacy

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacyChatCodecTest {
    @Test fun translatedChatAndNestedExtras() {
        assertEquals("Luna: Hello!", readLegacyChat("""{"translate":"chat.type.text","with":["Luna",{"text":"Hello","extra":[{"text":"!"}]}]}"""))
        assertEquals("Line 1\nLine 2", readLegacyChat("""[{"text":"Line 1\n"},{"text":"Line 2"}]"""))
    }
}
