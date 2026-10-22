package fr.tf1plus

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.CancellationException
import org.json.JSONException

/** In-memory diagnostics, with fixed operation labels and no sensitive content. */
internal object TF1Diagnostics {
    enum class Operation(val area: String, val label: String) {
        LOGIN("Auth", "Account sign-in"),
        SESSION("Auth", "Session check"),
        REFRESH("Auth", "Session refresh"),
        BOOTSTRAP("Auth", "Gigya bootstrap"),
        GIGYA("Auth", "Gigya authentication"),
        TOKEN("Auth", "Playback token exchange"),
        LOGOUT("Auth", "Account sign-out"),
        CATEGORIES("Catalogue", "Load categories"),
        PROGRAMS("Catalogue", "Load programmes"),
        PROGRAM("Catalogue", "Load programme details"),
        EDITORIAL("Catalogue", "Load editorial seasons"),
        EPISODES("Catalogue", "Load replay episodes"),
        SEARCH_PROGRAMS("Search", "Search programmes"),
        SEARCH_VIDEOS("Search", "Search replays"),
        PLAYBACK("Playback", "Resolve replay"),
        LICENSE("License", "Request Widevine license")
    }

    enum class Event(val label: String, val area: String) {
        EPISODE_SELECTED("Programme selected", "Playback"),
        PLAYBACK_REQUESTED("Replay link requested", "Playback"),
        LINK_EMITTED("DRM stream handed to player", "Playback"),
        PLAYER_INITIALIZED("Player requested media interceptor", "Playback"),
        PLUGIN_LOADED("Plugin loaded", "Plugin"),
        PLUGIN_CLOSED("Plugin unloaded", "Plugin"),
        TOKEN_REUSED("Playback token reused", "Auth"),
        ACCOUNT_MISSING("No saved account", "Auth"),
        STORAGE_FAILED("Unable to read encrypted credentials", "Auth"),
        EDITORIAL_FALLBACK("Season list unavailable; using replay list", "Catalogue"),
        NO_REPLAYS("No replay episodes returned", "Catalogue"),
        MANIFEST_READY("DASH manifest resolved", "Playback"),
        LICENSE_REQUEST("License requested by player", "License"),
        LICENSE_OK("License response accepted", "License"),
        CONNECTED("Standard receiver connected", "Cast"),
        PREPARING("Prepare replay", "Cast"),
        SESSION("Check Cast session", "Cast"),
        NETWORK("Select network interface", "Cast"),
        FETCH("Fetch manifest", "Cast"),
        REWRITE("Prepare relayed manifest", "Cast"),
        READY("Relay link ready", "Cast"),
        LOAD_SENT("Playback command sent", "Cast"),
        LOAD_ACCEPTED("Command accepted by TV", "Cast"),
        MANIFEST("Manifest requested by TV", "Cast"),
        LICENSE("License requested by TV", "Cast"),
        CAST_LICENSE_OK("License sent to TV", "Cast"),
        LICENSE_FAILED("License request failed", "Cast"),
        PEER_REJECTED("Relay connection rejected: unexpected device", "Cast"),
        STOPPED("Relay stopped", "Cast")
    }

    private val buffer = TF1DiagnosticBuffer()

    private var stage = Event.SESSION

    @Synchronized fun record(event: Event) {
        if (event.area == "Cast") stage = event
        buffer.append("[${event.area}] ${event.label}")
    }

    @Synchronized fun failed(): String = stage.name.also {
        buffer.append("[Cast] Failed at stage $it")
    }

    fun rejected(status: Int) { buffer.append("[Cast] Command rejected (code $status)") }

    fun failure(operation: Operation, error: Exception) {
        val detail = when (error) {
            is TF1HttpException -> " (HTTP ${error.status})"
            is TF1DeliveryException -> " (delivery ${error.code})"
            is TF1AuthException -> " (Gigya error ${error.code})"
            is IOException -> " (network)"
            is JSONException -> " (invalid JSON)"
            else -> ""
        }
        buffer.append("[${operation.area}] ${operation.label}: failed$detail")
    }

    fun <T> trace(operation: Operation, block: () -> T): T {
        buffer.append("[${operation.area}] ${operation.label}: started")
        return try {
            block().also { value ->
                val count = if (value is Collection<*>) " (${value.size} results)" else ""
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
    fun report(): String = "TF1Plus - plugin diagnostics\n" + snapshot() + "\n"
    @Synchronized fun clear() { buffer.clear(); stage = Event.SESSION }
}

/** Buffer keeps at most 100 entries, and discards raw exception messages and request payloads. */
internal class TF1DiagnosticBuffer(
    private val capacity: Int = 100,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: TimeZone = TimeZone.getDefault()
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
