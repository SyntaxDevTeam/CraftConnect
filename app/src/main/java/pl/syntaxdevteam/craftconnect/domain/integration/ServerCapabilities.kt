package pl.syntaxdevteam.craftconnect.domain.integration

/**
 * Stable domain-facing feature model used by UI and session orchestration.
 *
 * A capability describes what the user may do, while [CapabilityProvider]
 * describes how CraftConnect can currently provide that feature. Keeping those
 * concerns separate lets AuthGatewayX transparently replace RCON for overlapping
 * administration features without changing presentation code.
 */
enum class ServerCapability {
    CHAT,
    PLAYER_COMMANDS,
    PLAYER_LIST,
    SERVER_STATUS,
    MOTD,

    PAIRING,
    SERVER_STATS,
    SERVER_INFO,
    CONSOLE_VIEW,
    CONSOLE_EXECUTE,
    BRANDING,
    DIAGNOSTICS,
}

enum class CapabilityProvider {
    MINECRAFT,
    RCON,
    AUTH_GATEWAY_X,
}

data class ProvidedCapability(
    val capability: ServerCapability,
    val provider: CapabilityProvider,
)

data class ServerCapabilitySnapshot(
    val capabilities: Set<ProvidedCapability> = emptySet(),
) {
    fun supports(capability: ServerCapability): Boolean =
        capabilities.any { it.capability == capability }

    fun providersFor(capability: ServerCapability): Set<CapabilityProvider> =
        capabilities.asSequence()
            .filter { it.capability == capability }
            .mapTo(linkedSetOf()) { it.provider }

    /**
     * AuthGatewayX is authoritative for overlapping enhanced features. RCON is
     * only a fallback, and the standard Minecraft session remains the baseline.
     */
    fun preferredProvider(capability: ServerCapability): CapabilityProvider? =
        providersFor(capability).minByOrNull(::providerPriority)

    fun plus(other: ServerCapabilitySnapshot): ServerCapabilitySnapshot =
        ServerCapabilitySnapshot(capabilities + other.capabilities)

    companion object {
        val MINECRAFT_BASELINE = ServerCapabilitySnapshot(
            setOf(
                ProvidedCapability(ServerCapability.CHAT, CapabilityProvider.MINECRAFT),
                ProvidedCapability(ServerCapability.PLAYER_COMMANDS, CapabilityProvider.MINECRAFT),
                ProvidedCapability(ServerCapability.PLAYER_LIST, CapabilityProvider.MINECRAFT),
                ProvidedCapability(ServerCapability.SERVER_STATUS, CapabilityProvider.MINECRAFT),
                ProvidedCapability(ServerCapability.MOTD, CapabilityProvider.MINECRAFT),
            ),
        )

        val RCON_LITE = ServerCapabilitySnapshot(
            setOf(
                ProvidedCapability(ServerCapability.CONSOLE_EXECUTE, CapabilityProvider.RCON),
            ),
        )

        fun authGatewayX(granted: Set<ServerCapability>): ServerCapabilitySnapshot =
            ServerCapabilitySnapshot(
                granted.mapTo(linkedSetOf()) {
                    ProvidedCapability(it, CapabilityProvider.AUTH_GATEWAY_X)
                },
            )

        private fun providerPriority(provider: CapabilityProvider): Int = when (provider) {
            CapabilityProvider.AUTH_GATEWAY_X -> 0
            CapabilityProvider.RCON -> 1
            CapabilityProvider.MINECRAFT -> 2
        }
    }
}

/** Stateless merger used by session orchestration and tests. */
object ServerCapabilityResolver {
    fun resolve(
        minecraftConnected: Boolean,
        rconAvailable: Boolean,
        authGatewayXCapabilities: Set<ServerCapability> = emptySet(),
    ): ServerCapabilitySnapshot {
        var snapshot = ServerCapabilitySnapshot()
        if (minecraftConnected) snapshot = snapshot.plus(ServerCapabilitySnapshot.MINECRAFT_BASELINE)
        if (rconAvailable) snapshot = snapshot.plus(ServerCapabilitySnapshot.RCON_LITE)
        if (authGatewayXCapabilities.isNotEmpty()) {
            snapshot = snapshot.plus(ServerCapabilitySnapshot.authGatewayX(authGatewayXCapabilities))
        }
        return snapshot
    }
}
