package pl.syntaxdevteam.craftconnect.protocol.chat

import org.junit.Assert.*
import org.junit.Test

class ChatHistoryTest {
    @Test fun retainsEarlyMessagesCapsMemoryAndClearsConnection() {
        val history = ChatHistory { 123L }
        history.append("§aWelcome")
        assertEquals("Welcome", history.messages.value.single().content)
        assertEquals(123L, history.messages.value.single().receivedAtEpochMillis)
        repeat(600) { history.append("line $it") }
        assertEquals(500, history.messages.value.size)
        assertEquals("line 100", history.messages.value.first().content)
        history.clear()
        assertTrue(history.messages.value.isEmpty())
    }
}
