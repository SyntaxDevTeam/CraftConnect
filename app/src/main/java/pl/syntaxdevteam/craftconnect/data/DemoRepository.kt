package pl.syntaxdevteam.craftconnect.data

import pl.syntaxdevteam.craftconnect.domain.model.PlayerInfo
import pl.syntaxdevteam.craftconnect.domain.model.PlayerRole
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile

object DemoRepository {
    val servers = listOf(
        ServerProfile("local", "Local Server", "10.0.2.2:25565", false, 0, 0, null, true),
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

