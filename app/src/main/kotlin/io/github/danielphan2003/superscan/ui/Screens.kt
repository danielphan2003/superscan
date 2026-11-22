package io.github.danielphan2003.superscan.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.danielphan2003.superscan.Api
import io.github.danielphan2003.superscan.Events
import io.github.danielphan2003.superscan.Incident
import io.github.danielphan2003.superscan.Status
import io.github.danielphan2003.superscan.Store
import io.github.danielphan2003.superscan.registerPush
import io.github.danielphan2003.superscan.syncEndpoint
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.UnifiedPush
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// --- formatting ----------------------------------------------------------------

private val whenFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault())
private fun local(iso: String?): String = iso?.let { runCatching { whenFormat.format(Instant.parse(it)) }.getOrNull() }.orEmpty()
private fun duration(from: String, to: String): String {
    val min = runCatching { Duration.between(Instant.parse(from), Instant.parse(to)).toMinutes() }.getOrDefault(0).coerceAtLeast(1)
    return if (min < 60) "$min min" else if (min % 60 == 0L) "${min / 60} h" else "${min / 60} h ${min % 60} min"
}
private fun statusColor(status: String): Color = when (status) {
    "ok" -> StatusColors.ok
    "warn" -> StatusColors.warn
    "down" -> StatusColors.down
    else -> StatusColors.unknown
}
private val groupLabels = linkedMapOf(
    "site" to "Websites",
    "gated" to "Websites behind the login",
    "mail" to "Mail",
    "monitor" to "Monitor",
)

// --- app -----------------------------------------------------------------------

