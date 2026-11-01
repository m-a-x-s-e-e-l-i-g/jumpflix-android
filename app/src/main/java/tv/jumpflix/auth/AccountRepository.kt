// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.auth

import android.content.Context
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

class AccountRepository private constructor(context: Context) {
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
                saved = AuthSession(saved.accessToken, saved.refreshToken, saved.expiresAt, api.verify(saved))
                store.save(saved)
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
            var current = session ?: store.load()
            if (current != null && current.expiresAt <= System.currentTimeMillis() / 1000 + 60) {
                current = api.refresh(current.refreshToken)
                store.save(current)
                session = current
            }
            val transaction = GoogleOAuth.create().copy(expectedUserId = current?.profile?.id)
            val url = if (current != null) api.linkGoogle(current, transaction) else GoogleOAuth.authorizationUrl(baseUrl, transaction)
            store.savePending(transaction)
            url
        }
    }
    suspend fun completeGoogle(uri: java.net.URI): AccountProfile = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(GoogleOAuth.isCallback(uri)) { "Invalid sign-in return link." }
            val transaction = store.takePending()?.takeUnless { it.expired() }
                ?: throw IllegalArgumentException("Google sign-in expired. Please start it again in the app.")
            val signedIn = api.exchangeCode(GoogleOAuth.code(uri), transaction.verifier)
            require(transaction.expectedUserId == null || signedIn.profile.id == transaction.expectedUserId) {
                "Google could not be connected to this JUMPFLIX account. Your existing session has been kept."
            }
            val verified = AuthSession(signedIn.accessToken, signedIn.refreshToken, signedIn.expiresAt, api.verify(signedIn))
            store.save(verified)
            session = verified
            verified.profile
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

    internal suspend fun signedInUserId(): String? = withContext(Dispatchers.IO) { mutex.withLock { store.load()?.profile?.id } }

    /** Serialize token rotation and bind every data request to its initiating account. */
    internal suspend fun <T> authenticated(expectedUserId: String, action: (AuthSession) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            var current = store.load() ?: throw IOException("Sign in to sync with JUMPFLIX.")
            if (current.profile.id != expectedUserId) throw IOException("The signed-in account changed. Please try again.")
            if (current.expiresAt <= System.currentTimeMillis() / 1000 + 60) {
                current = api.refresh(current.refreshToken)
                check(current.profile.id == expectedUserId) { "The account session changed." }
                store.save(current)
            }
            session = current
            action(current)
        }
    }

    companion object {
        @Volatile private var instance: AccountRepository? = null
        fun get(context: Context): AccountRepository = instance ?: synchronized(this) {
            instance ?: AccountRepository(context.applicationContext).also { instance = it }
        }
    }
}
