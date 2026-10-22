package fr.tf1plus

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

internal interface TF1CredentialsStore {
    fun save(credentials: TF1Credentials)
    fun read(): TF1Credentials?
    fun clear()
}

internal class TF1EncryptedStore(context: Context) : TF1CredentialsStore {
    private val preferences = context.applicationContext.getSharedPreferences("tf1plus", Context.MODE_PRIVATE)
    private val alias = "fr.tf1plus.credentials.v1"

    override fun save(credentials: TF1Credentials) {
        val serialized = JSONObject().put("email", credentials.email)
            .put("password", credentials.password).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.iv + cipher.doFinal(serialized)
        check(preferences.edit().putString("account",
            Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit()) {
            "Could not securely store TF1+ credentials."
        }
    }

    override fun read(): TF1Credentials? = preferences.getString("account", null)?.let { value ->
        try {
            val bytes = Base64.decode(value, Base64.NO_WRAP)
            if (bytes.size < 29) return null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
            val json = JSONObject(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8))
            TF1Credentials(json.getString("email"), json.getString("password"))
        } catch (_: Exception) {
            TF1Diagnostics.record(TF1Diagnostics.Event.STORAGE_FAILED)
            null // Android Keystore keys cannot be restored from device backups.
        }
    }

    override fun clear() {
        preferences.edit().remove("account").apply()
    }

    @Synchronized private fun secretKey(): SecretKey {
        val keystore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keystore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256).build())
        return generator.generateKey()
    }
}

class TF1SettingsFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        val status = TextView(context).apply {
            text = if (TF1Runtime.auth.isConfigured()) "TF1+ account saved." else "Sign in with your TF1+ account."
        }
        val email = EditText(context).apply {
            hint = "Email address"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            isSingleLine = true
            isSaveEnabled = false
        }
        val password = EditText(context).apply {
            hint = "Password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            isSaveEnabled = false
        }
        layout.addView(status)
        layout.addView(email)
        layout.addView(password)
        layout.addView(Button(context).apply {
            text = "Plugin diagnostics"
            setOnClickListener {
                val report = TF1Diagnostics.report()
                AlertDialog.Builder(context).setTitle("Plugin diagnostics").setMessage(report)
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Copy") { _, _ -> copyTF1DiagnosticReport(context, report) }
                    .setNegativeButton("Clear") { _, _ -> TF1Diagnostics.clear() }
                    .show()
            }
        })
        val dialog = AlertDialog.Builder(context).setTitle("TF1+").setView(layout)
            .setPositiveButton("Sign in", null)
            .setNeutralButton("Sign out", null)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val connect = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val disconnect = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            connect.setOnClickListener {
                val address = email.text.toString().trim()
                val secret = password.text.toString()
                if (address.isBlank() || secret.isBlank()) {
                    status.text = "Enter your email address and password."
                    return@setOnClickListener
                }
                password.text.clear()
                connect.isEnabled = false
                disconnect.isEnabled = false
                status.text = "Signing in..."
                val owner = activity ?: return@setOnClickListener
                Thread {
                    val result = try {
                        TF1Runtime.auth.signIn(address, secret)
                        "Signed in to TF1+."
                    } catch (_: Exception) {
                        "TF1+ sign-in failed. Check your credentials and network."
                    }
                    owner.runOnUiThread {
                        if (isAdded && dialog.isShowing) {
                            status.text = result
                            connect.isEnabled = true
                            disconnect.isEnabled = true
                        }
                    }
                }.start()
            }
            disconnect.setOnClickListener {
                TF1Runtime.auth.signOut()
                email.text.clear()
                password.text.clear()
                status.text = "Signed out of TF1+."
            }
            email.requestFocus()
        }
        return dialog
    }
}

internal fun copyTF1DiagnosticReport(context: Context, report: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("TF1Plus plugin diagnostics", report))
}
