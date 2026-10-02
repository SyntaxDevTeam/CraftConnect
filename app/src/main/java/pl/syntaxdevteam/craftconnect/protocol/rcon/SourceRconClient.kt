package pl.syntaxdevteam.craftconnect.protocol.rcon

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.CharBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.syntaxdevteam.craftconnect.domain.integration.RconCommandClient
import pl.syntaxdevteam.craftconnect.domain.integration.RconCommandResult
import pl.syntaxdevteam.craftconnect.domain.integration.RconConfiguration
import pl.syntaxdevteam.craftconnect.domain.integration.RconException

/**
 * Small stateless Source RCON client used by Console Lite.
 *
 * Every call establishes a fresh authenticated socket. This is intentionally
 * isolated from the Minecraft session so an RCON failure can never disconnect
 * the normal CraftConnect connection.
 */
class SourceRconClient(
    private val connectTimeoutMillis: Int = 3_000,
    private val responseTimeoutMillis: Int = 4_000,
    private val tailTimeoutMillis: Int = 120,
) : RconCommandClient {
    override suspend fun execute(
        configuration: RconConfiguration,
        password: CharArray,
        command: String,
    ): RconCommandResult = withContext(Dispatchers.IO) {
        require(command.isNotBlank()) { "RCON command must not be blank" }
        val commandBytes = command.toByteArray(Charsets.UTF_8)
        require(commandBytes.size <= MAX_COMMAND_BYTES) { "RCON command is too large" }
        val passwordBytes = encodeUtf8(password)
        try {
            Socket().use { socket ->
                socket.tcpNoDelay = true
                socket.connect(InetSocketAddress(configuration.host, configuration.port), connectTimeoutMillis)
                socket.soTimeout = responseTimeoutMillis

                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                RconPacketCodec.write(output, AUTH_REQUEST_ID, RconPacketCodec.TYPE_AUTH, passwordBytes)
                awaitAuthentication(input)

                RconPacketCodec.write(output, COMMAND_REQUEST_ID, RconPacketCodec.TYPE_COMMAND, commandBytes)
                val response = ByteArrayOutputStream()
                appendCommandResponse(RconPacketCodec.read(input), response)

                // Minecraft may split a long command response into several packets.
                // There is no stream terminator, so collect immediately available
                // chunks for a short bounded tail window and then close the socket.
                socket.soTimeout = tailTimeoutMillis
                while (response.size() < MAX_RESPONSE_BYTES) {
                    val packet = try {
                        RconPacketCodec.read(input)
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                    appendCommandResponse(packet, response)
                }
                require(response.size() <= MAX_RESPONSE_BYTES) { "RCON response exceeded safety limit" }
                RconCommandResult(response.toString(Charsets.UTF_8.name()))
            }
        } catch (failure: RconException) {
            throw failure
        } catch (failure: IOException) {
            throw RconException.Network(failure)
        } catch (failure: IllegalArgumentException) {
            throw RconException.Protocol(failure)
        } finally {
            passwordBytes.fill(0)
            commandBytes.fill(0)
        }
    }

    private fun awaitAuthentication(input: java.io.InputStream) {
        repeat(MAX_AUTH_PACKETS) {
            val packet = RconPacketCodec.read(input)
            if (packet.requestId == -1) throw RconException.AuthenticationRejected()
            if (packet.requestId == AUTH_REQUEST_ID && packet.type == RconPacketCodec.TYPE_AUTH_RESPONSE) return
            // Some Source implementations emit an empty response-value packet before
            // the auth response. Ignore only that documented shape.
            if (packet.type != RconPacketCodec.TYPE_RESPONSE_VALUE || packet.body.isNotEmpty()) {
                throw RconException.Protocol()
            }
        }
        throw RconException.Protocol()
    }

    private fun appendCommandResponse(packet: RconPacket, response: ByteArrayOutputStream) {
        if (packet.requestId != COMMAND_REQUEST_ID || packet.type != RconPacketCodec.TYPE_RESPONSE_VALUE) {
            throw RconException.Protocol()
        }
        if (response.size() + packet.body.size > MAX_RESPONSE_BYTES) throw RconException.Protocol()
        response.write(packet.body)
    }

    private fun encodeUtf8(chars: CharArray): ByteArray {
        val encoded = Charsets.UTF_8.encode(CharBuffer.wrap(chars))
        return ByteArray(encoded.remaining()).also { encoded.get(it) }
    }

    private companion object {
        const val AUTH_REQUEST_ID = 0x4343
        const val COMMAND_REQUEST_ID = 0x4344
        const val MAX_AUTH_PACKETS = 3
        const val MAX_COMMAND_BYTES = 1_400
        const val MAX_RESPONSE_BYTES = 256 * 1024
    }
}
