package pl.syntaxdevteam.craftconnect.protocol.modern

import kotlin.random.Random
import org.xbill.DNS.Lookup
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.Type

internal data class MinecraftServerEndpoint(
    val connectionHost: String,
    val connectionPort: Int,
    val handshakeHost: String,
    val handshakePort: Int,
)

internal data class SrvTarget(
    val host: String,
    val port: Int,
    val priority: Int,
    val weight: Int,
)

/** Resolves the SRV records used by Minecraft Java when an address has no explicit port. */
internal class MinecraftServerAddressResolver(
    private val lookupSrv: (String) -> List<SrvTarget> = ::lookupMinecraftSrv,
    private val random: Random = Random.Default,
) {
    fun resolve(address: String): MinecraftServerEndpoint {
        val parsed = ParsedAddress.parse(address)
        if (parsed.hasExplicitPort || parsed.isIpLiteral) return parsed.directEndpoint()

        val targets = runCatching { lookupSrv("_minecraft._tcp.${parsed.host}") }.getOrDefault(emptyList())
        val selected = selectSrvTarget(targets) ?: return parsed.directEndpoint()
        return MinecraftServerEndpoint(
            connectionHost = selected.host.trimEnd('.'),
            connectionPort = selected.port,
            handshakeHost = parsed.host,
            handshakePort = parsed.port,
        )
    }

    private fun selectSrvTarget(targets: List<SrvTarget>): SrvTarget? {
        val usable = targets.filter { it.host != "." && it.port in 1..65_535 }
        val bestPriority = usable.minOfOrNull(SrvTarget::priority) ?: return null
        val candidates = usable.filter { it.priority == bestPriority }
        val totalWeight = candidates.sumOf(SrvTarget::weight)
        if (totalWeight <= 0) return candidates.randomOrNull(random)

        var choice = random.nextInt(totalWeight)
        return candidates.first { target ->
            choice -= target.weight
            choice < 0
        }
    }

    private data class ParsedAddress(
        val host: String,
        val port: Int,
        val hasExplicitPort: Boolean,
        val isIpLiteral: Boolean,
    ) {
        fun directEndpoint() = MinecraftServerEndpoint(host, port, host, port)

        companion object {
            fun parse(address: String): ParsedAddress {
                val value = address.trim()
                require(value.isNotEmpty()) { "Invalid server address" }

                val host: String
                val portText: String?
                val explicitPort: Boolean
                if (value.startsWith('[')) {
                    val closingBracket = value.indexOf(']')
                    require(closingBracket > 1) { "Invalid server address" }
                    host = value.substring(1, closingBracket)
                    val suffix = value.substring(closingBracket + 1)
                    require(suffix.isEmpty() || suffix.startsWith(':')) { "Invalid server address" }
                    explicitPort = suffix.startsWith(':')
                    portText = suffix.removePrefix(":").takeIf { explicitPort }
                } else {
                    val separator = value.lastIndexOf(':')
                    explicitPort = separator > 0 && value.indexOf(':') == separator
                    host = if (explicitPort) value.substring(0, separator) else value
                    portText = if (explicitPort) value.substring(separator + 1) else null
                }

                val port = portText?.toIntOrNull() ?: DEFAULT_PORT
                require(host.isNotBlank() && port in 1..65_535 && (!explicitPort || portText?.toIntOrNull() != null)) {
                    "Invalid server address"
                }
                return ParsedAddress(
                    host = host,
                    port = port,
                    hasExplicitPort = explicitPort,
                    isIpLiteral = host.contains(':') || IPV4.matches(host),
                )
            }
        }
    }

    private companion object {
        const val DEFAULT_PORT = 25_565
        val IPV4 = Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")
    }
}

private fun lookupMinecraftSrv(name: String): List<SrvTarget> =
    Lookup(name, Type.SRV).run().orEmpty().filterIsInstance<SRVRecord>().map { record ->
        SrvTarget(
            host = record.target.toString(),
            port = record.port,
            priority = record.priority,
            weight = record.weight,
        )
    }
