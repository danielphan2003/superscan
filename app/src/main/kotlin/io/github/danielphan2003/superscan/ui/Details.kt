package io.github.danielphan2003.superscan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.danielphan2003.superscan.Incident

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(title: String, onBack: () -> Unit, data: MonitorState, subtitle: String? = null, content: LazyListScope.() -> Unit) {
    val listState = rememberLazyListState()
    val scrolled by remember(listState) {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 }
    }
    Scaffold(topBar = {
        InstrumentHeader(title, subtitle, scrolled = scrolled, onBack = onBack)
    }) { padding ->
        PullToRefreshBox(isRefreshing = data.refreshing && data.status != null, onRefresh = data.refresh, modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(), state = listState,
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                content = content,
            )
        }
    }
}

@Composable
fun IncidentsScreen(data: MonitorState, onBack: () -> Unit, open: (Int) -> Unit) {
    val incidents = data.status?.incidents.orEmpty()
    DetailScaffold("Incidents", onBack, data) {
        val ongoing = incidents.filter { it.ended == null }
        val past = incidents.filter { it.ended != null }
        if (incidents.isEmpty()) item {
            if (data.status == null) QuietProgress()
            else Text("No incidents in 30 days", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (ongoing.isNotEmpty()) item {
            SectionHeader("Ongoing", top = 0.dp)
            IncidentList(ongoing, data, open)
        }
        if (past.isNotEmpty()) item {
            SectionHeader("Resolved", top = if (ongoing.isEmpty()) 0.dp else 24.dp)
            IncidentList(past, data, open)
        }
    }
}

@Composable
private fun IncidentList(list: List<Incident>, data: MonitorState, open: (Int) -> Unit) {
    FlatCard {
        list.forEachIndexed { i, inc ->
            if (i > 0) InsetDivider()
            IncidentRow(inc, data.now) { open(inc.id) }
        }
    }
}

@Composable
fun IncidentScreen(data: MonitorState, id: Int, onBack: () -> Unit, openCheck: (String) -> Unit) {
    val inc = data.status?.incidents?.firstOrNull { it.id == id }
    DetailScaffold("Incident #$id", onBack, data) {
        if (inc == null) {
            item {
                if (data.status == null) QuietProgress()
                else Text("This incident is no longer listed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@DetailScaffold
        }
        val ongoing = inc.ended == null
        item {
            StatusPill(if (ongoing) "Ongoing" else "Resolved", if (ongoing) "down" else "ok")
            Text(inc.title, Modifier.padding(top = 12.dp, bottom = 20.dp), style = MaterialTheme.typography.headlineSmall)
            FlatCard {
                Column(Modifier.padding(16.dp)) {
                    Text(tabularNumbers("Started ${clock(inc.started)}"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = 12.dp)) {
                        StatTile(if (ongoing) "ongoing" else "duration", duration(inc.started, inc.ended), Modifier.weight(1f))
                        StatTile("affected checks", inc.checks.size.toString(), Modifier.weight(1f))
                    }
                }
            }
        }
        item {
            SectionHeader("Affected checks")
            val byName = data.status?.checks.orEmpty().associateBy { it.name }
            FlatCard {
                inc.checks.forEachIndexed { i, name ->
                    if (i > 0) InsetDivider()
                    val check = byName[name]
                    Row(
                        Modifier.fillMaxWidth().let { m -> check?.let { c -> m.clickable { openCheck(c.id) } } ?: m }
                            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusDot(statusColor(check?.status ?: "unknown"))
                        Text(name, Modifier.padding(start = 16.dp, end = 8.dp).weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${statusWord(check?.status ?: "unknown")} now", style = MaterialTheme.typography.bodySmall, color = statusColor(check?.status ?: "unknown"))
                        if (check != null) Icon(Icons.Rounded.ChevronRight, null, Modifier.padding(start = 8.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (inc.notes.isNotEmpty()) item {
            SectionHeader("Timeline")
            FlatCard {
                Column(Modifier.padding(vertical = 16.dp)) {
                    inc.notes.forEachIndexed { i, n ->
                        if (i == 0 || calendarDate(n.at) != calendarDate(inc.notes[i - 1].at)) {
                            Text(dateLabel(n.at), Modifier.padding(start = 16.dp, top = if (i == 0) 0.dp else 8.dp, bottom = 12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TimelineEntry(n.at, n.text, first = i == 0, last = i == inc.notes.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineEntry(at: String, text: String, first: Boolean, last: Boolean) {
    val color = when {
        text.startsWith("First failure:") || text.startsWith("Also down:") -> StatusColors.down
        text.startsWith("Alerted:") -> StatusColors.warn
        text.startsWith("Back:") || text.startsWith("Resolved after") -> StatusColors.ok
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val line = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp)) {
        Text(tabularNumbers(clock(at).substringAfterLast(", ")), Modifier.width(48.dp).padding(top = 1.dp).semantics { contentDescription = clock(at) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.width(24.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(1.dp).height(6.dp).background(if (first) Color.Transparent else line))
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Box(Modifier.width(1.dp).weight(1f).background(if (last) Color.Transparent else line))
        }
        Text(text, Modifier.weight(1f).padding(start = 8.dp, bottom = if (last) 0.dp else 20.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CheckScreen(data: MonitorState, id: String, onBack: () -> Unit, openIncident: (Int) -> Unit) {
    val c = data.status?.checks?.firstOrNull { it.id == id }
    DetailScaffold(c?.name ?: "Check", onBack, data, subtitle = c?.let { GROUPS[it.group] ?: it.group }) {
        if (c == null) {
            item {
                if (data.status == null) QuietProgress()
                else Text("This check is no longer listed.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@DetailScaffold
        }
        item {
            val tone = statusTone(c.status)
            FlatCard(color = tone.container, border = tone.border, radius = 24.dp) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusIcon(c.status)
                        Text(statusWord(c.status), Modifier.padding(start = 12.dp), style = MaterialTheme.typography.headlineSmall)
                    }
                    if (c.note.isNotEmpty()) Text(c.note.replaceFirstChar { it.uppercase() }, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (c.uptime == null && c.ms == null && c.avgMs == null) {
                        // Nothing measured yet: one line instead of a row of dashes.
                        Text("No data yet", Modifier.padding(top = 20.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        StatTile("uptime, 30 days", percent(c.uptime), Modifier.padding(top = 24.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                            StatTile("response now", if (c.status == "down" && c.ms == null) "No response" else millis(c.ms), Modifier.weight(1f), large = false)
                            StatTile("average response", millis(c.avgMs), Modifier.weight(1f), large = false)
                        }
                    }
                }
            }
        }
        item {
            SectionHeader("Last 30 days")
            FlatCard {
                Column(Modifier.padding(16.dp)) {
                    UptimeBars(c.days, Modifier.fillMaxWidth(), height = 32.dp)
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("30 days ago", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f))
                        Text("Today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FlowRow(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Legend(StatusColors.ok.copy(alpha = 0.48f), "No failures")
                        Legend(StatusColors.warn, "A few")
                        Legend(StatusColors.down, "Many")
                        Legend(MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), "No data", empty = true)
                    }
                }
            }
        }
        if (c.what.isNotEmpty()) item {
            SectionHeader("How it's checked")
            FlatCard { Text(c.what, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium) }
        }
        val related = data.status?.incidents.orEmpty().filter { c.name in it.checks }
        item {
            SectionHeader("Incidents")
            if (related.isEmpty()) Text("No incidents in 30 days", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else IncidentList(related, data, openIncident)
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, empty: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 8.dp, height = if (empty) 2.dp else 8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
