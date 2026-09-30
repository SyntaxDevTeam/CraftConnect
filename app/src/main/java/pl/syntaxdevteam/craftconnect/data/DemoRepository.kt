package pl.syntaxdevteam.craftconnect.data

import pl.syntaxdevteam.craftconnect.domain.model.ChatMessage
import pl.syntaxdevteam.craftconnect.domain.model.PlayerInfo
import pl.syntaxdevteam.craftconnect.domain.model.PlayerRole
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile

object DemoRepository {
    val servers = listOf(
        ServerProfile("local", "Local Server", "10.0.2.2:25565", false, 0, 0, null, true),
    )

    val messages = listOf(
        ChatMessage("NotSteve", "how's everyone doing?", "21:16", PlayerRole.VIP),
        ChatMessage("Luna", "good! grinding skyblock atm", "21:16", PlayerRole.MVP),
        ChatMessage("PixelFox", "anyone for bed wars?", "21:17"),
        ChatMessage("zKairo", "count me in!", "21:17", PlayerRole.MVP),
        ChatMessage("Plancke", "enjoy your games!", "21:18", PlayerRole.ADMIN),
        ChatMessage("CraftyDev", "this app is so clean", "21:18"),
        ChatMessage("RedSyntax", "headless client, zero rendering", "21:19"),
    )

    val players = listOf(
        PlayerInfo("Plancke", PlayerRole.ADMIN, 12),
        PlayerInfo("zKairo", PlayerRole.MVP, 38),
        PlayerInfo("Luna", PlayerRole.MVP, 41),
        PlayerInfo("NotSteve", PlayerRole.VIP, 52),
        PlayerInfo("PixelFox", PlayerRole.VIP, 67),
        PlayerInfo("EnderLily", PlayerRole.MVP, 71),
        PlayerInfo("CraftyDev", PlayerRole.PLAYER, 85),
        PlayerInfo("RedSyntax", PlayerRole.PLAYER, 93),
    )
}
