package io.github.danielphan2003.superscan.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
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

private val RouteStackSaver = listSaver<SnapshotStateList<Route>, String>(
    save = { routes -> routes.map { route ->
        when (route) {
            Route.Home -> "home"
            Route.Incidents -> "incidents"
            is Route.Incident -> "incident:${route.id}"
            is Route.Check -> "check:${route.id}"
        }
    } },
    restore = { routes -> routes.map { value ->
        when {
            value == "incidents" -> Route.Incidents
            value.startsWith("incident:") -> Route.Incident(value.substringAfter(':').toInt())
            value.startsWith("check:") -> Route.Check(value.substringAfter(':'))
            else -> Route.Home
        }
    }.toMutableStateList() },
)

@Composable
fun SuperscanApp(activity: ComponentActivity, requestedIncident: MutableState<Int?>) {
    val context = LocalContext.current
    val store = remember { Store(context) }
    var paired by remember { mutableStateOf(store.paired) }
    val stack = rememberSaveable(saver = RouteStackSaver) { mutableStateListOf<Route>(Route.Home) }

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
    val screenState = rememberSaveableStateHolder()
    MonitorData(store) { data ->
        // Compose scales these animations with Android's animator duration setting.
        AnimatedContent(
            targetState = stack.toList(), contentKey = { it.last() }, label = "navigation",
            transitionSpec = {
                val forward = targetState.size > initialState.size
                (slideInHorizontally(tween(200)) { if (forward) it / 10 else -it / 10 } + fadeIn(tween(200)))
                    .togetherWith(slideOutHorizontally(tween(150)) { if (forward) -it / 10 else it / 10 } + fadeOut(tween(150)))
            },
        ) { destinations ->
            val route = destinations.last()
            screenState.SaveableStateProvider(route.toString()) {
                when (route) {
                    Route.Home -> HomeScreen(activity, store, data, navigate = { stack.add(it) }, onUnpaired = { paired = false; stack.clear(); stack.add(Route.Home) })
                    Route.Incidents -> IncidentsScreen(data, onBack = back, open = { stack.add(Route.Incident(it)) })
                    is Route.Incident -> IncidentScreen(data, route.id, onBack = back, openCheck = { stack.add(Route.Check(it)) })
                    is Route.Check -> CheckScreen(data, route.id, onBack = back, openIncident = { stack.add(Route.Incident(it)) })
                }
            }
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
    var server by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
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
                // Paired from here on: a push registration problem shows as a
                // notice on the home screen instead of failing the pairing.
                runCatching { connectPush(activity, store, vapid) }.onFailure {
                    store.pushProblem = "Couldn't register for alerts: ${it.message ?: it.javaClass.simpleName}"
                }
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

    Scaffold(
        bottomBar = {
            Column(Modifier.imePadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Button(onClick = { start() }, enabled = ready, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text("Pair monitor")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).consumeWindowInsets(padding).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(colorResource(R.color.ic_launcher_background)),
                contentAlignment = Alignment.Center,
            ) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(96.dp))
            }
            Text("Superscan", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.displaySmall)
            Text(
                "Your checks and incidents, with alerts delivered through UnifiedPush.",
                Modifier.padding(top = 8.dp, bottom = 32.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = server, onValueChange = { server = it.trim() }, label = { Text("Monitor address") },
                placeholder = { Text("https://monitor.example.com") }, shape = RoundedCornerShape(12.dp),
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = code, onValueChange = { code = it.uppercase().take(12) }, label = { Text("Pairing code") },
                placeholder = { Text("ABCD-2345") }, shape = RoundedCornerShape(12.dp),
                supportingText = { Text("A one-time code from your monitor.") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (ready) start() }),
            )
            val tone = statusTone(if (distributors.isNotEmpty()) "ok" else "warn")
            Row(
                Modifier.padding(top = 24.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(tone.container).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (distributors.isNotEmpty()) Icons.Rounded.CheckCircle else Icons.Rounded.Info, null, Modifier.size(18.dp), tint = tone.foreground)
                Text(
                    if (distributors.isNotEmpty()) "Alerts via ${distributorName(context, distributors.first())}"
                    else "Install Sunup or ntfy to receive alerts. You can pair now and add one later.",
                    Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let {
                Text("Couldn't pair: $it", Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

private fun distributorName(context: android.content.Context, pkg: String): String = runCatching {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
}.getOrDefault(pkg)
