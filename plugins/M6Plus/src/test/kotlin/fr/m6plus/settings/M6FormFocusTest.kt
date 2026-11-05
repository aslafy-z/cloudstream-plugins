package fr.m6plus.settings

import android.app.Activity
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class M6FormFocusTest {
    private lateinit var email: EditText
    private lateinit var password: EditText
    private lateinit var diagnostics: Button
    private lateinit var connect: Button

    @Before fun setUp() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        email = EditText(activity).apply { isSingleLine = true }
        password = EditText(activity).apply { isSingleLine = true }
        diagnostics = Button(activity)
        connect = Button(activity)
        activity.setContentView(LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            listOf(email, password, diagnostics, connect).forEach(::addView)
        })
        chainFormFocus(email, password, diagnostics, connect)
    }

    private fun press(keyCode: Int) {
        val focused = listOf(email, password, diagnostics, connect).single { it.isFocused }
        focused.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        focused.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    @Test fun `down leaves the email field even when the cursor is not at the end`() {
        email.setText("someone@example.com")
        email.requestFocus()
        email.setSelection(3)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue(password.isFocused)
        press(KeyEvent.KEYCODE_DPAD_DOWN)
        assertTrue(diagnostics.isFocused)
    }

    @Test fun `up from the password field returns to the email field`() {
        password.setText("secret")
        password.requestFocus()
        password.setSelection(0)
        press(KeyEvent.KEYCODE_DPAD_UP)
        assertTrue(email.isFocused)
    }

    @Test fun `keyboard next moves from email to password`() {
        assertEquals(EditorInfo.IME_ACTION_NEXT, email.imeOptions)
        assertEquals(EditorInfo.IME_ACTION_DONE, password.imeOptions)
        email.requestFocus()
        email.onEditorAction(EditorInfo.IME_ACTION_NEXT)
        assertTrue(password.isFocused)
    }

    @Test fun `fields are tagged as email and password for autofill`() {
        markCredentialFields(email, password)
        assertEquals(
            listOf(android.view.View.AUTOFILL_HINT_EMAIL_ADDRESS, android.view.View.AUTOFILL_HINT_USERNAME),
            email.autofillHints!!.toList()
        )
        assertEquals(listOf(android.view.View.AUTOFILL_HINT_PASSWORD), password.autofillHints!!.toList())
    }

    @Test fun `password stays readable when built like the settings dialog`() {
        val field = EditText(email.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
        }
        markCredentialFields(email, field)
        assertTrue(field.transformationMethod !is android.text.method.PasswordTransformationMethod)
    }

    @Test fun `explicit order covers the buttons`() {
        assertEquals(password.id, email.nextFocusDownId)
        assertEquals(diagnostics.id, password.nextFocusDownId)
        assertEquals(connect.id, diagnostics.nextFocusDownId)
        assertEquals(diagnostics.id, connect.nextFocusUpId)
    }
}
