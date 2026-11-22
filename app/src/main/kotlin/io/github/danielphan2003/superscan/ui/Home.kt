package io.github.danielphan2003.superscan.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.danielphan2003.superscan.Api
import io.github.danielphan2003.superscan.Check
import io.github.danielphan2003.superscan.Events
import io.github.danielphan2003.superscan.Incident
import io.github.danielphan2003.superscan.Status
import io.github.danielphan2003.superscan.Store
import io.github.danielphan2003.superscan.syncEndpoint
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush

val GROUPS = linkedMapOf(
    "site" to "Websites",
    "gated" to "Behind the login",
    "mail" to "Mail",
    "monitor" to "Monitor",
)

/** Everything the screens read: the last status, errors, and a clock for "x min ago". */
class MonitorState(
    val status: Status?,
    val error: String?,
    val refreshing: Boolean,
    val loadedAt: Instant?,
    val now: Instant,
    val api: Api,
    val refresh: () -> Unit,
)

/** Loads the monitor's status, every 30 s while the app is on screen and whenever a push arrives. */
@Composable
fun MonitorData(store: Store, content: @Composable (MonitorState) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val api = remember(store.server, store.token) { Api(store.server!!, store.token) }
    val changed by Events.changed.collectAsState()
    var status by remember { mutableStateOf<Status?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var loadedAt by remember { mutableStateOf<Instant?>(null) }
    var now by remember { mutableStateOf(Instant.now()) }

    suspend fun load() {
        refreshing = true
        try {
            status = api.status(); error = null; loadedAt = Instant.now()
        } catch (e: Exception) {
            error = e.message ?: "unreachable"
        } finally {
            refreshing = false
        }
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, changed) {
        syncEndpoint(context)
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { load(); delay(30_000) }
        }
    }
    LaunchedEffect(Unit) { while (true) { now = Instant.now(); delay(10_000) } }

    content(MonitorState(status, error, refreshing, loadedAt, now, api) { scope.launch { load() } })
}

