package io.github.danielphan2003.superscan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.danielphan2003.superscan.Incident

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, data: MonitorState, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { padding ->
        PullToRefreshBox(isRefreshing = data.refreshing && data.status != null, onRefresh = data.refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

// --- all incidents -------------------------------------------------------------

@Composable
fun IncidentsScreen(data: MonitorState, onBack: () -> Unit, open: (Int) -> Unit) {
    val incidents = data.status?.incidents.orEmpty()
    DetailScaffold("Incidents", onBack, data) {
        val ongoing = incidents.filter { it.ended == null }
        val past = incidents.filter { it.ended != null }
        if (incidents.isEmpty()) item { Text("No incidents so far.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (ongoing.isNotEmpty()) {
            item { SectionHeader("Ongoing") }
            item { IncidentList(ongoing, data, open) }
        }
        if (past.isNotEmpty()) {
            item { SectionHeader("Resolved") }
            item { IncidentList(past, data, open) }
        }
    }
}

@Composable
private fun IncidentList(list: List<Incident>, data: MonitorState, open: (Int) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        list.forEachIndexed { i, inc ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            IncidentRow(inc, data.now) { open(inc.id) }
        }
    }
}

// --- one incident --------------------------------------------------------------

@Composable
fun IncidentScreen(data: MonitorState, id: Int, onBack: () -> Unit, openCheck: (String) -> Unit) {
    val inc = data.status?.incidents?.firstOrNull { it.id == id }
    DetailScaffold("Incident #$id", onBack, data) {
        if (inc == null) {
            item { Text(if (data.status == null) "Loading…" else "This incident is no longer listed.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@DetailScaffold
        }
        val ongoing = inc.ended == null
        item {
            AssistChip(
                onClick = {},
                label = { Text(if (ongoing) "Ongoing" else "Resolved") },
                leadingIcon = { StatusDot(if (ongoing) StatusColors.down else StatusColors.ok) },
                colors = AssistChipDefaults.assistChipColors(),
            )
        }
        item { Text(inc.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp)) {
                    StatTile("started", clock(inc.started), Modifier.weight(1f))
                    StatTile(if (ongoing) "so far" else "lasted", duration(inc.started, inc.ended), Modifier.weight(1f))
                    StatTile("checks", inc.checks.size.toString(), Modifier.weight(1f))
                }
            }
        }
        item { SectionHeader("Affected") }
        item {
            val byName = data.status?.checks.orEmpty().associateBy { it.name }
            ElevatedCard(Modifier.fillMaxWidth()) {
                inc.checks.forEachIndexed { i, name ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val check = byName[name]
                    Row(
                        Modifier.fillMaxWidth().let { m -> check?.let { c -> m.clickable { openCheck(c.id) } } ?: m }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(statusColor(check?.status ?: "unknown"))
                        Text(name, Modifier.padding(start = 14.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Text(statusWord(check?.status ?: "unknown") + " now", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item { SectionHeader("Timeline") }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    inc.notes.forEachIndexed { i, n -> TimelineEntry(clock(n.at), n.text, first = i == 0, last = i == inc.notes.lastIndex) }
                }
            }
        }
    }
}

@Composable
private fun TimelineEntry(time: String, text: String, first: Boolean, last: Boolean) {
    val line = MaterialTheme.colorScheme.outlineVariant
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp)) {
        Box(Modifier.width(16.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(2.dp).height(10.dp).background(if (first) MaterialTheme.colorScheme.surface.copy(alpha = 0f) else line))
                Box(Modifier.size(10.dp).background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape))
                Box(Modifier.width(2.dp).weight(1f).background(if (last) MaterialTheme.colorScheme.surface.copy(alpha = 0f) else line))
            }
        }
        Column(Modifier.padding(start = 12.dp, top = 4.dp, bottom = 14.dp)) {
            Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// --- one check -----------------------------------------------------------------

@Composable
fun CheckScreen(data: MonitorState, id: String, onBack: () -> Unit, openIncident: (Int) -> Unit) {
    val c = data.status?.checks?.firstOrNull { it.id == id }
    DetailScaffold(c?.name ?: "Check", onBack, data) {
        if (c == null) {
            item { Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            return@DetailScaffold
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(statusColor(c.status), 14.dp)
                Text(statusWord(c.status), Modifier.padding(start = 10.dp), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("  ·  ${GROUPS[c.group] ?: c.group}", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (c.note.isNotEmpty()) item { Text(c.note.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp)) {
                    StatTile("uptime, 30 days", percent(c.uptime), Modifier.weight(1f))
                    StatTile("response now", millis(c.ms), Modifier.weight(1f))
                    StatTile("average", millis(c.avgMs), Modifier.weight(1f))
                }
            }
        }
        item { SectionHeader("Last 30 days") }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    UptimeBars(c.days, Modifier.fillMaxWidth(), height = 36.dp, gap = 3.dp)
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        Text("30 days ago", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("Today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Legend(StatusColors.ok, "no failures"); Legend(StatusColors.warn, "a few"); Legend(StatusColors.down, "many"); Legend(MaterialTheme.colorScheme.surfaceVariant, "no data")
                    }
                }
            }
        }
        if (c.what.isNotEmpty()) {
            item { SectionHeader("How it's checked") }
            item {
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Text(c.what, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        val related = data.status?.incidents.orEmpty().filter { c.name in it.checks }
        item { SectionHeader("Incidents") }
        item {
            ElevatedCard(Modifier.fillMaxWidth()) {
                if (related.isEmpty()) Text("None involving ${c.name}.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                related.forEachIndexed { i, inc ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    IncidentRow(inc, data.now) { openIncident(inc.id) }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
        Text(label, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
