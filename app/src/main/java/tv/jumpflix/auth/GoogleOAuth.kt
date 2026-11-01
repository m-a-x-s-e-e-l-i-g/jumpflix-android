// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.auth

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import okhttp3.HttpUrl.Companion.toHttpUrl

data class OAuthTransaction(val verifier: String, val createdAt: Long, val expectedUserId: String? = null) {
    val challenge: String get() = GoogleOAuth.challenge(verifier)
    fun expired(now: Long = System.currentTimeMillis()): Boolean = now - createdAt !in 0..600_000L
    override fun toString() = "OAuthTransaction([redacted])"
}

object GoogleOAuth {
    const val CALLBACK = "tv.jumpflix.android://auth/callback"
    fun create(): OAuthTransaction = OAuthTransaction(Base64.getUrlEncoder().withoutPadding()
        .encodeToString(ByteArray(32).also { SecureRandom().nextBytes(it) }), System.currentTimeMillis())
    fun challenge(verifier: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
    fun authorizationUrl(origin: String, transaction: OAuthTransaction): String = origin.toHttpUrl().newBuilder()
        .addPathSegments("auth/v1/authorize").addQueryParameter("provider", "google")
        .addQueryParameter("redirect_to", CALLBACK).addQueryParameter("code_challenge", transaction.challenge)
        .addQueryParameter("code_challenge_method", "s256").build().toString()
    fun isReturnLink(uri: URI): Boolean = uri.scheme == "tv.jumpflix.android" && uri.host == "auth" &&
        uri.path == "/callback" && uri.userInfo == null && uri.port == -1
    fun isCallback(uri: URI): Boolean = isReturnLink(uri) && uri.fragment == null
    fun code(uri: URI): String {
        require(isCallback(uri)) { "Invalid Google sign-in return link." }
        val params = uri.rawQuery.orEmpty().split('&').map { it.split('=', limit = 2) }
            .associate { URLDecoder.decode(it[0], "UTF-8") to URLDecoder.decode(it.getOrElse(1) { "" }, "UTF-8") }
        require(!params.containsKey("error")) { "Google sign-in was cancelled or could not be completed. Please try again." }
        return params["code"]?.takeIf { it.matches(Regex("[A-Za-z0-9._~-]{1,2048}")) }
            ?: throw IllegalArgumentException("Google sign-in did not return an authorization code.")
    }
}
