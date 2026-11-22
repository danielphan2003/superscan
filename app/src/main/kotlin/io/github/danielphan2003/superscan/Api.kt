package io.github.danielphan2003.superscan

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class Check(val id: String, val name: String, val group: String, val status: String, val note: String)
data class Note(val at: String, val text: String)
data class Incident(
    val id: Int,
    val title: String,
    val started: String,
    val ended: String?,
    val checks: List<String>,
    val notes: List<Note>,
)
data class Status(val name: String, val now: String, val checks: List<Check>, val incidents: List<Incident>)

class ApiException(val code: Int, message: String) : IOException(message)

/** The monitor's small JSON API (docs: the uptime Worker's /api routes). */
class Api(private val server: String, private val token: String? = null) {

    private suspend fun call(method: String, path: String, body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(server.trimEnd('/') + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("Accept", "application/json")
                conn.setRequestProperty("User-Agent", "Superscan/${BuildConfig.VERSION_NAME}")
                token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                val code = conn.responseCode
                val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                val json = runCatching { JSONObject(text) }.getOrNull()
                if (code !in 200..299) throw ApiException(code, json?.optString("error")?.ifEmpty { null } ?: "HTTP $code")
                json ?: JSONObject()
            } finally {
                conn.disconnect()
            }
        }

    /** Public: the monitor's name and VAPID key, before pairing. */
    suspend fun info(): Pair<String, String?> {
        val j = call("GET", "/api/info")
        return j.optString("name", "monitor") to j.optString("vapid").ifEmpty { null }
    }

    /** Trade a one-time pairing code for this device's token. */
    suspend fun pair(code: String): String =
        call("POST", "/api/pair", JSONObject().put("code", code.trim()).put("device", "${Build.MANUFACTURER} ${Build.MODEL}"))
            .getString("token")

    suspend fun sendSubscription(endpoint: String, p256dh: String, auth: String) {
        call("POST", "/api/subscription", JSONObject().put("endpoint", endpoint).put("p256dh", p256dh).put("auth", auth))
    }

    suspend fun unpair() {
        call("DELETE", "/api/device")
    }

    /** A short-lived link that signs this browser in to the status page. */
    suspend fun webLink(): String = call("POST", "/api/weblink").getString("url")

    suspend fun status(): Status {
        val j = call("GET", "/api/status")
        return Status(
            name = j.optString("name"),
            now = j.optString("now"),
            checks = j.getJSONArray("checks").objects().map {
                Check(it.getString("id"), it.getString("name"), it.getString("group"), it.getString("status"), it.optString("note"))
            },
            incidents = j.getJSONArray("incidents").objects().map { i ->
                Incident(
                    id = i.getInt("id"),
                    title = i.getString("title"),
                    started = i.getString("started"),
                    ended = i.optString("ended").ifEmpty { null }.takeIf { !i.isNull("ended") },
                    checks = i.getJSONArray("checks").strings(),
                    notes = i.getJSONArray("notes").objects().map { Note(it.getString("at"), it.getString("text")) },
                )
            },
        )
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }
private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
