// SPDX-License-Identifier: GPL-3.0-only
package tv.jumpflix.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** Keystore-backed encryption; the password is never stored. Backups are disabled. */
class SessionStore(context: Context, private val namespace: String = "jumpflix-auth") {
    private val prefs = context.getSharedPreferences(namespace, Context.MODE_PRIVATE)
    private val alias = "$namespace.session.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
    fun save(session: AuthSession) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ciphertext = cipher.doFinal(session.toJson().toString().toByteArray(Charsets.UTF_8))
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." + Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        check(prefs.edit().putString("session", value).commit()) { "Could not save the account session securely." }
    }
    fun load(): AuthSession? {
        val value = prefs.getString("session", null) ?: return null
        return runCatching {
            val parts = value.split('.')
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            }
            AuthSession.parse(JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)))
        }.getOrElse { clear(); null }
    }
    fun clear() { prefs.edit().remove("session").remove("google-pkce").commit() }
    fun savePending(transaction: OAuthTransaction) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val data = JSONObject().put("verifier", transaction.verifier).put("createdAt", transaction.createdAt).toString()
        val value = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." +
            Base64.encodeToString(cipher.doFinal(data.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
        check(prefs.edit().putString("google-pkce", value).commit()) { "Could not start Google sign-in securely." }
    }
    fun takePending(): OAuthTransaction? {
        val value = prefs.getString("google-pkce", null) ?: return null
        clearPending()
        return runCatching {
            val parts = value.split('.')
            require(parts.size == 2)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
            }
            val json = JSONObject(String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8))
            OAuthTransaction(json.getString("verifier"), json.getLong("createdAt"))
        }.getOrNull()
    }
    fun clearPending() { prefs.edit().remove("google-pkce").commit() }
}