// --- home ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    activity: ComponentActivity,
    store: Store,
    data: MonitorState,
    navigate: (Route) -> Unit,
    onUnpaired: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var confirmUnpair by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scrolled by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    var forcedOpen by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var forcedClosed by rememberSaveable { mutableStateOf(emptyList<String>()) }

    fun reRegister() = scope.launch {
        val vapid = runCatching { Api(store.server!!).info().second }.getOrNull()
        store.endpointSent = false
        connectPush(activity, store, vapid)
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            InstrumentHeader(
                title = data.status?.name ?: store.monitorName ?: "Superscan",
                subtitle = data.status?.let { "Updated ${ago(it.now, data.now)} · every minute" } ?: "Connecting to your monitor",
                scrolled = scrolled,
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More options") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Send test alert") }, onClick = {
                            menu = false
                            scope.launch {
                                val msg = runCatching { data.api.test() }.fold({ "Test alert sent. It should arrive in a few seconds." }, { "Test failed: ${it.message}" })
                                snackbar.showSnackbar(msg)
                            }
                        })
                        DropdownMenuItem(text = { Text("Open web page") }, onClick = {
                            menu = false
                            scope.launch {
                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(data.api.webLink()))) }
                                    .onFailure { snackbar.showSnackbar("Couldn't open the web page: ${it.message}") }
                            }
                        })
                        DropdownMenuItem(text = { Text("Notification settings") }, onClick = {
                            menu = false
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        })
                        DropdownMenuItem(text = { Text("Re-register alerts") }, onClick = { menu = false; reRegister() })
                        DropdownMenuItem(text = { Text("Unpair") }, onClick = { menu = false; confirmUnpair = true })
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = data.refreshing && data.status != null,
            onRefresh = data.refresh,
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                store.pushProblem?.let { problem ->
                    item(key = "push") { Notice(Icons.Rounded.NotificationsOff, problem, "Retry") { reRegister() } }
                }
                if (data.error != null) {
                    item(key = "error") {
                        Notice(
                            Icons.Rounded.WifiOff,
                            if (data.status == null) "Can't reach the monitor: ${data.error}" else "Can't reach the monitor; showing what it said ${ago(data.loadedAt?.toString(), data.now)}.",
                            "Retry", data.refresh,
                        )
                    }
                }
                val s = data.status
                if (s == null) {
                    if (data.error == null) item(key = "loading") { QuietProgress() }
                    return@LazyColumn
                }
                item(key = "hero") { HeroCard(s) { navigate(Route.Incident(it)) } }

                item(key = "checks") {
                    SectionHeader("Checks")
                    FlatCard {
                        val groups = GROUPS.entries.filter { (group, _) -> s.checks.any { it.group == group } }
                        groups.forEachIndexed { index, (group, label) ->
                            val checks = s.checks.filter { it.group == group }
                            val open = group in forcedOpen || (group !in forcedClosed && checks.any { !it.healthy })
                            if (index > 0) InsetDivider()
                            CheckGroup(label, checks, open, onToggle = {
                                if (open) { forcedClosed = forcedClosed + group; forcedOpen = forcedOpen - group }
                                else { forcedOpen = forcedOpen + group; forcedClosed = forcedClosed - group }
                            }) { navigate(Route.Check(it)) }
                        }
                    }
                }
                val recent = s.incidents.take(3)
                if (recent.isNotEmpty()) {
                    item(key = "incidents") {
                        SectionHeader("Recent incidents") {
                            if (s.incidents.size > 3) TextButton(onClick = { navigate(Route.Incidents) }) { Text("See all") }
                        }
                        FlatCard {
                            recent.forEachIndexed { i, inc ->
                                if (i > 0) InsetDivider()
                                IncidentRow(inc, data.now) { navigate(Route.Incident(inc.id)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair this device?") },
            text = { Text("It stops getting alerts and forgets the monitor. Pairing again needs a new code.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    scope.launch {
                        runCatching { data.api.unpair() }
                        UnifiedPush.unregister(context)
                        store.clear()
                        onUnpaired()
                    }
                }) { Text("Unpair") }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Notice(icon: ImageVector, text: String, action: String? = null, onAction: () -> Unit = {}) {
    val tone = statusTone("warn")
    FlatCard(Modifier.padding(bottom = 16.dp), color = tone.container, border = tone.border) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(20.dp), tint = tone.foreground)
            Text(text, Modifier.weight(1f).padding(horizontal = 12.dp), style = MaterialTheme.typography.bodyMedium)
            action?.let { TextButton(onClick = onAction) { Text(it) } }
        }
    }
}

@Composable
private fun HeroCard(s: Status, openIncident: (Int) -> Unit) {
    val open = s.incidents.firstOrNull { it.ended == null }
    val state = if (open != null) "down" else worst(s.checks)
    val (icon, title) = when (state) {
        "down" -> Icons.Rounded.Error to "Checks need attention"
        "warn" -> Icons.Rounded.Warning to "Recovering"
        "unknown" -> Icons.Rounded.Schedule to "Waiting for checks"
        else -> Icons.Rounded.CheckCircle to "All systems operational"
    }
    val waiting = s.checks.count { it.status == "unknown" }
    val tone = statusTone(state)
    val container by animateColorAsState(tone.container, tween(200), label = "status surface")
    val border by animateColorAsState(tone.border, tween(200), label = "status border")
    val foreground by animateColorAsState(tone.foreground, tween(200), label = "status icon")
    val runs = s.checks.sumOf { c -> c.days.sumOf { it.ok + it.fail } }
    val overall = if (runs == 0) null else s.checks.sumOf { c -> c.days.sumOf { it.ok } } * 100.0 / runs
    FlatCard(
        Modifier.let { m -> open?.let { o -> m.clickable(role = Role.Button) { openIncident(o.id) } } ?: m },
        color = container, border = border, radius = 24.dp,
    ) {
        Column(Modifier.padding(20.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(foreground.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(24.dp), tint = foreground)
            }
            Text(if (state == "ok" && waiting > 0) "Checks are passing" else title, Modifier.padding(top = 12.dp), style = MaterialTheme.typography.headlineSmall)
            if (open != null) {
                Text(open.title, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Since ${clock(open.started)} · ${duration(open.started, null)} ongoing", Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodySmall, color = foreground)
            } else if (s.incidents.isEmpty() && state == "ok") {
                Text("No incidents in 30 days", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (waiting > 0 && state != "unknown") {
                Text("$waiting ${if (waiting == 1) "check waiting" else "checks waiting"} for a first run", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth().padding(top = 24.dp)) {
                StatTile("healthy checks", "${s.checks.count { it.status == "ok" }} / ${s.checks.size}", Modifier.weight(1f))
                StatTile("uptime, 30 days", percent(overall), Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun IncidentRow(inc: Incident, now: Instant, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 72.dp).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (inc.ended == null) StatusColors.down else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Column(Modifier.padding(start = 16.dp, end = 8.dp).weight(1f)) {
            Text(inc.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (inc.ended == null) "Ongoing for ${duration(inc.started, null)}" else "${ago(inc.started, now)} · lasted ${duration(inc.started, inc.ended)}",
                Modifier.padding(top = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CheckGroup(label: String, checks: List<Check>, open: Boolean, onToggle: () -> Unit, openCheck: (String) -> Unit) {
    val down = checks.count { it.status == "down" }
    val recovering = checks.count { it.status == "warn" }
    val waiting = checks.count { it.status == "unknown" }
    val summary = when {
        down > 0 -> "$down down"
        recovering > 0 -> "$recovering recovering"
        waiting > 0 -> "$waiting waiting"
        else -> "${checks.size} of ${checks.size} up"
    }
    val state = worst(checks)
    val rotation by animateFloatAsState(if (open) 180f else 0f, tween(200), label = "chevron")
    Column(Modifier.animateContentSize(tween(200))) {
        Row(
            Modifier.fillMaxWidth().semantics { stateDescription = if (open) "Expanded" else "Collapsed" }.clickable(role = Role.Button, onClick = onToggle).heightIn(min = 56.dp).padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(if (state == "ok") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else statusColor(state))
            Text(label, Modifier.padding(start = 16.dp, end = 8.dp).weight(1f), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            StatusPill(summary, state, neutralWhenOk = true)
            Icon(Icons.Rounded.ExpandMore, if (open) "Collapse $label" else "Expand $label", Modifier.padding(start = 8.dp).size(20.dp).rotate(rotation), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) {
            checks.forEach { c ->
                InsetDivider()
                CheckRow(c) { openCheck(c.id) }
            }
        }
    }
}

@Composable
private fun CheckRow(c: Check, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (c.status == "ok") StatusColors.ok.copy(alpha = 0.55f) else statusColor(c.status), 6.dp)
            Text(c.name, Modifier.padding(start = 18.dp, end = 12.dp).weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when { c.status == "down" -> "Down"; c.status == "unknown" -> "Waiting"; else -> millis(c.ms) },
                Modifier.width(76.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End,
                style = MaterialTheme.typography.bodySmall, color = if (c.status == "down") StatusColors.down else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.padding(start = 24.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            UptimeBars(c.days, Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(percent(c.uptime), Modifier.width(76.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if ((c.status == "down" || c.status == "warn") && c.note.isNotBlank()) {
            Text(c.note.replaceFirstChar { it.uppercase() }, Modifier.padding(start = 24.dp, top = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
