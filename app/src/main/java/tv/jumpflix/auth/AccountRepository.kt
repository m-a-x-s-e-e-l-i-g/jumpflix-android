// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.auth

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AccountRepository(context: Context) {
    private val store = SessionStore(context)
    private val mutex = Mutex()
    private val api: AuthApi
    private val baseUrl: String
    private var session: AuthSession? = null
    init {
        val config = JSONObject(context.assets.open("auth-config.json").bufferedReader().use { it.readText() })
        val url = config.getString("url")
        require(java.net.URI(url).scheme == "https")
        baseUrl = url
        api = AuthApi(url, config.getString("publishableKey"))
    }
    suspend fun restore(): AccountProfile? = withContext(Dispatchers.IO) {
        mutex.withLock {
            var saved = store.load() ?: return@withLock null
            try {
                if (saved.expiresAt <= System.currentTimeMillis() / 1000 + 60) {
                    saved = api.refresh(saved.refreshToken)
                    store.save(saved)
                }
                api.verify(saved)
                session = saved
                saved.profile
            } catch (error: AuthFailure) {
                if (error.status in setOf(400, 401, 403, 422)) { store.clear(); session = null }
                throw error
            }
        }
    }
    suspend fun signIn(email: String, password: String): AccountProfile = withContext(Dispatchers.IO) {
        mutex.withLock {
            val signedIn = api.signIn(email, password)
            store.save(signedIn)
            store.clearPending()
            session = signedIn
            signedIn.profile
        }
    }
    suspend fun beginGoogle(): String = withContext(Dispatchers.IO) {
        mutex.withLock {
            val transaction = GoogleOAuth.create()
            store.savePending(transaction)
            GoogleOAuth.authorizationUrl(baseUrl, transaction)
        }
    }
    suspend fun completeGoogle(uri: java.net.URI): AccountProfile = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(GoogleOAuth.isCallback(uri)) { "Invalid sign-in return link." }
            val transaction = store.takePending()?.takeUnless { it.expired() }
                ?: throw IllegalArgumentException("Google sign-in expired. Please start it again in the app.")
            val signedIn = api.exchangeCode(GoogleOAuth.code(uri), transaction.verifier)
            api.verify(signedIn)
            store.save(signedIn)
            session = signedIn
            signedIn.profile
        }
    }
    suspend fun cancelGoogle() = withContext(Dispatchers.IO) { mutex.withLock { store.clearPending() } }
    suspend fun signOut() = withContext(Dispatchers.IO) {
        mutex.withLock {
            var saved = session ?: store.load()
            // Keep the session if revocation fails, so a network error is retryable.
            if (saved != null) {
                try {
                    if (saved.expiresAt <= System.currentTimeMillis() / 1000 + 60) {
                        saved = api.refresh(saved.refreshToken)
                        store.save(saved)
                        session = saved
                    }
                    api.signOut(saved.accessToken)
                } catch (error: AuthFailure) {
                    // Invalid/revoked sessions can still be removed from this device.
                    if (error.status !in setOf(400, 401, 403, 422)) throw error
                }
            }
            store.clear()
            session = null
        }
    }
}
