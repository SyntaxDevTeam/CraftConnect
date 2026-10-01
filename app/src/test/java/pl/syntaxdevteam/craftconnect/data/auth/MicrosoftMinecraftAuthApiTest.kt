package pl.syntaxdevteam.craftconnect.data.auth

import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import pl.syntaxdevteam.craftconnect.domain.auth.*

class MicrosoftMinecraftAuthApiTest {
    private val uuid = "12345678123456781234567812345678"
    private fun response(status: Int, json: String) = AuthResponse(status, JSONObject(json))

    @Test fun `device flow observes polling and slow down then returns tokens`() = runTest {
        var now = 0L
        val waits = mutableListOf<Long>()
        val responses = ArrayDeque(listOf(response(400, """{"error":"authorization_pending"}"""),
            response(400, """{"error":"slow_down"}"""), response(200, """{"access_token":"access","refresh_token":"refresh"}""")))
        val api = MicrosoftMinecraftAuthApi("client", AuthTransport { url, body, _, _ ->
            assertTrue(url.endsWith("/consumers/oauth2/v2.0/token"))
            assertTrue(body!!.contains("device_code=device"))
            responses.removeFirst()
        }, { now }, { now += it; waits += it })
        val tokens = api.awaitTokens(DeviceAuthorization("device", "code", "https://microsoft.com/devicelogin", 60_000, 5000))
        assertEquals(listOf(5000L, 5000L, 10000L), waits)
        assertEquals("refresh", tokens.refreshToken)
    }

    @Test fun `expired code sends no late token request`() = runTest {
        var now = 0L
        val api = MicrosoftMinecraftAuthApi("client", AuthTransport { _, _, _, _ -> error("No request expected") }, { now }, { now += it })
        try { api.awaitTokens(DeviceAuthorization("d", "u", "", 1000, 5000)); fail() }
        catch (failure: AuthenticationException) { assertEquals(AuthProblem.EXPIRED, failure.problem) }
    }

    @Test fun `declined authorization stops polling`() = runTest {
        val api = MicrosoftMinecraftAuthApi("client", AuthTransport { _, _, _, _ -> response(400, """{"error":"authorization_declined"}""") }, { 0 }, {})
        try { api.awaitTokens(DeviceAuthorization("d", "u", "", 10000, 1)); fail() }
        catch (failure: AuthenticationException) { assertEquals(AuthProblem.DECLINED, failure.problem) }
    }

    @Test fun `unexpected verification host is rejected`() = runTest {
        val api = MicrosoftMinecraftAuthApi("client", AuthTransport { _, _, _, _ -> response(200, """{"verification_uri":"https://example.com/phishing"}""") })
        try { api.begin(); fail() }
        catch (failure: AuthenticationException) { assertEquals(AuthProblem.INVALID_RESPONSE, failure.problem) }
    }

    @Test fun `Minecraft pipeline verifies entitlement and returns service profile`() = runTest {
        val requests = mutableListOf<String>()
        val api = MicrosoftMinecraftAuthApi("client", AuthTransport { url, body, _, bearer ->
            requests += url
            when {
                url.contains("user/authenticate") -> {
                    assertEquals("d=ms-token", JSONObject(body!!).getJSONObject("Properties").getString("RpsTicket"))
                    response(200, """{"Token":"user","DisplayClaims":{"xui":[{"uhs":"hash"}]}}""")
                }
                url.contains("xsts/authorize") -> response(200, """{"Token":"xsts","DisplayClaims":{"xui":[{"uhs":"hash"}]}}""")
                url.endsWith("login_with_xbox") -> {
                    assertEquals("XBL3.0 x=hash;xsts", JSONObject(body!!).getString("identityToken"))
                    response(200, """{"access_token":"mc-token","expires_in":3600}""")
                }
                url.endsWith("mcstore") -> { assertEquals("mc-token", bearer); response(200, """{"items":[{"name":"game_minecraft"}]}""") }
                else -> { assertEquals("mc-token", bearer); response(200, """{"id":"$uuid","name":"Player"}""") }
            }
        }, { 100 })
        val result = api.minecraft("ms-token")
        assertEquals(uuid, result.identity.uuid)
        assertEquals("Player", result.identity.username)
        assertEquals(3_600_100L, result.expiresAt)
        assertEquals(5, requests.size)
    }

    @Test fun `no license and unapproved app are distinct failures`() = runTest {
        for (blocked in listOf(false, true)) {
            val api = MicrosoftMinecraftAuthApi("client", AuthTransport { url, _, _, _ -> when {
                url.contains("xboxlive.com") -> response(200, """{"Token":"x","DisplayClaims":{"xui":[{"uhs":"h"}]}}""")
                url.endsWith("login_with_xbox") -> if (blocked) response(403, "{}") else response(200, """{"access_token":"mc","expires_in":10}""")
                url.endsWith("mcstore") -> response(200, """{"items":[]}""")
                else -> error("Profile must not be fetched without entitlement")
            } })
            try { api.minecraft("ms"); fail() }
            catch (failure: AuthenticationException) { assertEquals(if (blocked) AuthProblem.APP_APPROVAL else AuthProblem.NO_LICENSE, failure.problem) }
        }
    }

    @Test fun `refresh retains previous token if provider does not rotate it`() = runTest {
        val api = MicrosoftMinecraftAuthApi("client", AuthTransport { _, body, _, _ ->
            assertTrue(body!!.contains("grant_type=refresh_token"))
            response(200, """{"access_token":"new"}""")
        })
        assertEquals("old", api.refresh("old").refreshToken)
    }

    @Test fun `join posts only to session server and requires 204`() = runTest {
        val api = MicrosoftMinecraftAuthApi("client", AuthTransport { url, body, _, _ ->
            assertEquals("https://sessionserver.mojang.com/session/minecraft/join", url)
            assertEquals("mc", JSONObject(body!!).getString("accessToken"))
            assertEquals(uuid, JSONObject(body).getString("selectedProfile"))
            assertEquals("-123", JSONObject(body).getString("serverId"))
            response(204, "{}")
        })
        api.join(MinecraftIdentity("Player", uuid, "mc"), "-123")
    }
}
