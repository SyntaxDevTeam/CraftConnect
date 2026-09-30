package pl.syntaxdevteam.craftconnect.data

import pl.syntaxdevteam.craftconnect.domain.model.ChatMessage
import pl.syntaxdevteam.craftconnect.domain.model.PlayerInfo
import pl.syntaxdevteam.craftconnect.domain.model.PlayerRole
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile

object DemoRepository {
    val servers = listOf(
        ServerProfile("hypixel", "Hypixel", "mc.hypixel.net", true, 184320, 200000, 46, true),
        ServerProfile("minemen", "Minemen Club", "minemen.club", true, 1203, 5000, 31),
        ServerProfile("mcci", "MCCI", "play.mccisland.net", true, 3421, 10000, 55),
        ServerProfile("cubecraft", "CubeCraft", "play.cubecraft.net", true, 2876, 20000, 63),
        ServerProfile("local", "Local Server", "192.168.1.100:25565", false, 0, 20, null),
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
