package fr.m6plus.settings

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
import android.widget.Button
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import fr.m6plus.diagnostics.M6Diagnostics
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import fr.m6plus.M6Runtime

class M6SettingsFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (24 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, padding / 2)
        }
        val status = TextView(context).apply {
            text = if (M6Runtime.sessions.isConfigured()) "M6+ account saved." else "Sign in with your M6+ account."
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
                val report = M6Diagnostics.report()
                AlertDialog.Builder(context).setTitle("Plugin diagnostics").setMessage(report)
                    .setPositiveButton("Close", null)
                    .setNeutralButton("Copy") { _, _ -> copyDiagnosticReport(context, report) }
                    .setNegativeButton("Clear") { _, _ -> M6Diagnostics.clear() }
                    .show()
            }
        })
        val dialog = AlertDialog.Builder(context)
            .setTitle("M6+")
            .setView(layout)
            .setPositiveButton("Sign in", null)
            .setNeutralButton("Sign out", null)
            .setNegativeButton("Close", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val connect = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val disconnect = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            connect.setOnClickListener {
                val login = email.text.toString().trim()
                val secret = password.text.toString()
                if (login.isBlank() || secret.isEmpty()) {
                    status.text = "Enter your email address and password."
                    return@setOnClickListener
                }
                password.text.clear()
                connect.isEnabled = false
                disconnect.isEnabled = false
                status.text = "Signing in..."
                val activity = activity ?: return@setOnClickListener
                Thread {
                    val message = try {
                        M6Runtime.sessions.login(login, secret)
                        M6Runtime.clearPlaybackCache()
                        "Signed in to M6+."
                    } catch (_: Exception) { "Sign-in failed. Check your credentials and internet connection." }
                    activity.runOnUiThread {
                        if (isAdded && dialog.isShowing) {
                            status.text = message
                            connect.isEnabled = true
                            disconnect.isEnabled = true
                        }
                    }
                }.start()
            }
            disconnect.setOnClickListener {
                M6Runtime.stopCast()
                M6Runtime.sessions.logout()
                email.text.clear()
                password.text.clear()
                status.text = "Signed out of M6+."
            }
            email.requestFocus()
        }
        return dialog
    }
}

internal fun copyDiagnosticReport(context: Context, report: String) {
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
        .setPrimaryClip(ClipData.newPlainText("M6Plus plugin diagnostics", report))
}
