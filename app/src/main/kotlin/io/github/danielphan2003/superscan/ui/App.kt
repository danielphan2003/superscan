package io.github.danielphan2003.superscan.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.danielphan2003.superscan.Api
import io.github.danielphan2003.superscan.Events
import io.github.danielphan2003.superscan.R
import io.github.danielphan2003.superscan.Store
import io.github.danielphan2003.superscan.registerPush
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush

/** Where the user is: a small back stack instead of a navigation library. */
sealed interface Route {
    data object Home : Route
    data object Incidents : Route
    data class Incident(val id: Int) : Route
    data class Check(val id: String) : Route
}

@Composable
fun SuperscanApp(activity: ComponentActivity, requestedIncident: MutableState<Int?>) {
    val context = LocalContext.current
    val store = remember { Store(context) }
    var paired by remember { mutableStateOf(store.paired) }
    val stack = remember { mutableStateListOf<Route>(Route.Home) }

    // A tapped notification asks for its incident; open it on top of whatever is shown.
    val requested = requestedIncident.value
    LaunchedEffect(requested) {
        if (requested != null) {
            requestedIncident.value = null
            if (stack.lastOrNull() != Route.Incident(requested)) stack.add(Route.Incident(requested))
        }
    }

    if (!paired) {
        SetupScreen(activity, onPaired = { paired = true })
        return
    }
    if (stack.size > 1) BackHandler { stack.removeAt(stack.lastIndex) }
    val back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    MonitorData(store) { data ->
        when (val route = stack.last()) {
            Route.Home -> HomeScreen(activity, store, data, navigate = { stack.add(it) }, onUnpaired = { paired = false; stack.clear(); stack.add(Route.Home) })
            Route.Incidents -> IncidentsScreen(data, onBack = back, open = { stack.add(Route.Incident(it)) })
            is Route.Incident -> IncidentScreen(data, route.id, onBack = back, openCheck = { stack.add(Route.Check(it)) })
            is Route.Check -> CheckScreen(data, route.id, onBack = back, openIncident = { stack.add(Route.Incident(it)) })
        }
    }
}

/** Picks the distributor (asking only if there are several) and registers. */
fun connectPush(activity: ComponentActivity, store: Store, vapid: String?) {
    UnifiedPush.tryUseCurrentOrDefaultDistributor(activity) { success ->
        if (success) {
            store.pushProblem = null
            registerPush(activity, vapid)
        } else {
            store.pushProblem = "No UnifiedPush distributor found. Install one, such as Sunup or ntfy, then tap Retry."
        }
        Events.emit()
    }
}

// --- setup ---------------------------------------------------------------------

@Composable
private fun SetupScreen(activity: ComponentActivity, onPaired: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf("https://") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val distributors = remember { UnifiedPush.getDistributors(context).filter { it != context.packageName } }
    // Debug builds may also talk to a monitor on the emulator's host (wrangler dev).
    val secure = server.startsWith("https://") || (io.github.danielphan2003.superscan.BuildConfig.DEBUG && server.startsWith("http://10.0.2.2"))
    val ready = !busy && secure && server.length > 9 && code.replace("-", "").length >= 6

    fun pair() {
        busy = true; error = null
        scope.launch {
            try {
                val (name, vapid) = Api(server).info()
                val token = Api(server).pair(code)
                val store = Store(context)
                store.server = server.trimEnd('/')
                store.token = token
                store.monitorName = name
                store.endpointSent = false
                connectPush(activity, store, vapid)
                onPaired()
            } catch (e: Exception) {
                error = e.message ?: "Pairing failed"
            } finally {
                busy = false
            }
        }
    }

    // Ask for notifications before pairing: the monitor confirms with a push
    // right away, and Android drops it while the permission is still undecided.
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { pair() }
    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) pair()
        else permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Scaffold { padding ->
        Column(
            Modifier.padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                Modifier.size(88.dp).clip(CircleShape).background(colorResource(R.color.ic_launcher_background)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(132.dp))
            }
            Text("Superscan", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Your monitor's checks, incidents and alerts, delivered through UnifiedPush.",
                style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (distributors.isNotEmpty()) Icons.Filled.CheckCircle else Icons.Filled.Info, null,
                        tint = if (distributors.isNotEmpty()) StatusColors.ok else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        if (distributors.isNotEmpty()) "Alerts will come through ${distributorName(context, distributors.first())}."
                        else "Install a UnifiedPush distributor such as Sunup or ntfy to get alerts. You can pair now and add one later.",
                        Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            OutlinedTextField(
                value = server, onValueChange = { server = it.trim() }, label = { Text("Monitor address") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            OutlinedTextField(
                value = code, onValueChange = { code = it.uppercase().take(12) }, label = { Text("Pairing code") },
                supportingText = { Text("A one-time code from whoever runs the monitor, like ABCD-2345.") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ready) start() }),
            )
            error?.let {
                ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Text("Couldn't pair: $it", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
            Button(onClick = { start() }, enabled = ready, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (busy) "Pairing…" else "Pair")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun distributorName(context: android.content.Context, pkg: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)
