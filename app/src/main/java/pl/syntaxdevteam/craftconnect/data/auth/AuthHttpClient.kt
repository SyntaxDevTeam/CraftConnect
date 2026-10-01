package pl.syntaxdevteam.craftconnect.data.auth

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import pl.syntaxdevteam.craftconnect.domain.auth.AuthenticationException
import pl.syntaxdevteam.craftconnect.domain.auth.AuthProblem

internal class AuthResponse(val status: Int, val body: JSONObject)
internal fun interface AuthTransport {
    suspend fun request(url: String, body: String?, contentType: String, bearer: String?): AuthResponse
}

/** Fixed HTTPS service endpoints only; never follow a redirect with credentials. */
internal class AuthHttpClient : AuthTransport {
    override suspend fun request(url: String, body: String?, contentType: String, bearer: String?): AuthResponse = withContext(Dispatchers.IO) {
        val endpoint = URL(url)
        require(endpoint.protocol == "https" && endpoint.host in HOSTS)
        val connection = endpoint.openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.requestMethod = if (body == null) "GET" else "POST"
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", contentType)
            if (endpoint.host.endsWith(".xboxlive.com")) connection.setRequestProperty("x-xbl-contract-version", "1")
            bearer?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { stream ->
                val bytes = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (bytes.size() <= 1_048_576) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    bytes.write(buffer, 0, count)
                }
                bytes.toByteArray() } ?: byteArrayOf()
            if (bytes.size > 1_048_576) throw AuthenticationException(AuthProblem.INVALID_RESPONSE)
            val json = if (bytes.isEmpty()) JSONObject() else try { JSONObject(String(bytes, Charsets.UTF_8)) }
                catch (_: Exception) { JSONObject() }
            AuthResponse(status, json)
        } catch (failure: java.io.IOException) {
            throw AuthenticationException(AuthProblem.NETWORK)
        } finally { connection.disconnect() }
    }

    private companion object {
        val HOSTS = setOf("login.microsoftonline.com", "user.auth.xboxlive.com", "xsts.auth.xboxlive.com", "api.minecraftservices.com", "sessionserver.mojang.com")
    }
}

internal fun form(vararg fields: Pair<String, String>): String = fields.joinToString("&") {
    URLEncoder.encode(it.first, "UTF-8") + "=" + URLEncoder.encode(it.second, "UTF-8")
}
