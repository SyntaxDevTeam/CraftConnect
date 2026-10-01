package pl.syntaxdevteam.craftconnect.domain.model

/** Display text delivered by the current server; no inferred permission ranks. */
data class ReceivedChatMessage(
    val id: Long,
    val content: String,
    val receivedAtEpochMillis: Long,
)
