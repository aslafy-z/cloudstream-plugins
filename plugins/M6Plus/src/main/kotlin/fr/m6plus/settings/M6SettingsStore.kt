package fr.m6plus.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import fr.m6plus.api.M6Session
import fr.m6plus.auth.M6SessionStorage
import fr.m6plus.auth.M6StoredAccount
import fr.m6plus.diagnostics.M6Diagnostics
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal class M6SettingsStore(context: Context) : M6SessionStorage {
    private val prefs = context.applicationContext.getSharedPreferences("m6plus", Context.MODE_PRIVATE)
    private val alias = "fr.m6plus.session.v1"

    override val deviceId: String get() = prefs.getString("device", null) ?: ("_luid_" + UUID.randomUUID()).also {
        prefs.edit().putString("device", it).apply()
    }

    override fun save(account: M6StoredAccount) {
        val data = JSONObject().put("email", account.email).put("password", account.password)
            .put("uid", account.session.uid).put("jwt", account.session.jwt).toString()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val packed = cipher.iv + cipher.doFinal(data.toByteArray(Charsets.UTF_8))
        // One encrypted record prevents mixing identities after a partial save.
        if (!prefs.edit().putString("account", Base64.encodeToString(packed, Base64.NO_WRAP)).commit()) {
            throw IllegalStateException("Unable to save the M6+ session on this device.")
        }
    }

    override fun read(): M6StoredAccount? = prefs.getString("account", null)?.let { encoded ->
        try {
            val packed = Base64.decode(encoded, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
            val data = JSONObject(String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8))
            M6StoredAccount(data.getString("email"), data.getString("password"), M6Session(data.getString("uid"), data.getString("jwt")))
        } catch (_: Exception) {
            M6Diagnostics.record(M6Diagnostics.Event.STORAGE_FAILED)
            null // Keystore state cannot be restored onto another device.
        }
    }

    override fun clear() {
        prefs.edit().remove("account").apply()
    }

    @Synchronized private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
}
