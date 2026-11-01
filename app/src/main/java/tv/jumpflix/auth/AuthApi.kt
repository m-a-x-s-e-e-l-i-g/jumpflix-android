// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.auth

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class AccountProfile(val id: String, val email: String, val name: String)

/** Tokens never enter UI state, logs, or diagnostic toString output. */
class AuthSession(val accessToken: String, val refreshToken: String, val expiresAt: Long, val profile: AccountProfile) {
    fun toJson(): JSONObject = JSONObject().put("access_token", accessToken).put("refresh_token", refreshToken)
        .put("expires_at", expiresAt).put("user", JSONObject().put("id", profile.id).put("email", profile.email)
            .put("user_metadata", JSONObject().put("display_name", profile.name)))
    override fun toString() = "AuthSession([redacted])"

    companion object {
        fun parse(json: JSONObject): AuthSession {
            val user = json.getJSONObject("user")
            val metadata = user.optJSONObject("user_metadata")
            val email = user.optString("email")
            return AuthSession(json.getString("access_token"), json.getString("refresh_token"),
                if (json.has("expires_at")) json.getLong("expires_at") else System.currentTimeMillis() / 1000 + json.getLong("expires_in"),
                AccountProfile(user.getString("id"), email, metadata?.optString("display_name")?.takeIf { it.isNotBlank() }
                    ?: metadata?.optString("full_name")?.takeIf { it.isNotBlank() } ?: email.substringBefore('@')))
        }
    }
}

class AuthFailure(val status: Int, val code: String, message: String) : IOException(message)

/** Talks to the same hosted Supabase Auth service as jumpflix.tv, using a public key. */
class AuthApi(private val baseUrl: String, private val publicKey: String,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()) {
    private fun request(path: String, body: JSONObject? = null, accessToken: String? = null): JSONObject {
        val builder = Request.Builder().url(baseUrl.trimEnd('/') + "/auth/v1/" + path).header("apikey", publicKey)
            .header("Accept", "application/json")
        accessToken?.let { builder.header("Authorization", "Bearer $it") }
        if (body != null) builder.post(body.toString().toRequestBody("application/json".toMediaType()))
        return client.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val data = runCatching { JSONObject(text) }.getOrDefault(JSONObject())
            if (!response.isSuccessful) {
                val code = data.optString("error_code", data.optString("error"))
                val message = when {
                    response.code == 429 -> "Too many attempts. Please wait a moment and try again."
                    code == "email_not_confirmed" -> "Please confirm your email using the JUMPFLIX confirmation email first."
                    code == "captcha_failed" -> "This sign-in needs a browser verification. Please use jumpflix.tv for now."
                    response.code in setOf(400, 401, 403, 422) -> "Could not sign in. Check your JUMPFLIX email and password."
                    else -> "The account service is temporarily unavailable. Please try again."
                }
                throw AuthFailure(response.code, code, message)
            }
            data
        }
    }

    fun signIn(email: String, password: String): AuthSession = AuthSession.parse(request("token?grant_type=password", JSONObject().put("email", email.trim()).put("password", password)))
    fun refresh(refreshToken: String): AuthSession = AuthSession.parse(request("token?grant_type=refresh_token", JSONObject().put("refresh_token", refreshToken)))
    fun exchangeCode(code: String, verifier: String): AuthSession = AuthSession.parse(request("token?grant_type=pkce", JSONObject().put("auth_code", code).put("code_verifier", verifier)))
    fun verify(session: AuthSession) {
        val user = request("user", accessToken = session.accessToken)
        if (user.getString("id") != session.profile.id) throw IOException("The account session could not be verified.")
    }
    // Local scope avoids signing the user out of the website or their other devices.
    fun signOut(accessToken: String) { request("logout?scope=local", JSONObject(), accessToken) }
}
