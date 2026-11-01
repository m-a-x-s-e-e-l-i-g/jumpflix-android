// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.auth

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AuthApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: AuthApi
    @Before fun setup() { server = MockWebServer(); server.start(); api = AuthApi(server.url("/").toString(), "public-test-key") }
    @After fun cleanup() { server.shutdown() }
    private fun session() = """{"access_token":"access-token","refresh_token":"refresh-token","expires_in":3600,"user":{"id":"account-123","email":"viewer@example.test","user_metadata":{"display_name":"Viewer"}}}"""

    @Test fun `sign in uses the existing auth password grant and exposes no tokens in display state`() {
        server.enqueue(MockResponse().setBody(session()))
        val result = api.signIn(" viewer@example.test ", "test password")
        val request = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=password", request.path)
        assertEquals("public-test-key", request.getHeader("apikey"))
        assertNull(request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("viewer@example.test", body.getString("email"))
        assertEquals("test password", body.getString("password"))
        assertEquals("Viewer", result.profile.name)
        assertFalse(result.toString().contains("access-token"))
        assertFalse(result.toString().contains("refresh-token"))
        assertFalse(result.toJson().toString().contains("test password"))
    }
    @Test fun `wrong credentials use a friendly error without echoing server diagnostics`() {
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error_code":"invalid_credentials","msg":"private server diagnostic"}"""))
        val failure = assertThrows(AuthFailure::class.java) { api.signIn("viewer@example.test", "incorrect") }
        assertEquals(400, failure.status)
        assertFalse(failure.message!!.contains("private"))
    }
    @Test fun `refresh token rotation stores the newly returned refresh token`() {
        server.enqueue(MockResponse().setBody(session().replace("refresh-token", "rotated-token")))
        val result = api.refresh("old-token")
        val request = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=refresh_token", request.path)
        assertEquals("old-token", JSONObject(request.body.readUtf8()).getString("refresh_token"))
        assertEquals("rotated-token", result.refreshToken)
    }
    @Test fun `sign out revokes only the native session`() {
        server.enqueue(MockResponse().setResponseCode(204))
        api.signOut("access-token")
        val request = server.takeRequest()
        assertEquals("/auth/v1/logout?scope=local", request.path)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
    }
    @Test fun `restored sessions are verified against the server identity`() {
        server.enqueue(MockResponse().setBody("""{"id":"another-account"}"""))
        assertThrows(java.io.IOException::class.java) { api.verify(AuthSession.parse(JSONObject(session()))) }
    }
    @Test fun `Google authorization codes use the PKCE grant and the device verifier`() {
        server.enqueue(MockResponse().setBody(session()))
        api.exchangeCode("one-time-code", "device-private-verifier")
        val request = server.takeRequest()
        assertEquals("/auth/v1/token?grant_type=pkce", request.path)
        val body = JSONObject(request.body.readUtf8())
        assertEquals("one-time-code", body.getString("auth_code"))
        assertEquals("device-private-verifier", body.getString("code_verifier"))
    }
    @Test fun `signed in Google connections use the authenticated identity link endpoint`() {
        server.enqueue(MockResponse().setBody("""{"url":"https://accounts.google.com/o/oauth2/auth?state=test"}"""))
        val transaction = OAuthTransaction("private-link-verifier", System.currentTimeMillis(), "account-123")
        api.linkGoogle(AuthSession.parse(JSONObject(session())), transaction)
        val request = server.takeRequest()
        assertEquals("/auth/v1/user/identities/authorize", request.requestUrl!!.encodedPath)
        assertEquals("Bearer access-token", request.getHeader("Authorization"))
        assertEquals("true", request.requestUrl!!.queryParameter("skip_http_redirect"))
        assertEquals(transaction.challenge, request.requestUrl!!.queryParameter("code_challenge"))
        assertFalse(request.path!!.contains(transaction.verifier))
    }
    @Test fun `connected providers come from server identities rather than editable user metadata`() {
        val user = JSONObject("""{"id":"account-123","email":"viewer@example.test","user_metadata":{"providers":["google"]},"identities":[{"provider":"email"}]}""")
        assertFalse(AccountProfile.parse(user).providers.contains("google"))
        user.getJSONArray("identities").put(JSONObject().put("provider", "google"))
        val original = AuthSession.parse(JSONObject(session()).put("user", user))
        assertTrue(original.profile.providers.contains("google"))
        assertTrue(AuthSession.parse(original.toJson()).profile.providers.contains("google"))
    }
}
