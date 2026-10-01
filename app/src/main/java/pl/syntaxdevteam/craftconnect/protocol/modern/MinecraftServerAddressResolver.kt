package pl.syntaxdevteam.craftconnect.protocol.modern

import java.net.InetAddress
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.random.Random
import org.xbill.DNS.AAAARecord
import org.xbill.DNS.ARecord
import org.xbill.DNS.DClass
import org.xbill.DNS.Lookup
import org.xbill.DNS.Message
import org.xbill.DNS.Name
import org.xbill.DNS.Record
import org.xbill.DNS.SRVRecord
import org.xbill.DNS.Section
import org.xbill.DNS.Type

internal data class MinecraftServerEndpoint(
    val connectionHost: String,
    val connectionPort: Int,
    val handshakeHost: String,
    val handshakePort: Int,
    val connectionAddresses: List<InetAddress> = emptyList(),
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
    private val lookupAddresses: (String) -> List<InetAddress> = ::lookupHostAddresses,
    private val random: Random = Random.Default,
) {
    fun resolve(address: String): MinecraftServerEndpoint {
        val parsed = ParsedAddress.parse(address)
        if (parsed.hasExplicitPort || parsed.isIpLiteral) return parsed.directEndpoint()

        val targets = runCatching { lookupSrv("_minecraft._tcp.${parsed.host}") }.getOrDefault(emptyList())
        val selected = selectSrvTarget(targets) ?: return parsed.directEndpoint()
        val connectionHost = selected.host.trimEnd('.')
        return MinecraftServerEndpoint(
            connectionHost = connectionHost,
            connectionPort = selected.port,
            handshakeHost = parsed.host,
            handshakePort = parsed.port,
            connectionAddresses = runCatching { lookupAddresses(connectionHost) }.getOrDefault(emptyList()),
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
    lookupRecords(name, Type.SRV).filterIsInstance<SRVRecord>().map { record ->
        SrvTarget(
            host = record.target.toString(),
            port = record.port,
            priority = record.priority,
            weight = record.weight,
        )
    }

private fun lookupHostAddresses(host: String): List<InetAddress> = buildList {
    lookupRecords(host, Type.A).filterIsInstance<ARecord>().forEach { add(it.address) }
    lookupRecords(host, Type.AAAA).filterIsInstance<AAAARecord>().forEach { add(it.address) }
}

/**
 * Some Android/network DNS resolvers omit records whose owner contains underscores, even though
 * Minecraft SRV providers use such technical target names. Retry an unsuccessful system lookup
 * against public recursive resolvers instead of passing the unresolvable SRV target to Android.
 */
private fun lookupRecords(name: String, type: Int): List<Record> {
    runLookup(name, type).takeIf { it.isNotEmpty() }?.let { return it }
    DNS_OVER_HTTPS_ENDPOINTS.forEach { endpoint ->
        runDohLookup(endpoint, name, type).takeIf { it.isNotEmpty() }?.let { return it }
    }
    return emptyList()
}

private fun runLookup(name: String, type: Int): List<Record> = runCatching {
    Lookup(name, type).run().orEmpty().toList()
}.getOrDefault(emptyList())

private fun runDohLookup(endpoint: String, name: String, type: Int): List<Record> = runCatching {
    val query = Message.newQuery(Record.newRecord(Name.fromString(name, Name.root), type, DClass.IN)).toWire()
    val connection = URL(endpoint).openConnection() as HttpsURLConnection
    try {
        connection.requestMethod = "POST"
        connection.connectTimeout = DNS_TIMEOUT_MILLIS
        connection.readTimeout = DNS_TIMEOUT_MILLIS
        connection.doOutput = true
        connection.setFixedLengthStreamingMode(query.size)
        connection.setRequestProperty("Content-Type", "application/dns-message")
        connection.setRequestProperty("Accept", "application/dns-message")
        connection.outputStream.use { it.write(query) }
        if (connection.responseCode !in 200..299) return@runCatching emptyList()
        Message(connection.inputStream.use { it.readBytes() })
            .getSection(Section.ANSWER)
            .filter { it.type == type }
    } finally {
        connection.disconnect()
    }
}.getOrDefault(emptyList())

private const val DNS_TIMEOUT_MILLIS = 3_000
private val DNS_OVER_HTTPS_ENDPOINTS = listOf(
    "https://cloudflare-dns.com/dns-query",
    "https://dns.google/dns-query",
)
