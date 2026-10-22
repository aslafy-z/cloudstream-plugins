package fr.rmcplus

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object RMCDiagnostics {
    private val entries = ArrayDeque<String>()
    @Synchronized fun record(event: String) {
        if (entries.size == 100) entries.removeFirst()
        entries.addLast("[${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT).format(Date())}] $event")
    }
    @Synchronized fun report() = "RMCPlus - plugin diagnostics\n" + entries.joinToString("\n") + "\n"
    @Synchronized fun clear() = entries.clear()
    fun <T> trace(operation: String, block: () -> T): T {
        record("$operation: started")
        return try { block().also { record("$operation: succeeded") } }
        catch (e: Exception) {
            record("$operation: failed" + if (e is RMCException) " (HTTP ${e.status})" else "")
            throw e
        }
    }
}
