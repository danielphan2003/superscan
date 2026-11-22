package io.github.danielphan2003.superscan

import android.content.Context
import androidx.core.content.edit

/** What the app remembers: the monitor it is paired with and its push registration. */
class Store(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("superscan", Context.MODE_PRIVATE)

    var server: String?
        get() = prefs.getString("server", null)
        set(v) = prefs.edit { putString("server", v) }

    /** Device token the monitor issued at pairing; sent as a bearer token. */
    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit { putString("token", v) }

    var monitorName: String?
        get() = prefs.getString("monitorName", null)
        set(v) = prefs.edit { putString("monitorName", v) }

    /** The push endpoint and keys the distributor gave us, and whether the monitor has them. */
    var endpoint: String?
        get() = prefs.getString("endpoint", null)
        set(v) = prefs.edit { putString("endpoint", v) }
    var p256dh: String?
        get() = prefs.getString("p256dh", null)
        set(v) = prefs.edit { putString("p256dh", v) }
    var auth: String?
        get() = prefs.getString("auth", null)
        set(v) = prefs.edit { putString("auth", v) }
    var endpointSent: Boolean
        get() = prefs.getBoolean("endpointSent", false)
        set(v) = prefs.edit { putBoolean("endpointSent", v) }

    /** Last push registration problem, shown in the app until it is fixed. */
    var pushProblem: String?
        get() = prefs.getString("pushProblem", null)
        set(v) = prefs.edit { putString("pushProblem", v) }

    val paired: Boolean get() = server != null && token != null

    fun clear() = prefs.edit { clear() }
}
