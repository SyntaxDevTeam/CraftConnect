package pl.syntaxdevteam.craftconnect.domain.server

import kotlinx.coroutines.flow.StateFlow
import pl.syntaxdevteam.craftconnect.domain.model.MinecraftVersion
import pl.syntaxdevteam.craftconnect.domain.model.ServerProfile

interface ServerRepository {
    val servers: StateFlow<List<ServerProfile>>

    fun create(name: String, address: String, favorite: Boolean, minecraftVersion: MinecraftVersion = MinecraftVersion.JAVA_26_3): ServerProfile
    fun update(server: ServerProfile)
    fun delete(id: String)
}

