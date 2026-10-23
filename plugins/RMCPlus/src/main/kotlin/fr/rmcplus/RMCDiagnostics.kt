package fr.rmcplus

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object RMCDiagnostics {
    enum class Cast(val label: String) {
        CONNECTED("Standard receiver connected"),
        PREPARING("Prepare replay"),
        SESSION("Check Cast session"),
        NETWORK("Select network interface"),
        FETCH("Fetch manifest"),
        REWRITE("Prepare relayed manifest"),
        READY("Relay link ready"),
        DIRECT("Unprotected stream sent without relay"),
        UNSUPPORTED("Widevine HLS replay cannot be relayed"),
        LOAD_SENT("Playback command sent"),
        LOAD_ACCEPTED("Command accepted by TV"),
        MANIFEST("Manifest requested by TV"),
        LICENSE("License requested by TV"),
        LICENSE_OK("License sent to TV"),
        LICENSE_FAILED("License request failed"),
        PEER_REJECTED("Relay connection rejected: unexpected device"),
        STOPPED("Relay stopped")
    }
    private val entries = ArrayDeque<String>()
    private var stage = Cast.SESSION
    @Synchronized fun record(event: Cast) { stage = event; record("[Cast] ${event.label}") }
    @Synchronized fun failed(): String = stage.name.also { record("[Cast] Failed at stage $it") }
    fun rejected(status: Int) = record("[Cast] Command rejected (code $status)")
    @Synchronized fun record(event: String) {
        if (entries.size == 100) entries.removeFirst()
        entries.addLast("[${SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT).format(Date())}] $event")
    }
    @Synchronized fun report() = "RMCPlus - plugin diagnostics\n" + entries.joinToString("\n") + "\n"
    @Synchronized fun clear() { entries.clear(); stage = Cast.SESSION }
    fun <T> trace(operation: String, block: () -> T): T {
        record("$operation: started")
        return try { block().also { record("$operation: succeeded") } }
        catch (e: Exception) {
            record("$operation: failed" + if (e is RMCException) " (HTTP ${e.status})" else "")
            throw e
        }
    }
}
