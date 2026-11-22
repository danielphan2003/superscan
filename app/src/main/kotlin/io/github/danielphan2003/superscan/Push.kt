package io.github.danielphan2003.superscan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

private const val TAG = "Superscan"
const val INSTANCE = "default"
const val EXTRA_INCIDENT = "incident"

/** Bumped whenever something the UI shows changes behind its back. */
object Events {
    val changed = MutableStateFlow(0L)
    fun emit() { changed.value = System.nanoTime() }
}

private val syncLock = Mutex()

/** Sends the current push endpoint to the monitor, if it does not have it yet. */
suspend fun syncEndpoint(context: Context) = syncLock.withLock {
    val store = Store(context)
    val server = store.server ?: return@withLock
    val token = store.token ?: return@withLock
    val endpoint = store.endpoint ?: return@withLock
    val p256dh = store.p256dh ?: return@withLock
    val auth = store.auth ?: return@withLock
    if (store.endpointSent) return@withLock
    try {
        Api(server, token).sendSubscription(endpoint, p256dh, auth)
        store.endpointSent = true
        store.pushProblem = null
    } catch (e: Exception) {
        Log.w(TAG, "could not send the endpoint", e)
        store.pushProblem = "Could not reach the monitor to register for alerts: ${e.message}"
    }
    Events.emit()
}

/** Registers with the UnifiedPush distributor, using the monitor's VAPID key. */
fun registerPush(context: Context, vapid: String?) {
    UnifiedPush.register(context, INSTANCE, messageForDistributor = "Superscan alerts", vapid = vapid)
}

class PushServiceImpl : PushService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        val store = Store(this)
        val keys = endpoint.pubKeySet
        if (keys == null) {
            store.pushProblem = "The distributor gave no encryption keys; update it or pick another."
            Events.emit()
            return
        }
        if (store.endpoint != endpoint.url || store.p256dh != keys.pubKey || store.auth != keys.auth) {
            store.endpoint = endpoint.url
            store.p256dh = keys.pubKey
            store.auth = keys.auth
            store.endpointSent = false
        }
        scope.launch { syncEndpoint(applicationContext) }
    }

    override fun onMessage(message: PushMessage, instance: String) {
        if (!message.decrypted) {
            Log.w(TAG, "dropped a message that was not encrypted for this app")
            return
        }
        val json = runCatching { JSONObject(message.content.toString(Charsets.UTF_8)) }.getOrNull() ?: return
        Notifications.show(
            this,
            title = json.optString("title", getString(R.string.app_name)),
            body = json.optString("body"),
            incident = if (json.isNull("incident")) null else json.optInt("incident"),
            alert = json.optString("kind") == "down",
        )
        Events.emit()
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Store(this).pushProblem = when (reason) {
            FailedReason.VAPID_REQUIRED -> "The distributor requires VAPID, and the monitor did not provide a key."
            else -> "Push registration failed ($reason). Open your UnifiedPush distributor, then retry here."
        }
        Events.emit()
    }

    override fun onUnregistered(instance: String) {
        val store = Store(this)
        store.endpoint = null
        store.endpointSent = false
        store.pushProblem = "Alerts are off: the distributor unregistered this app."
        Events.emit()
    }
}

object Notifications {
    private const val ALERTS = "alerts"
    private const val UPDATES = "updates"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.channel_alerts_description) }
        )
        nm.createNotificationChannel(
            NotificationChannel(UPDATES, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_updates_description) }
        )
    }

    fun show(context: Context, title: String, body: String, incident: Int?, alert: Boolean) {
        createChannels(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            incident?.let { putExtra(EXTRA_INCIDENT, it) }
        }
        val pending = PendingIntent.getActivity(
            context, incident ?: 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = android.app.Notification.Builder(context, if (alert) ALERTS else UPDATES)
            .setSmallIcon(R.drawable.ic_stat_scan)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull().orEmpty())
            .setStyle(android.app.Notification.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(if (alert) android.app.Notification.CATEGORY_ERROR else android.app.Notification.CATEGORY_STATUS)
            .setColor(if (alert) 0xFFD9473F.toInt() else 0xFF2E9E6B.toInt())
            .build()
        val nm = context.getSystemService(NotificationManager::class.java)
        // One notification per incident, and one for news without an incident:
        // updates replace the earlier one instead of stacking.
        nm.notify(incident ?: 0, notification)
    }
}
