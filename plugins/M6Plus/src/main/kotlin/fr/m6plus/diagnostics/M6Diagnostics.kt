package fr.m6plus.diagnostics

import fr.m6plus.api.M6ApiException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CancellationException
import org.json.JSONException

/** Only fixed labels and numeric status/counts enter this memory-only report. */
internal object M6Diagnostics {
    enum class Operation(val area: String, val label: String) {
        LOGIN("Auth", "Account sign-in"),
        SESSION("Auth", "Session check"),
        REFRESH("Auth", "Session refresh"),
        LOGOUT("Auth", "Account sign-out"),
        CATEGORIES("Catalogue", "Load categories"),
        PROGRAMS("Catalogue", "Load programmes"),
        PROGRAM("Catalogue", "Load programme details"),
        EPISODES("Catalogue", "Load episodes"),
        SEARCH("Search", "Primary search"),
        SEARCH_FALLBACK("Search", "Catalogue search fallback"),
        PLAYBACK("Playback", "Resolve replay"),
        LICENSE("License", "Request Widevine license"),
        NATIVE_PREPARE("Native Cast", "Prepare replay and profiles"),
    }

    enum class Event(val label: String, val area: String = "Cast") {
        PLUGIN_LOADED("Plugin loaded", "Plugin"),
        PLUGIN_CLOSED("Plugin unloaded", "Plugin"),
        SESSION_REUSED("Valid session reused", "Auth"),
        ACCOUNT_MISSING("No saved account", "Auth"),
        STORAGE_FAILED("Unable to read the saved account", "Auth"),
        SEASON_FALLBACK("Season category unavailable; using the unfiltered list", "Catalogue"),
        CONNECTED("Standard receiver connected"),
        PREPARING("Prepare replay"),
        SESSION("Check Cast session"),
        NETWORK("Select network interface"),
        FETCH("Fetch manifest"),
        REWRITE("Prepare relayed manifest"),
        READY("Relay link ready"),
        LOAD_SENT("Playback command sent"),
        LOAD_ACCEPTED("Command accepted by TV"),
        MANIFEST("Manifest requested by TV"),
        LICENSE("License requested by TV"),
        LICENSE_OK("License sent to TV"),
        LICENSE_FAILED("License request failed"),
        PEER_REJECTED("Relay connection rejected: unexpected device"),
        STOPPED("Relay stopped"),
        NATIVE_CONNECTING("Select receiver", "Native Cast"),
        NATIVE_SENT("Playback command sent", "Native Cast"),
        NATIVE_ACCEPTED("Command accepted by TV", "Native Cast"),
        NATIVE_FAILED("Connection or playback launch failed", "Native Cast"),
        NATIVE_STOPPED("Session stopped", "Native Cast"),
        LOCAL_LICENSE_REQUEST("License requested by player", "License"),
        LOCAL_LICENSE_OK("License sent to player", "License"),
    }

    private val buffer = M6DiagnosticBuffer()
    private var stage = Event.SESSION

    @Synchronized fun record(event: Event) {
        if (event.area == "Cast") stage = event
        buffer.append("[${event.area}] ${event.label}")
    }

    @Synchronized fun failed(): String {
        val code = stage.name
        buffer.append("[Cast] Failed at stage $code")
        return code
    }

    fun rejected(status: Int) { buffer.append("[Cast] Command rejected (code $status)") }

    fun failure(operation: Operation, error: Exception) {
        val reason = when (error) {
            is M6ApiException -> " (HTTP ${error.status})"
            is IOException -> " (network)"
            is JSONException -> " (invalid response)"
            else -> ""
        }
        buffer.append("[${operation.area}] ${operation.label}: failed$reason")
    }

    fun <T> trace(operation: Operation, block: () -> T): T {
        buffer.append("[${operation.area}] ${operation.label}: started")
        return try {
            block().also { result ->
                val count = if (result is Collection<*>) " (${result.size} results)" else ""
                buffer.append("[${operation.area}] ${operation.label}: succeeded$count")
            }
        } catch (error: CancellationException) {
            buffer.append("[${operation.area}] ${operation.label}: cancelled")
            throw error
        } catch (error: Exception) {
            failure(operation, error)
            throw error
        }
    }

    fun snapshot(): String = buffer.snapshot()
    fun report(): String = "M6Plus - plugin diagnostics\n" + snapshot() + "\n"
    @Synchronized fun clear() { buffer.clear(); stage = Event.SESSION }
}

internal class M6DiagnosticBuffer(
    private val capacity: Int = 100,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: TimeZone = TimeZone.getDefault(),
) {
    init { require(capacity > 0) }
    private val entries = ArrayDeque<String>()

    @Synchronized fun append(message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.ROOT).apply {
            timeZone = zone
        }.format(Date(now()))
        if (entries.size == capacity) entries.removeFirst()
        entries.addLast("[$timestamp] $message")
    }

    @Synchronized fun snapshot(): String = if (entries.isEmpty())
        "No operations recorded in this session." else entries.joinToString("\n")
    @Synchronized fun clear() = entries.clear()
}
