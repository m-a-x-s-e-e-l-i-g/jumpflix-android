// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import tv.jumpflix.auth.AuthSession
import tv.jumpflix.auth.SessionStore
import tv.jumpflix.auth.OAuthTransaction

class SessionStoreTest {
    @Test fun tokensAreEncryptedAndSurviveStoreRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val namespace = "test-session-${java.util.UUID.randomUUID()}"
        val store = SessionStore(context, namespace)
        try {
            val session = AuthSession.parse(JSONObject("""{"access_token":"test-access-sensitive","refresh_token":"test-refresh-sensitive","expires_at":2000000000,"user":{"id":"test-user","email":"test@example.test"}}"""))
            store.save(session)
            val persisted = context.getSharedPreferences(namespace, 0).getString("session", "")!!
            assertFalse(persisted.contains("test-access-sensitive"))
            assertFalse(persisted.contains("test-refresh-sensitive"))
            assertFalse(persisted.contains("test@example.test"))
            assertEquals("test-refresh-sensitive", SessionStore(context, namespace).load()!!.refreshToken)
            store.savePending(OAuthTransaction("private-pkce-verifier", System.currentTimeMillis(), "test-user"))
            val pending = context.getSharedPreferences(namespace, 0).getString("google-pkce", "")!!
            assertFalse(pending.contains("private-pkce-verifier"))
            val restored = SessionStore(context, namespace).takePending()!!
            assertEquals("private-pkce-verifier", restored.verifier)
            assertEquals("test-user", restored.expectedUserId)
            assertNull(store.takePending())
            assertNotNull(store.load())
            store.clear()
            assertNull(store.load())
        } finally {
            context.deleteSharedPreferences(namespace)
            java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry("$namespace.session.v1") }
        }
    }
}
