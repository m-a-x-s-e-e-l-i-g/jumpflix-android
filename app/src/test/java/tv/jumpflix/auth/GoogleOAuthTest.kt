// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.auth

import java.net.URI
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class GoogleOAuthTest {
    @Test fun `PKCE uses the RFC7636 SHA256 challenge vector`() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", GoogleOAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }
    @Test fun `browser URL includes a challenge but never its private verifier`() {
        val transaction = GoogleOAuth.create()
        val url = GoogleOAuth.authorizationUrl("https://example.supabase.co", transaction).toHttpUrl()
        assertEquals("/auth/v1/authorize", url.encodedPath)
        assertEquals("google", url.queryParameter("provider"))
        assertEquals(GoogleOAuth.CALLBACK, url.queryParameter("redirect_to"))
        assertEquals("s256", url.queryParameter("code_challenge_method"))
        assertFalse(url.toString().contains(transaction.verifier))
        assertFalse(transaction.toString().contains(transaction.verifier))
        assertNotEquals(transaction.verifier, GoogleOAuth.create().verifier)
    }
    @Test fun `only the exact app callback with a code is accepted`() {
        assertEquals("abc-123", GoogleOAuth.code(URI("${GoogleOAuth.CALLBACK}?code=abc-123")))
        for (url in listOf("https://auth/callback?code=abc", "tv.jumpflix.android://auth.evil/callback?code=abc", "tv.jumpflix.android://auth/other?code=abc", "${GoogleOAuth.CALLBACK}#access_token=unexpected")) {
            assertThrows(IllegalArgumentException::class.java) { GoogleOAuth.code(URI(url)) }
        }
        assertThrows(IllegalArgumentException::class.java) { GoogleOAuth.code(URI("${GoogleOAuth.CALLBACK}?error=access_denied")) }
    }
    @Test fun `pending sign in expires after ten minutes and rejects future timestamps`() {
        val transaction = OAuthTransaction("private-verifier", 1_000_000)
        assertFalse(transaction.expired(1_100_000))
        assertTrue(transaction.expired(1_600_001))
        assertTrue(transaction.expired(999_999))
    }
}
