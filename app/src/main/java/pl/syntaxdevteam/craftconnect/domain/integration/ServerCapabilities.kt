package pl.syntaxdevteam.craftconnect.domain.integration

/**
 * Stable domain-facing capability model used by UI and session orchestration.
 *
 * Transport implementations (Minecraft, RCON, AuthGatewayX) may expose a subset
 * of these capabilities. Presentation code must depend on this model instead of
 * concrete protocol classes.
 */
enum class ServerCapability {
    MINECRAFT_CHAT,
    MINECRAFT_COMMANDS,
    PLAYER_LIST,
    SERVER_STATUS,

    RCON_COMMANDS,

    AGX_PAIRING,
    AGX_STATUS,
    AGX_STATS,
    AGX_CONSOLE_VIEW,
    AGX_CONSOLE_EXECUTE,
    AGX_BRANDING,
    AGX_DIAGNOSTICS,
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

    /**
     * Provider precedence for overlapping administration features.
     * AuthGatewayX is authoritative, RCON is a fallback, Minecraft is baseline.
     */
    fun preferredProvider(capability: ServerCapability): CapabilityProvider? =
        capabilities
            .asSequence()
            .filter { it.capability == capability }
            .map { it.provider }
            .minByOrNull { providerPriority(it) }

    private fun providerPriority(provider: CapabilityProvider): Int = when (provider) {
        CapabilityProvider.AUTH_GATEWAY_X -> 0
        CapabilityProvider.RCON -> 1
        CapabilityProvider.MINECRAFT -> 2
    }
}
