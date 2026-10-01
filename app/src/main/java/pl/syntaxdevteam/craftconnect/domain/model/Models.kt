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
    val minecraftVersion: MinecraftVersion = MinecraftVersion.JAVA_26_1,
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

enum class AccountType {
    OFFLINE,
    MICROSOFT,
}

data class AccountProfile(
    val id: String,
    val username: String,
    val type: AccountType,
)

data class ServerDialogField(
    val key: String,
    val label: String,
    val maxLength: Int,
    val secret: Boolean,
)

data class ServerDialogRequest(
    val title: String,
    val message: String?,
    val fields: List<ServerDialogField>,
    val submitActionId: String,
    val submitLabel: String,
    val cancelActionId: String?,
    val cancelLabel: String?,
)

sealed interface ServerDialogEvent {
    data class Show(val dialog: ServerDialogRequest) : ServerDialogEvent
    data object Clear : ServerDialogEvent
}

