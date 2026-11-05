package fr.tf1plus

import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText

/**
 * Declares what each sign-in field holds, so the keyboard and password managers
 * offer an email in the email field and a password in the password field.
 */
internal fun markCredentialFields(email: EditText, password: EditText) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        email.setAutofillHints(View.AUTOFILL_HINT_EMAIL_ADDRESS, View.AUTOFILL_HINT_USERNAME)
        password.setAutofillHints(View.AUTOFILL_HINT_PASSWORD)
        email.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
        password.importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
    }
}

/**
 * Gives the sign-in form an explicit up/down focus order for TV remotes.
 *
 * Views built in code have no ids, so Android picks the next view by geometry, starting
 * from the text cursor (TextView.getFocusedRect) rather than from the field. DPAD_DOWN in
 * an EditText whose cursor is not at the end also only moves the cursor. Depending on the
 * text entered, focus then stays on the email field or lands past the password field.
 */
internal fun chainFormFocus(vararg views: View) {
    views.forEach { if (it.id == View.NO_ID) it.id = View.generateViewId() }
    views.forEachIndexed { index, view ->
        val previous = views.getOrNull(index - 1)
        val next = views.getOrNull(index + 1)
        previous?.let { view.nextFocusUpId = it.id }
        next?.let {
            view.nextFocusDownId = it.id
            view.nextFocusForwardId = it.id
        }
        if (view !is EditText) return@forEachIndexed
        view.imeOptions = if (next is EditText) EditorInfo.IME_ACTION_NEXT else EditorInfo.IME_ACTION_DONE
        view.setOnEditorActionListener { _, actionId, _ ->
            actionId == EditorInfo.IME_ACTION_NEXT && next?.requestFocus() == true
        }
        // Runs before the EditText's own handling, which would move the cursor instead.
        view.setOnKeyListener { _, keyCode, event ->
            val target = when (keyCode) {
                KeyEvent.KEYCODE_DPAD_DOWN -> next
                KeyEvent.KEYCODE_DPAD_UP -> previous
                else -> null
            } ?: return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_DOWN) target.requestFocus()
            true
        }
    }
}
