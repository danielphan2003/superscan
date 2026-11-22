package io.github.danielphan2003.superscan.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.danielphan2003.superscan.Check
import io.github.danielphan2003.superscan.Day
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// --- time ----------------------------------------------------------------------

private val zone = ZoneId.systemDefault()
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
private val dayTimeFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(zone)

fun parse(iso: String?): Instant? = iso?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** "21:04" today, "3 Nov, 21:04" otherwise. */
fun clock(iso: String?): String {
    val t = parse(iso) ?: return ""
    val today = Instant.now().atZone(zone).toLocalDate()
    return if (t.atZone(zone).toLocalDate() == today) timeFormat.format(t) else dayTimeFormat.format(t)
}

/** "just now", "4 min ago", "2 h ago", "yesterday", "5 days ago". */
fun ago(iso: String?, now: Instant = Instant.now()): String {
    val t = parse(iso) ?: return "never"
    val min = Duration.between(t, now).toMinutes()
    return when {
        min < 1 -> "just now"
        min < 60 -> "$min min ago"
        min < 24 * 60 -> "${min / 60} h ago"
        min < 48 * 60 -> "yesterday"
        else -> "${min / 1440} days ago"
    }
}

fun duration(from: String?, to: String?): String {
    val a = parse(from) ?: return ""
    val b = parse(to) ?: Instant.now()
    val min = Duration.between(a, b).toMinutes().coerceAtLeast(1)
    return when {
        min < 60 -> "$min min"
        min < 24 * 60 -> if (min % 60 == 0L) "${min / 60} h" else "${min / 60} h ${min % 60} min"
        else -> "${min / 1440} d ${(min % 1440) / 60} h"
    }
}

fun percent(p: Double?): String = when {
    p == null -> "–"
    p >= 99.995 -> "100%"
    p >= 99.0 -> "%.2f%%".format(p)
    else -> "%.1f%%".format(p)
}

fun millis(ms: Int?): String = ms?.let { if (it < 1000) "$it ms" else "%.1f s".format(it / 1000.0) } ?: "–"

// --- status colors -------------------------------------------------------------

fun statusColor(status: String): Color = when (status) {
    "ok" -> StatusColors.ok
    "warn" -> StatusColors.warn
    "down" -> StatusColors.down
    else -> StatusColors.unknown
}

fun statusWord(status: String): String = when (status) {
    "ok" -> "Up"
    "warn" -> "Recovering"
    "down" -> "Down"
    else -> "Waiting"
}

@Composable
fun StatusDot(color: Color, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

// --- uptime bars ---------------------------------------------------------------

private fun dayColor(d: Day, empty: Color): Color {
    val total = d.ok + d.fail
    if (total == 0) return empty
    val failShare = d.fail.toDouble() / total
    return when {
        d.fail == 0 -> StatusColors.ok
        failShare < 0.02 -> StatusColors.warn
        else -> StatusColors.down
    }
}

/** One bar per day, oldest on the left; grey where there are no runs yet. */
@Composable
fun UptimeBars(days: List<Day>, modifier: Modifier = Modifier, height: Dp = 16.dp, gap: Dp = 2.dp) {
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val ran = days.count { it.ok + it.fail > 0 }
    val bad = days.count { it.fail > 0 }
    Row(
        modifier.height(height).semantics { contentDescription = "$ran days with checks, $bad with failures" },
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        days.forEach { d ->
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(dayColor(d, empty)))
        }
    }
}

// --- small building blocks -----------------------------------------------------

@Composable
fun SectionHeader(text: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(top = 12.dp, start = 4.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The worst status among checks: down > warn > ok. A check that has not run yet does not count, unless none has. */
fun worst(checks: List<Check>): String = when {
    checks.any { it.status == "down" } -> "down"
    checks.any { it.status == "warn" } -> "warn"
    checks.all { it.status == "unknown" } -> "unknown"
    else -> "ok"
}

/** Up, or not run yet: neither needs attention. */
val Check.healthy: Boolean get() = status == "ok" || status == "unknown"
