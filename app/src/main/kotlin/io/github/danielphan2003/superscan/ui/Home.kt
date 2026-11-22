package io.github.danielphan2003.superscan.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush
import java.time.Instant

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
    val expanded = remember { mutableStateMapOf<String, Boolean>() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    fun reRegister() = scope.launch {
        val vapid = runCatching { Api(store.server!!).info().second }.getOrNull()
        store.endpointSent = false
        connectPush(activity, store, vapid)
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            MediumTopAppBar(
                title = { Text(store.monitorName ?: "Superscan", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options") }
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
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                store.pushProblem?.let { problem ->
                    item(key = "push") { Notice(Icons.Filled.NotificationsOff, problem, "Retry") { reRegister() } }
                }
                if (data.error != null) {
                    item(key = "error") {
                        Notice(
                            Icons.Filled.WifiOff,
                            if (data.status == null) "Can't reach the monitor: ${data.error}" else "Can't reach the monitor; showing what it said ${ago(data.loadedAt?.toString(), data.now)}.",
                            "Retry", data.refresh,
                        )
                    }
                }
                val s = data.status
                if (s == null) {
                    if (data.error == null) item(key = "loading") { Text("Loading…", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    return@LazyColumn
                }
                item(key = "hero") { HeroCard(s, data) { navigate(Route.Incident(it)) } }

                val recent = s.incidents.take(3)
                item(key = "incidents-header") {
                    SectionHeader("Recent incidents") {
                        if (s.incidents.isNotEmpty()) TextButton(onClick = { navigate(Route.Incidents) }) { Text("All ${s.incidents.size}") }
                    }
                }
                item(key = "incidents") {
                    if (recent.isEmpty()) {
                        Text("No incidents so far.", Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else ElevatedCard(Modifier.fillMaxWidth()) {
                        recent.forEachIndexed { i, inc ->
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            IncidentRow(inc, data.now) { navigate(Route.Incident(inc.id)) }
                        }
                    }
                }

                for ((group, label) in GROUPS) {
                    val checks = s.checks.filter { it.group == group }
                    if (checks.isEmpty()) continue
                    item(key = "group-$group") {
                        val healthy = checks.all { it.healthy }
                        val open = expanded[group] ?: !healthy
                        GroupCard(label, checks, open, onToggle = { expanded[group] = !open }) { navigate(Route.Check(it)) }
                    }
                }
                item(key = "footer") {
                    Text(
                        "Checked every minute from outside your network. Pull down to refresh.",
                        Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
    ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(text, Modifier.weight(1f).padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
            action?.let { TextButton(onClick = onAction) { Text(it) } }
        }
    }
}

@Composable
private fun HeroCard(s: Status, data: MonitorState, openIncident: (Int) -> Unit) {
    val open = s.incidents.firstOrNull { it.ended == null }
    val state = if (open != null) "down" else worst(s.checks)
    val (icon, title) = when (state) {
        "down" -> Icons.Filled.ErrorOutline to (open?.title ?: "${s.checks.count { it.status == "down" }} checks down")
        "warn" -> Icons.Filled.WarningAmber to "Recovering"
        else -> Icons.Filled.CheckCircle to "All systems operational"
    }
    val container = when (state) {
        "down" -> MaterialTheme.colorScheme.errorContainer
        "warn" -> StatusColors.warn.copy(alpha = 0.22f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
        else -> StatusColors.ok.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surfaceContainerHigh)
    }
    // Weighted by runs, so a check with a handful of runs cannot swing it.
    val runs = s.checks.sumOf { c -> c.days.sumOf { it.ok + it.fail } }
    val overall = if (runs == 0) null else s.checks.sumOf { c -> c.days.sumOf { it.ok } } * 100.0 / runs
    ElevatedCard(
        Modifier.fillMaxWidth().let { m -> open?.let { o -> m.clickable { openIncident(o.id) } } ?: m },
        colors = CardDefaults.elevatedCardColors(containerColor = container),
    ) {
        Column(Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(36.dp), tint = statusColor(state))
                Text(title, Modifier.padding(start = 14.dp), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            Row {
                StatTile("checks healthy", "${s.checks.count { it.healthy }}/${s.checks.size}", Modifier.weight(1f))
                StatTile("uptime, 30 days", percent(overall), Modifier.weight(1f))
                StatTile(if (open != null) "ongoing" else "last checked", if (open != null) duration(open.started, null) else ago(s.now, data.now), Modifier.weight(1f))
            }
            if (open != null) {
                Text(
                    "Since ${clock(open.started)} · tap for the timeline",
                    Modifier.padding(top = 10.dp), style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
fun IncidentRow(inc: Incident, now: Instant, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(if (inc.ended == null) StatusColors.down else StatusColors.ok)
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(inc.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (inc.ended == null) "Ongoing for ${duration(inc.started, null)}" else "${ago(inc.started, now)} · lasted ${duration(inc.started, inc.ended)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroupCard(label: String, checks: List<Check>, open: Boolean, onToggle: () -> Unit, openCheck: (String) -> Unit) {
    val down = checks.count { it.status == "down" }
    val recovering = checks.count { it.status == "warn" }
    val up = checks.count { it.healthy }
    val summary = when {
        down > 0 -> "$down down"
        recovering > 0 -> "$recovering recovering"
        else -> "$up of ${checks.size} up"
    }
    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(statusColor(worst(checks)), 12.dp)
            Text(label, Modifier.padding(start = 14.dp).weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(Icons.Filled.ExpandMore, if (open) "Collapse" else "Expand", Modifier.padding(start = 4.dp).rotate(rotation))
        }
        AnimatedVisibility(open) {
            Column {
                checks.forEach { c ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    CheckRow(c) { openCheck(c.id) }
                }
            }
        }
    }
}

@Composable
private fun CheckRow(c: Check, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(statusColor(c.status))
            Text(c.name, Modifier.padding(start = 14.dp).weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val trailing = when {
                c.status == "down" -> statusWord(c.status)
                c.ms != null -> millis(c.ms)
                c.status == "unknown" -> "waiting"
                else -> null
            }
            trailing?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = if (c.status == "down") StatusColors.down else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (c.days.isNotEmpty()) {
            Row(Modifier.padding(start = 24.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                UptimeBars(c.days, Modifier.weight(1f), height = 12.dp)
                Spacer(Modifier.width(10.dp))
                Text(percent(c.uptime), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (c.status == "down" || c.status == "warn") {
            Text(c.note, Modifier.padding(start = 24.dp, top = 6.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
