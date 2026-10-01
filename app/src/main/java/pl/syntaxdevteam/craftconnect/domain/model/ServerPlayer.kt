package pl.syntaxdevteam.craftconnect.domain.model

/** Data visible in the server's tab list; no inferred ranks or permissions. */
data class ServerPlayer(
    val uuid: String,
    val name: String,
    val displayName: String? = null,
    val pingMs: Int? = null,
    val gameMode: Int = 0,
    val listed: Boolean = false,
    val listOrder: Int = 0,
)
