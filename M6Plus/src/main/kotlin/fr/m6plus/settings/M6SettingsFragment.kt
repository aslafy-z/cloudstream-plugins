package fr.m6plus.settings

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.WindowManager
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
            text = if (M6Runtime.sessions.isConfigured()) "Compte M6+ enregistré." else "Connectez-vous avec votre compte M6+."
        }
        val email = EditText(context).apply {
            hint = "Adresse e-mail"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            isSingleLine = true
            isSaveEnabled = false
        }
        val password = EditText(context).apply {
            hint = "Mot de passe"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            isSaveEnabled = false
        }
        layout.addView(status)
        layout.addView(email)
        layout.addView(password)
        val dialog = AlertDialog.Builder(context)
            .setTitle("M6+")
            .setView(layout)
            .setPositiveButton("Connexion", null)
            .setNeutralButton("Déconnexion", null)
            .setNegativeButton("Fermer", null)
            .create()
        dialog.setOnShowListener {
            dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val connect = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val disconnect = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            connect.setOnClickListener {
                val login = email.text.toString().trim()
                val secret = password.text.toString()
                if (login.isBlank() || secret.isEmpty()) {
                    status.text = "Saisissez votre e-mail et votre mot de passe."
                    return@setOnClickListener
                }
                password.text.clear()
                connect.isEnabled = false
                disconnect.isEnabled = false
                status.text = "Connexion en cours…"
                val activity = activity ?: return@setOnClickListener
                Thread {
                    val message = try {
                        M6Runtime.sessions.login(login, secret)
                        "Connexion M6+ réussie."
                    } catch (_: Exception) { "Connexion impossible. Vérifiez vos identifiants et votre connexion Internet." }
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
                M6Runtime.sessions.logout()
                email.text.clear()
                password.text.clear()
                status.text = "Compte M6+ déconnecté."
            }
            email.requestFocus()
        }
        return dialog
    }
}
