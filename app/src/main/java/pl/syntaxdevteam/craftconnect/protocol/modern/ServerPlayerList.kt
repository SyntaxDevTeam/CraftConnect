package pl.syntaxdevteam.craftconnect.protocol.modern

import java.io.DataInputStream
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import pl.syntaxdevteam.craftconnect.domain.model.ServerPlayer
import pl.syntaxdevteam.craftconnect.protocol.legacy.readProtocolString
import pl.syntaxdevteam.craftconnect.protocol.legacy.readVarInt

/** Retains unlisted profiles for subsequent updates, but exposes only listed players. */
internal class ServerPlayerList {
    private val profiles = mutableMapOf<String, ServerPlayer>()
    private val state = MutableStateFlow<List<ServerPlayer>>(emptyList())
    val players = state.asStateFlow()

    @Synchronized fun clear() { profiles.clear(); state.value = emptyList() }

    @Synchronized fun update(input: DataInputStream) {
        val actions = input.readUnsignedByte()
        val count = input.readVarInt().also { require(it in 0..4_096) }
        val updates = List(count) {
            val uuid = UUID(input.readLong(), input.readLong()).toString()
            var player = profiles[uuid] ?: ServerPlayer(uuid, "")
            if (actions and 0x01 != 0) {
                player = player.copy(name = input.readProtocolString(16))
                repeat(input.readVarInt().also { require(it in 0..64) }) {
                    input.readProtocolString(); input.readProtocolString()
                    if (input.readBoolean()) input.readProtocolString()
                }
            }
            if (actions and 0x02 != 0 && input.readBoolean()) {
                input.readLong(); input.readLong(); input.readLong() // session UUID, expiry
                repeat(2) {
                    val size = input.readVarInt().also { require(it in 0..8_192) }
                    input.readFully(ByteArray(size)) // public key and signature
                }
            }
            if (actions and 0x04 != 0) player = player.copy(gameMode = input.readVarInt())
            if (actions and 0x08 != 0) player = player.copy(listed = input.readBoolean())
            if (actions and 0x10 != 0) player = player.copy(pingMs = input.readVarInt().takeIf { it >= 0 })
            if (actions and 0x20 != 0) player = player.copy(
                displayName = if (input.readBoolean()) input.readAnonymousNbt().chatText() else null,
            )
            if (actions and 0x40 != 0) player = player.copy(listOrder = input.readVarInt())
            if (actions and 0x80 != 0) input.readBoolean() // show hat; no skin rendering
            player
        }
        require(input.available() == 0) { "Unexpected player list data" }
        // Decode the whole packet before publishing; a malformed entry cannot partially apply.
        val known = updates.filter { it.name.isNotEmpty() }
        require((profiles.keys + known.map { it.uuid }).size <= 4_096)
        known.forEach { profiles[it.uuid] = it }
        publish()
    }

    @Synchronized fun remove(input: DataInputStream) {
        val ids = List(input.readVarInt().also { require(it in 0..4_096) }) {
            UUID(input.readLong(), input.readLong()).toString()
        }
        require(input.available() == 0)
        ids.forEach(profiles::remove)
        publish()
    }

    private fun publish() {
        state.value = profiles.values.filter { it.listed }.sortedWith(
            compareByDescending<ServerPlayer> { it.listOrder }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.uuid },
        )
    }
}
