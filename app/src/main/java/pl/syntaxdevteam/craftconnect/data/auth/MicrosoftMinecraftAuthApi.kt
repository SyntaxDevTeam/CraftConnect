package pl.syntaxdevteam.craftconnect.data.auth

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import pl.syntaxdevteam.craftconnect.domain.auth.*

class MicrosoftMinecraftAuthApi internal constructor(
    private val clientId: String,
    private val http: AuthTransport,
    private val clock: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) },
) : MicrosoftAuthApi {
    constructor(clientId: String) : this(clientId, AuthHttpClient())

    override suspend fun begin(): DeviceAuthorization {
        val body = checked(http.request("$AUTH/devicecode", form("client_id" to clientId, "scope" to SCOPE), FORM, null), AuthProblem.CONFIGURATION)
        val uri = body.getString("verification_uri")
        // A provider response cannot direct users to a third-party credential collection page.
        val parsed = java.net.URI(uri)
        if (parsed.scheme != "https" || parsed.host !in setOf("microsoft.com", "www.microsoft.com", "login.microsoftonline.com")) {
            throw AuthenticationException(AuthProblem.INVALID_RESPONSE)
        }
        return DeviceAuthorization(body.getString("device_code"), body.getString("user_code"), uri,
            clock() + body.getLong("expires_in").coerceIn(1, 1800) * 1000,
            body.optLong("interval", 5).coerceIn(1, 60) * 1000)
    }

    override suspend fun awaitTokens(authorization: DeviceAuthorization): MicrosoftTokens {
        var interval = authorization.intervalMillis
        while (clock() < authorization.expiresAt) {
            pause(interval)
            if (clock() >= authorization.expiresAt) break
            val response = http.request("$AUTH/token", form("client_id" to clientId,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code", "device_code" to authorization.deviceCode), FORM, null)
            if (response.status in 200..299) return tokens(response.body)
            when (response.body.optString("error")) {
                "authorization_pending" -> Unit
                "slow_down" -> interval += 5000
                "authorization_declined", "access_denied" -> throw AuthenticationException(AuthProblem.DECLINED)
                "expired_token", "bad_verification_code" -> throw AuthenticationException(AuthProblem.EXPIRED)
                else -> { checked(response, AuthProblem.CONFIGURATION) }
            }
        }
        throw AuthenticationException(AuthProblem.EXPIRED)
    }

    override suspend fun refresh(refreshToken: String): MicrosoftTokens = tokens(checked(
        http.request("$AUTH/token", form("client_id" to clientId, "grant_type" to "refresh_token",
            "refresh_token" to refreshToken, "scope" to SCOPE), FORM, null), AuthProblem.REAUTHENTICATE), refreshToken)

    override suspend fun minecraft(accessToken: String): VerifiedMinecraftAccount {
        val user = json("https://user.auth.xboxlive.com/user/authenticate", JSONObject()
            .put("Properties", JSONObject().put("AuthMethod", "RPS").put("SiteName", "user.auth.xboxlive.com").put("RpsTicket", "d=$accessToken"))
            .put("RelyingParty", "http://auth.xboxlive.com").put("TokenType", "JWT"), AuthProblem.XBOX_ACCOUNT)
        val xsts = json("https://xsts.auth.xboxlive.com/xsts/authorize", JSONObject()
            .put("Properties", JSONObject().put("SandboxId", "RETAIL").put("UserTokens", JSONArray().put(user.getString("Token"))))
            .put("RelyingParty", "rp://api.minecraftservices.com/").put("TokenType", "JWT"), AuthProblem.XBOX_ACCOUNT)
        fun hash(body: JSONObject) = body.getJSONObject("DisplayClaims").getJSONArray("xui").getJSONObject(0).getString("uhs")
        if (hash(user) != hash(xsts)) throw AuthenticationException(AuthProblem.INVALID_RESPONSE)
        val login = json("$MC/authentication/login_with_xbox", JSONObject()
            .put("identityToken", "XBL3.0 x=${hash(xsts)};${xsts.getString("Token")}"), AuthProblem.APP_APPROVAL)
        val token = login.getString("access_token")
        val entitlements = checked(http.request("$MC/entitlements/mcstore", null, JSON, token), AuthProblem.NO_LICENSE)
        val items = entitlements.getJSONArray("items")
        if ((0 until items.length()).none { items.getJSONObject(it).optString("name") in setOf("game_minecraft", "product_minecraft") }) {
            throw AuthenticationException(AuthProblem.NO_LICENSE)
        }
        val profile = checked(http.request("$MC/minecraft/profile", null, JSON, token), AuthProblem.NO_PROFILE)
        val id = profile.getString("id")
        val name = profile.getString("name")
        if (!Regex("[a-fA-F0-9]{32}").matches(id) || !Regex("[A-Za-z0-9_]{3,16}").matches(name)) {
            throw AuthenticationException(AuthProblem.INVALID_RESPONSE)
        }
        return VerifiedMinecraftAccount(MinecraftIdentity(name, id.lowercase(), token),
            clock() + login.getLong("expires_in").coerceIn(1, 86400) * 1000)
    }

    override suspend fun join(identity: MinecraftIdentity, serverHash: String) {
        val response = http.request("https://sessionserver.mojang.com/session/minecraft/join", JSONObject()
            .put("accessToken", identity.accessToken).put("selectedProfile", identity.uuid.replace("-", ""))
            .put("serverId", serverHash).toString(), JSON, null)
        if (response.status != 204) {
            checked(response, AuthProblem.REAUTHENTICATE)
            throw AuthenticationException(AuthProblem.INVALID_RESPONSE)
        }
    }

    private suspend fun json(url: String, body: JSONObject, problem: AuthProblem) = checked(http.request(url, body.toString(), JSON, null), problem)
    private fun checked(response: AuthResponse, fallback: AuthProblem): JSONObject {
        if (response.status !in 200..299) throw AuthenticationException(when {
            response.status == 429 || response.status >= 500 -> AuthProblem.NETWORK
            response.body.optString("error") == "invalid_grant" -> AuthProblem.REAUTHENTICATE
            else -> fallback
        })
        return response.body
    }
    private fun tokens(body: JSONObject, previousRefresh: String? = null): MicrosoftTokens {
        val refresh = body.optString("refresh_token").ifBlank { previousRefresh.orEmpty() }
        if (refresh.isBlank()) throw AuthenticationException(AuthProblem.INVALID_RESPONSE)
        return MicrosoftTokens(body.getString("access_token"), refresh)
    }
    private companion object {
        const val AUTH = "https://login.microsoftonline.com/consumers/oauth2/v2.0"
        const val MC = "https://api.minecraftservices.com"
        const val SCOPE = "XboxLive.signin offline_access"
        const val FORM = "application/x-www-form-urlencoded"
        const val JSON = "application/json"
    }
}