@Composable
fun SuperscanApp(activity: ComponentActivity, requestedIncident: MutableState<Int?>) {
    val context = LocalContext.current
    val store = remember { Store(context) }
    var paired by remember { mutableStateOf(store.paired) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(paired) {
        if (paired && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    if (!paired) {
        SetupScreen(activity, onPaired = { paired = true })
    } else {
        MainScreen(activity, store, requestedIncident, onUnpaired = { paired = false })
    }
}

/** Picks the distributor (asking only if there are several) and registers. */
private fun connectPush(activity: ComponentActivity, store: Store, vapid: String?) {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupScreen(activity: ComponentActivity, onPaired: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf("https://") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { LargeTopAppBar(title = { Text("Superscan") }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(horizontal = 16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Pair with your monitor to see its checks and incidents, and to get alerts through your UnifiedPush distributor.",
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedTextField(
                value = server, onValueChange = { server = it.trim() }, label = { Text("Monitor address") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = code, onValueChange = { code = it.uppercase() }, label = { Text("Pairing code") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            )
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                enabled = !busy && server.startsWith("https://") && server.length > 8 && code.isNotBlank(),
                onClick = {
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
                            error = "Pairing failed: ${e.message}"
                        } finally {
                            busy = false
                        }
                    }
                },
            ) { Text(if (busy) "Pairing…" else "Pair") }
        }
    }
}

// --- main ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(
    activity: ComponentActivity,
    store: Store,
    requestedIncident: MutableState<Int?>,
    onUnpaired: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val changed by Events.changed.collectAsState()
    var status by remember { mutableStateOf<Status?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var openIncident by remember { mutableStateOf<Int?>(null) }
    var menu by remember { mutableStateOf(false) }
    var confirmUnpair by remember { mutableStateOf(false) }
    val api = remember(store.server, store.token) { Api(store.server!!, store.token) }

    suspend fun load() {
        refreshing = true
        try {
            status = api.status(); error = null
        } catch (e: Exception) {
            error = "Could not reach the monitor: ${e.message}"
        } finally {
            refreshing = false
        }
    }

    LaunchedEffect(changed) { load(); syncEndpoint(context) }
    LaunchedEffect(requestedIncident.value) {
        requestedIncident.value?.let { openIncident = it; requestedIncident.value = null }
    }

    val incident = openIncident?.let { id -> status?.incidents?.firstOrNull { it.id == id } }
    if (openIncident != null) {
        BackHandler { openIncident = null }
        IncidentScreen(incident, status?.now, onBack = { openIncident = null })
        return
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { Text(store.monitorName ?: "Superscan") },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(onClick = { scope.launch { load() } }) { Icon(Icons.Filled.Refresh, "Refresh") }
                    IconButton(onClick = {
                        scope.launch {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(api.webLink())))
                            } catch (e: Exception) {
                                snackbar.showSnackbar("Could not open the status page: ${e.message}")
                            }
                        }
                    }) { Icon(Icons.Filled.OpenInBrowser, "Open the status page") }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Re-register alerts") }, onClick = {
                            menu = false
                            scope.launch {
                                val vapid = runCatching { Api(store.server!!).info().second }.getOrNull()
                                store.endpointSent = false
                                connectPush(activity, store, vapid)
                            }
                        })
                        DropdownMenuItem(text = { Text("Unpair") }, onClick = { menu = false; confirmUnpair = true })
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { scope.launch { load() } },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                store.pushProblem?.let { problem ->
                    item {
                        NoticeCard(Icons.Filled.NotificationsOff, problem, action = "Retry") {
                            scope.launch {
                                val vapid = runCatching { Api(store.server!!).info().second }.getOrNull()
                                store.endpointSent = false
                                connectPush(activity, store, vapid)
                            }
                        }
                    }
                }
                error?.let { item { NoticeCard(Icons.Filled.ErrorOutline, it) } }
                status?.let { s ->
                    item { OverallCard(s) { openIncident = it } }
                    for ((group, label) in groupLabels) {
                        val checks = s.checks.filter { it.group == group }
                        if (checks.isEmpty()) continue
                        item { SectionHeader(label) }
                        item {
                            ElevatedCard(Modifier.fillMaxWidth()) {
                                checks.forEachIndexed { i, c ->
                                    if (i > 0) Spacer(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                                    CheckRow(c.name, c.note, statusColor(c.status))
                                }
                            }
                        }
                    }
                    item { SectionHeader("Incidents") }
                    if (s.incidents.isEmpty()) item { Text("None yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(s.incidents, key = { it.id }) { inc -> IncidentCard(inc, s.now) { openIncident = inc.id } }
                }
            }
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Unpair?") },
            text = { Text("This device stops getting alerts and forgets the monitor. Pairing again needs a new code.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    scope.launch {
                        runCatching { api.unpair() }
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
private fun SectionHeader(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun NoticeCard(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, action: String? = null, onAction: () -> Unit = {}) {
    ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Text(text, Modifier.weight(1f).padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            action?.let { TextButton(onClick = onAction) { Text(it) } }
        }
    }
}

@Composable
private fun OverallCard(s: Status, onOpen: (Int) -> Unit) {
    val open = s.incidents.firstOrNull { it.ended == null }
    val down = s.checks.count { it.status == "down" }
    val good = open == null && down == 0
    ElevatedCard(
        Modifier.fillMaxWidth().let { m -> open?.let { o -> m.clickable { onOpen(o.id) } } ?: m },
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (good) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
        ),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (good) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null,
                modifier = Modifier.size(40.dp), tint = if (good) StatusColors.ok else StatusColors.down,
            )
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    if (good) "All good" else open?.title ?: "$down down",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                )
                Text(
                    open?.let { "Since ${local(it.started)} · tap for the timeline" } ?: "${s.checks.size} checks · updated ${local(s.now)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CheckRow(name: String, note: String, color: Color) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Column(Modifier.padding(start = 14.dp)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            if (note.isNotEmpty()) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun IncidentCard(inc: Incident, now: String, onClick: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).background(if (inc.ended == null) StatusColors.down else StatusColors.ok, CircleShape))
            Column(Modifier.padding(start = 14.dp)) {
                Text(inc.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${local(inc.started)} · " + (inc.ended?.let { "lasted ${duration(inc.started, it)}" } ?: "ongoing for ${duration(inc.started, now)}"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// --- one incident --------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncidentScreen(inc: Incident?, now: String?, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(inc?.let { "Incident #${it.id}" } ?: "Incident") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { padding ->
        if (inc == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading, or no longer listed.") }
            return@Scaffold
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text(inc.title, style = MaterialTheme.typography.headlineSmall) }
            item {
                Text(
                    "Started ${local(inc.started)} · " +
                        (inc.ended?.let { "resolved ${local(it)}, after ${duration(inc.started, it)}" } ?: "ongoing for ${duration(inc.started, now ?: inc.started)}"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item { SectionHeader("Checks involved") }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    inc.checks.forEach { CheckRow(it, "", if (inc.ended == null) StatusColors.down else StatusColors.ok) }
                }
            }
            item { SectionHeader("Timeline") }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        inc.notes.forEach { n ->
                            Row {
                                Text(local(n.at), Modifier.width(110.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(n.text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
