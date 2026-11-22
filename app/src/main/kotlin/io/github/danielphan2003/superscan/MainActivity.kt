package io.github.danielphan2003.superscan

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import io.github.danielphan2003.superscan.ui.SuperscanApp
import io.github.danielphan2003.superscan.ui.SuperscanTheme

class MainActivity : ComponentActivity() {
    /** The incident a tapped notification asked for; the UI clears it once shown. */
    private val requestedIncident = mutableStateOf<Int?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.createChannels(this)
        requestedIncident.value = incidentFrom(intent)
        setContent {
            SuperscanTheme {
                SuperscanApp(activity = this, requestedIncident = requestedIncident)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        incidentFrom(intent)?.let { requestedIncident.value = it }
    }

    private fun incidentFrom(intent: Intent?): Int? =
        intent?.takeIf { it.hasExtra(EXTRA_INCIDENT) }?.getIntExtra(EXTRA_INCIDENT, 0)
}
