package io.github.danielphan2003.superscan.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.danielphan2003.superscan.Check
import io.github.danielphan2003.superscan.Day
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

@Composable
fun statusColor(status: String): Color = when (status) {
    "ok" -> StatusColors.ok
    "warn" -> StatusColors.warn
    "down" -> StatusColors.down
    else -> StatusColors.unknown
}

fun statusWord(status: String) = when (status) {
    "ok" -> "Up"
    "warn" -> "Recovering"
    "down" -> "Down"
    else -> "Waiting"
}

@Composable
fun StatusDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** Flat grouped surfaces, with no elevation or shadow. */
@Composable
fun FlatCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    border: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
    radius: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(radius), color = color, border = BorderStroke(1.dp, border)) {
        Column(content = content)
    }
}

@Composable
fun InsetDivider(start: Dp = 40.dp) {
    HorizontalDivider(Modifier.padding(start = start, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
}

@Composable
fun InstrumentHeader(
    title: String,
    subtitle: String? = null,
    scrolled: Boolean = false,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val background by animateColorAsState(
        if (scrolled) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface,
        tween(200), label = "header surface",
    )
    Column(Modifier.background(background).windowInsetsPadding(WindowInsets.statusBars)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = if (subtitle == null) 64.dp else 76.dp)
                .padding(start = if (onBack == null) 20.dp else 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", Modifier.size(24.dp)) } }
            Column(Modifier.weight(1f).padding(vertical = 10.dp, horizontal = if (onBack == null) 0.dp else 4.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            actions()
        }
        HorizontalDivider(color = if (scrolled) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f) else Color.Transparent)
    }
}

/** One rounded bar per UTC day. Unmeasured days are baseline ticks. */
@Composable
fun UptimeBars(days: List<Day>, modifier: Modifier = Modifier, height: Dp = 7.dp, gap: Dp = 2.dp) {
    val ok = StatusColors.ok.copy(alpha = 0.48f)
    val warn = StatusColors.warn
    val down = StatusColors.down
    val empty = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    val ran = days.count { it.ok + it.fail > 0 }
    val bad = days.count { it.fail > 0 }
    Canvas(modifier.height(height).semantics { contentDescription = "$ran days with checks, $bad with failures" }) {
        if (days.isEmpty()) return@Canvas
        val gapPx = gap.toPx()
        val barWidth = ((size.width - gapPx * (days.size - 1)) / days.size).coerceAtLeast(0f)
        days.forEachIndexed { i, d ->
            val total = d.ok + d.fail
            val noData = total == 0
            val color = when {
                noData -> empty
                d.fail == 0 -> ok
                d.fail.toDouble() / total < 0.02 -> warn
                else -> down
            }
            val h = if (noData) 2.dp.toPx() else size.height
            drawRoundRect(color, Offset(i * (barWidth + gapPx), size.height - h), Size(barWidth, h), CornerRadius(2.dp.toPx()))
        }
    }
}

@Composable
fun SectionHeader(text: String, top: Dp = 24.dp, trailing: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = top, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, large: Boolean = true) {
    Column(modifier) {
        Text(value, style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge)
        Text(label, Modifier.padding(top = 2.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatusPill(text: String, status: String, neutralWhenOk: Boolean = false) {
    val tone = statusTone(status)
    val neutral = neutralWhenOk && status == "ok"
    Surface(
        color = if (neutral) MaterialTheme.colorScheme.surfaceContainerHigh else tone.container,
        contentColor = if (neutral) MaterialTheme.colorScheme.onSurfaceVariant else tone.foreground,
        shape = RoundedCornerShape(8.dp),
    ) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun QuietProgress() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

/** Unmeasured checks do not signal an outage. */
fun worst(checks: List<Check>): String = when {
    checks.any { it.status == "down" } -> "down"
    checks.any { it.status == "warn" } -> "warn"
    checks.all { it.status == "unknown" } -> "unknown"
    else -> "ok"
}

val Check.healthy: Boolean get() = status == "ok" || status == "unknown"
