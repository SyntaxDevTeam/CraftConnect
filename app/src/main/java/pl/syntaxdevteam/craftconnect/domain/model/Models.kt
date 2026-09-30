package pl.syntaxdevteam.craftconnect.domain.model

data class ServerProfile(
    val id: String,
    val name: String,
    val address: String,
    val online: Boolean,
    val playersOnline: Int,
    val playersMax: Int,
    val pingMs: Int?,
    val favorite: Boolean = false,
)

enum class PlayerRole {
    ADMIN, MVP, VIP, PLAYER
}

data class ChatMessage(
    val author: String,
    val content: String,
    val time: String,
    val role: PlayerRole = PlayerRole.PLAYER,
)

data class PlayerInfo(
    val name: String,
    val role: PlayerRole,
    val pingMs: Int,
)
