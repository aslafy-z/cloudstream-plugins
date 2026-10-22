package fr.m6plus.settings

import android.content.ClipboardManager
import android.content.Context
import android.content.ClipDescription
import fr.m6plus.diagnostics.M6Diagnostics
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [28])
class M6DiagnosticClipboardTest {
    @Test fun `copy preserves the displayed report and every newline`() {
        M6Diagnostics.clear()
        try {
            M6Diagnostics.record(M6Diagnostics.Event.PLUGIN_LOADED)
            M6Diagnostics.record(M6Diagnostics.Event.CONNECTED)
            val displayedReport = M6Diagnostics.report()
            val context = RuntimeEnvironment.getApplication()
            copyDiagnosticReport(context, displayedReport)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip!!
            assertTrue(clip.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN))
            val copied = clip.getItemAt(0).coerceToText(context).toString()
            assertEquals(displayedReport, copied)
            assertEquals(3, copied.count { it == '\n' })
            assertFalse(copied.contains("\\n"))
        } finally {
            M6Diagnostics.clear()
        }
    }
}
