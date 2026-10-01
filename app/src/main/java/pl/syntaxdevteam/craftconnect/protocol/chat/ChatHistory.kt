package pl.syntaxdevteam.craftconnect.protocol.chat

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.syntaxdevteam.craftconnect.domain.model.ReceivedChatMessage

/** Retains early login messages even before a screen subscribes. Memory stays bounded. */
internal class ChatHistory(private val clock: () -> Long = System::currentTimeMillis) {
    private val state = MutableStateFlow<List<ReceivedChatMessage>>(emptyList())
    val messages = state.asStateFlow()
    private var nextId = 0L

    @Synchronized fun clear() { state.value = emptyList() }

    @Synchronized fun append(text: String) {
        val clean = text.replace(Regex("§[0-9a-fk-orx]", RegexOption.IGNORE_CASE), "").take(16_384)
        if (clean.isBlank()) return
        state.value = (state.value.takeLast(499) + ReceivedChatMessage(nextId++, clean, clock(), text.take(65_536)))
    }
}

